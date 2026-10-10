package fr.alptis.remuneration.api;

import fr.alptis.remuneration.cycle.CycleQueryService;
import fr.alptis.remuneration.cycle.CycleQueryService.AnomalyView;
import fr.alptis.remuneration.cycle.CycleQueryService.CatalogEntry;
import fr.alptis.remuneration.cycle.ControlService;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the controls screen (CTL-01 to CTL-05, section 8.6): running the controls
 * of a period, listing its anomalies, tracing a decision on one, and reading the control catalogue.
 */
@Path("/api/ui/v1/controls")
public class ControlsResource {

    private final ControlService controls;
    private final CycleQueryService query;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param controls    the control service
     * @param query       the cycle read model
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public ControlsResource(ControlService controls, CycleQueryService query, CurrentUser currentUser,
                            EntityScope scope) {
        this.controls = controls;
        this.query = query;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Runs the controls of an entity's period.
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the number of anomalies found
     */
    @POST
    @Path("/{entityCode}/{month}/run")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public RunResult run(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return new RunResult(controls.run(entityCode, LocalDate.parse(month)));
    }

    /**
     * Lists the anomalies of an entity's period.
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the anomaly views
     */
    @GET
    @Path("/{entityCode}/{month}/anomalies")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<AnomalyView> anomalies(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.anomalies(entityCode, LocalDate.parse(month));
    }

    /**
     * Counts a period's anomalies by severity and by control family (for the controls chart).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the anomaly breakdown
     */
    @GET
    @Path("/{entityCode}/{month}/anomaly-breakdown")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public fr.alptis.remuneration.cycle.CycleQueryService.AnomalyBreakdown anomalyBreakdown(
            @PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.anomalyBreakdown(entityCode, LocalDate.parse(month));
    }

    /**
     * Reads the control catalogue of an entity.
     *
     * @param entityCode the entity code
     * @return the catalogue entries
     */
    @GET
    @Path("/{entityCode}/catalog")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<CatalogEntry> catalog(@PathParam("entityCode") String entityCode) {
        requireScope(entityCode);
        return query.catalog(entityCode);
    }

    /**
     * Records a decision on an anomaly (CTL-05).
     *
     * @param anomalyId the anomaly id
     * @param request   the decision request
     * @return the decided anomaly summary
     */
    @POST
    @Path("/anomalies/{anomalyId}/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public DecisionResult decide(@PathParam("anomalyId") Long anomalyId, DecisionRequest request) {
        ControlResult result = controls.decide(anomalyId, ControlDecision.valueOf(request.decision()),
                currentUser.username(), request.justification());
        return new DecisionResult(result.getId(), result.decision);
    }

    /**
     * Checks the entity is within the caller's partition.
     *
     * @param entityCode the entity code
     */
    private void requireScope(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
    }

    /**
     * Outcome of a control run.
     *
     * @param anomalies the number of anomalies found
     */
    public record RunResult(int anomalies) {
    }

    /**
     * Request body of a decision.
     *
     * @param decision      the decision name
     * @param justification the justification
     */
    public record DecisionRequest(String decision, String justification) {
    }

    /**
     * Result of a decision.
     *
     * @param id       the anomaly id
     * @param decision the decision taken
     */
    public record DecisionResult(Long id, ControlDecision decision) {
    }
}
