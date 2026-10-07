package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.security.AuditQueryService;
import fr.alptis.remuneration.security.AuditQueryService.AuditView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface endpoint for the audit trail screen (requirement ADM-04). The trail is read-only,
 * paginated, most recent first, and partitioned by entity.
 */
@Path("/api/ui/v1/audit")
public class AuditResource {

    /** The audit query service. */
    private final AuditQueryService service;

    /**
     * Creates the resource.
     *
     * @param service the audit query service
     */
    @Inject
    public AuditResource(AuditQueryService service) {
        this.service = service;
    }

    /**
     * Returns one page of audit traces.
     *
     * @param page the zero-based page index
     * @param size the page size
     * @return the page of traces
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"AUDITOR", "IT_ADMIN", "BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER"})
    public PageResult<AuditView> list(@QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("size") @DefaultValue("20") int size) {
        return service.list(page, size);
    }
}
