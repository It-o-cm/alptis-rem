package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.RunStatus;
import fr.alptis.remuneration.domain.RunType;
import fr.alptis.remuneration.api.TestDatabaseReset;
import fr.alptis.remuneration.cycle.CalculationQueryService.ExecutionsView;
import fr.alptis.remuneration.cycle.CalculationQueryService.RunView;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link CalculationQueryService}: the runs of a period read with their counters (full and
 * partial, missing, null and malformed) and the units left in failure, with the unknown-entity and
 * no-period guards (CAL-01, CAL-12).
 */
@QuarkusTest
class CalculationQueryServiceTest {

    @Inject
    CalculationQueryService query;
    @Inject
    TestDatabaseReset reset;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    /** Resets the baseline before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
    }

    /** Returns an entity by code. */
    private static LegalEntity entity(String code) {
        return LegalEntity.<LegalEntity>find("code", code).firstResult();
    }

    /** Creates a period at month M for an entity. */
    private static Period period(LegalEntity e) {
        Period p = new Period();
        p.entity = e;
        p.periodMonth = M;
        p.state = PeriodState.CALCULATED;
        p.persist();
        return p;
    }

    /** Creates a run with counters and parameters. */
    private static void run(Period period, RunType type, String counters, String parameters) {
        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = type;
        run.status = RunStatus.COMPLETED;
        run.startedAt = java.time.OffsetDateTime.parse("2026-10-01T09:00:00Z");
        run.launchedBy = "tester";
        run.counters = counters;
        run.parameters = parameters;
        run.setCreatedAt(java.time.OffsetDateTime.parse("2026-10-01T09:00:00Z"));
        run.persist();
    }

    /** Creates a held event on an entity. */
    private static BusinessEvent held(LegalEntity e, String key, Contract contract, HoldReason reason) {
        BusinessEvent ev = new BusinessEvent();
        ev.entity = e;
        ev.sourceSystem = "SYS";
        ev.sourceKey = key;
        ev.eventType = EventType.TERMINATION;
        ev.eventDate = M;
        ev.contract = contract;
        ev.processingStatus = ProcessingStatus.ON_HOLD;
        ev.holdReason = reason;
        ev.persist();
        return ev;
    }

    /**
     * The executions view reads a period's runs — full, partial, with missing, null and malformed
     * counters — and lists the held units, including a bare one with null fields.
     */
    @Test
    @TestTransaction
    void reads_runs_and_failed_units() {
        LegalEntity alptis = entity("ALPTIS_ASSURANCES");
        Period period = period(alptis);
        run(period, RunType.ACTUAL, "{\"events\":5,\"lines\":12}", null);
        run(period, RunType.ACTUAL, "{\"coverages\":3,\"lines\":4}", "{\"partial\":{\"type\":\"RULE\",\"value\":\"R1\"}}");
        run(period, RunType.ACTUAL, null, "{}");
        Contract contract = new Contract();
        contract.entity = alptis;
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-1";
        contract.persist();
        fr.alptis.remuneration.domain.Coverage coverage = new fr.alptis.remuneration.domain.Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "GAR-1";
        coverage.effectDate = M;
        coverage.persist();
        held(alptis, "H-1", contract, HoldReason.UNKNOWN_REFERENCE).coverage = coverage;
        held(alptis, "H-2", null, null);

        ExecutionsView view = query.executions("ALPTIS_ASSURANCES", M);
        assertEquals(3, view.runs().size());
        assertTrue(view.failed().stream().anyMatch(f -> "GAR-1".equals(f.coverageCode())));
        RunView full = view.runs().stream().filter(r -> r.processed() == 5).findFirst().orElseThrow();
        assertEquals(12, full.lines());
        assertNull(full.partialScope());
        RunView partial = view.runs().stream().filter(r -> "RULE R1".equals(r.partialScope())).findFirst()
                .orElseThrow();
        assertEquals(3, partial.processed());
        RunView nulls = view.runs().stream().filter(r -> r.lines() == 0 && r.partialScope() == null
                && r.processed() == 0).findFirst().orElseThrow();
        assertEquals(0, nulls.processed());
        assertEquals(2, view.failed().size());
        assertTrue(view.failed().stream().anyMatch(f -> f.contractNumber() == null && f.holdReason() == null));
    }

    /** An unknown entity yields an empty view; a period-less month yields no runs but still the units. */
    @Test
    @TestTransaction
    void guards_unknown_entity_and_missing_period() {
        LegalEntity alptis = entity("ALPTIS_ASSURANCES");
        held(alptis, "H-1", null, HoldReason.NO_RULE);

        assertTrue(query.executions("NOPE", M).runs().isEmpty());
        assertTrue(query.executions("NOPE", M).failed().isEmpty());

        ExecutionsView noPeriod = query.executions("ALPTIS_ASSURANCES", LocalDate.of(2099, 1, 1));
        assertTrue(noPeriod.runs().isEmpty());
        assertEquals(1, noPeriod.failed().size());
    }
}
