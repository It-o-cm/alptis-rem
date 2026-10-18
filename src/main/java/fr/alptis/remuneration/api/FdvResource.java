package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.salesforce.SalesForceQueryService;
import fr.alptis.remuneration.salesforce.SalesForceQueryService.CollaboratorView;
import fr.alptis.remuneration.salesforce.SalesForceQueryService.MeasureView;
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
     * Creates or updates a variable plan of a post (FDV-04), by the business administrator.
     *
     * @param entityCode the entity code
     * @param request    the plan fields
     * @return a 201 response with the saved plan
     */
    @POST
    @Path("/{entity}/plans")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public jakarta.ws.rs.core.Response savePlan(@PathParam("entity") String entityCode, PlanRequest request) {
        requireScope(entityCode);
        var saved = service.savePlan(entityCode, request.toInput());
        return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.CREATED)
                .entity(new PlanView(saved.jobCode, saved.component, saved.envelopeAmount, saved.instalmentAmount,
                        saved.parameters, saved.validFrom, saved.validTo))
                .build();
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
     * Creates a collaborator managed at the screen (FDV-01), by the business administrator.
     *
     * @param entityCode the entity code
     * @param request    the collaborator fields
     * @return a 201 response with the created collaborator
     */
    @POST
    @Path("/{entity}/collaborators")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public jakarta.ws.rs.core.Response createCollaborator(@PathParam("entity") String entityCode,
                                                          CollaboratorRequest request) {
        requireScope(entityCode);
        service.createCollaborator(entityCode, request.toInput(request.externalId()));
        return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.CREATED)
                .entity(collaborator(entityCode, request.externalId())).build();
    }

    /**
     * Updates a collaborator's post, team, region, manager and dates (FDV-01), by the business
     * administrator.
     *
     * @param entityCode the entity code
     * @param externalId the collaborator's external id
     * @param request    the new collaborator fields
     * @return the updated collaborator view
     */
    @jakarta.ws.rs.PUT
    @Path("/{entity}/collaborators/{externalId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public CollaboratorView updateCollaborator(@PathParam("entity") String entityCode,
                                               @PathParam("externalId") String externalId,
                                               CollaboratorRequest request) {
        requireScope(entityCode);
        service.updateCollaborator(entityCode, externalId, request.toInput(externalId));
        return collaborator(entityCode, externalId);
    }

    /**
     * Reads back one collaborator by external id after a write.
     *
     * @param entityCode the entity code
     * @param externalId the collaborator's external id
     * @return the collaborator view
     */
    private CollaboratorView collaborator(String entityCode, String externalId) {
        return query.collaborators(entityCode).stream()
                .filter(c -> c.externalId().equals(externalId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Collaborateur inconnu : " + externalId));
    }

    /**
     * Returns the measures received for an entity's collaborators (FDV-02).
     *
     * @param entityCode the entity code
     * @return the measure views
     */
    @GET
    @Path("/{entity}/measures")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "SALES_MANAGER", "AUDITOR"})
    public List<MeasureView> measures(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return query.measures(entityCode);
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
     * Creates an objective (FDV-03), individual or collective, versioned, by the business administrator.
     *
     * @param entityCode the entity code
     * @param request    the objective fields
     * @return a 201 response with the created objective
     */
    @POST
    @Path("/{entity}/objectives")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public jakarta.ws.rs.core.Response createObjective(@PathParam("entity") String entityCode,
                                                       ObjectiveRequest request) {
        requireScope(entityCode);
        var created = service.createObjective(entityCode, request.toInput());
        return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.CREATED)
                .entity(new ObjectiveView(created.employee == null ? null : created.employee.externalId,
                        created.objectiveType.name(), created.periodFrom, created.periodTo, created.targetValue,
                        created.versionNo))
                .build();
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
     * Runs the annual individual regularization of January N+1 (FDV-06), launched by the business
     * administrator.
     *
     * @param entityCode the entity code
     * @param year       the regularized year
     * @return the number of regularization results written
     */
    @POST
    @Path("/{entity}/{year}/annual-regularization")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public java.util.Map<String, Integer> annualRegularization(@PathParam("entity") String entityCode,
                                                               @PathParam("year") int year) {
        requireScope(entityCode);
        return java.util.Map.of("regularized", service.runAnnualRegularization(entityCode, year));
    }

    /**
     * Computes the quarterly qualitative variable of the quarter ending at a month (FDV-07), launched by
     * the business administrator.
     *
     * @param entityCode the entity code
     * @param month      the last month of the quarter (ISO date)
     * @return the number of results written
     */
    @POST
    @Path("/{entity}/{month}/quarterly-qualitative")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public java.util.Map<String, Integer> quarterlyQualitative(@PathParam("entity") String entityCode,
                                                               @PathParam("month") String month) {
        requireScope(entityCode);
        return java.util.Map.of("computed", service.runQuarterlyQualitative(entityCode, LocalDate.parse(month)));
    }

    /**
     * Settles the annual collective quantitative variable in a month (FDV-08), launched by the business
     * administrator. The settled year is the month's year minus one: January N+1 settles, June N+1
     * regularizes.
     *
     * @param entityCode the entity code
     * @param month      the settlement month (ISO date, January or June N+1)
     * @return the number of collective results written
     */
    @POST
    @Path("/{entity}/{month}/annual-collective")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public java.util.Map<String, Integer> annualCollective(@PathParam("entity") String entityCode,
                                                           @PathParam("month") String month) {
        requireScope(entityCode);
        return java.util.Map.of("collective", service.runAnnualCollective(entityCode, LocalDate.parse(month)));
    }

    /**
     * Settles the annual variable of animators and managers in a month (FDV-09), launched by the business
     * administrator. The measured year is the month's year minus one.
     *
     * @param entityCode the entity code
     * @param month      the settlement month (ISO date, January N+1)
     * @return the number of manager results written
     */
    @POST
    @Path("/{entity}/{month}/manager-variable")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public java.util.Map<String, Integer> managerVariable(@PathParam("entity") String entityCode,
                                                          @PathParam("month") String month) {
        requireScope(entityCode);
        return java.util.Map.of("managers", service.runManagerVariable(entityCode, LocalDate.parse(month)));
    }

    /**
     * Settles the direct-sales variable in a month (FDV-10), launched by the business administrator: paid
     * at the January fraction in January N+1, settled in full in June N+1. The measured year is the
     * month's year minus one.
     *
     * @param entityCode the entity code
     * @param month      the settlement month (ISO date, January or June N+1)
     * @return the number of direct-sales results written
     */
    @POST
    @Path("/{entity}/{month}/direct-sales-variable")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public java.util.Map<String, Integer> directSalesVariable(@PathParam("entity") String entityCode,
                                                              @PathParam("month") String month) {
        requireScope(entityCode);
        return java.util.Map.of("directSales", service.runDirectSalesVariable(entityCode, LocalDate.parse(month)));
    }

    /**
     * Settles the termination reduction in a month (FDV-12), launched by the business administrator: it
     * reduces the variable for contracts terminated before the parameterized duration. The measured year
     * is the month's year minus one.
     *
     * @param entityCode the entity code
     * @param month      the settlement month (ISO date, January N+1)
     * @return the number of reduction results written
     */
    @POST
    @Path("/{entity}/{month}/termination-reduction")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public java.util.Map<String, Integer> terminationReduction(@PathParam("entity") String entityCode,
                                                               @PathParam("month") String month) {
        requireScope(entityCode);
        return java.util.Map.of("reductions", service.runTerminationReduction(entityCode, LocalDate.parse(month)));
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
     * Returns the per-person synthesis of the variables to pay for a month (FDV-14).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the pay-summary rows
     */
    @GET
    @Path("/{entity}/{month}/pay-summary")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"FINANCE_CONTROLLER", "BUSINESS_ADMIN", "SALES_MANAGER", "AUDITOR"})
    public List<SalesForceQueryService.PaySummaryRow> paySummary(@PathParam("entity") String entityCode,
                                                                 @PathParam("month") String month) {
        requireScope(entityCode);
        return query.paySummary(entityCode, LocalDate.parse(month));
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
        requireCollaboratorVisibility(entityCode, externalId);
        return query.myVariable(entityCode, externalId);
    }

    /**
     * Confines the commercial screen (FDV-10): a sales representative sees only its own variable, a
     * sales manager only its own and its team's (the collaborators it manages); the business
     * administrator and the auditor see everyone. The collaborator is linked to the dev user by its
     * matricule (the username). Out of perimeter, the collaborator is reported as not found.
     *
     * @param entityCode the entity code
     * @param externalId the requested collaborator's matricule
     */
    private void requireCollaboratorVisibility(String entityCode, String externalId) {
        if (currentUser.hasRole("BUSINESS_ADMIN") || currentUser.hasRole("AUDITOR")) {
            return;
        }
        String me = currentUser.username();
        if (externalId.equals(me)) {
            return;
        }
        if (currentUser.hasRole("SALES_MANAGER")) {
            fr.alptis.remuneration.domain.Employee target =
                    fr.alptis.remuneration.domain.Employee.<fr.alptis.remuneration.domain.Employee>find(
                            "entity.code = ?1 and externalId = ?2", entityCode, externalId).firstResult();
            if (target != null && target.manager != null && me.equals(target.manager.externalId)) {
                return;
            }
        }
        throw new ResourceNotFoundException("Collaborateur hors de votre périmètre : " + externalId);
    }

    /**
     * Simulates a commercial's monthly variable for a month, optionally with a hypothetical weighted
     * production (FDV-16).
     *
     * @param entityCode the entity code
     * @param externalId the commercial's external id
     * @param month      the month to simulate
     * @param production a hypothetical weighted production, or null to use the measured one
     * @return the simulation result
     */
    @GET
    @Path("/{entity}/simulate/{externalId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"SALES_REP", "SALES_MANAGER", "BUSINESS_ADMIN", "AUDITOR"})
    public SalesForceService.SimulationResult simulate(@PathParam("entity") String entityCode,
                                                       @PathParam("externalId") String externalId,
                                                       @jakarta.ws.rs.QueryParam("month") String month,
                                                       @jakarta.ws.rs.QueryParam("production") BigDecimal production) {
        requireScope(entityCode);
        requireCollaboratorVisibility(entityCode, externalId);
        return service.simulateMonthly(entityCode, externalId, LocalDate.parse(month), production);
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
     * A variable-plan create/update request (FDV-04).
     *
     * @param jobCode          the post
     * @param component        the variable component
     * @param envelopeAmount   the annual envelope
     * @param instalmentAmount the monthly instalment, or null
     * @param parameters       the criteria/scales/absence rules (JSON), or null
     * @param validFrom        the validity start
     * @param validTo          the validity end, or null
     */
    public record PlanRequest(JobCode jobCode, VariableComponent component, BigDecimal envelopeAmount,
                              BigDecimal instalmentAmount, String parameters, LocalDate validFrom, LocalDate validTo) {

        /**
         * Converts this request to a service input.
         *
         * @return the plan input
         */
        SalesForceService.PlanInput toInput() {
            return new SalesForceService.PlanInput(jobCode, component, envelopeAmount, instalmentAmount, parameters,
                    validFrom, validTo);
        }
    }

    /**
     * An objective create request (FDV-03).
     *
     * @param employeeExternalId the target employee, or null for a collective objective
     * @param jobCode            the post
     * @param objectiveType      the objective type
     * @param periodFrom         the period start
     * @param periodTo           the period end
     * @param targetValue        the target value
     */
    public record ObjectiveRequest(String employeeExternalId, JobCode jobCode, ObjectiveType objectiveType,
                                   LocalDate periodFrom, LocalDate periodTo, BigDecimal targetValue) {

        /**
         * Converts this request to a service input.
         *
         * @return the objective input
         */
        SalesForceService.ObjectiveInput toInput() {
            return new SalesForceService.ObjectiveInput(employeeExternalId, jobCode, objectiveType, periodFrom,
                    periodTo, targetValue);
        }
    }

    /**
     * A collaborator create/update request (FDV-01).
     *
     * @param externalId        the external id (ignored on update, taken from the path)
     * @param lastName          the last name
     * @param firstName         the first name
     * @param jobCode           the post
     * @param team              the team
     * @param region            the region
     * @param managerExternalId the manager's external id, or null
     * @param validFrom         the validity start
     * @param validTo           the validity end, or null
     */
    public record CollaboratorRequest(String externalId, String lastName, String firstName, JobCode jobCode,
                                      String team, String region, String managerExternalId, LocalDate validFrom,
                                      LocalDate validTo) {

        /**
         * Converts this request to a service input under the given external id.
         *
         * @param id the external id to use (the path id on update, the body id on create)
         * @return the collaborator input
         */
        SalesForceService.CollaboratorInput toInput(String id) {
            return new SalesForceService.CollaboratorInput(id, lastName, firstName, jobCode, team, region,
                    managerExternalId, validFrom, validTo);
        }
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
