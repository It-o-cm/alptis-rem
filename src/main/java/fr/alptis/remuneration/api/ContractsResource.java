package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.integration.ReferentialQueryService;
import fr.alptis.remuneration.integration.ReferentialQueryService.ContractView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface endpoint for the contract and coverage search screen (section 5.2), partitioned by
 * entity.
 */
@Path("/api/ui/v1/contracts")
public class ContractsResource {

    private final ReferentialQueryService query;

    /**
     * Creates the resource.
     *
     * @param query the referential query service
     */
    @Inject
    public ContractsResource(ReferentialQueryService query) {
        this.query = query;
    }

    /**
     * Searches contracts by number, each with its coverages.
     *
     * @param q    the search term
     * @param page the zero-based page index
     * @param size the page size
     * @return a page of contract views
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public PageResult<ContractView> search(@QueryParam("q") @DefaultValue("") String q,
                                           @QueryParam("page") @DefaultValue("0") int page,
                                           @QueryParam("size") @DefaultValue("20") int size) {
        return query.searchContracts(q, page, size);
    }
}
