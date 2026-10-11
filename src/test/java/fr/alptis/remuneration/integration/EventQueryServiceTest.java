package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import fr.alptis.remuneration.integration.EventQueryService.EventDetail;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link EventQueryService} with hand-made scopes and seeded events: the restricted and
 * unrestricted partitions, the key/status/type filters (including blank and unknown values), the
 * detail with its produced lines, the held-by-reason counts and the not-found and out-of-scope
 * guards (ING-06, P8, section 5.3).
 */
@QuarkusTest
class EventQueryServiceTest {

    /** Builds a query service for the given visible codes ({@code *} for unrestricted). */
    private EventQueryService service(List<String> entities) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("tester"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        return new EventQueryService(new EntityScope(new CurrentUser(identity)));
    }

    /** Returns an entity by code. */
    private static LegalEntity entity(String code) {
        return LegalEntity.<LegalEntity>find("code", code).firstResult();
    }

    /** Creates a contract of an entity. */
    private static Contract contract(String entityCode, String number) {
        Contract c = new Contract();
        c.entity = entity(entityCode);
        c.sourceSystem = "SYS";
        c.contractNumber = number;
        c.persist();
        return c;
    }

    /** Creates an event of an entity, with an optional contract, status and hold reason. */
    private static BusinessEvent event(String entityCode, String key, EventType type, Contract contract,
                                       ProcessingStatus status, HoldReason reason) {
        BusinessEvent e = new BusinessEvent();
        e.entity = entity(entityCode);
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = type;
        e.eventDate = LocalDate.of(2026, 9, 1);
        e.receivedDate = LocalDate.of(2026, 9, 1);
        e.contract = contract;
        e.processingStatus = status;
        e.holdReason = reason;
        e.persist();
        return e;
    }

    /** Attaches a produced commission line to an event. */
    private static void producedLine(BusinessEvent e, LineNature nature, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = e.entity;
        line.event = e;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /**
     * The search filters by key, status and type within the entity partition, ignoring blank and
     * unknown filter values, and the unrestricted scope sees every entity.
     */
    @Test
    @TestTransaction
    void searches_with_filters_and_partitions() {
        Contract a1 = contract("ALPTIS_ASSURANCES", "AD-1");
        event("ALPTIS_ASSURANCES", "K-PREMIUM", EventType.PREMIUM_COLLECTED, a1, ProcessingStatus.PROCESSED, null);
        event("ALPTIS_ASSURANCES", "K-HELD", EventType.TERMINATION, a1, ProcessingStatus.ON_HOLD,
                HoldReason.UNKNOWN_REFERENCE);
        event("UNEP", "K-UNEP", EventType.PREMIUM_COLLECTED, null, ProcessingStatus.PROCESSED, null);

        EventQueryService alptis = service(List.of("ALPTIS_ASSURANCES"));
        // Restricted partition: only the two Alptis events, blank filters apply nothing.
        assertEquals(2, alptis.search(null, "", "", 0, 20).total());
        // A blank (non-null) search term is also ignored.
        assertEquals(2, alptis.search("", null, null, 0, 20).total());
        // Match on the source key.
        assertEquals(1, alptis.search("k-premium", null, null, 0, 20).total());
        // Match on the contract number.
        assertEquals(2, alptis.search("ad-1", null, null, 0, 20).total());
        // Status and type filters.
        assertEquals(1, alptis.search(null, "ON_HOLD", null, 0, 20).total());
        assertEquals(1, alptis.search(null, null, "TERMINATION", 0, 20).total());
        // Unknown filter values are ignored, not errors.
        assertEquals(2, alptis.search(null, "BOGUS", "ALSO_BOGUS", -1, 0).total());
        // Unrestricted scope sees UNEP too.
        assertEquals(3, service(List.of("*")).search(null, null, null, 0, 20).total());
    }

    /**
     * The detail returns the event's produced lines, and guards the missing and out-of-scope ids.
     */
    @Test
    @TestTransaction
    void detail_returns_produced_lines_and_guards_scope() {
        Contract a1 = contract("ALPTIS_ASSURANCES", "AD-1");
        BusinessEvent premium = event("ALPTIS_ASSURANCES", "K-P", EventType.PREMIUM_COLLECTED, a1,
                ProcessingStatus.PROCESSED, null);
        producedLine(premium, LineNature.LINEAR, "120.00");
        producedLine(premium, LineNature.ACQUISITION, "30.00");
        BusinessEvent unep = event("UNEP", "K-U", EventType.PREMIUM_COLLECTED, null, ProcessingStatus.PROCESSED, null);

        EventQueryService alptis = service(List.of("ALPTIS_ASSURANCES"));
        EventDetail detail = alptis.detail(premium.getId());
        assertEquals("AD-1", detail.event().contractNumber());
        assertEquals(2, detail.event().lineCount());
        assertEquals(2, detail.lines().size());
        assertEquals("LINEAR", detail.lines().get(0).nature());

        assertThrows(ResourceNotFoundException.class, () -> alptis.detail(999_999L));
        assertThrows(ResourceNotFoundException.class, () -> alptis.detail(unep.getId()));
    }

    /**
     * An event with no produced line and no contract yields an empty detail with null fields.
     */
    @Test
    @TestTransaction
    void detail_of_a_bare_event_has_no_lines() {
        BusinessEvent held = event("ALPTIS_ASSURANCES", "K-H", EventType.TERMINATION, null,
                ProcessingStatus.ON_HOLD, HoldReason.NO_RULE);

        EventDetail detail = service(List.of("ALPTIS_ASSURANCES")).detail(held.getId());
        assertTrue(detail.lines().isEmpty());
        assertNull(detail.event().contractNumber());
        assertEquals("NO_RULE", detail.event().holdReason());
    }

    /**
     * Held events are counted by reason within the partition, bucketing a reason-less one under a dash;
     * the unrestricted scope counts every entity.
     */
    @Test
    @TestTransaction
    void counts_held_events_by_reason() {
        event("ALPTIS_ASSURANCES", "H-1", EventType.TERMINATION, null, ProcessingStatus.ON_HOLD,
                HoldReason.UNKNOWN_REFERENCE);
        event("ALPTIS_ASSURANCES", "H-2", EventType.TERMINATION, null, ProcessingStatus.ON_HOLD,
                HoldReason.UNKNOWN_REFERENCE);
        event("ALPTIS_ASSURANCES", "H-3", EventType.TERMINATION, null, ProcessingStatus.ON_HOLD, null);
        event("UNEP", "H-4", EventType.TERMINATION, null, ProcessingStatus.ON_HOLD, HoldReason.NO_RULE);

        EventQueryService alptis = service(List.of("ALPTIS_ASSURANCES"));
        List<EventQueryService.ReasonCount> counts = alptis.heldByReason();
        assertEquals(2, counts.stream().filter(c -> c.reason().equals("UNKNOWN_REFERENCE"))
                .mapToLong(EventQueryService.ReasonCount::count).sum());
        assertEquals(1, counts.stream().filter(c -> c.reason().equals("—"))
                .mapToLong(EventQueryService.ReasonCount::count).sum());

        assertEquals(4, service(List.of("*")).heldByReason().stream()
                .mapToLong(EventQueryService.ReasonCount::count).sum());
    }
}
