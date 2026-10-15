package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementProduct;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.rules.AgreementService.EnrollmentView;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link AgreementService}: agreements, discount conventions (PAR-09, twelve-month limit) and
 * enrollments to reserved rules (PAR-10), active only once the agreement is signed. Runs against
 * UNEP in rolled-back transactions.
 */
@QuarkusTest
class AgreementServiceTest {

    @Inject
    AuditService audit;
    @Inject
    DateTimeProvider clock;

    /** Builds a service seeing UNEP. */
    private AgreementService service() {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("admin-unep"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, List.of("UNEP"))
                .build();
        CurrentUser user = new CurrentUser(identity);
        return new AgreementService(new EntityScope(user), user, audit, clock);
    }

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Persists a distributor. */
    private static void distributor(String code) {
        Distributor d = new Distributor();
        d.partnerCode = code;
        d.legalName = code;
        d.persist();
    }

    /** Persists a reserved rule in a UNEP plan and returns it. */
    private static Rule reservedRule() {
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "RESERVED_PLAN";
        plan.label = "Réservé";
        plan.direction = Direction.INBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "RCD";
        rule.label = "RCD";
        rule.family = RuleFamily.PORTFOLIO;
        rule.persist();
        return rule;
    }

    /**
     * Records agreements and lists them; a discount convention respects the twelve-month limit.
     */
    @Test
    @TestTransaction
    void records_agreements_and_discount_conventions() {
        distributor("D1");
        AgreementService svc = service();
        svc.createAgreement("UNEP", AgreementType.PARTNERSHIP, "D1", null, "AGR-1",
                LocalDate.of(2026, 1, 1), null, true);
        svc.createAgreement("UNEP", AgreementType.RCD_AMENDMENT, null, null, "AGR-2",
                LocalDate.of(2026, 1, 1), null, false);
        assertEquals(2, svc.listAgreements().size());

        svc.createAdvanceAgreement("UNEP", "D1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 360, 50,
                (short) 2);
        // An open-ended convention (no end date), and one with no start date, skip the check.
        svc.createAdvanceAgreement("UNEP", "D1", LocalDate.of(2026, 1, 1), null, 360, 50, (short) 2);
        svc.createAdvanceAgreement("UNEP", "D1", null, LocalDate.of(2026, 12, 31), 360, 50, (short) 2);
        // An agreement with an empty partner code resolves to no distributor.
        svc.createAgreement("UNEP", AgreementType.MANDATE, "", "", "AGR-3", LocalDate.of(2026, 1, 1), null, false);
        assertEquals(3, svc.listAdvanceAgreements().size());
        assertThrows(ResourceNotFoundException.class, () -> svc.createAgreement("NOPE", AgreementType.MANDATE,
                null, null, "X", LocalDate.of(2026, 1, 1), null, false));
        assertThrows(ValidationException.class, () -> svc.createAdvanceAgreement("UNEP", "D1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1), 360, 50, (short) 2));
        assertThrows(ValidationException.class, () -> svc.createAdvanceAgreement("UNEP", "NOPE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 360, 50, (short) 2));
    }

    /**
     * The convention fiche cumulates its guarantees' advanced and offset amounts (null amounts count
     * as zero), lists its products (a null product shows a dash), and computes the outstanding debt
     * and the rupture amount; an out-of-scope entity and an unknown distributor are not found.
     */
    @Test
    @TestTransaction
    void reads_the_convention_fiche() {
        distributor("D1");
        AgreementService svc = service();
        svc.createAdvanceAgreement("UNEP", "D1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 360, 50,
                (short) 2);
        AdvanceAgreement agreement = AdvanceAgreement.<AdvanceAgreement>find("distributor.partnerCode", "D1")
                .firstResult();
        agreement.split = "\"FIFTY_FIFTY\"";

        Product product = new Product();
        product.entity = unep();
        product.code = "SANTE";
        product.persist();
        product(agreement, product);
        product(agreement, null);

        Coverage coverage = new Coverage();
        coverage.coverageCode = "CV1";
        coverage.persist();
        position(agreement, coverage, AdvanceStatus.ACTIVE, "10", "5", "2", "3", "1", "0", "1");
        position(agreement, null, null, null, null, null, null, null, null, null);

        AgreementService.AdvanceDetail detail = svc.advanceDetail("UNEP", "D1");
        assertEquals("\"FIFTY_FIFTY\"", detail.split());
        assertEquals(2, detail.products().size());
        assertEquals(2, detail.positions().size());
        assertEquals(0, detail.totalAdvanced().compareTo(new BigDecimal("17")));
        assertEquals(0, detail.totalOffset().compareTo(new BigDecimal("4")));
        // outstanding = 17 advanced − 4 offset − 1 adjustment = 12, recovered on rupture.
        assertEquals(0, detail.outstanding().compareTo(new BigDecimal("12")));
        assertEquals(0, detail.ruptureAmount().compareTo(new BigDecimal("12")));
        assertEquals("CV1", detail.positions().get(0).coverageCode());
        assertEquals("—", detail.positions().get(1).coverageCode());
        assertNull(detail.positions().get(1).status());

        // Out of scope and unknown distributor.
        assertThrows(ResourceNotFoundException.class, () -> svc.advanceDetail("ALPTIS_ASSURANCES", "D1"));
        assertThrows(ResourceNotFoundException.class, () -> svc.advanceDetail("UNEP", "NOPE"));

        // A convention with no distributor, to cover the null-partner branch of the synthesis.
        AdvanceAgreement bare = new AdvanceAgreement();
        bare.entity = unep();
        bare.persist();

        // The per-convention discount synthesis (BRD-09): the detailed convention and the bare one.
        var summary = svc.discountSummary("UNEP");
        assertEquals(2, summary.size());
        var detailed = summary.stream().filter(s -> "D1".equals(s.partnerCode())).findFirst().orElseThrow();
        assertEquals(0, detailed.outstanding().compareTo(new BigDecimal("12")));
        assertEquals(2, detailed.positions().size());
        assertEquals(1, summary.stream().filter(s -> s.partnerCode() == null).count());
        // Out of scope.
        assertThrows(ResourceNotFoundException.class, () -> svc.discountSummary("ALPTIS_ASSURANCES"));
    }

    /** Persists a product line of a convention (product may be null). */
    private static void product(AdvanceAgreement agreement, Product product) {
        AdvanceAgreementProduct link = new AdvanceAgreementProduct();
        link.advanceAgreement = agreement;
        link.product = product;
        link.persist();
    }

    /** Persists an advance position of a convention with its advanced and offset breakdown. */
    private static void position(AdvanceAgreement agreement, Coverage coverage, AdvanceStatus status,
                                 String advAcq, String advSrv, String advLin, String offAcq, String offSrv,
                                 String offLin, String adjustment) {
        AdvancePosition position = new AdvancePosition();
        position.advanceAgreement = agreement;
        position.coverage = coverage;
        position.status = status;
        position.advancedAcquisition = advAcq == null ? null : new BigDecimal(advAcq);
        position.advancedServicing = advSrv == null ? null : new BigDecimal(advSrv);
        position.advancedLinear = advLin == null ? null : new BigDecimal(advLin);
        position.offsetAcquisition = offAcq == null ? null : new BigDecimal(offAcq);
        position.offsetServicing = offSrv == null ? null : new BigDecimal(offSrv);
        position.offsetLinear = offLin == null ? null : new BigDecimal(offLin);
        position.adjustmentAmount = adjustment == null ? null : new BigDecimal(adjustment);
        position.persist();
    }

    /**
     * An enrollment is active only when its agreement is signed, pending otherwise.
     */
    @Test
    @TestTransaction
    void enrolls_according_to_signature() {
        distributor("D1");
        distributor("D2");
        DistributorGroup group = new DistributorGroup();
        group.code = "G1";
        group.label = "Groupe";
        group.persist();
        reservedRule();
        AgreementService svc = service();
        svc.createAgreement("UNEP", AgreementType.PARTNERSHIP, "D1", null, "SIGNED", LocalDate.of(2026, 1, 1),
                null, true);
        svc.createAgreement("UNEP", AgreementType.PARTNERSHIP, null, "G1", "UNSIGNED", LocalDate.of(2026, 1, 1),
                null, false);

        EnrollmentView active = svc.enroll("UNEP", "RESERVED_PLAN", "RCD", "D1", null, "SIGNED",
                LocalDate.of(2026, 1, 1));
        assertEquals("ACTIVE", active.status());
        EnrollmentView pending = svc.enroll("UNEP", "RESERVED_PLAN", "RCD", null, "G1", "UNSIGNED",
                LocalDate.of(2026, 1, 1));
        assertEquals("PENDING_SIGNATURE", pending.status());

        List<EnrollmentView> mass = svc.enrollMass("UNEP", "RESERVED_PLAN", "RCD", List.of("D1", "D2"), "SIGNED",
                LocalDate.of(2026, 1, 1));
        assertEquals(2, mass.size());
        assertEquals(4, svc.listEnrollments("UNEP", "RESERVED_PLAN", "RCD").size());
    }

    /**
     * Enrolling against an unknown agreement, or without a distributor or group, is refused.
     */
    @Test
    @TestTransaction
    void refuses_unknown_agreement_and_empty_target() {
        distributor("D1");
        reservedRule();
        AgreementService svc = service();
        svc.createAgreement("UNEP", AgreementType.PARTNERSHIP, "D1", null, "AGR", LocalDate.of(2026, 1, 1), null, true);
        assertThrows(ResourceNotFoundException.class,
                () -> svc.enroll("UNEP", "RESERVED_PLAN", "RCD", "D1", null, "NOPE", LocalDate.of(2026, 1, 1)));
        assertThrows(ValidationException.class,
                () -> svc.enroll("UNEP", "RESERVED_PLAN", "RCD", null, null, "AGR", LocalDate.of(2026, 1, 1)));
    }

    /**
     * Reads and writes are refused outside the user's entity scope.
     */
    @Test
    @TestTransaction
    void refuses_out_of_scope() {
        reservedRule();
        AgreementService svc = service();
        assertThrows(ResourceNotFoundException.class, () -> svc.createAgreement("ALPTIS_ASSURANCES",
                AgreementType.PARTNERSHIP, null, null, "X", LocalDate.of(2026, 1, 1), null, true));
        assertThrows(ResourceNotFoundException.class, () -> svc.listEnrollments("UNEP", "NOPE", "RCD"));
        assertThrows(ResourceNotFoundException.class, () -> svc.listEnrollments("UNEP", "RESERVED_PLAN", "NOPE"));
    }

    /**
     * The views tolerate sparse entities persisted with their optional fields left null.
     */
    @Test
    @TestTransaction
    void views_tolerate_sparse_entities() {
        LegalEntity unep = unep();
        fr.alptis.remuneration.domain.Agreement bareAgreement = new fr.alptis.remuneration.domain.Agreement();
        bareAgreement.entity = unep;
        bareAgreement.reference = "BARE";
        bareAgreement.persist();
        fr.alptis.remuneration.domain.AdvanceAgreement bareAdvance =
                new fr.alptis.remuneration.domain.AdvanceAgreement();
        bareAdvance.entity = unep;
        bareAdvance.persist();
        Rule rule = reservedRule();
        fr.alptis.remuneration.domain.Enrollment bareEnrollment = new fr.alptis.remuneration.domain.Enrollment();
        bareEnrollment.rule = rule;
        bareEnrollment.persist();

        AgreementService svc = service();
        assertEquals("BARE", svc.listAgreements().get(0).reference());
        assertEquals(1, svc.listAdvanceAgreements().size());
        assertEquals(1, svc.listEnrollments("UNEP", "RESERVED_PLAN", "RCD").size());
    }

    /**
     * An unrestricted user lists agreements and conventions across entities.
     */
    @Test
    @TestTransaction
    void unrestricted_lists_everything() {
        distributor("D1");
        service().createAgreement("UNEP", AgreementType.PARTNERSHIP, "D1", null, "A", LocalDate.of(2026, 1, 1),
                null, true);
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("auditor"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, List.of("*"))
                .build();
        CurrentUser user = new CurrentUser(identity);
        AgreementService all = new AgreementService(new EntityScope(user), user, audit, clock);
        assertEquals(1, all.listAgreements().size());
        all.listAdvanceAgreements();
    }
}
