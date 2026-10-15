package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the partial recalculation (CAL-12, decision D13): re-processing the garanties of a
 * distributor, a product or a rule replays only their lines, idempotently, and leaves the rest
 * untouched. The scene is planted by the volume generator. Runs against UNEP in rolled-back
 * transactions.
 */
@QuarkusTest
class PartialRecalcTest {

    @Inject
    VolumeDataGenerator generator;
    @Inject
    CalculationEngine engine;

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    /**
     * A partial recalc by distributor replays that distributor's coverage only; the lines stay
     * identical and complete, and the other coverage is left as it was.
     */
    @Test
    @TestTransaction
    void partial_by_distributor_reprocesses_one_coverage() {
        generator.generate("UNEP", MONTH, 2, 1);
        engine.runReal("UNEP", MONTH);
        assertEquals(2, CommissionLine.count("status = ?1", LineStatus.EARNED));

        CalculationRun run = engine.runPartial("UNEP", MONTH, "DISTRIBUTOR", "VOL-P0");
        assertTrue(run.counters.contains("\"coverages\":1"));
        assertEquals(2, CommissionLine.count("status = ?1", LineStatus.EARNED));
    }

    /**
     * A partial recalc by product or by rule replays every matching coverage.
     */
    @Test
    @TestTransaction
    void partial_by_product_and_rule_reprocess_all() {
        generator.generate("UNEP", MONTH, 2, 1);
        engine.runReal("UNEP", MONTH);

        CalculationRun byProduct = engine.runPartial("UNEP", MONTH, "PRODUCT", VolumeDataGenerator.PRODUCT);
        assertTrue(byProduct.counters.contains("\"coverages\":2"));

        CalculationRun byRule = engine.runPartial("UNEP", MONTH, "RULE", "VOLUME_LIN");
        assertTrue(byRule.counters.contains("\"coverages\":2"));
        assertEquals(2, CommissionLine.count("status = ?1", LineStatus.EARNED));
    }

    /**
     * An unknown scope kind and an unknown entity are rejected.
     */
    @Test
    @TestTransaction
    void unknown_scope_and_entity_are_rejected() {
        generator.generate("UNEP", MONTH, 1, 1);
        engine.runReal("UNEP", MONTH);
        assertThrows(ValidationException.class, () -> engine.runPartial("UNEP", MONTH, "NOPE", "x"));
        assertThrows(ResourceNotFoundException.class,
                () -> engine.runPartial("GHOST", MONTH, "DISTRIBUTOR", "VOL-P0"));
    }
}
