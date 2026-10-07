package fr.alptis.remuneration.security;

import fr.alptis.remuneration.domain.AuditEvent;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;

/**
 * Records manual actions in the append-only, hash-chained audit trail (requirement ADM-04,
 * structural choice 9). Each trace carries the author, the time, the before and after values, and
 * the fingerprint of the previous trace, so the trail cannot be altered without detection.
 */
@ApplicationScoped
public class AuditService {

    /** Marker used as the previous fingerprint of the very first trace. */
    public static final String GENESIS = "GENESIS";

    /** Source of the current time. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public AuditService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Appends a trace to the audit trail, chained to the previous one by fingerprint.
     *
     * @param actor      the user performing the action
     * @param entity     the entity the action is scoped to, or {@code null} for a global action
     * @param action     the action code (e.g. {@code UPDATE_ENTITY})
     * @param objectType the type of the affected object
     * @param objectId   the technical id of the affected object
     * @param before     the JSON before value, or {@code null}
     * @param after      the JSON after value, or {@code null}
     * @return the persisted audit event
     */
    @Transactional
    public AuditEvent record(String actor, LegalEntity entity, String action, String objectType,
                             Long objectId, String before, String after) {
        AuditEvent last = AuditEvent.find("order by id desc").firstResult();
        String previousHash = last == null ? GENESIS : last.hash;
        OffsetDateTime now = clock.now();

        AuditEvent event = new AuditEvent();
        event.occurredAt = now;
        event.userId = actor;
        event.entity = entity;
        event.action = action;
        event.objectType = objectType;
        event.objectId = objectId;
        event.beforeValue = before;
        event.afterValue = after;
        event.previousHash = previousHash;
        event.hash = fingerprint(previousHash, now, actor, action, objectType, objectId, before, after);
        event.persist();
        return event;
    }

    /**
     * Computes the SHA-256 fingerprint chaining a trace to the previous one.
     *
     * @param previousHash the previous trace fingerprint
     * @param now          the trace time
     * @param actor        the author
     * @param action       the action code
     * @param objectType   the object type
     * @param objectId     the object id
     * @param before       the before value
     * @param after        the after value
     * @return the lowercase hexadecimal SHA-256 fingerprint
     */
    private static String fingerprint(String previousHash, OffsetDateTime now, String actor, String action,
                                      String objectType, Long objectId, String before, String after) {
        String payload = previousHash + '|' + now + '|' + actor + '|' + action + '|' + objectType
                + '|' + objectId + '|' + before + '|' + after;
        return HashUtil.sha256(payload);
    }
}
