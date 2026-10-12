package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.reporting.ReportingService;
import fr.alptis.remuneration.reporting.ReportingService.Consolidated;
import fr.alptis.remuneration.reporting.ReportingService.Dashboard;
import fr.alptis.remuneration.reporting.ReportingService.Indicators;
import fr.alptis.remuneration.reporting.ReportingService.LineFilter;
import fr.alptis.remuneration.reporting.ReportingService.Margin;
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
 * Interface endpoints for the restitution screens (section 8.12): the dashboards over a month range
 * (REP-01), the filtered "paid to whom, when, why" cross-search (REP-02/03), the margin by product,
 * insurer and distributor (REP-08), the operating indicators month by month (REP-09) and the
 * consolidated group view by entity and month (REP-07). Every read is confined to the caller's
 * visible entities; the consolidated view is reserved to cross-entity users (CR-70, REP-07).
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
     * Returns the dashboards of an entity over a month range, with the REP-03 filters (REP-01).
     *
     * @param entityCode  the entity code
     * @param from        the first month (inclusive), or null for the default range
     * @param to          the last month (inclusive), or null for the default range
     * @param product     the product code filter, or null
     * @param distributor the distributor filter, or null
     * @param group       the distributor group filter, or null
     * @param delegation  the sales-delegation filter, or null
     * @param nature      the nature filter, or null
     * @return the dashboard view
     */
    @GET
    @Path("/{entity}/dashboard")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public Dashboard dashboard(@PathParam("entity") String entityCode, @QueryParam("from") String from,
                               @QueryParam("to") String to, @QueryParam("product") String product,
                               @QueryParam("distributor") String distributor, @QueryParam("group") String group,
                               @QueryParam("delegation") String delegation, @QueryParam("nature") String nature) {
        requireScope(entityCode);
        return reporting.dashboard(entityCode, month(from), month(to), filter(product, distributor, group,
                delegation, nature));
    }

    /**
     * Returns the operating indicators of an entity month by month over a range (REP-09).
     *
     * @param entityCode the entity code
     * @param from       the first month (inclusive), or null for the default range
     * @param to         the last month (inclusive), or null for the default range
     * @return the indicators view
     */
    @GET
    @Path("/{entity}/indicators")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public Indicators indicators(@PathParam("entity") String entityCode, @QueryParam("from") String from,
                                 @QueryParam("to") String to) {
        requireScope(entityCode);
        return reporting.indicators(entityCode, month(from), month(to));
    }

    /**
     * Answers "paid to whom, when, why" with its filters (REP-02/03).
     *
     * @param entityCode  the entity code
     * @param product     the product code filter, or null
     * @param distributor the distributor filter, or null
     * @param group       the distributor group filter, or null
     * @param delegation  the sales-delegation filter, or null
     * @param nature      the nature filter, or null
     * @param month       the period month filter, or null
     * @return the matching rows
     */
    @GET
    @Path("/{entity}/search")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<SearchRow> search(@PathParam("entity") String entityCode, @QueryParam("product") String product,
                                  @QueryParam("distributor") String distributor, @QueryParam("group") String group,
                                  @QueryParam("delegation") String delegation, @QueryParam("nature") String nature,
                                  @QueryParam("month") String month) {
        requireScope(entityCode);
        return reporting.search(entityCode, filter(product, distributor, group, delegation, nature), month(month));
    }

    /**
     * Returns the margin by product, insurer and distributor of an entity (REP-08).
     *
     * @param entityCode the entity code
     * @return the margin view
     */
    @GET
    @Path("/{entity}/margin")
    @RolesAllowed({"BUSINESS_ADMIN", "FINANCE_CONTROLLER", "AUDITOR"})
    public Margin margin(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return reporting.margin(entityCode);
    }

    /**
     * Reconciles the commissions received from insurers with the expected amount (FIN-08).
     *
     * @param entityCode the entity code
     * @return the insurer reconciliation
     */
    @GET
    @Path("/{entity}/insurer-reconciliation")
    @RolesAllowed({"BUSINESS_ADMIN", "FINANCE_CONTROLLER", "AUDITOR"})
    public fr.alptis.remuneration.reporting.ReportingService.InsurerReconciliation insurerReconciliation(
            @PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return reporting.insurerReconciliation(entityCode);
    }

    /**
     * Exports the cross-search as CSV at line grain (REP-04).
     *
     * @param entityCode  the entity code
     * @param product     the product filter, or null
     * @param distributor the distributor filter, or null
     * @param group       the group filter, or null
     * @param delegation  the delegation filter, or null
     * @param nature      the nature filter, or null
     * @param month       the month filter, or null
     * @return the CSV text
     */
    @GET
    @Path("/{entity}/export/search")
    @Produces("text/csv")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public String exportSearch(@PathParam("entity") String entityCode, @QueryParam("product") String product,
                               @QueryParam("distributor") String distributor, @QueryParam("group") String group,
                               @QueryParam("delegation") String delegation, @QueryParam("nature") String nature,
                               @QueryParam("month") String month) {
        requireScope(entityCode);
        return reporting.exportCsv(entityCode, "SEARCH", null, null,
                filter(product, distributor, group, delegation, nature), month(month));
    }

    /**
     * Exports the dashboard commissions by month and nature as CSV (REP-04).
     *
     * @param entityCode  the entity code
     * @param from        the first month, or null
     * @param to          the last month, or null
     * @param product     the product filter, or null
     * @param distributor the distributor filter, or null
     * @param group       the group filter, or null
     * @param delegation  the delegation filter, or null
     * @param nature      the nature filter, or null
     * @return the CSV text
     */
    @GET
    @Path("/{entity}/export/dashboard")
    @Produces("text/csv")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public String exportDashboard(@PathParam("entity") String entityCode, @QueryParam("from") String from,
                                  @QueryParam("to") String to, @QueryParam("product") String product,
                                  @QueryParam("distributor") String distributor, @QueryParam("group") String group,
                                  @QueryParam("delegation") String delegation, @QueryParam("nature") String nature) {
        requireScope(entityCode);
        return reporting.exportCsv(entityCode, "DASHBOARD", month(from), month(to),
                filter(product, distributor, group, delegation, nature), null);
    }

    /**
     * Exports the operating indicators month by month as CSV (REP-04).
     *
     * @param entityCode the entity code
     * @param from       the first month, or null
     * @param to         the last month, or null
     * @return the CSV text
     */
    @GET
    @Path("/{entity}/export/indicators")
    @Produces("text/csv")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public String exportIndicators(@PathParam("entity") String entityCode, @QueryParam("from") String from,
                                   @QueryParam("to") String to) {
        requireScope(entityCode);
        return reporting.exportCsv(entityCode, "INDICATORS", month(from), month(to), LineFilter.none(), null);
    }

    /**
     * Exports the margin by product, insurer and distributor as CSV (REP-04).
     *
     * @param entityCode the entity code
     * @return the CSV text
     */
    @GET
    @Path("/{entity}/export/margin")
    @Produces("text/csv")
    @RolesAllowed({"BUSINESS_ADMIN", "FINANCE_CONTROLLER", "AUDITOR"})
    public String exportMargin(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return reporting.exportCsv(entityCode, "MARGIN", null, null, LineFilter.none(), null);
    }

    /**
     * Exports the consolidated group view as CSV, reserved to cross-entity users (REP-04, REP-07).
     *
     * @param from the first month, or null
     * @param to   the last month, or null
     * @return the CSV text
     */
    @GET
    @Path("/export/consolidated")
    @Produces("text/csv")
    @RolesAllowed({"AUDITOR", "IT_ADMIN", "BUSINESS_ADMIN", "FINANCE_CONTROLLER", "VIEWER", "BUSINESS_VALIDATOR", "OPERATOR"})
    public String exportConsolidated(@QueryParam("from") String from, @QueryParam("to") String to) {
        if (!scope.unrestricted()) {
            throw new ResourceNotFoundException("Vision consolidée réservée aux utilisateurs groupe");
        }
        return reporting.exportCsv(null, "CONSOLIDATED", month(from), month(to), LineFilter.none(), null);
    }

    /**
     * Returns the consolidated group view over a range (REP-07), reserved to cross-entity users.
     *
     * @param from the first month (inclusive), or null for the default range
     * @param to   the last month (inclusive), or null for the default range
     * @return the consolidated view
     */
    @GET
    @Path("/consolidated")
    @RolesAllowed({"AUDITOR", "IT_ADMIN", "BUSINESS_ADMIN", "FINANCE_CONTROLLER", "VIEWER", "BUSINESS_VALIDATOR", "OPERATOR"})
    public Consolidated consolidated(@QueryParam("from") String from, @QueryParam("to") String to) {
        if (!scope.unrestricted()) {
            throw new ResourceNotFoundException("Vision consolidée réservée aux utilisateurs groupe");
        }
        return reporting.consolidated(month(from), month(to));
    }

    /**
     * Parses an optional month parameter.
     *
     * @param value the ISO date, or null
     * @return the parsed date, or null
     */
    private static LocalDate month(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    /**
     * Builds a line filter from the query parameters.
     *
     * @param product     the product filter, or null
     * @param distributor the distributor filter, or null
     * @param group       the group filter, or null
     * @param delegation  the delegation filter, or null
     * @param nature      the nature filter, or null
     * @return the line filter
     */
    private static LineFilter filter(String product, String distributor, String group, String delegation,
                                     String nature) {
        return new LineFilter(product, distributor, group, delegation,
                nature == null ? null : LineNature.valueOf(nature));
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
