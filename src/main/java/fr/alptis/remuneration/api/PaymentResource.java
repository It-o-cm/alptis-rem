package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.payment.PaymentQueryService;
import fr.alptis.remuneration.payment.PaymentQueryService.LotDetail;
import fr.alptis.remuneration.payment.PaymentQueryService.LotView;
import fr.alptis.remuneration.payment.PaymentService;
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
 * Interface endpoints for the payment screens (section 8.9, 7.1): the lots of a period and the detail
 * of a lot, the preparation of a lot's orders, the Finance validation that emits the SEPA file, the
 * SEPA download, the recording of a bank result, and the creation of an off-cycle lot.
 */
@Path("/api/ui/v1/payment")
public class PaymentResource {

    private final PaymentQueryService query;
    private final PaymentService payment;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param query       the payment read model
     * @param payment     the payment service
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public PaymentResource(PaymentQueryService query, PaymentService payment, CurrentUser currentUser,
                           EntityScope scope) {
        this.query = query;
        this.payment = payment;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Lists the payment lots of an entity's period.
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the lot summaries
     */
    @GET
    @Path("/{entityCode}/{month}/lots")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<LotView> lots(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.lots(entityCode, LocalDate.parse(month));
    }

    /**
     * Returns the detail of a lot.
     *
     * @param batchId the batch id
     * @return the lot detail
     */
    @GET
    @Path("/lots/{batchId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public LotDetail lot(@PathParam("batchId") Long batchId) {
        LotDetail detail = query.lot(batchId);
        requireBatchScope(batchId);
        return detail;
    }

    /**
     * Prepares a lot's orders (IBAN resolution, cheque routing, exclusions).
     *
     * @param batchId the batch id
     * @return the lot detail after preparation
     */
    @POST
    @Path("/lots/{batchId}/prepare")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public LotDetail prepare(@PathParam("batchId") Long batchId) {
        requireBatchScope(batchId);
        payment.prepareOrderDetails(batchId);
        return query.lot(batchId);
    }

    /**
     * Validates a lot for Finance and emits its SEPA file.
     *
     * @param batchId the batch id
     * @return the lot detail after validation
     */
    @POST
    @Path("/lots/{batchId}/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("FINANCE_CONTROLLER")
    public LotDetail validate(@PathParam("batchId") Long batchId) {
        requireBatchScope(batchId);
        payment.validateFinance(batchId, currentUser.username());
        return query.lot(batchId);
    }

    /**
     * Returns the SEPA file of a lot.
     *
     * @param batchId the batch id
     * @return the SEPA XML
     */
    @GET
    @Path("/lots/{batchId}/sepa")
    @Produces(MediaType.APPLICATION_XML)
    @RolesAllowed({"FINANCE_CONTROLLER", "AUDITOR"})
    public String sepa(@PathParam("batchId") Long batchId) {
        requireBatchScope(batchId);
        return payment.sepaXml(batchId);
    }

    /**
     * Records the bank result of an order (PAY-06).
     *
     * @param orderId the order id
     * @param request the bank result
     * @return the order status after recording
     */
    @POST
    @Path("/orders/{orderId}/result")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("FINANCE_CONTROLLER")
    public ResultResponse result(@PathParam("orderId") Long orderId, ResultRequest request) {
        PaymentOrder order = PaymentOrder.findById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Ordre inconnu : " + orderId);
        }
        requireScope(order.batch.entity.code);
        PaymentOrder updated = payment.recordBankResult(orderId, request.settled(), request.reason());
        return new ResultResponse(updated.getId(), updated.status.name());
    }

    /**
     * Creates an off-cycle lot for a distributor (PAY-07).
     *
     * @param entityCode  the entity code
     * @param month       the period month
     * @param partnerCode the distributor partner code
     * @return the created lot summary
     */
    @POST
    @Path("/{entityCode}/{month}/off-cycle/{partnerCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public LotView offCycle(@PathParam("entityCode") String entityCode, @PathParam("month") String month,
                            @PathParam("partnerCode") String partnerCode) {
        requireScope(entityCode);
        PaymentBatch batch = payment.offCycleLot(entityCode, partnerCode, LocalDate.parse(month));
        return new LotView(batch.getId(), batch.batchType, batch.status, batch.batchReference, batch.totalAmount,
                PaymentOrder.count("batch = ?1", batch), 0L);
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
     * Checks the batch's entity is within the caller's partition.
     *
     * @param batchId the batch id
     */
    private void requireBatchScope(Long batchId) {
        PaymentBatch batch = PaymentBatch.findById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("Lot inconnu : " + batchId);
        }
        requireScope(batch.entity.code);
    }

    /**
     * Request body of a bank result.
     *
     * @param settled whether the transfer settled
     * @param reason  the rejection reason, when not settled
     */
    public record ResultRequest(boolean settled, String reason) {
    }

    /**
     * Response of a bank result.
     *
     * @param id     the order id
     * @param status the order status
     */
    public record ResultResponse(Long id, String status) {
    }
}
