package fr.alptis.remuneration.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DateTimeProvider}, covering the system clock and the freeze/reset path.
 */
class DateTimeProviderTest {

    /**
     * Verifies that freezing at an instant pins {@code instant()}, {@code now()} and {@code today()}
     * to that fixed point, in the default Paris zone.
     */
    @Test
    void fix_at_instant_pins_all_reads() {
        DateTimeProvider provider = new DateTimeProvider();
        Instant fixed = Instant.parse("2026-09-15T10:00:00Z");

        provider.fixAt(fixed);

        assertEquals(fixed, provider.instant());
        assertEquals(DateTimeProvider.DEFAULT_ZONE, provider.zone());
        // Paris is at +02:00 in September; 10:00Z is 12:00 local on the 15th.
        assertEquals(LocalDate.of(2026, 9, 15), provider.today());
        assertEquals(12, provider.now().getHour());
    }

    /**
     * Verifies that freezing at an {@link OffsetDateTime} resolves to the same underlying instant.
     */
    @Test
    void fix_at_offset_date_time_uses_its_instant() {
        DateTimeProvider provider = new DateTimeProvider();
        OffsetDateTime moment = OffsetDateTime.of(2026, 1, 20, 9, 0, 0, 0, ZoneOffset.ofHours(1));

        provider.fixAt(moment);

        assertEquals(moment.toInstant(), provider.instant());
        assertEquals(LocalDate.of(2026, 1, 20), provider.today());
    }

    /**
     * Verifies that a fresh provider uses the system clock and that reset restores it after a freeze.
     */
    @Test
    void reset_restores_the_system_clock() {
        DateTimeProvider provider = new DateTimeProvider();
        Instant beforeFreeze = provider.instant();
        assertTrue(beforeFreeze.isAfter(Instant.parse("2020-01-01T00:00:00Z")));

        provider.fixAt(Instant.parse("1999-12-31T23:00:00Z"));
        assertEquals(Instant.parse("1999-12-31T23:00:00Z"), provider.instant());

        provider.reset();
        assertEquals(DateTimeProvider.DEFAULT_ZONE, provider.zone());
        assertTrue(provider.instant().isAfter(Instant.parse("2020-01-01T00:00:00Z")));
    }
}
