package fr.alptis.remuneration.api;

import fr.alptis.remuneration.reporting.PurgeService;
import fr.alptis.remuneration.reporting.SupervisionService;
import fr.alptis.remuneration.reporting.SupervisionService.Health;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;

/**
 * Interface endpoints for the technical administration screens (section 3, 8.13): the supervision of
 * treatments and flows (OUT-07, NFR-16) and the purge/anonymisation of personal data (ADM-06). Both
 * are reserved to the technical administrator, who has no right on amounts.
 */
@Path("/api/ui/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private final SupervisionService supervision;
    private final PurgeService purge;

    /**
     * Creates the resource.
     *
     * @param supervision the supervision service
     * @param purge       the purge service
     */
    @Inject
    public AdminResource(SupervisionService supervision, PurgeService purge) {
        this.supervision = supervision;
        this.purge = purge;
    }

    /**
     * Returns the health and metrics of the treatments and flows (OUT-07, NFR-16).
     *
     * @return the health view
     */
    @GET
    @Path("/supervision")
    @RolesAllowed({"IT_ADMIN", "AUDITOR"})
    public Health supervision() {
        return supervision.health();
    }

    /**
     * Returns the count of personal records eligible for anonymisation (ADM-06). No amount is
     * reported for a preview.
     *
     * @return the eligible count
     */
    @GET
    @Path("/purge/eligible")
    @RolesAllowed({"IT_ADMIN"})
    public PurgeResult eligible() {
        return new PurgeResult(purge.eligibleCount(), null);
    }

    /**
     * Anonymises the personal data past its retention period (ADM-06). The result also carries the
     * total commission amount preserved, so the screen shows that the amounts stay untouched.
     *
     * @return the number of anonymised records and the preserved amount
     */
    @POST
    @Path("/purge")
    @RolesAllowed({"IT_ADMIN"})
    public PurgeResult purge() {
        return new PurgeResult(purge.purge(), purge.preservedAmount());
    }

    /**
     * The count of a purge preview or action, with the commission amount preserved by the action.
     *
     * @param count           the number of records
     * @param preservedAmount the total commission amount retained, null for a preview
     */
    public record PurgeResult(long count, BigDecimal preservedAmount) {
    }
}
