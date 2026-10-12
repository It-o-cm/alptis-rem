package fr.alptis.remuneration.api;

import fr.alptis.remuneration.cycle.AdjustmentService;
import fr.alptis.remuneration.cycle.AdjustmentService.ClaimDetail;
import fr.alptis.remuneration.cycle.AdjustmentService.ClaimView;
import fr.alptis.remuneration.domain.Claim;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Interface endpoints for the claims screen (REG-06, section 8.5): list a distributor's claims, read
 * one with its contested commission lines, record a new claim and close it once resolved.
 * Partitioned by entity.
 */
@Path("/api/ui/v1/claims")
public class ClaimsResource {

    private final AdjustmentService adjustments;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param adjustments the adjustment and claim service
     * @param scope       the entity partition
     */
    @Inject
    public ClaimsResource(AdjustmentService adjustments, EntityScope scope) {
        this.adjustments = adjustments;
        this.scope = scope;
    }

    /**
     * Lists an entity's claims (REG-06).
     *
     * @param entityCode the entity code
     * @return the claim views
     */
    @GET
    @Path("/{entityCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ClaimView> list(@PathParam("entityCode") String entityCode) {
        LegalEntity entity = requireEntity(entityCode);
        return adjustments.claims(entity);
    }

    /**
     * Returns one claim with its contested lines (REG-06).
     *
     * @param id the claim id
     * @return the claim detail
     */
    @GET
    @Path("/detail/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public ClaimDetail detail(@PathParam("id") Long id) {
        requireScope(entityOfClaim(id));
        return adjustments.claimDetail(id);
    }

    /**
     * Records a distributor's claim, linked to the contested commission lines (REG-06).
     *
     * @param entityCode the entity code
     * @param request    the claim request
     * @return the opened claim
     */
    @POST
    @Path("/{entityCode}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public ClaimView record(@PathParam("entityCode") String entityCode, ClaimRequest request) {
        LegalEntity entity = requireEntity(entityCode);
        Distributor distributor = Distributor.<Distributor>find("partnerCode", request.distributorCode())
                .firstResult();
        if (distributor == null) {
            throw new ResourceNotFoundException("Distributeur inconnu : " + request.distributorCode());
        }
        List<CommissionLine> contested = new ArrayList<>();
        if (request.contestedLineIds() != null) {
            for (Long lineId : request.contestedLineIds()) {
                CommissionLine line = CommissionLine.findById(lineId);
                if (line != null) {
                    contested.add(line);
                }
            }
        }
        Claim claim = adjustments.recordClaim(entity, distributor, request.subject(), contested);
        return adjustments.claimDetail(claim.getId()).claim();
    }

    /**
     * Closes a claim once resolved (REG-06).
     *
     * @param id      the claim id
     * @param request the resolution request
     * @return the closed claim
     */
    @POST
    @Path("/{id}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public ClaimView resolve(@PathParam("id") Long id, ResolveRequest request) {
        requireScope(entityOfClaim(id));
        adjustments.resolveClaim(id, request.resolution());
        return adjustments.claimDetail(id).claim();
    }

    /**
     * Returns the entity code of a claim, failing when the claim is unknown.
     *
     * @param id the claim id
     * @return the entity code
     */
    private static String entityOfClaim(Long id) {
        Claim claim = Claim.findById(id);
        if (claim == null) {
            throw new ResourceNotFoundException("Réclamation inconnue : " + id);
        }
        return claim.entity.code;
    }

    /**
     * Finds an entity within the caller's partition or fails.
     *
     * @param entityCode the entity code
     * @return the entity
     */
    private LegalEntity requireEntity(String entityCode) {
        requireScope(entityCode);
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        return entity;
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
     * Request body to record a claim.
     *
     * @param distributorCode  the claiming distributor's partner code
     * @param subject          the claim subject
     * @param contestedLineIds the ids of the contested commission lines
     */
    public record ClaimRequest(String distributorCode, String subject, List<Long> contestedLineIds) {
    }

    /**
     * Request body to resolve a claim.
     *
     * @param resolution the resolution text
     */
    public record ResolveRequest(String resolution) {
    }
}
