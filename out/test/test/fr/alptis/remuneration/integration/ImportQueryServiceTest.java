package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.ImportRejectionStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ImportQueryService} with hand-made scopes and a seeded journal: the restricted and
 * unrestricted partitions, rejection resolution with its audit, held-event listing and the
 * not-found and out-of-scope guards (ING-04, ING-05).
 */
@QuarkusTest
class ImportQueryServiceTest {

    @Inject
    AuditService audit;

    @Inject
    DateTimeProvider clock;

    /** Builds a query service for the given visible codes ({@code *} for unrestricted). */
    private ImportQueryService service(List<String> entities) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("tester"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        CurrentUser user = new CurrentUser(identity);
        return new ImportQueryService(new EntityScope(user), user, audit, clock);
    }

    /** Returns an entity by code. */
    private static LegalEntity entity(String code) {
        return LegalEntity.<LegalEntity>find("code", code).firstResult();
    }

    /** Creates a journalled batch for an entity. */
    private ImportBatch batch(String entityCode, String fileName) {
        ImportBatch b = new ImportBatch();
        b.entity = entity(entityCode);
        b.flowCode = "events";
        b.sourceSystem = "SYS";
        b.fileName = fileName;
        b.fileHash = fileName;
        b.receivedAt = clock.now();
        b.importedBy = "import";
        b.linesRead = 0;
        b.linesFiltered = 0;
        b.linesLoaded = 0;
        b.linesRejected = 0;
        b.linesOnHold = 0;
        b.status = ImportBatchStatus.LOADED;
        b.persist();
        return b;
    }

    /** Creates an open rejection on a batch. */
    private ImportRejection rejection(ImportBatch b) {
        ImportRejection r = new ImportRejection();
        r.importBatch = b;
        r.lineNumber = 2;
        r.rawLine = "raw";
        r.reasonCode = RejectionReasonCode.MISSING_FIELD;
        r.status = ImportRejectionStatus.OPEN;
        r.persist();
        return r;
    }

    /**
     * Lists batches in both partitions, exercising the non-positive size and negative page branches.
     */
    @Test
    @TestTransaction
    void lists_batches_in_both_partitions() {
        batch("ALPTIS_ASSURANCES", "a.csv");
        batch("UNEP", "u.csv");

        assertEquals(1, service(List.of("ALPTIS_ASSURANCES")).list(0, 20).total());
        assertEquals(2, service(List.of("*")).list(-1, 0).total());
    }

    /**
     * Lists the rejections and held events of a batch, covering the null contract and reason branches.
     */
    @Test
    @TestTransaction
    void lists_rejections_and_held_events() {
        ImportBatch b = batch("ALPTIS_ASSURANCES", "a.csv");
        rejection(b);
        Contract contract = new Contract();
        contract.entity = entity("ALPTIS_ASSURANCES");
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-1";
        contract.persist();
        heldEvent(b, "H-1", contract, HoldReason.UNKNOWN_REFERENCE);
        heldEvent(b, "H-2", null, null);

        ImportQueryService service = service(List.of("ALPTIS_ASSURANCES"));
        assertEquals(1, service.rejections(b.getId()).size());
        assertEquals(2, service.heldEvents(b.getId()).size());
    }

    /**
     * Correcting then discarding resolves the rejections and records the decisions.
     */
    @Test
    @TestTransaction
    void corrects_and_discards_rejections() {
        ImportBatch b = batch("ALPTIS_ASSURANCES", "a.csv");
        ImportRejection r1 = rejection(b);
        ImportRejection r2 = rejection(b);

        ImportQueryService service = service(List.of("ALPTIS_ASSURANCES"));
        service.correct(r1.getId());
        service.discard(r2.getId());

        assertEquals(ImportRejectionStatus.CORRECTED, ImportRejection.<ImportRejection>findById(r1.getId()).status);
        assertEquals(ImportRejectionStatus.DISCARDED, ImportRejection.<ImportRejection>findById(r2.getId()).status);
    }

    /**
     * A missing or out-of-scope batch or rejection is reported as not found.
     */
    @Test
    @TestTransaction
    void guards_against_missing_and_out_of_scope() {
        ImportBatch unep = batch("UNEP", "u.csv");
        ImportRejection unepRejection = rejection(unep);
        ImportQueryService alptis = service(List.of("ALPTIS_ASSURANCES"));

        assertThrows(ResourceNotFoundException.class, () -> alptis.rejections(999_999L));
        assertThrows(ResourceNotFoundException.class, () -> alptis.rejections(unep.getId()));
        assertThrows(ResourceNotFoundException.class, () -> alptis.correct(999_999L));
        assertThrows(ResourceNotFoundException.class, () -> alptis.correct(unepRejection.getId()));
    }

    /** Creates an on-hold event on a batch. */
    private void heldEvent(ImportBatch b, String key, Contract contract, HoldReason reason) {
        BusinessEvent e = new BusinessEvent();
        e.entity = b.entity;
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = EventType.PREMIUM_COLLECTED;
        e.eventDate = LocalDate.of(2026, 1, 1);
        e.contract = contract;
        e.processingStatus = ProcessingStatus.ON_HOLD;
        e.holdReason = reason;
        e.importBatch = b;
        e.persist();
    }
}
