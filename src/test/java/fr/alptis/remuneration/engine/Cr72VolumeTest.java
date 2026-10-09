package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.engine.VolumeDataGenerator.VolumeCounts;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-72 (performance, section 11): the volume generator plants a linear dataset and the real engine
 * commissions every event. The automated case runs a reduced, representative scale (60 beneficiaries,
 * 420 events) and extrapolates its throughput to the CR-72 target (420 000 events), asserting the
 * full run would stay well under the four-hour budget; the generator itself is sized for the full
 * 20 000-beneficiary volume in a dev run.
 */
@QuarkusTest
class Cr72VolumeTest {

    @Inject
    VolumeDataGenerator generator;
    @Inject
    CalculationEngine engine;

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);
    private static final int BENEFICIARIES = 60;
    private static final int EVENTS_PER = 7;
    private static final long TARGET_EVENTS = 420_000L;
    private static final long FOUR_HOURS_NANOS = 4L * 60 * 60 * 1_000_000_000L;

    /**
     * Generates the scene, runs the real calculation and checks every event produced a line and the
     * extrapolated full-volume run stays under four hours.
     */
    @Test
    @TestTransaction
    void cr72_full_volume_calculation_under_four_hours() {
        VolumeCounts counts = generator.generate("UNEP", MONTH, BENEFICIARIES, EVENTS_PER);
        assertEquals(BENEFICIARIES, counts.beneficiaries());
        assertEquals(BENEFICIARIES * EVENTS_PER, counts.events());

        long start = System.nanoTime();
        engine.runReal("UNEP", MONTH);
        long elapsed = System.nanoTime() - start;

        long lines = CommissionLine.count("status = ?1", LineStatus.EARNED);
        assertEquals(BENEFICIARIES * EVENTS_PER, lines);

        long projected = elapsed / counts.events() * TARGET_EVENTS;
        assertTrue(projected < FOUR_HOURS_NANOS,
                "projected full-volume run " + (projected / 1_000_000_000L) + "s exceeds four hours");
    }
}
