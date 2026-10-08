package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleBasis;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.rules.RuleConfigService;
import fr.alptis.remuneration.rules.RuleConfigService.PlanView;
import fr.alptis.remuneration.rules.RuleConfigService.RuleView;
import fr.alptis.remuneration.rules.RuleConfigService.VersionInput;
import fr.alptis.remuneration.rules.RuleConfigService.VersionView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the rule-configuration screens (PAR-01 to PAR-08, PAR-12, PAR-14):
 * plans, rules, versions, their approval cycle and the annual duplication. Reads are open to the
 * business roles; writes and approval are reserved to the business administrator, the second of whom
 * approves (separation of duties, enforced server-side).
 */
@Path("/api/ui/v1")
public class RulesResource {

    private final RuleConfigService service;

    /**
     * Creates the resource.
     *
     * @param service the rule-configuration service
     */
    @Inject
    public RulesResource(RuleConfigService service) {
        this.service = service;
    }

    /**
     * Lists the plans of the user's entities.
     *
     * @return the plan views
     */
    @GET
    @Path("/plans")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<PlanView> plans() {
        return service.listPlans();
    }

    /**
     * Creates a plan.
     *
     * @param request the plan request
     * @return a 201 response with the created plan
     */
    @POST
    @Path("/plans")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response createPlan(PlanRequest request) {
        PlanView view = service.createPlan(request.entityCode(), request.code(), request.label(),
                request.direction(), request.vintage());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Duplicates a plan for the annual renewal.
     *
     * @param planCode the source plan code
     * @param request  the duplication request
     * @return a 201 response with the created plan
     */
    @POST
    @Path("/plans/{planCode}/duplicate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response duplicate(@PathParam("planCode") String planCode, DuplicateRequest request) {
        PlanView view = service.duplicatePlan(request.entityCode(), planCode, request.newCode(), request.newVintage());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Lists the rules of a plan.
     *
     * @param planCode   the plan code
     * @param entityCode the entity code
     * @return the rule views
     */
    @GET
    @Path("/plans/{planCode}/rules")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<RuleView> rules(@PathParam("planCode") String planCode,
                                @jakarta.ws.rs.QueryParam("entityCode") String entityCode) {
        return service.listRules(entityCode, planCode);
    }

    /**
     * Creates a rule in a plan.
     *
     * @param planCode the plan code
     * @param request  the rule request
     * @return a 201 response with the created rule
     */
    @POST
    @Path("/plans/{planCode}/rules")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response createRule(@PathParam("planCode") String planCode, RuleRequest request) {
        RuleView view = service.createRule(request.entityCode(), planCode, request.code(), request.label(),
                request.family());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Lists the versions of a rule, with their detected conflicts.
     *
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param entityCode the entity code
     * @return the version views
     */
    @GET
    @Path("/plans/{planCode}/rules/{ruleCode}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<VersionView> versions(@PathParam("planCode") String planCode, @PathParam("ruleCode") String ruleCode,
                                      @jakarta.ws.rs.QueryParam("entityCode") String entityCode) {
        return service.listVersions(entityCode, planCode, ruleCode);
    }

    /**
     * Adds a draft version to a rule.
     *
     * @param planCode the plan code
     * @param ruleCode the rule code
     * @param request  the version request
     * @return a 201 response with the created version
     */
    @POST
    @Path("/plans/{planCode}/rules/{ruleCode}/versions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response addVersion(@PathParam("planCode") String planCode, @PathParam("ruleCode") String ruleCode,
                               VersionRequest request) {
        VersionView view = service.addVersion(request.entityCode(), planCode, ruleCode, request.toInput());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Approves a draft version (second administrator, no conflict).
     *
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param versionNo  the version number
     * @param entityCode the entity code
     * @return the approved version view
     */
    @POST
    @Path("/plans/{planCode}/rules/{ruleCode}/versions/{versionNo}/approve")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public VersionView approve(@PathParam("planCode") String planCode, @PathParam("ruleCode") String ruleCode,
                               @PathParam("versionNo") int versionNo,
                               @jakarta.ws.rs.QueryParam("entityCode") String entityCode) {
        return service.approveVersion(entityCode, planCode, ruleCode, versionNo);
    }

    /**
     * Returns the plausibility alerts (PAR-14) for a parameter payload.
     *
     * @param request the plausibility request
     * @return the alert messages
     */
    @POST
    @Path("/rule-versions/plausibility")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public List<String> plausibility(PlausibilityRequest request) {
        return service.plausibilityAlerts(request.parameters());
    }

    /**
     * Plan creation request.
     *
     * @param entityCode the entity code
     * @param code       the plan code
     * @param label      the plan label
     * @param direction  the direction
     * @param vintage    the vintage
     */
    public record PlanRequest(String entityCode, String code, String label, Direction direction, Short vintage) {
    }

    /**
     * Plan duplication request.
     *
     * @param entityCode the entity code
     * @param newCode    the new plan code
     * @param newVintage the new vintage
     */
    public record DuplicateRequest(String entityCode, String newCode, Short newVintage) {
    }

    /**
     * Rule creation request.
     *
     * @param entityCode the entity code
     * @param code       the rule code
     * @param label      the rule label
     * @param family     the calculation family
     */
    public record RuleRequest(String entityCode, String code, String label, RuleFamily family) {
    }

    /**
     * Rule-version creation request.
     *
     * @param entityCode        the entity code
     * @param validFrom         the validity start
     * @param validTo           the validity end
     * @param versionAnchor     the historisation anchor
     * @param scope             the perimeter (JSON)
     * @param triggerEventTypes the triggering event types
     * @param basis             the basis
     * @param outputNature      the produced line nature
     * @param parameters        the family parameters (JSON)
     * @param clawbackPolicy    the clawback policy (JSON)
     * @param paymentSchedule   the payment schedule (JSON)
     * @param priority          the priority
     * @param exclusionGroup    the exclusion group
     */
    public record VersionRequest(String entityCode, LocalDate validFrom, LocalDate validTo, VersionAnchor versionAnchor,
                                 String scope, List<String> triggerEventTypes, RuleBasis basis, LineNature outputNature,
                                 String parameters, String clawbackPolicy, String paymentSchedule, Integer priority,
                                 String exclusionGroup) {

        /**
         * Converts this request to the service input.
         *
         * @return the version input
         */
        VersionInput toInput() {
            return new VersionInput(validFrom, validTo, versionAnchor, scope, triggerEventTypes, basis, outputNature,
                    parameters, clawbackPolicy, paymentSchedule, priority, exclusionGroup);
        }
    }

    /**
     * Plausibility request.
     *
     * @param parameters the family parameters (JSON)
     */
    public record PlausibilityRequest(String parameters) {
    }
}
