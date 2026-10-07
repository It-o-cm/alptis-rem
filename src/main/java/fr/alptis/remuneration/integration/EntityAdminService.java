package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ConcurrentUpdateException;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import fr.alptis.remuneration.domain.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reads and updates the per-entity parameters of section 5.1 and requirement ADM-05 (minimum
 * payment amount, advance settlement delay and tolerance, payment calendar). Every read is
 * partitioned by entity (CR-70) and every update is recorded in the audit trail (ADM-04).
 */
@ApplicationScoped
public class EntityAdminService {

    /** The entity partition of the current request. */
    private final EntityScope scope;

    /** The current user, used as the audit author. */
    private final CurrentUser currentUser;

    /** The audit trail. */
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param scope       the entity partition of the current request
     * @param currentUser the current user
     * @param audit       the audit trail
     */
    @Inject
    public EntityAdminService(EntityScope scope, CurrentUser currentUser, AuditService audit) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /**
     * Lists the entities the current user may see.
     *
     * @return the visible entities
     */
    @Transactional
    public List<EntityView> list() {
        List<LegalEntity> entities = scope.unrestricted()
                ? LegalEntity.listAll()
                : LegalEntity.list("code in ?1", scope.visibleCodes());
        return entities.stream().map(EntityAdminService::toView).toList();
    }

    /**
     * Returns one entity by its code.
     *
     * @param code the entity code
     * @return the entity view
     * @throws ResourceNotFoundException if the entity does not exist or is not visible to the user
     */
    @Transactional
    public EntityView get(String code) {
        return toView(require(code));
    }

    /**
     * Updates the parameters of one entity and records the change in the audit trail.
     *
     * @param code            the entity code
     * @param update          the new parameter values
     * @param expectedVersion the optimistic-lock version the caller last saw
     * @return the updated entity view
     * @throws ResourceNotFoundException   if the entity does not exist or is not visible
     * @throws ConcurrentUpdateException if the entity was modified concurrently
     */
    @Transactional
    public EntityView update(String code, EntityUpdate update, long expectedVersion) {
        LegalEntity entity = require(code);
        if (entity.getVersion() != expectedVersion) {
            throw new ConcurrentUpdateException(
                    "Cette entité a été modifiée entre-temps : rechargez la page avant d'enregistrer.");
        }
        String before = snapshot(entity);
        entity.minPaymentAmount = update.minPaymentAmount();
        entity.advanceSettlementDays = update.advanceSettlementDays();
        entity.advanceSettlementTolerance = update.advanceSettlementTolerance();
        entity.paymentCalendar = update.paymentCalendar();
        audit.record(currentUser.username(), entity, "UPDATE_ENTITY_PARAMETERS", "LegalEntity",
                entity.getId(), before, snapshot(entity));
        return toView(entity);
    }

    /**
     * Loads an entity by code, enforcing the entity partition.
     *
     * @param code the entity code
     * @return the entity
     * @throws ResourceNotFoundException if the entity is missing or out of the user's scope
     */
    private LegalEntity require(String code) {
        LegalEntity entity = LegalEntity.find("code", code).firstResult();
        if (entity == null || !scope.canSee(code)) {
            throw new ResourceNotFoundException("Entité introuvable : " + code);
        }
        return entity;
    }

    /**
     * Builds the exchange view of an entity.
     *
     * @param entity the entity
     * @return its view
     */
    private static EntityView toView(LegalEntity entity) {
        return new EntityView(entity.code, entity.name, entity.minPaymentAmount,
                entity.advanceSettlementDays, entity.advanceSettlementTolerance, entity.paymentCalendar,
                entity.getVersion());
    }

    /**
     * Builds a compact JSON snapshot of an entity's editable parameters for the audit trail.
     *
     * @param entity the entity
     * @return the JSON snapshot
     */
    private static String snapshot(LegalEntity entity) {
        return "{\"minPaymentAmount\":" + entity.minPaymentAmount
                + ",\"advanceSettlementDays\":" + entity.advanceSettlementDays
                + ",\"advanceSettlementTolerance\":" + entity.advanceSettlementTolerance
                + ",\"paymentCalendar\":" + jsonString(entity.paymentCalendar) + "}";
    }

    /**
     * Renders a string as a JSON value, or {@code null}.
     *
     * @param value the string, possibly null
     * @return the JSON representation
     */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Exchange view of an entity and its parameters.
     *
     * @param code                       the entity code
     * @param name                       the entity name
     * @param minPaymentAmount           the minimum payment amount
     * @param advanceSettlementDays      the advance settlement delay in days
     * @param advanceSettlementTolerance the advance settlement tolerance
     * @param paymentCalendar            the payment calendar JSON
     * @param version                    the optimistic-lock version
     */
    public record EntityView(String code, String name, BigDecimal minPaymentAmount,
                             Integer advanceSettlementDays, BigDecimal advanceSettlementTolerance,
                             String paymentCalendar, long version) {
    }

    /**
     * Exchange payload to update an entity's parameters.
     *
     * @param minPaymentAmount           the minimum payment amount
     * @param advanceSettlementDays      the advance settlement delay in days
     * @param advanceSettlementTolerance the advance settlement tolerance
     * @param paymentCalendar            the payment calendar JSON
     */
    public record EntityUpdate(BigDecimal minPaymentAmount, Integer advanceSettlementDays,
                               BigDecimal advanceSettlementTolerance, String paymentCalendar) {
    }
}
