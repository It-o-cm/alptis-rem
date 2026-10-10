package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.ImportRejectionStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import fr.alptis.remuneration.reporting.ExportService;
import fr.alptis.remuneration.security.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;

/**
 * Builds the flows and administration scene of the demonstration dataset: import batches (two loaded
 * cleanly, one partially loaded with rejections of several reasons — some already handled — and held
 * events), the export journal produced by the real export service, and an audit trail written through
 * the real audit service with varied authors that respect the separation of duties.
 *
 * <p>Import batches and their rejections are journal records created directly (the brief's simplest
 * route for referential/journal data); the export files and the audit chain go through their services.</p>
 */
@ApplicationScoped
public class DemoFluxBuilder {

    /** The author stamped on the journal records of this block. */
    private static final String AUTHOR = "demo-flux";

    private final ExportService exports;
    private final AuditService audit;
    private final DateTimeProvider clock;

    /**
     * Creates the builder.
     *
     * @param exports the export service
     * @param audit   the audit service
     * @param clock   the application date/time provider
     */
    @Inject
    public DemoFluxBuilder(ExportService exports, AuditService audit, DateTimeProvider clock) {
        this.exports = exports;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Builds the flows and audit scene for both entities.
     *
     * @param world the shared demo world
     */
    void build(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            importJournal(entity);
            exportJournal(entity);
        }
        auditTrail(world);
    }

    /**
     * Creates an entity's import journal: two cleanly loaded batches and one partially loaded batch
     * carrying rejections of several reasons (two already handled) and held events.
     *
     * @param entity the entity
     */
    private void importJournal(LegalEntity entity) {
        batch(entity, "events-2026-07.csv", ImportBatchStatus.LOADED, 1200, 1200, 0, 0);
        batch(entity, "events-2026-08.csv", ImportBatchStatus.LOADED, 1310, 1310, 0, 0);
        ImportBatch partial = batch(entity, "events-2026-09.csv", ImportBatchStatus.PARTIALLY_LOADED,
                1000, 940, 5, 3);
        rejection(partial, 12, RejectionReasonCode.STRUCTURE, ImportRejectionStatus.OPEN);
        rejection(partial, 48, RejectionReasonCode.MISSING_FIELD, ImportRejectionStatus.OPEN);
        rejection(partial, 91, RejectionReasonCode.AMOUNT, ImportRejectionStatus.CORRECTED);
        rejection(partial, 134, RejectionReasonCode.FUTURE_DATE, ImportRejectionStatus.DISCARDED);
        rejection(partial, 205, RejectionReasonCode.DUPLICATE, ImportRejectionStatus.OPEN);
        heldEvent(entity, partial, "UNKNOWN-CONTRACT-5567", HoldReason.UNKNOWN_REFERENCE);
        heldEvent(entity, partial, "UNKNOWN-CONTRACT-5568", HoldReason.UNKNOWN_REFERENCE);
        heldEvent(entity, partial, "OUT-OF-GRID-7781", HoldReason.RATE_OUT_OF_GRID);
    }

    /**
     * Creates one import batch.
     *
     * @param entity   the entity
     * @param fileName the source file name
     * @param status   the batch status
     * @param read     the lines read
     * @param loaded   the lines loaded
     * @param rejected the lines rejected
     * @param onHold   the lines held
     * @return the persisted batch
     */
    private ImportBatch batch(LegalEntity entity, String fileName, ImportBatchStatus status, int read, int loaded,
                              int rejected, int onHold) {
        ImportBatch batch = new ImportBatch();
        batch.entity = entity;
        batch.flowCode = "EVENTS";
        batch.sourceSystem = "LEGACY";
        batch.fileName = fileName;
        batch.fileHash = "demo-" + entity.code + "-" + fileName;
        batch.receivedAt = clock.now();
        batch.importedBy = "operator-" + entity.code.toLowerCase();
        batch.linesRead = read;
        batch.linesFiltered = 0;
        batch.linesLoaded = loaded;
        batch.linesRejected = rejected;
        batch.linesOnHold = onHold;
        batch.status = status;
        stamp(batch);
        batch.persist();
        return batch;
    }

    /**
     * Creates one import rejection on a batch.
     *
     * @param batch  the batch
     * @param line   the rejected line number
     * @param reason the rejection reason
     * @param status the rejection status
     */
    private void rejection(ImportBatch batch, int line, RejectionReasonCode reason, ImportRejectionStatus status) {
        ImportRejection rejection = new ImportRejection();
        rejection.importBatch = batch;
        rejection.lineNumber = line;
        rejection.rawLine = "ligne " + line + " rejetée (" + reason + ")";
        rejection.reasonCode = reason;
        rejection.status = status;
        stamp(rejection);
        rejection.persist();
    }

    /**
     * Creates one held event attached to an import batch.
     *
     * @param entity the entity
     * @param batch  the import batch
     * @param key    the source key
     * @param reason the hold reason
     */
    private void heldEvent(LegalEntity entity, ImportBatch batch, String key, HoldReason reason) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = "LEGACY";
        event.sourceKey = key;
        event.importBatch = batch;
        event.eventDate = LocalDate.of(2026, 9, 1);
        event.receivedDate = LocalDate.of(2026, 9, 1);
        event.processingStatus = ProcessingStatus.ON_HOLD;
        event.holdReason = reason;
        stamp(event);
        event.persist();
    }

    /**
     * Produces the export journal of an entity through the real export service (commission lines and
     * accounts snapshots).
     *
     * @param entity the entity
     */
    private void exportJournal(LegalEntity entity) {
        exports.dataExport(entity.code, ExportService.ExportObject.COMMISSION_LINES, "operator-demo");
        exports.dataExport(entity.code, ExportService.ExportObject.ACCOUNTS, "finance-demo");
    }

    /**
     * Writes the audit trail through the real audit service, with varied authors reflecting the demo's
     * actions and respecting the separation of duties (the operator imports and calculates, a different
     * validator validates, Finance validates the payment, the manager validates the variable).
     *
     * @param world the shared demo world
     */
    private void auditTrail(DemoWorld world) {
        LegalEntity alptis = world.entity(DemoCodes.ALPTIS);
        LegalEntity unep = world.entity(DemoCodes.UNEP);
        trace("operator-alptis", alptis, "IMPORT_EVENTS", "IMPORT_BATCH");
        trace("admin-alptis", alptis, "CALCULATE_PERIOD", "PERIOD");
        trace("validator-alptis", alptis, "VALIDATE_BUSINESS", "PERIOD");
        trace("finance-alptis", alptis, "VALIDATE_FINANCE", "PAYMENT_BATCH");
        trace("manager-alptis", alptis, "VALIDATE_VARIABLE", "VARIABLE_RESULT");
        trace("operator-unep", unep, "IMPORT_EVENTS", "IMPORT_BATCH");
        trace("validator-unep", unep, "VALIDATE_BUSINESS", "PERIOD");
        trace("it-admin", null, "RESET_DATASET", "SYSTEM");
    }

    /**
     * Records one audit trace.
     *
     * @param actor      the acting user
     * @param entity     the entity, or null for a global action
     * @param action     the action code
     * @param objectType the affected object type
     */
    private void trace(String actor, LegalEntity entity, String action, String objectType) {
        audit.record(actor, entity, action, objectType, 1L, null,
                "{\"demo\":true,\"action\":\"" + action + "\"}");
    }

    /**
     * Stamps the creation audit columns.
     *
     * @param entity the entity to stamp
     */
    private void stamp(fr.alptis.remuneration.domain.BaseEntity entity) {
        entity.setCreatedAt(clock.now());
        entity.setCreatedBy(AUTHOR);
    }
}
