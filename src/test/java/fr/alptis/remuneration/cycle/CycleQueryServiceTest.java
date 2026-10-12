package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.api.TestDatabaseReset;
import fr.alptis.remuneration.cycle.CycleQueryService.AnomalyBreakdown;
import fr.alptis.remuneration.cycle.CycleQueryService.PeriodDetail;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the anomaly breakdown of the cycle read model: counts by severity and by control family,
 * including a control with no family. Runs against UNEP in a rolled-back transaction.
 */
@QuarkusTest
class CycleQueryServiceTest {

    @Inject
    CycleQueryService query;
    @Inject
    TestDatabaseReset reset;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    /** Resets the database to a clean baseline before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
    }

    /**
     * The breakdown counts anomalies by severity and by family, bucketing a family-less control under
     * a dash.
     */
    @Test
    @TestTransaction
    void anomaly_breakdown_counts_by_severity_and_family() {
        LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        Period period = new Period();
        period.entity = unep;
        period.periodMonth = M;
        period.state = PeriodState.CALCULATED;
        period.persist();
        anomaly(period, control(unep, "WITH-FAMILY", ControlFamily.ACCOUNT), ControlSeverity.WARNING);
        anomaly(period, control(unep, "WITH-FAMILY", ControlFamily.ACCOUNT), ControlSeverity.WARNING);
        anomaly(period, control(unep, "NO-FAMILY", null), ControlSeverity.BLOCKING);

        AnomalyBreakdown breakdown = query.anomalyBreakdown("UNEP", M);
        assertEquals(2, severityCount(breakdown, "WARNING"));
        assertEquals(1, severityCount(breakdown, "BLOCKING"));
        assertTrue(breakdown.byFamily().stream().anyMatch(c -> c.label().equals("ACCOUNT") && c.count() == 2));
        assertTrue(breakdown.byFamily().stream().anyMatch(c -> c.label().equals("—") && c.count() == 1));
    }

    /**
     * A calculated period's detail carries the target calendar (the first milestone reached, the later
     * ones not), its payment lots with their order counts, and the entity's remaining backlog (held and
     * pending events).
     */
    @Test
    @TestTransaction
    void detail_exposes_calendar_lots_and_remaining() {
        LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        Period period = period(unep, PeriodState.CALCULATED);
        PaymentBatch batch = new PaymentBatch();
        batch.entity = unep;
        batch.period = period;
        batch.batchReference = "UNEP-CYCLE-2026-09";
        batch.batchType = BatchType.CYCLE;
        batch.status = BatchStatus.ISSUED;
        batch.totalAmount = new BigDecimal("1200.00");
        batch.persist();
        order(batch);
        heldOrPending(unep, "H1", ProcessingStatus.ON_HOLD);
        heldOrPending(unep, "P1", ProcessingStatus.PENDING);
        heldOrPending(unep, "P2", ProcessingStatus.PENDING);

        PeriodDetail detail = query.detail("UNEP", M);

        // Calendar: milestone 1 (calcul) reached at CALCULATED, milestone 2 (contrôles) not yet.
        assertEquals(7, detail.calendar().size());
        assertTrue(detail.calendar().get(0).reached());
        assertEquals("Jour ouvré 1", detail.calendar().get(0).businessDay());
        assertFalse(detail.calendar().get(1).reached());
        // Lots: the single cycle lot with its order count and total.
        assertEquals(1, detail.lots().size());
        assertEquals("CYCLE", detail.lots().get(0).batchType());
        assertEquals(1, detail.lots().get(0).orderCount());
        assertEquals(0, detail.lots().get(0).totalAmount().compareTo(new BigDecimal("1200.00")));
        // Remaining backlog.
        assertEquals(1, detail.heldEvents());
        assertEquals(2, detail.pendingEvents());
    }

    /** A closed period has every calendar milestone reached, and no lot when none was built. */
    @Test
    @TestTransaction
    void detail_of_a_closed_period_has_all_milestones_reached() {
        LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        period(unep, PeriodState.CLOSED);

        PeriodDetail detail = query.detail("UNEP", M);

        assertTrue(detail.calendar().stream().allMatch(CycleQueryService.CalendarMilestone::reached));
        assertTrue(detail.lots().isEmpty());
        assertEquals(0, detail.heldEvents());
        assertEquals(0, detail.pendingEvents());
    }

    /** Creates a UNEP period in a given state at month M. */
    private static Period period(LegalEntity entity, PeriodState state) {
        Period period = new Period();
        period.entity = entity;
        period.periodMonth = M;
        period.state = state;
        period.persist();
        return period;
    }

    /** Creates a minimal issued payment order in a batch. */
    private static void order(PaymentBatch batch) {
        PaymentOrder o = new PaymentOrder();
        o.batch = batch;
        o.amount = new BigDecimal("1200.00");
        o.status = PaymentOrderStatus.ISSUED;
        o.persist();
    }

    /** Creates a held or pending business event of the entity. */
    private static void heldOrPending(LegalEntity entity, String key, ProcessingStatus status) {
        BusinessEvent e = new BusinessEvent();
        e.entity = entity;
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = EventType.PREMIUM_COLLECTED;
        e.eventDate = M;
        e.receivedDate = M;
        e.processingStatus = status;
        e.persist();
    }

    /** The control-version history guards an unknown entity (CTL-04). */
    @Test
    @TestTransaction
    void control_versions_guards_unknown_entity() {
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class,
                () -> query.controlVersions("NOPE", "DATA-FUTURE"));
    }

    /** Returns the count of a severity in a breakdown. */
    private static long severityCount(AnomalyBreakdown breakdown, String severity) {
        return breakdown.bySeverity().stream().filter(c -> c.label().equals(severity))
                .mapToLong(CycleQueryService.Count::count).sum();
    }

    /** Creates (once) a control definition of a code and family. */
    private static ControlDefinition control(LegalEntity entity, String code, ControlFamily family) {
        ControlDefinition existing = ControlDefinition.<ControlDefinition>find("entity = ?1 and code = ?2",
                entity, code).firstResult();
        if (existing != null) {
            return existing;
        }
        ControlDefinition def = new ControlDefinition();
        def.entity = entity;
        def.code = code;
        def.label = code;
        def.family = family;
        def.severity = ControlSeverity.WARNING;
        def.active = Boolean.TRUE;
        def.validFrom = M;
        def.persist();
        return def;
    }

    /** Creates an open anomaly of a severity. */
    private static void anomaly(Period period, ControlDefinition control, ControlSeverity severity) {
        ControlResult result = new ControlResult();
        result.period = period;
        result.control = control;
        result.objectType = "TEST";
        result.objectId = 1L;
        result.severity = severity;
        result.decision = ControlDecision.OPEN;
        result.persist();
    }
}
