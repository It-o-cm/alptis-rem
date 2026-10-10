package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.api.TestDatabaseReset;
import fr.alptis.remuneration.cycle.CycleQueryService.AnomalyBreakdown;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
