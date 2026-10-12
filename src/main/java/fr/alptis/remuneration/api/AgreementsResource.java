package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.rules.AgreementService;
import fr.alptis.remuneration.rules.AgreementService.AdvanceView;
import fr.alptis.remuneration.rules.AgreementService.AgreementView;
import fr.alptis.remuneration.rules.AgreementService.EnrollmentView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for agreements, discount conventions (PAR-09) and enrollments to reserved
 * rules (PAR-10). Reads are open to the business roles; writes are reserved to the business
 * administrator.
 */
@Path("/api/ui/v1")
public class AgreementsResource {

    private final AgreementService service;

    /**
     * Creates the resource.
     *
     * @param service the agreement service
     */
    @Inject
    public AgreementsResource(AgreementService service) {
        this.service = service;
    }

    /**
     * Lists the agreements of the user's entities.
     *
     * @return the agreement views
     */
    @GET
    @Path("/agreements")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<AgreementView> agreements() {
        return service.listAgreements();
    }

    /**
     * Records an agreement.
     *
     * @param request the agreement request
     * @return a 201 response with the created agreement
     */
    @POST
    @Path("/agreements")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response createAgreement(AgreementRequest request) {
        AgreementView view = service.createAgreement(request.entityCode(), request.type(), request.partnerCode(),
                request.groupCode(), request.reference(), request.validFrom(), request.validTo(), request.signed());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Lists the discount conventions of the user's entities.
     *
     * @return the discount-convention views
     */
    @GET
    @Path("/advance-agreements")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<AdvanceView> advanceAgreements() {
        return service.listAdvanceAgreements();
    }

    /**
     * Returns the fiche of a distributor's discount convention (PAR-09, CPT-08, §6.4).
     *
     * @param entityCode  the entity code
     * @param partnerCode the distributor partner code
     * @return the convention detail
     */
    @GET
    @Path("/advance-agreements/{entityCode}/{partnerCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public AgreementService.AdvanceDetail advanceAgreement(@PathParam("entityCode") String entityCode,
                                                           @PathParam("partnerCode") String partnerCode) {
        return service.advanceDetail(entityCode, partnerCode);
    }

    /**
     * Records a discount convention.
     *
     * @param request the discount-convention request
     * @return a 201 response with the created convention
     */
    @POST
    @Path("/advance-agreements")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response createAdvanceAgreement(AdvanceRequest request) {
        AdvanceView view = service.createAdvanceAgreement(request.entityCode(), request.partnerCode(),
                request.validFrom(), request.validTo(), request.anticipationDays(), request.maxContracts(),
                request.unpaidRankThreshold());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Lists the enrollments of a rule.
     *
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param entityCode the entity code
     * @return the enrollment views
     */
    @GET
    @Path("/plans/{planCode}/rules/{ruleCode}/enrollments")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<EnrollmentView> enrollments(@PathParam("planCode") String planCode,
                                            @PathParam("ruleCode") String ruleCode,
                                            @QueryParam("entityCode") String entityCode) {
        return service.listEnrollments(entityCode, planCode, ruleCode);
    }

    /**
     * Enrolls one or several distributors to a reserved rule.
     *
     * @param planCode the plan code
     * @param ruleCode the rule code
     * @param request  the enrollment request
     * @return a 201 response with the created enrollment views
     */
    @POST
    @Path("/plans/{planCode}/rules/{ruleCode}/enrollments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response enroll(@PathParam("planCode") String planCode, @PathParam("ruleCode") String ruleCode,
                           EnrollRequest request) {
        List<EnrollmentView> views;
        if (request.partnerCodes() != null && !request.partnerCodes().isEmpty()) {
            views = service.enrollMass(request.entityCode(), planCode, ruleCode, request.partnerCodes(),
                    request.agreementRef(), request.validFrom());
        } else {
            views = List.of(service.enroll(request.entityCode(), planCode, ruleCode, request.partnerCode(),
                    request.groupCode(), request.agreementRef(), request.validFrom()));
        }
        return Response.status(Response.Status.CREATED).entity(views).build();
    }

    /**
     * Agreement creation request.
     *
     * @param entityCode  the entity code
     * @param type        the agreement type
     * @param partnerCode the distributor, if any
     * @param groupCode   the group, if any
     * @param reference   the reference
     * @param validFrom   the validity start
     * @param validTo     the validity end, if any
     * @param signed      whether it is recorded as signed
     */
    public record AgreementRequest(String entityCode, AgreementType type, String partnerCode, String groupCode,
                                   String reference, LocalDate validFrom, LocalDate validTo, boolean signed) {
    }

    /**
     * Discount-convention creation request.
     *
     * @param entityCode          the entity code
     * @param partnerCode         the distributor
     * @param validFrom           the validity start
     * @param validTo             the validity end
     * @param anticipationDays    the anticipation in days
     * @param maxContracts        the cap on enrollments
     * @param unpaidRankThreshold the unpaid rank triggering the clawback
     */
    public record AdvanceRequest(String entityCode, String partnerCode, LocalDate validFrom, LocalDate validTo,
                                 Integer anticipationDays, Integer maxContracts, Short unpaidRankThreshold) {
    }

    /**
     * Enrollment creation request (unit when partnerCode/groupCode set, mass when partnerCodes set).
     *
     * @param entityCode   the entity code
     * @param partnerCode  the distributor, for a unit enrollment
     * @param groupCode    the group, for a unit enrollment
     * @param partnerCodes the distributors, for a mass enrollment
     * @param agreementRef the agreement reference
     * @param validFrom    the enrollment start
     */
    public record EnrollRequest(String entityCode, String partnerCode, String groupCode, List<String> partnerCodes,
                                String agreementRef, LocalDate validFrom) {
    }
}
