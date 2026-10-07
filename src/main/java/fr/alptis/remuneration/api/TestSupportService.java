package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.DateTimeProvider;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Back-end support for the end-to-end test harness, available only outside production.
 *
 * <p>The end-to-end suite drives the real application in the {@code dev} profile; this service
 * provides the few shortcuts it is allowed to use (section 1 of {@code TESTS-E2E.md}): pinning
 * and releasing the {@link DateTimeProvider} clock. Database reset and named dataset loading are
 * added in step 1, together with the first entities and dataset loaders, so that both the
 * "found" and "not found" paths can be exercised under the 100% branch-coverage rule.</p>
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class TestSupportService {

    /** Provider whose clock this service pins and releases. */
    private final DateTimeProvider dateTimeProvider;

    /**
     * Creates the service.
     *
     * @param dateTimeProvider the application date/time provider to control
     */
    @Inject
    public TestSupportService(DateTimeProvider dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    /**
     * Returns the current date and time as seen through the application clock.
     *
     * @return the current {@link OffsetDateTime}
     */
    public OffsetDateTime now() {
        return dateTimeProvider.now();
    }

    /**
     * Freezes the application clock at the given instant.
     *
     * @param instant the instant to freeze the clock at
     */
    public void fixClock(Instant instant) {
        dateTimeProvider.fixAt(instant);
    }

    /**
     * Releases the application clock, restoring the system clock.
     */
    public void resetClock() {
        dateTimeProvider.reset();
    }
}
