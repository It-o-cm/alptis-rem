package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.integration.ReferentialQueryService;
import fr.alptis.remuneration.integration.ReferentialQueryService.DistributorDetail;
import fr.alptis.remuneration.integration.ReferentialQueryService.DistributorView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface endpoints for distributor search (REF-02, REP-03) and the hierarchy/production-code
 * detail (REF-03), partitioned by entity.
 */
@Path("/api/ui/v1/distributors")
public class DistributorsResource {

    private final ReferentialQueryService query;

    /**
     * Creates the resource.
     *
     * @param query the referential query service
     */
    @Inject
    public DistributorsResource(ReferentialQueryService query) {
        this.query = query;
    }

    /**
     * Searches distributors by code or name.
     *
     * @param q    the search term
     * @param page the zero-based page index
     * @param size the page size
     * @return a page of distributor views
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public PageResult<DistributorView> search(@QueryParam("q") @DefaultValue("") String q,
                                              @QueryParam("page") @DefaultValue("0") int page,
                                              @QueryParam("size") @DefaultValue("20") int size) {
        return query.searchDistributors(q, page, size);
    }

    /**
     * Returns one distributor with its statuses and production codes.
     *
     * @param partnerCode the partner code
     * @return the distributor detail
     */
    @GET
    @Path("/{partnerCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public DistributorDetail detail(@PathParam("partnerCode") String partnerCode) {
        return query.distributor(partnerCode);
    }
}
