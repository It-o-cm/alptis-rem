package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.AdjustmentStatus;
import fr.alptis.remuneration.domain.AdjustmentType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
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
import fr.alptis.remuneration.api.TestDatabaseReset;
import fr.alptis.remuneration.cycle.ValidationService.ValidationSummary;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ValidationService} (WKF-02): the period summary (totals, month-on-month gap, breakdown
 * by nature), the second-level rule (total, gap or adjustment), the single- and two-level validation,
 * the parameter override and the not-found guards.
 */
@QuarkusTest
class ValidationServiceTest {

    @Inject
    ValidationService service;
    @Inject
    TestDatabaseReset reset;

    private static final Set<Role> VALIDATOR = Set.of(Role.BUSINESS_VALIDATOR);

    private static long accountSeq = 1;

    @BeforeEach
    void setUp() {
        reset.reset();
    }

    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a UNEP period in a state at a month. */
    private static Period period(LocalDate month, PeriodState state) {
        Period p = new Period();
        p.entity = unep();
        p.periodMonth = month;
        p.state = state;
        p.persist();
        return p;
    }

    /** Adds a line of a nature and amount to a period, on a fresh production-code account. */
    private static void line(Period period, LineNature nature, String amount) {
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = accountSeq++;
        account.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /** Adds a line with a nil (null) amount to a period, on a fresh account. */
    private static void nullLine(Period period, LineNature nature) {
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = accountSeq++;
        account.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = null;
        line.persist();
    }

    /** Adds a submitted adjustment to a period. */
    private static void adjustment(Period period) {
        Adjustment a = new Adjustment();
        a.entity = unep();
        a.period = period;
        a.adjustmentType = AdjustmentType.CORRECTION;
        a.amount = new BigDecimal("1.00");
        a.reason = "x";
        a.status = AdjustmentStatus.SUBMITTED;
        a.persist();
    }

    /**
     * The summary carries the current and previous totals, their gap, the breakdown by nature and the
     * adjustment count; a small, adjustment-free, steady month needs no second level.
     */
    @Test
    @TestTransaction
    void summary_totals_gap_breakdown_and_no_second_level() {
        Period previous = period(LocalDate.of(2026, 8, 1), PeriodState.CLOSED);
        line(previous, LineNature.LINEAR, "1000.00");
        Period current = period(LocalDate.of(2026, 9, 1), PeriodState.CONTROLLED);
        line(current, LineNature.LINEAR, "1000.00");
        line(current, LineNature.ACQUISITION, "50.00");
        nullLine(current, LineNature.INSURER);

        ValidationSummary summary = service.summary("UNEP", LocalDate.of(2026, 9, 1));
        assertEquals(0, summary.currentTotal().compareTo(new BigDecimal("1050.00")));
        assertEquals(0, summary.previousTotal().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, summary.gap().compareTo(new BigDecimal("0.05")));
        assertEquals(3, summary.byNature().size());
        assertEquals(0, summary.adjustmentCount());
        assertFalse(summary.secondLevelRequired());
    }

    /** A single level validates a steady month straight to business-validated. */
    @Test
    @TestTransaction
    void single_level_validation_transitions() {
        Period previous = period(LocalDate.of(2026, 8, 1), PeriodState.CLOSED);
        line(previous, LineNature.LINEAR, "1000.00");
        Period current = period(LocalDate.of(2026, 9, 1), PeriodState.CONTROLLED);
        line(current, LineNature.LINEAR, "1000.00");

        ValidationSummary summary = service.validate("UNEP", LocalDate.of(2026, 9, 1), "val1", VALIDATOR);
        assertEquals("BUSINESS_VALIDATED", summary.state());
    }

    /**
     * A month with an adjustment needs a second level: the first validation records the validator and
     * leaves it controlled, the same validator is refused, a different one completes it.
     */
    @Test
    @TestTransaction
    void two_level_validation_requires_a_second_validator() {
        Period current = period(LocalDate.of(2026, 9, 1), PeriodState.CONTROLLED);
        line(current, LineNature.LINEAR, "500.00");
        adjustment(current);

        ValidationSummary first = service.validate("UNEP", LocalDate.of(2026, 9, 1), "val1", VALIDATOR);
        assertTrue(first.secondLevelRequired());
        assertEquals("CONTROLLED", first.state());
        assertEquals("val1", first.firstLevelValidatedBy());

        assertThrows(ValidationException.class,
                () -> service.validate("UNEP", LocalDate.of(2026, 9, 1), "val1", VALIDATOR));

        ValidationSummary second = service.validate("UNEP", LocalDate.of(2026, 9, 1), "val2", VALIDATOR);
        assertEquals("BUSINESS_VALIDATED", second.state());
    }

    /**
     * The gap triggers a second level when a nil previous month faces a non-nil current one; a steady
     * previous month and a flat current one give a nil gap.
     */
    @Test
    @TestTransaction
    void gap_rule_covers_the_nil_previous_cases() {
        Period withLines = period(LocalDate.of(2026, 9, 1), PeriodState.CONTROLLED);
        line(withLines, LineNature.LINEAR, "500.00");
        assertTrue(service.summary("UNEP", LocalDate.of(2026, 9, 1)).secondLevelRequired());
        assertEquals(0, service.summary("UNEP", LocalDate.of(2026, 9, 1)).gap().compareTo(BigDecimal.ONE));

        Period empty = period(LocalDate.of(2026, 7, 1), PeriodState.CONTROLLED);
        assertEquals(0, service.summary("UNEP", LocalDate.of(2026, 7, 1)).gap().compareTo(BigDecimal.ZERO));
        assertFalse(service.summary("UNEP", LocalDate.of(2026, 7, 1)).secondLevelRequired());
        assertEquals(PeriodState.CONTROLLED, empty.state);
    }

    /** A lowered total threshold (entity parameter) makes a small month require a second level. */
    @Test
    @TestTransaction
    void total_threshold_parameter_triggers_second_level() {
        Period previous = period(LocalDate.of(2026, 8, 1), PeriodState.CLOSED);
        line(previous, LineNature.LINEAR, "1000.00");
        Period current = period(LocalDate.of(2026, 9, 1), PeriodState.CONTROLLED);
        line(current, LineNature.LINEAR, "1000.00");
        line(current, LineNature.ACQUISITION, "50.00");
        EntityParameter p = new EntityParameter();
        p.entity = unep();
        p.paramKey = ValidationService.TOTAL_THRESHOLD_KEY;
        p.paramValue = "500";
        p.persist();

        assertTrue(service.summary("UNEP", LocalDate.of(2026, 9, 1)).secondLevelRequired());
    }

    /**
     * A steady month under the total and gap thresholds still needs a second level when it carries an
     * adjustment (the third second-level rule).
     */
    @Test
    @TestTransaction
    void adjustment_alone_triggers_second_level_on_a_steady_month() {
        Period previous = period(LocalDate.of(2026, 8, 1), PeriodState.CLOSED);
        line(previous, LineNature.LINEAR, "1000.00");
        Period current = period(LocalDate.of(2026, 9, 1), PeriodState.CONTROLLED);
        line(current, LineNature.LINEAR, "1050.00");
        adjustment(current);

        ValidationSummary summary = service.summary("UNEP", LocalDate.of(2026, 9, 1));
        assertFalse(summary.gap().compareTo(new BigDecimal("0.20")) >= 0);
        assertTrue(summary.secondLevelRequired());
    }

    /** The summary guards an unknown entity and period. */
    @Test
    @TestTransaction
    void guards_unknown_entity_and_period() {
        assertThrows(ResourceNotFoundException.class, () -> service.summary("NOPE", LocalDate.of(2026, 9, 1)));
        assertThrows(ResourceNotFoundException.class, () -> service.summary("UNEP", LocalDate.of(2026, 9, 1)));
    }
}
