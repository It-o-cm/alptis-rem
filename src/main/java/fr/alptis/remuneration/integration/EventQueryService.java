package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads business events for the event search and detail screens (ING-06, section 5.3): a paginated
 * search by key, contract, status and type; the detail of one event with its status, hold reason and
 * the commission lines it produced; and the held events grouped by reason (P8). Everything is
 * partitioned by entity (section 12.2).
 */
@ApplicationScoped
public class EventQueryService {

    /** The entity partition. */
    private final EntityScope scope;

    /**
     * Creates the service.
     *
     * @param scope the entity partition
     */
    @Inject
    public EventQueryService(EntityScope scope) {
        this.scope = scope;
    }

    /**
     * Searches events within the user's entity partition, newest first.
     *
     * @param q      a term matched against the source key and the contract number, or null
     * @param status a processing status name to filter on, or null/blank for all
     * @param type   an event type name to filter on, or null/blank for all
     * @param page   the zero-based page index
     * @param size   the page size
     * @return a page of event views
     */
    @Transactional
    public PageResult<EventView> search(String q, String status, String type, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        StringBuilder jpql = new StringBuilder("1 = 1");
        Parameters params = new Parameters();
        if (!scope.unrestricted()) {
            jpql.append(" and entity.code in :codes");
            params.and("codes", scope.visibleCodes());
        }
        if (q != null && !q.isBlank()) {
            // contract.id (the foreign key) keeps the match null-safe without forcing a join that would
            // drop events with no contract (e.g. held unknown-reference events).
            jpql.append(" and (lower(sourceKey) like :q or contract.id in "
                    + "(select c.id from Contract c where lower(c.contractNumber) like :q))");
            params.and("q", "%" + q.toLowerCase() + "%");
        }
        ProcessingStatus parsedStatus = parseStatus(status);
        if (parsedStatus != null) {
            jpql.append(" and processingStatus = :status");
            params.and("status", parsedStatus);
        }
        EventType parsedType = parseType(type);
        if (parsedType != null) {
            jpql.append(" and eventType = :type");
            params.and("type", parsedType);
        }
        jpql.append(" order by eventDate desc, id desc");
        PanacheQuery<BusinessEvent> query = BusinessEvent.find(jpql.toString(), params);
        long total = query.count();
        List<EventView> items = query.page(Page.of(safePage, safeSize)).<BusinessEvent>list().stream()
                .map(EventQueryService::toView).toList();
        return new PageResult<>(items, safePage, safeSize, total);
    }

    /**
     * Returns the detail of one event — its fields, status, hold reason and produced lines — within the
     * user's partition.
     *
     * @param id the event id
     * @return the event detail
     * @throws ResourceNotFoundException when the event is unknown or out of the partition
     */
    @Transactional
    public EventDetail detail(long id) {
        BusinessEvent event = BusinessEvent.<BusinessEvent>findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Événement inconnu : " + id));
        if (!scope.canSee(event.entity.code)) {
            throw new ResourceNotFoundException("Événement hors périmètre : " + id);
        }
        List<ProducedLine> lines = new ArrayList<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("event = ?1 order by id", event)) {
            lines.add(new ProducedLine(line.nature.name(), line.direction.name(), line.amount, line.status.name()));
        }
        return new EventDetail(toView(event), lines);
    }

    /**
     * Counts the held events by hold reason within the user's partition (P8, ING-06).
     *
     * @return the reason counts, by reason
     */
    @Transactional
    public List<ReasonCount> heldByReason() {
        StringBuilder jpql = new StringBuilder("processingStatus = :held");
        Parameters params = Parameters.with("held", ProcessingStatus.ON_HOLD);
        if (!scope.unrestricted()) {
            jpql.append(" and entity.code in :codes");
            params.and("codes", scope.visibleCodes());
        }
        jpql.append(" order by holdReason");
        Map<String, Long> byReason = new LinkedHashMap<>();
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(jpql.toString(), params)) {
            byReason.merge(event.holdReason == null ? "—" : event.holdReason.name(), 1L, Long::sum);
        }
        List<ReasonCount> counts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : byReason.entrySet()) {
            counts.add(new ReasonCount(entry.getKey(), entry.getValue()));
        }
        return counts;
    }

    /**
     * Parses a processing-status name, returning null when blank or unknown.
     *
     * @param status the status name
     * @return the status, or null
     */
    private static ProcessingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProcessingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses an event-type name, returning null when blank or unknown.
     *
     * @param type the type name
     * @return the type, or null
     */
    private static EventType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return EventType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Maps an event to its view.
     *
     * @param event the event
     * @return the view
     */
    private static EventView toView(BusinessEvent event) {
        return new EventView(event.getId(), event.sourceKey, event.sourceSystem, event.eventType.name(),
                event.contract == null ? null : event.contract.contractNumber,
                event.eventDate, event.processingStatus.name(),
                event.holdReason == null ? null : event.holdReason.name(),
                CommissionLine.count("event = ?1", event), event.entity.code);
    }

    /**
     * One row of the event search.
     *
     * @param id             the event id
     * @param sourceKey      the source key
     * @param sourceSystem   the source system
     * @param eventType      the event type code
     * @param contractNumber the contract number, possibly null
     * @param eventDate      the event date
     * @param status         the processing status code
     * @param holdReason     the hold reason code, possibly null
     * @param lineCount      the number of commission lines the event produced
     * @param entityCode     the entity code
     */
    public record EventView(Long id, String sourceKey, String sourceSystem, String eventType, String contractNumber,
                            LocalDate eventDate, String status, String holdReason, long lineCount,
                            String entityCode) {
    }

    /**
     * The detail of one event: its view and the lines it produced.
     *
     * @param event the event view
     * @param lines the produced commission lines
     */
    public record EventDetail(EventView event, List<ProducedLine> lines) {
    }

    /**
     * One commission line produced by an event.
     *
     * @param nature    the line nature code
     * @param direction the line direction code
     * @param amount    the line amount
     * @param status    the line status code
     */
    public record ProducedLine(String nature, String direction, BigDecimal amount, String status) {
    }

    /**
     * The count of held events for one reason.
     *
     * @param reason the hold reason code (or a dash)
     * @param count  the number of held events
     */
    public record ReasonCount(String reason, long count) {
    }
}
