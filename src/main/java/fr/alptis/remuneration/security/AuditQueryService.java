package fr.alptis.remuneration.security;

import fr.alptis.remuneration.domain.AuditEvent;
import fr.alptis.remuneration.domain.PageResult;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reads the audit trail for the audit screen (requirement ADM-04), most recent first, partitioned
 * by entity: a group-level user (auditor, technical administrator) sees every trace, an entity
 * user sees only its entity's traces.
 */
@ApplicationScoped
public class AuditQueryService {

    /** Default page size. */
    private static final int DEFAULT_SIZE = 20;

    /** Maximum page size. */
    private static final int MAX_SIZE = 200;

    /** The entity partition of the current request. */
    private final EntityScope scope;

    /**
     * Creates the service.
     *
     * @param scope the entity partition of the current request
     */
    @Inject
    public AuditQueryService(EntityScope scope) {
        this.scope = scope;
    }

    /**
     * Returns one page of audit traces, most recent first.
     *
     * @param page the zero-based page index
     * @param size the page size (defaulted and capped)
     * @return the page of traces
     */
    @Transactional
    public PageResult<AuditView> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        PanacheQuery<AuditEvent> query = scope.unrestricted()
                ? AuditEvent.find("order by id desc")
                : AuditEvent.find("entity.code in ?1 order by id desc", scope.visibleCodes());
        long total = query.count();
        List<AuditView> items = query.page(Page.of(safePage, safeSize)).<AuditEvent>list().stream()
                .map(AuditQueryService::toView)
                .toList();
        return new PageResult<>(items, safePage, safeSize, total);
    }

    /**
     * Builds the exchange view of an audit trace.
     *
     * @param event the audit event
     * @return its view
     */
    private static AuditView toView(AuditEvent event) {
        String entityCode = event.entity == null ? null : event.entity.code;
        return new AuditView(event.occurredAt, event.userId, entityCode, event.action, event.objectType,
                event.objectId, event.beforeValue, event.afterValue, event.previousHash, event.hash);
    }

    /**
     * Exchange view of an audit trace.
     *
     * @param occurredAt   when the action happened
     * @param userId       the author
     * @param entityCode   the entity code, or {@code null} for a global action
     * @param action       the action code
     * @param objectType   the affected object type
     * @param objectId     the affected object id
     * @param beforeValue  the before value (JSON)
     * @param afterValue   the after value (JSON)
     * @param previousHash the previous trace fingerprint
     * @param hash         this trace fingerprint
     */
    public record AuditView(OffsetDateTime occurredAt, String userId, String entityCode, String action,
                            String objectType, Long objectId, String beforeValue, String afterValue,
                            String previousHash, String hash) {
    }
}
