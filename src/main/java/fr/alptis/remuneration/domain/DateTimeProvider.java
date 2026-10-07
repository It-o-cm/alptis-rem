package fr.alptis.remuneration.domain;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Single source of the current date and time for the whole application.
 *
 * <p>Every place that needs to know "now" or "today" reads it here instead of calling the
 * system clock directly, so that behaviour is deterministic and the end-to-end suite can pin
 * the clock through the {@code /api/test} endpoint. The underlying {@link Clock} can be frozen
 * to a fixed instant and later reset to the system clock.</p>
 */
@ApplicationScoped
public class DateTimeProvider {

    /** Default time zone of the group's legal entities. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

    /** The clock currently in effect; defaults to the system clock in the default zone. */
    private Clock clock = Clock.system(DEFAULT_ZONE);

    /**
     * Returns the current instant on the clock in effect.
     *
     * @return the current {@link Instant}
     */
    public Instant instant() {
        return clock.instant();
    }

    /**
     * Returns the current date and time, with offset, on the clock in effect.
     *
     * @return the current {@link OffsetDateTime}
     */
    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /**
     * Returns the current calendar day on the clock in effect.
     *
     * @return the current {@link LocalDate}
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Returns the time zone of the clock in effect.
     *
     * @return the active {@link ZoneId}
     */
    public ZoneId zone() {
        return clock.getZone();
    }

    /**
     * Freezes the clock at the given instant, in the default zone, so that subsequent reads
     * return that fixed point in time. Used by the dev-only test endpoint.
     *
     * @param fixedInstant the instant to freeze the clock at
     */
    public void fixAt(Instant fixedInstant) {
        this.clock = Clock.fixed(fixedInstant, DEFAULT_ZONE);
    }

    /**
     * Freezes the clock at the given date and time. Used by the dev-only test endpoint.
     *
     * @param fixedDateTime the date and time to freeze the clock at
     */
    public void fixAt(OffsetDateTime fixedDateTime) {
        fixAt(fixedDateTime.toInstant());
    }

    /**
     * Restores the system clock in the default zone, undoing any previous freeze.
     */
    public void reset() {
        this.clock = Clock.system(DEFAULT_ZONE);
    }
}
