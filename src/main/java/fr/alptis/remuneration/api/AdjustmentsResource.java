package fr.alptis.remuneration.api;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.cycle.AdjustmentService;
import fr.alptis.remuneration.cycle.CycleQueryService;
import fr.alptis.remuneration.cycle.CycleQueryService.AdjustmentView;
import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.AdjustmentStatus;
import fr.alptis.remuneration.domain.AdjustmentType;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
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
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the adjustments screen (REG-01 to REG-05, section 8.5): listing a period's
 * adjustments, submitting one, and its second-user approval which applies the resulting line.
 */
@Path("/api/ui/v1/adjustments")
public class AdjustmentsResource {

    private final AdjustmentService adjustments;
    private final AccountService accounts;
    private final CycleQueryService query;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param adjustments the adjustment service
     * @param accounts    the distributor-account service
     * @param query       the cycle read model
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public AdjustmentsResource(AdjustmentService adjustments, AccountService accounts, CycleQueryService query,
                               CurrentUser currentUser, EntityScope scope) {
        this.adjustments = adjustments;
        this.accounts = accounts;
        this.query = query;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Lists the adjustments of an entity's period.
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the adjustment views
     */
    @GET
    @Path("/{entityCode}/{month}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<AdjustmentView> list(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.adjustments(entityCode, LocalDate.parse(month));
    }

    /**
     * Submits an adjustment on an entity's period (REG-01, REG-02).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param request    the adjustment request
     * @return a 201 response with the submitted adjustment
     */
    @POST
    @Path("/{entityCode}/{month}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public Response create(@PathParam("entityCode") String entityCode, @PathParam("month") String month,
                           AdjustmentRequest request) {
        requireScope(entityCode);
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, LocalDate.parse(month))
                .firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + entityCode + " " + month);
        }
        DistributorAccount account = accounts.accountFor(entity, PayeeType.valueOf(request.payeeType()),
                request.payeeId());
        DistributorAccount counterpart = request.counterpartPayeeId() == null ? null
                : accounts.accountFor(entity, PayeeType.valueOf(request.counterpartPayeeType()),
                request.counterpartPayeeId());
        Adjustment adjustment = adjustments.create(entity, period, AdjustmentType.valueOf(request.type()),
                request.amount(), account, request.reason(), request.attachmentRef(),
                Boolean.TRUE.equals(request.autoReverse()), null, counterpart, currentUser.username());
        return Response.status(Response.Status.CREATED)
                .entity(new AdjustmentResult(adjustment.getId(), adjustment.status)).build();
    }

    /**
     * Approves and applies an adjustment (REG-03).
     *
     * @param adjustmentId the adjustment id
     * @return the applied adjustment
     */
    @POST
    @Path("/{adjustmentId}/approve")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR"})
    public AdjustmentResult approve(@PathParam("adjustmentId") Long adjustmentId) {
        Adjustment adjustment = adjustments.approve(adjustmentId, currentUser.username(), currentUser.appRoles());
        return new AdjustmentResult(adjustment.getId(), adjustment.status);
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
     * Request body of an adjustment.
     *
     * @param type                 the adjustment type name
     * @param amount               the signed amount
     * @param payeeType            the account payee type
     * @param payeeId              the account payee id
     * @param reason               the mandatory reason
     * @param attachmentRef        an optional attachment reference
     * @param autoReverse          whether to auto-reverse on the next period
     * @param counterpartPayeeType the transfer counterpart payee type, or null
     * @param counterpartPayeeId   the transfer counterpart payee id, or null
     */
    public record AdjustmentRequest(String type, BigDecimal amount, String payeeType, Long payeeId, String reason,
                                    String attachmentRef, Boolean autoReverse, String counterpartPayeeType,
                                    Long counterpartPayeeId) {
    }

    /**
     * Result of an adjustment operation.
     *
     * @param id     the adjustment id
     * @param status the adjustment status
     */
    public record AdjustmentResult(Long id, AdjustmentStatus status) {
    }
}
