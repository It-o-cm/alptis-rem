package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.DateTimeProvider;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TestSupportResource}, instantiated directly with real collaborators so the
 * dev-only resource is covered without starting a container.
 */
class TestSupportResourceTest {

    /**
     * Builds a resource backed by a real service and provider.
     *
     * @return a ready-to-use resource and the provider it ultimately controls
     */
    /** Records whether the reset was invoked. */
    private static final boolean[] RESET_CALLED = {false};

    private static TestSupportResource newResource(DateTimeProvider provider) {
        TestDatabaseReset fakeReset = new TestDatabaseReset(null, null) {
            @Override
            public void reset() {
                RESET_CALLED[0] = true;
            }
        };
        return new TestSupportResource(new TestSupportService(provider), fakeReset);
    }

    /**
     * Verifies that the ping probe reports {@code ok} and the current application time.
     */
    @Test
    void ping_reports_ok_and_current_time() {
        DateTimeProvider provider = new DateTimeProvider();
        provider.fixAt(Instant.parse("2026-09-15T10:00:00Z"));
        TestSupportResource resource = newResource(provider);

        TestSupportResource.PingResponse response = resource.ping();

        assertEquals("ok", response.status());
        assertEquals(Instant.parse("2026-09-15T10:00:00Z"), response.now().toInstant());
    }

    /**
     * Verifies that the clock endpoints freeze and release the application clock and return 204.
     */
    @Test
    void clock_endpoints_fix_and_reset() {
        DateTimeProvider provider = new DateTimeProvider();
        TestSupportResource resource = newResource(provider);

        Instant fixed = Instant.parse("2026-09-15T10:00:00Z");
        Response fixResponse = resource.fixClock(new TestSupportResource.ClockRequest(fixed));
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), fixResponse.getStatus());
        assertEquals(fixed, provider.instant());

        Response resetResponse = resource.resetClock();
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), resetResponse.getStatus());
    }

    /**
     * Verifies that the reset endpoint delegates to the database reset and returns 204.
     */
    @Test
    void reset_endpoint_delegates_and_returns_204() {
        RESET_CALLED[0] = false;
        TestSupportResource resource = newResource(new DateTimeProvider());

        Response response = resource.reset();

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(RESET_CALLED[0]);
    }
}

