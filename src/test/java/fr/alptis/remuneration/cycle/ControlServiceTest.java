package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the control catalogue (section 8.6, step 9): running the active controls over a period,
 * flagging each offending object, skipping unknown and inactive controls, and tracing the decision
 * taken on an anomaly. Runs against UNEP, inside rolled-back transactions.
 */
@QuarkusTest
class ControlServiceTest {

    @Inject
    ControlService service;
    @Inject
    AccountService accounts;

    private static final LocalDate M = LocalDate.of(2026, 3, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates the period. */
    private static Period period() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CALCULATED;
        period.persist();
        return period;
    }

    /** Creates an active control definition of a code and severity. */
    private static void control(String code, ControlSeverity severity, boolean active) {
        ControlDefinition def = new ControlDefinition();
        def.entity = unep();
        def.code = code;
        def.label = code;
        def.family = ControlFamily.DATA_QUALITY;
        def.severity = severity;
        def.active = active;
        def.persist();
    }

    /** Creates an event eligible to the period. */
    private static BusinessEvent event(Period period, EventType type, LocalDate date, String basis,
                                       ProcessingStatus status, HoldReason hold) {
        BusinessEvent e = new BusinessEvent();
        e.entity = unep();
        e.sourceSystem = "SYS";
        e.sourceKey = "K" + System.identityHashCode(new Object());
        e.eventType = type;
        e.eventDate = date;
        e.basisAmount = basis == null ? null : new BigDecimal(basis);
        e.processingStatus = status;
        e.holdReason = hold;
        e.eligiblePeriod = period;
        e.persist();
        return e;
    }

    /** Creates a coverage. */
    private static Coverage coverage(String number) {
        Product p = Product.find("code", "SANTE_CTL").firstResult();
        if (p == null) {
            p = new Product();
            p.entity = unep();
            p.code = "SANTE_CTL";
            p.label = "s";
            p.persist();
        }
        Contract c = new Contract();
        c.entity = unep();
        c.sourceSystem = "SYS";
        c.contractNumber = number;
        c.product = p;
        c.persist();
        Coverage cov = new Coverage();
        cov.contract = c;
        cov.coverageCode = number + "-C1";
        cov.effectDate = M;
        cov.persist();
        return cov;
    }

    /** Posts a commission line of a nature on a coverage. */
    private static void line(Period period, Coverage coverage, LineNature nature, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.coverage = coverage;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /** Posts a commission line on an account (no coverage). */
    private static void accountLine(Period period, DistributorAccount account, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /**
     * Each active control flags its offending objects; unknown and inactive controls do nothing; a
     * clean object raises no anomaly.
     */
    @Test
    @TestTransaction
    void flags_each_anomaly() {
        Period p = period();
        ControlDefinition.delete("entity = ?1", unep());
        control("DATA-PREMIUM", ControlSeverity.WARNING, true);
        control("DATA-FUTURE", ControlSeverity.WARNING, true);
        control("CALC-NO-RULE", ControlSeverity.BLOCKING, true);
        control("CLAWBACK-EXCESS", ControlSeverity.WARNING, true);
        control("ACCOUNT-DEBTOR", ControlSeverity.WARNING, true);
        control("FOO-UNKNOWN", ControlSeverity.INFO, true);
        control("DATA-PREMIUM", ControlSeverity.WARNING, false);
        event(p, EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 5), "0", ProcessingStatus.PROCESSED, null);
        event(p, EventType.PREMIUM_COLLECTED, LocalDate.of(2999, 1, 5), "100", ProcessingStatus.PROCESSED, null);
        event(p, EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 5), "50", ProcessingStatus.ON_HOLD,
                HoldReason.NO_RULE);
        Coverage excess = coverage("X");
        line(p, excess, LineNature.CLAWBACK, "-50");
        line(p, excess, LineNature.LINEAR, "30");
        Coverage clean = coverage("Y");
        line(p, clean, LineNature.CLAWBACK, "-20");
        line(p, clean, LineNature.LINEAR, "100");
        line(p, clean, LineNature.OUTSTANDING, "5");
        accountLine(p, accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, 777L), "-100");
        accountLine(p, accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, 888L), "40");

        int count = service.run("UNEP", M);

        assertEquals(5, count);
        assertEquals(5, ControlResult.count("period = ?1 and decision = ?2", p, ControlDecision.OPEN));
    }

    /**
     * The premium check flags a null premium and tolerates a clean one, the future check tolerates a
     * null date, the clawback check sums acquisition and servicing, and a control with no severity
     * defaults to a warning.
     */
    @Test
    @TestTransaction
    void control_edge_branches() {
        Period p = period();
        ControlDefinition.delete("entity = ?1", unep());
        control("DATA-PREMIUM", null, true);
        control("DATA-FUTURE", ControlSeverity.WARNING, true);
        control("CLAWBACK-EXCESS", ControlSeverity.WARNING, true);
        event(p, EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 5), null, ProcessingStatus.PROCESSED, null);
        event(p, EventType.PREMIUM_COLLECTED, null, "100", ProcessingStatus.PROCESSED, null);
        Coverage c = coverage("X");
        line(p, c, LineNature.ACQUISITION, "20");
        line(p, c, LineNature.SERVICING, "10");
        line(p, c, LineNature.CLAWBACK, "-40");

        assertEquals(2, service.run("UNEP", M));
        assertEquals(ControlSeverity.WARNING,
                ControlResult.<ControlResult>find("period = ?1 and objectType = ?2", p, "EVENT").firstResult().severity);
    }

    /**
     * A decision is traced on an anomaly; a rerun replaces the open anomalies but keeps the decided one.
     */
    @Test
    @TestTransaction
    void decide_and_rerun() {
        Period p = period();
        ControlDefinition.delete("entity = ?1", unep());
        control("ACCOUNT-DEBTOR", ControlSeverity.WARNING, true);
        DistributorAccount debtor = accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, 777L);
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = p;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal("-100");
        line.account = debtor;
        line.persist();

        service.run("UNEP", M);
        ControlResult anomaly = ControlResult.<ControlResult>find("period", p).firstResult();
        service.decide(anomaly.getId(), ControlDecision.ACCEPTED, "op", "solde repris");
        assertEquals(ControlDecision.ACCEPTED,
                ControlResult.<ControlResult>findById(anomaly.getId()).decision);

        service.run("UNEP", M);
        assertEquals(1, ControlResult.count("period = ?1 and decision = ?2", p, ControlDecision.ACCEPTED));
        assertEquals(1, ControlResult.count("period = ?1 and decision = ?2", p, ControlDecision.OPEN));
    }

    /**
     * An unknown entity, period or anomaly is reported as not found.
     */
    @Test
    @TestTransaction
    void not_found() {
        assertThrows(ResourceNotFoundException.class, () -> service.run("NOPE", M));
        assertThrows(ResourceNotFoundException.class, () -> service.run("UNEP", M));
        assertThrows(ResourceNotFoundException.class,
                () -> service.decide(-1L, ControlDecision.FIXED, "op", "x"));
    }
}
