package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.reporting.ReportingService;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface endpoint for the commission-line detail screen (CAL-10, FIN-07): one line with its
 * event, rule version, basis and rate, its related contract, coverage, bordereau and payment order,
 * and the native explanation the calculation produced (D14). Partitioned by the caller's entities.
 */
@Path("/api/ui/v1/commission-lines")
public class CommissionLinesResource {

    private final ReportingService reporting;

    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param reporting the reporting service holding the line-detail query
     * @param scope     the entity partition
     */
    @Inject
    public CommissionLinesResource(ReportingService reporting, EntityScope scope) {
        this.reporting = reporting;
        this.scope = scope;
    }

    /**
     * Returns a commission line's detail with its explanation (CAL-10), within the caller's partition.
     *
     * @param id the commission-line id
     * @return the line detail
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public ReportingService.LineDetail line(@PathParam("id") Long id) {
        ReportingService.LineDetail detail = reporting.lineDetail(id);
        if (!scope.canSee(detail.entityCode())) {
            throw new ResourceNotFoundException("Ligne hors périmètre : " + id);
        }
        return detail;
    }
}
