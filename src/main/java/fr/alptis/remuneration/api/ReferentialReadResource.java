package fr.alptis.remuneration.api;

import fr.alptis.remuneration.integration.ExternalCodeAdminService;
import fr.alptis.remuneration.integration.ReferentialQueryService;
import fr.alptis.remuneration.integration.ReferentialQueryService.BankAccountView;
import fr.alptis.remuneration.integration.ReferentialQueryService.ExternalCodeView;
import fr.alptis.remuneration.integration.ReferentialQueryService.PayeeRuleView;
import fr.alptis.remuneration.integration.ReferentialQueryService.ProductView;
import fr.alptis.remuneration.integration.ReferentialQueryService.QualityIndicatorView;
import fr.alptis.remuneration.integration.ReferentialQueryService.ReplacementOrderView;
import fr.alptis.remuneration.integration.ReferentialQueryService.TransferView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Interface endpoints for the referential consultation screens: products (REF-01), quality
 * indicators (REF-07) and bank accounts (REF-05, IBAN masked). All partitioned by entity.
 */
@Path("/api/ui/v1")
public class ReferentialReadResource {

    private final ReferentialQueryService query;
    private final ExternalCodeAdminService externalCodes;

    /**
     * Creates the resource.
     *
     * @param query         the referential query service
     * @param externalCodes the external-code correspondence writer
     */
    @Inject
    public ReferentialReadResource(ReferentialQueryService query, ExternalCodeAdminService externalCodes) {
        this.query = query;
        this.externalCodes = externalCodes;
    }

    /**
     * Lists the products of the user's entities.
     *
     * @return the product views
     */
    @GET
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ProductView> products() {
        return query.products();
    }

    /**
     * Lists the insurers (REF-01).
     *
     * @return the insurer views
     */
    @GET
    @Path("/insurers")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ReferentialQueryService.InsurerView> insurers() {
        return query.insurers();
    }

    /**
     * Lists the product ranges (REF-01).
     *
     * @return the range views
     */
    @GET
    @Path("/ranges")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ReferentialQueryService.RangeView> ranges() {
        return query.ranges();
    }

    /**
     * Lists the product options of the user's entities (REF-01).
     *
     * @return the option views
     */
    @GET
    @Path("/options")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ReferentialQueryService.OptionView> options() {
        return query.options();
    }

    /**
     * Lists the distributor groupings (CPT-02, REF-03).
     *
     * @return the grouping views
     */
    @GET
    @Path("/groups")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ReferentialQueryService.GroupView> groups() {
        return query.groups();
    }

    /**
     * Returns a grouping and its members (CPT-02).
     *
     * @param code the grouping code
     * @return the grouping detail
     */
    @GET
    @Path("/groups/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public ReferentialQueryService.GroupDetail group(@PathParam("code") String code) {
        return query.groupDetail(code);
    }

    /**
     * Lists the distinct sales delegations the user may see, so a delegation filter is picked from a
     * list instead of typed (REP-03, ADM-03).
     *
     * @return the sales-delegation codes
     */
    @GET
    @Path("/delegations")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<String> delegations() {
        return query.delegations();
    }

    /**
     * Lists the production codes the user may see, so a member statement is requested from a list
     * instead of typing a code (BRD-11, ADM-03).
     *
     * @return the production-code views
     */
    @GET
    @Path("/production-codes")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ReferentialQueryService.ProductionCodeOption> productionCodes() {
        return query.productionCodes();
    }

    /**
     * Lists the quality indicators of the user's entities.
     *
     * @return the quality-indicator views
     */
    @GET
    @Path("/quality-indicators")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<QualityIndicatorView> qualityIndicators() {
        return query.qualityIndicators();
    }

    /**
     * Lists the bank accounts of the user's entities, with masked IBANs.
     *
     * @return the bank-account views
     */
    @GET
    @Path("/bank-accounts")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<BankAccountView> bankAccounts() {
        return query.bankAccounts();
    }

    /**
     * Lists the portfolio transfers of the user's entities.
     *
     * @return the transfer views
     */
    @GET
    @Path("/portfolio-transfers")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "AUDITOR"})
    public List<TransferView> portfolioTransfers() {
        return query.portfolioTransfers();
    }

    /**
     * Lists the replacement orders of the user's entities.
     *
     * @return the replacement-order views
     */
    @GET
    @Path("/replacement-orders")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "AUDITOR"})
    public List<ReplacementOrderView> replacementOrders() {
        return query.replacementOrders();
    }

    /**
     * Lists the payment rules of the user's entities, with their shares.
     *
     * @return the payment-rule views
     */
    @GET
    @Path("/payee-rules")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<PayeeRuleView> payeeRules() {
        return query.payeeRules();
    }

    /**
     * Lists the external-code correspondences of the user's entities.
     *
     * @return the external-code views
     */
    @GET
    @Path("/external-codes")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR", "IT_ADMIN"})
    public List<ExternalCodeView> externalCodes() {
        return query.externalCodes();
    }

    /**
     * Maps a source system's contract identifier to an internal contract (REF-04, INT-22). The next
     * calculation releases the events held on that unknown reference.
     *
     * @param request the correspondence to add
     * @return the created correspondence view
     */
    @POST
    @Path("/external-codes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "IT_ADMIN"})
    public ExternalCodeView mapContract(MapContractRequest request) {
        return externalCodes.mapContract(request.entityCode(), request.sourceSystem(), request.externalCode(),
                request.internalContractNumber());
    }

    /**
     * Request to map a source system's contract identifier to an internal contract (REF-04).
     *
     * @param entityCode             the entity the contract belongs to
     * @param sourceSystem           the source system the external code is expressed in
     * @param externalCode           the contract identifier in that source system
     * @param internalContractNumber the internal contract number the code maps to
     */
    public record MapContractRequest(String entityCode, String sourceSystem, String externalCode,
                                     String internalContractNumber) {
    }
}
