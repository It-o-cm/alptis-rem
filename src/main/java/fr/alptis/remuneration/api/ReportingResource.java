package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.reporting.ReportingService;
import fr.alptis.remuneration.reporting.ReportingService.Dashboard;
import fr.alptis.remuneration.reporting.ReportingService.EntitySummary;
import fr.alptis.remuneration.reporting.ReportingService.Indicators;
import fr.alptis.remuneration.reporting.ReportingService.MarginRow;
import fr.alptis.remuneration.reporting.ReportingService.SearchRow;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the restitution screens (section 8.12): the dashboards (REP-01), the
 * "paid to whom, when, why" cross-search (REP-02/03), the margin by product (REP-08), the operating
 * indicators (REP-09) and the consolidated group view (REP-07). Every read is confined to the
 * caller's visible entities; the consolidated view is reserved to cross-entity users (CR-70, REP-07).
 */
@Path("/api/ui/v1/reporting")
@Produces(MediaType.APPLICATION_JSON)
public class ReportingResource {

    private final ReportingService reporting;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param reporting the reporting read model
     * @param scope     the entity partition
     */
    @Inject
    public ReportingResource(ReportingService reporting, EntityScope scope) {
        this.reporting = reporting;
        this.scope = scope;
    }

    /**
     * Returns the dashboards of an entity, optionally for a month (REP-01).
     *
     * @param entityCode the entity code
     * @param month      the period month, or null
     * @return the dashboard view
     */
    @GET
    @Path("/{entity}/dashboard")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public Dashboard dashboard(@PathParam("entity") String entityCode, @QueryParam("month") String month) {
        requireScope(entityCode);
        return reporting.dashboard(entityCode, month == null ? null : LocalDate.parse(month));
    }

    /**
     * Returns the operating indicators of an entity (REP-09).
     *
     * @param entityCode the entity code
     * @return the indicators view
     */
    @GET
    @Path("/{entity}/indicators")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public Indicators indicators(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return reporting.indicators(entityCode);
    }

    /**
     * Answers "paid to whom, when, why" with its filters (REP-02/03).
     *
     * @param entityCode  the entity code
     * @param product     the product code filter, or null
     * @param distributor the distributor filter, or null
     * @param nature      the nature filter, or null
     * @param month       the period month filter, or null
     * @return the matching rows
     */
    @GET
    @Path("/{entity}/search")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<SearchRow> search(@PathParam("entity") String entityCode, @QueryParam("product") String product,
                                  @QueryParam("distributor") String distributor,
                                  @QueryParam("nature") String nature, @QueryParam("month") String month) {
        requireScope(entityCode);
        return reporting.search(entityCode, product, distributor,
                nature == null ? null : LineNature.valueOf(nature),
                month == null ? null : LocalDate.parse(month));
    }

    /**
     * Returns the margin by product of an entity (REP-08).
     *
     * @param entityCode the entity code
     * @return the margin rows
     */
    @GET
    @Path("/{entity}/margin")
    @RolesAllowed({"BUSINESS_ADMIN", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<MarginRow> margin(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return reporting.margin(entityCode);
    }

    /**
     * Returns the consolidated group view (REP-07), reserved to cross-entity users.
     *
     * @return the per-entity summaries
     */
    @GET
    @Path("/consolidated")
    @RolesAllowed({"AUDITOR", "IT_ADMIN", "BUSINESS_ADMIN", "FINANCE_CONTROLLER", "VIEWER", "BUSINESS_VALIDATOR", "OPERATOR"})
    public List<EntitySummary> consolidated() {
        if (!scope.unrestricted()) {
            throw new ResourceNotFoundException("Vision consolidée réservée aux utilisateurs groupe");
        }
        return reporting.consolidated();
    }

    /**
     * Fails with a not-found when the entity is out of the caller's scope.
     *
     * @param entityCode the entity code
     */
    private void requireScope(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
    }
}
