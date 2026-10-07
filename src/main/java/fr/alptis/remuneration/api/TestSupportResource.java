package fr.alptis.remuneration.api;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Dev-only REST endpoint used by the end-to-end test harness.
 *
 * <p>Mounted at {@code /api/test} and present only in the {@code dev} profile, it is the single
 * shortcut the end-to-end suite may use; every business action still goes through the regular
 * interface. It currently exposes a liveness probe and control of the application clock.</p>
 */
@Path("/api/test")
@IfBuildProfile("dev")
public class TestSupportResource {

    /** Service backing the test shortcuts. */
    private final TestSupportService service;

    /**
     * Creates the resource.
     *
     * @param service the test-support service to delegate to
     */
    @Inject
    public TestSupportResource(TestSupportService service) {
        this.service = service;
    }

    /**
     * Liveness probe that also returns the current application time, proving that REST, JSON
     * serialization and the clock are wired together.
     *
     * @return a {@link PingResponse} with status {@code ok} and the current time
     */
    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public PingResponse ping() {
        return new PingResponse("ok", service.now());
    }

    /**
     * Freezes the application clock at the instant carried by the request.
     *
     * @param request the clock request carrying the instant to freeze at
     * @return an empty {@code 204 No Content} response
     */
    @PUT
    @Path("/clock")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response fixClock(ClockRequest request) {
        service.fixClock(request.instant());
        return Response.noContent().build();
    }

    /**
     * Releases the application clock, restoring the system clock.
     *
     * @return an empty {@code 204 No Content} response
     */
    @DELETE
    @Path("/clock")
    public Response resetClock() {
        service.resetClock();
        return Response.noContent().build();
    }

    /**
     * Response body of the liveness probe.
     *
     * @param status constant {@code ok} marker
     * @param now    the current application time
     */
    public record PingResponse(String status, OffsetDateTime now) {
    }

    /**
     * Request body carrying the instant at which to freeze the clock.
     *
     * @param instant the instant to freeze the application clock at
     */
    public record ClockRequest(Instant instant) {
    }
}
