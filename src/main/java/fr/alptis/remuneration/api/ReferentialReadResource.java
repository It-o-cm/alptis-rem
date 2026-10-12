package fr.alptis.remuneration.api;

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
import jakarta.ws.rs.GET;
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

    /**
     * Creates the resource.
     *
     * @param query the referential query service
     */
    @Inject
    public ReferentialReadResource(ReferentialQueryService query) {
        this.query = query;
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
}
