package fr.alptis.remuneration.api;

import fr.alptis.remuneration.cycle.CycleQueryService;
import fr.alptis.remuneration.cycle.CycleQueryService.PeriodDetail;
import fr.alptis.remuneration.cycle.CycleQueryService.PeriodSummary;
import fr.alptis.remuneration.cycle.PeriodService;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
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
 * Interface endpoints for the cycle screens (WKF-01, WKF-04, section 7): the advancement of the open
 * periods of the visible entities, the detail of one period, and its state transitions driven by the
 * caller's roles.
 */
@Path("/api/ui/v1/periods")
public class CycleResource {

    private final CycleQueryService query;
    private final PeriodService periods;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param query       the cycle read model
     * @param periods     the period cycle service
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public CycleResource(CycleQueryService query, PeriodService periods, CurrentUser currentUser, EntityScope scope) {
        this.query = query;
        this.periods = periods;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Lists the open periods of the visible entities (WKF-04).
     *
     * @return the period summaries
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<PeriodSummary> list() {
        return query.openPeriods(scope.unrestricted() ? null : scope.visibleCodes());
    }

    /**
     * Returns the detail of one entity's period.
     *
     * @param entityCode the entity code
     * @param month      the period month (ISO date of the first day)
     * @return the period detail
     */
    @GET
    @Path("/{entityCode}/{month}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public PeriodDetail detail(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.detail(entityCode, LocalDate.parse(month));
    }

    /**
     * Moves one period to a new state (WKF-01, section 7.1).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param request    the transition request
     * @return the period summary in its new state
     */
    @POST
    @Path("/{entityCode}/{month}/transition")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER"})
    public PeriodSummary transition(@PathParam("entityCode") String entityCode, @PathParam("month") String month,
                                    TransitionRequest request) {
        requireScope(entityCode);
        Period period = periods.transition(entityCode, LocalDate.parse(month),
                PeriodState.valueOf(request.toState()), currentUser.appRoles(), currentUser.username(),
                request.reason());
        return new PeriodSummary(entityCode, period.entity.name, period.periodMonth, period.state);
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
     * Request body of a transition.
     *
     * @param toState the target state name
     * @param reason  the reason, required for a Finance refusal
     */
    public record TransitionRequest(String toState, String reason) {
    }
}
