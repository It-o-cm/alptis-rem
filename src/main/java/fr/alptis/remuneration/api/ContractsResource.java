package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.integration.ReferentialQueryService;
import fr.alptis.remuneration.integration.ReferentialQueryService.ContractView;
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

    /**
     * Returns the fiche of a membership's guarantee: its affectations, remuneration choices, events,
     * commission lines and discount position (sections 5.2, 5.5).
     *
     * @param entityCode     the entity code
     * @param contractNumber the contract (membership) number
     * @param coverageCode   the guarantee code
     * @return the guarantee detail
     */
    @GET
    @Path("/{entityCode}/{contractNumber}/coverages/{coverageCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public ReferentialQueryService.CoverageDetail coverage(@PathParam("entityCode") String entityCode,
                                                           @PathParam("contractNumber") String contractNumber,
                                                           @PathParam("coverageCode") String coverageCode) {
        return query.coverage(entityCode, contractNumber, coverageCode);
    }
}
