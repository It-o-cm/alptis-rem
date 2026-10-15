package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.DateTimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TestSupportService}, using a real {@link DateTimeProvider}.
 */
class TestSupportServiceTest {

    /**
     * Verifies that {@code fixClock} pins the provider, {@code now} reflects it, and {@code resetClock}
     * restores the system clock.
     */
    @Test
    void fixes_reads_and_resets_the_clock() {
        DateTimeProvider provider = new DateTimeProvider();
        TestSupportService service = new TestSupportService(provider);

        Instant fixed = Instant.parse("2026-09-15T10:00:00Z");
        service.fixClock(fixed);
        assertEquals(fixed, service.now().toInstant());

        service.resetClock();
        assertTrue(service.now().toInstant().isAfter(Instant.parse("2020-01-01T00:00:00Z")));
    }
}
