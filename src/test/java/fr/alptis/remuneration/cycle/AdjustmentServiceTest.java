package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.AdjustmentStatus;
import fr.alptis.remuneration.domain.AdjustmentType;
import fr.alptis.remuneration.domain.Claim;
import fr.alptis.remuneration.domain.ClaimLine;
import fr.alptis.remuneration.domain.ClaimStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.EntityParameter;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.accounts.AccountService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the adjustments and claims (section 8.5, step 9): the mandatory reason, the two-signature
 * approval with its threshold, the applied manual and transfer lines, the auto-reversal, the bulk
 * import and the claim lifecycle. Runs against UNEP, inside rolled-back transactions.
 */
@QuarkusTest
class AdjustmentServiceTest {

    @Inject
    AdjustmentService service;
    @Inject
    AccountService accounts;

    private static final LocalDate M = LocalDate.of(2026, 3, 1);
    private long seq;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period in a state. */
    private static Period period(LocalDate month) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        return period;
    }

    /** Creates a distributor account. */
    private DistributorAccount account() {
        return accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, ++seq);
    }

    /** Sets the entity's adjustment approval threshold. */
    private static void threshold(String value) {
        EntityParameter parameter = new EntityParameter();
        parameter.entity = unep();
        parameter.paramKey = "ADJUSTMENT_APPROVAL_THRESHOLD";
        parameter.paramValue = value;
        parameter.validFrom = LocalDate.of(2020, 1, 1);
        parameter.persist();
    }

    /**
     * An adjustment needs a reason; with one it is submitted.
     */
    @Test
    @TestTransaction
    void create_requires_reason() {
        Period p = period(M);
        DistributorAccount a = account();
        assertThrows(ValidationException.class, () -> service.create(unep(), p, AdjustmentType.CORRECTION,
                new BigDecimal("9"), a, " ", null, false, null, null, "op1"));
        assertThrows(ValidationException.class, () -> service.create(unep(), p, AdjustmentType.CORRECTION,
                new BigDecimal("9"), a, null, null, false, null, null, "op1"));
        Adjustment adjustment = service.create(unep(), p, AdjustmentType.CORRECTION, new BigDecimal("9"), a,
                "taux corrigé", null, false, null, null, "op1");
        assertEquals(AdjustmentStatus.SUBMITTED, adjustment.status);
    }

    /**
     * An approval requires a second, distinct user, and refuses an unknown adjustment.
     */
    @Test
    @TestTransaction
    void approval_needs_distinct_second_user() {
        Period p = period(M);
        Adjustment adjustment = service.create(unep(), p, AdjustmentType.CORRECTION, new BigDecimal("9"), account(),
                "r", null, false, null, null, "op1");
        assertThrows(ValidationException.class, () -> service.approve(adjustment.getId(), "op1", java.util.Set.of(Role.OPERATOR)));
        assertThrows(ValidationException.class, () -> service.approve(adjustment.getId(), null, java.util.Set.of(Role.OPERATOR)));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(-1L, "op2", java.util.Set.of(Role.OPERATOR)));
    }

    /**
     * Below the threshold any authorised second user approves; without a threshold the business
     * validator may approve too.
     */
    @Test
    @TestTransaction
    void approve_below_threshold_and_validator() {
        threshold("500");
        Period p = period(M);
        Adjustment small = service.create(unep(), p, AdjustmentType.CORRECTION, new BigDecimal("100"), account(),
                "r", null, false, null, null, "op1");
        assertEquals(AdjustmentStatus.APPLIED, service.approve(small.getId(), "op2", java.util.Set.of(Role.OPERATOR)).status);
    }

    /**
     * Without a threshold the business validator may approve.
     */
    @Test
    @TestTransaction
    void approve_by_validator_without_threshold() {
        Period p = period(M);
        Adjustment adjustment = service.create(unep(), p, AdjustmentType.CORRECTION, new BigDecimal("9"), account(),
                "r", null, false, null, null, "op1");
        assertEquals(AdjustmentStatus.APPLIED,
                service.approve(adjustment.getId(), "val", java.util.Set.of(Role.BUSINESS_VALIDATOR)).status);
    }

    /**
     * A second user applies the adjustment as a MANUAL line (CR reclamation example: +9,00 €).
     */
    @Test
    @TestTransaction
    void approve_applies_manual_line() {
        Period p = period(M);
        DistributorAccount a = account();
        Adjustment adjustment = service.create(unep(), p, AdjustmentType.CORRECTION, new BigDecimal("9"), a,
                "r", null, false, null, null, "op1");
        Adjustment applied = service.approve(adjustment.getId(), "op2", java.util.Set.of(Role.OPERATOR));
        assertEquals(AdjustmentStatus.APPLIED, applied.status);
        assertNotNull(applied.resultingLine);
        assertEquals(0, applied.resultingLine.amount.compareTo(new BigDecimal("9")));
        assertEquals(LineNature.MANUAL, applied.resultingLine.nature);
    }

    /**
     * Beyond the threshold, only the business validator may approve.
     */
    @Test
    @TestTransaction
    void approve_above_threshold_needs_validator() {
        threshold("500");
        Period p = period(M);
        DistributorAccount a = account();
        Adjustment adjustment = service.create(unep(), p, AdjustmentType.EXCEPTIONAL_COMMISSION,
                new BigDecimal("1000"), a, "r", null, false, null, null, "op1");
        assertThrows(ValidationException.class, () -> service.approve(adjustment.getId(), "op2", java.util.Set.of(Role.OPERATOR)));
        Adjustment applied = service.approve(adjustment.getId(), "val", java.util.Set.of(Role.BUSINESS_VALIDATOR));
        assertEquals(AdjustmentStatus.APPLIED, applied.status);
    }

    /**
     * Without a threshold, an unauthorised role cannot approve.
     */
    @Test
    @TestTransaction
    void approve_rejects_unauthorised_role() {
        Period p = period(M);
        Adjustment adjustment = service.create(unep(), p, AdjustmentType.CORRECTION, new BigDecimal("9"), account(),
                "r", null, false, null, null, "op1");
        assertThrows(ValidationException.class,
                () -> service.approve(adjustment.getId(), "fin", java.util.Set.of(Role.FINANCE_CONTROLLER)));
    }

    /**
     * An auto-reversed advance creates its reprise on the next period, created when absent and reused
     * when present.
     */
    @Test
    @TestTransaction
    void auto_reverse_on_next_period() {
        Period p = period(M);
        DistributorAccount a = account();
        Adjustment advance = service.create(unep(), p, AdjustmentType.EXCEPTIONAL_ADVANCE, new BigDecimal("1000"), a,
                "avance", null, true, null, null, "op1");
        service.approve(advance.getId(), "op2", java.util.Set.of(Role.OPERATOR));
        Period april = Period.find("entity = ?1 and periodMonth = ?2", unep(), LocalDate.of(2026, 4, 1))
                .firstResult();
        assertNotNull(april);
        assertEquals(1, Adjustment.count("period = ?1 and amount = ?2", april, new BigDecimal("-1000")));

        // A second auto-reverse reuses the existing April period.
        Adjustment advance2 = service.create(unep(), p, AdjustmentType.EXCEPTIONAL_ADVANCE, new BigDecimal("200"), a,
                "avance", null, true, null, null, "op1");
        service.approve(advance2.getId(), "op2", java.util.Set.of(Role.OPERATOR));
        assertEquals(1, Period.count("entity = ?1 and periodMonth = ?2", unep(), LocalDate.of(2026, 4, 1)));
    }

    /**
     * An account transfer writes two TRANSFER lines between the account and its counterpart.
     */
    @Test
    @TestTransaction
    void account_transfer_writes_two_lines() {
        Period p = period(M);
        DistributorAccount from = account();
        DistributorAccount to = account();
        Adjustment transfer = service.create(unep(), p, AdjustmentType.ACCOUNT_TRANSFER, new BigDecimal("300"), from,
                "transfert", null, false, null, to, "op1");
        service.approve(transfer.getId(), "op2", java.util.Set.of(Role.BUSINESS_ADMIN));
        assertEquals(0, accounts.balance(from).compareTo(new BigDecimal("-300")));
        assertEquals(0, accounts.balance(to).compareTo(new BigDecimal("300")));
    }

    /**
     * Adjustments are imported in bulk as submitted.
     */
    @Test
    @TestTransaction
    void import_bulk() {
        Period p = period(M);
        Adjustment a1 = new Adjustment();
        a1.entity = unep();
        a1.period = p;
        a1.adjustmentType = AdjustmentType.CORRECTION;
        a1.amount = new BigDecimal("5");
        a1.account = account();
        a1.reason = "r1";
        a1.setCreatedBy("op1");
        Adjustment a2 = new Adjustment();
        a2.entity = unep();
        a2.period = p;
        a2.adjustmentType = AdjustmentType.CORRECTION;
        a2.amount = new BigDecimal("7");
        a2.account = account();
        a2.reason = "r2";
        a2.setCreatedBy("op1");

        assertEquals(2, service.importBulk(List.of(a1, a2)));
        assertEquals(2, Adjustment.count("period = ?1 and status = ?2", p, AdjustmentStatus.SUBMITTED));
    }

    /**
     * A claim is recorded against its contested lines and closed with its resolution.
     */
    @Test
    @TestTransaction
    void claim_lifecycle() {
        Period p = period(M);
        Distributor d = new Distributor();
        d.partnerCode = "D1";
        d.legalName = "D1";
        d.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = p;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal("15");
        line.persist();

        Claim claim = service.recordClaim(unep(), d, "taux 15 au lieu de 18", List.of(line));
        assertEquals(ClaimStatus.OPEN, claim.status);
        assertEquals(1, ClaimLine.count("claim", claim));
        Claim closed = service.resolveClaim(claim.getId(), "ajustement +9 saisi");
        assertEquals(ClaimStatus.CLOSED, closed.status);
        assertThrows(ResourceNotFoundException.class, () -> service.resolveClaim(-1L, "x"));
        // The fiche of the claim lists its contested line; an unknown claim is a not found.
        assertEquals(1, service.claimDetail(claim.getId()).lines().size());
        assertThrows(ResourceNotFoundException.class, () -> service.claimDetail(-1L));
    }
}
