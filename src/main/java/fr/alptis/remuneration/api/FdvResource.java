package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.salesforce.SalesForceQueryService;
import fr.alptis.remuneration.salesforce.SalesForceQueryService.CollaboratorView;
import fr.alptis.remuneration.salesforce.SalesForceQueryService.ObjectiveView;
import fr.alptis.remuneration.salesforce.SalesForceQueryService.PlanView;
import fr.alptis.remuneration.salesforce.SalesForceQueryService.ResultView;
import fr.alptis.remuneration.salesforce.SalesForceService;
import fr.alptis.remuneration.salesforce.SalesForceService.PayExport;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the sales-force module (section 8.14): the variable plans, collaborators
 * and objectives, the monthly calculation and its results, the manager validation and adjustment
 * (FDV-13), the pay export (FDV-14) and a commercial's own variable (FDV-15). The entity partition
 * confines every read and action.
 */
@Path("/api/ui/v1/fdv")
public class FdvResource {

    private final SalesForceService service;
    private final SalesForceQueryService query;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param service     the calculation service
     * @param query       the read model
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public FdvResource(SalesForceService service, SalesForceQueryService query, CurrentUser currentUser,
                       EntityScope scope) {
        this.service = service;
        this.query = query;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Returns the variable plans of an entity (FDV-04).
     *
     * @param entityCode the entity code
     * @return the plan views
     */
    @GET
    @Path("/{entity}/plans")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "SALES_MANAGER", "AUDITOR"})
    public List<PlanView> plans(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return query.plans(entityCode);
    }

    /**
     * Returns the collaborators of an entity (FDV-01).
     *
     * @param entityCode the entity code
     * @return the collaborator views
     */
    @GET
    @Path("/{entity}/collaborators")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "SALES_MANAGER", "AUDITOR"})
    public List<CollaboratorView> collaborators(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return query.collaborators(entityCode);
    }

    /**
     * Returns the objectives of an entity (FDV-03).
     *
     * @param entityCode the entity code
     * @return the objective views
     */
    @GET
    @Path("/{entity}/objectives")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "SALES_MANAGER", "AUDITOR"})
    public List<ObjectiveView> objectives(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return query.objectives(entityCode);
    }

    /**
     * Returns the variable results of a period (validation screen, FDV-13).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the result views
     */
    @GET
    @Path("/{entity}/{month}/results")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "SALES_MANAGER", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<ResultView> results(@PathParam("entity") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.results(entityCode, LocalDate.parse(month));
    }

    /**
     * Runs the monthly variable calculation (FDV-05), launched by the business administrator.
     *
     * @param entityCode the entity code
     * @param month      the processing month
     * @return the results after the run
     */
    @POST
    @Path("/{entity}/{month}/run")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public List<ResultView> run(@PathParam("entity") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        service.runMonth(entityCode, LocalDate.parse(month));
        return query.results(entityCode, LocalDate.parse(month));
    }

    /**
     * Validates a result (FDV-13), performed by the regional manager.
     *
     * @param id the result id
     * @return the updated result
     */
    @POST
    @Path("/results/{id}/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"SALES_MANAGER", "BUSINESS_ADMIN"})
    public ValidationResponse validate(@PathParam("id") Long id) {
        return new ValidationResponse(id, service.validate(id, currentUser.username()).status.name());
    }

    /**
     * Records a manager adjustment (FDV-13).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param request    the adjustment request
     * @return the adjustment result id
     */
    @POST
    @Path("/{entity}/{month}/adjust")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"SALES_MANAGER", "BUSINESS_ADMIN"})
    public AdjustmentResponse adjust(@PathParam("entity") String entityCode, @PathParam("month") String month,
                                     AdjustmentRequest request) {
        requireScope(entityCode);
        return new AdjustmentResponse(service.adjust(entityCode, LocalDate.parse(month), request.externalId(),
                request.amount(), request.reason(), currentUser.username()).getId());
    }

    /**
     * Produces the monthly pay export (FDV-14), launched by the Finance controller.
     *
     * @param entityCode the entity code
     * @param month      the computed period month
     * @return the pay export
     */
    @POST
    @Path("/{entity}/{month}/pay-export")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"FINANCE_CONTROLLER"})
    public PayExport payExport(@PathParam("entity") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return service.payExport(entityCode, LocalDate.parse(month), currentUser.username());
    }

    /**
     * Returns a commercial's own variable and history (FDV-15).
     *
     * @param entityCode the entity code
     * @param externalId the employee external id
     * @return the result views
     */
    @GET
    @Path("/{entity}/my-variable/{externalId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"SALES_REP", "SALES_MANAGER", "BUSINESS_ADMIN", "AUDITOR"})
    public List<ResultView> myVariable(@PathParam("entity") String entityCode,
                                       @PathParam("externalId") String externalId) {
        requireScope(entityCode);
        return query.myVariable(entityCode, externalId);
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

    /**
     * A manager adjustment request.
     *
     * @param externalId the employee external id
     * @param amount     the adjustment amount
     * @param reason     the adjustment reason
     */
    public record AdjustmentRequest(String externalId, BigDecimal amount, String reason) {
    }

    /**
     * The result of an adjustment: its id.
     *
     * @param id the adjustment result id
     */
    public record AdjustmentResponse(Long id) {
    }

    /**
     * The result of a validation: the result id and its new status.
     *
     * @param id     the result id
     * @param status the new status
     */
    public record ValidationResponse(Long id, String status) {
    }
}
