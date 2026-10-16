package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportMapping;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.ImportRejectionStatus;
import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reads the import journal (ING-05) and resolves rejected lines (ING-04), partitioned by entity.
 * A rejected line is corrected or discarded; the manual decision is recorded in the audit trail.
 */
@ApplicationScoped
public class ImportQueryService {

    private final EntityScope scope;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final DateTimeProvider clock;
    private final EventImportService events;

    /**
     * Creates the service.
     *
     * @param scope       the entity partition
     * @param currentUser the current user, audit author
     * @param audit       the audit trail
     * @param clock       the application date/time provider
     * @param events      the event-import service used to reprocess a corrected line
     */
    @Inject
    public ImportQueryService(EntityScope scope, CurrentUser currentUser, AuditService audit, DateTimeProvider clock,
                              EventImportService events) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Lists import batches, most recent first, within the user's entity partition.
     *
     * @param page the zero-based page index
     * @param size the page size
     * @return a page of batch views
     */
    @Transactional
    public PageResult<BatchView> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        PanacheQuery<ImportBatch> query = scope.unrestricted()
                ? ImportBatch.find("order by receivedAt desc")
                : ImportBatch.find("entity.code in ?1 order by receivedAt desc", scope.visibleCodes());
        long total = query.count();
        List<BatchView> items = query.page(Page.of(safePage, safeSize)).<ImportBatch>list().stream()
                .map(ImportQueryService::toView).toList();
        return new PageResult<>(items, safePage, safeSize, total);
    }

    /**
     * The standard inbound source systems of section 9.1, always offered so the upload form never
     * falls back to a free-text code.
     */
    private static final List<String> STANDARD_SOURCE_SYSTEMS =
            List.of("CLEVA", "KELIA", "RSI", "SALESFORCE", "SESAME");

    /**
     * Lists the source systems the upload form may choose from: the standard inbound systems of section
     * 9.1, plus any extra system configured by a mapping within the user's entity partition (ING-01,
     * ING-02), sorted and de-duplicated.
     *
     * @return the source system codes, sorted
     */
    @Transactional
    public List<String> sourceSystems() {
        java.util.SortedSet<String> systems = new java.util.TreeSet<>(STANDARD_SOURCE_SYSTEMS);
        List<ImportMapping> mappings = scope.unrestricted()
                ? ImportMapping.listAll()
                : ImportMapping.list("entity.code in ?1", scope.visibleCodes());
        mappings.forEach(m -> systems.add(m.sourceSystem));
        return List.copyOf(systems);
    }

    /**
     * Returns the rejected lines of a batch.
     *
     * @param batchId the batch id
     * @return the rejection views
     */
    @Transactional
    public List<RejectionView> rejections(long batchId) {
        ImportBatch batch = requireBatch(batchId);
        return ImportRejection.<ImportRejection>list("importBatch", batch).stream()
                .map(r -> new RejectionView(r.getId(), r.lineNumber, r.rawLine, r.reasonCode.name(), r.status.name()))
                .toList();
    }

    /**
     * Returns the on-hold events of a batch, with their hold reason.
     *
     * @param batchId the batch id
     * @return the held-event views
     */
    @Transactional
    public List<HeldEventView> heldEvents(long batchId) {
        ImportBatch batch = requireBatch(batchId);
        return BusinessEvent.<BusinessEvent>list("importBatch = ?1 and processingStatus = ?2",
                        batch, ProcessingStatus.ON_HOLD).stream()
                .map(e -> new HeldEventView(e.sourceKey,
                        e.contract == null ? null : e.contract.contractNumber,
                        e.holdReason == null ? null : e.holdReason.name()))
                .toList();
    }

    /**
     * Corrects a rejected line (INT-03): the corrected line is reprocessed alone against its batch; if
     * it now passes, the event is created, the batch counters are updated (one more loaded, one fewer
     * rejected) and the rejection is marked corrected with an audit trace. A corrected line that is
     * still invalid is refused with its new reason, leaving the rejection open. With no corrected line
     * the rejection is simply marked corrected (resolved by a later re-import), the prior behaviour.
     *
     * @param rejectionId   the rejection id
     * @param correctedLine the corrected data line in the batch's column order, or null/blank to only resolve
     */
    @Transactional
    public void correct(long rejectionId, String correctedLine) {
        ImportRejection rejection = ImportRejection.findById(rejectionId);
        if (rejection == null || !scope.canSee(rejection.importBatch.entity.code)) {
            throw new ResourceNotFoundException("Rejet introuvable.");
        }
        if (rejection.status != ImportRejectionStatus.OPEN) {
            throw new ValidationException("Ce rejet est déjà résolu.");
        }
        ImportBatch batch = rejection.importBatch;
        if (correctedLine != null && !correctedLine.isBlank() && batch.header != null) {
            EventImportService.ReprocessOutcome outcome = events.reprocess(batch, correctedLine.strip());
            if (!outcome.loaded()) {
                throw new ValidationException("La ligne corrigée est encore rejetée : " + outcome.reason().name());
            }
            audit.record(currentUser.username(), batch.entity, "REPROCESS_REJECTION", "ImportRejection",
                    rejection.getId(), "\"" + rejection.reasonCode.name() + "\"", "\"" + outcome.sourceKey() + "\"");
        }
        resolve(rejection, ImportRejectionStatus.CORRECTED, "CORRECT_REJECTION");
    }

    /**
     * Marks a rejected line as discarded (e.g. an accepted duplicate).
     *
     * @param rejectionId the rejection id
     */
    @Transactional
    public void discard(long rejectionId) {
        ImportRejection rejection = ImportRejection.findById(rejectionId);
        if (rejection == null || !scope.canSee(rejection.importBatch.entity.code)) {
            throw new ResourceNotFoundException("Rejet introuvable.");
        }
        resolve(rejection, ImportRejectionStatus.DISCARDED, "DISCARD_REJECTION");
    }

    /**
     * Resolves a rejection to the given status and records the decision.
     *
     * @param rejection the rejection
     * @param status    the new status
     * @param action    the audit action code
     */
    private void resolve(ImportRejection rejection, ImportRejectionStatus status, String action) {
        String before = "\"" + rejection.status.name() + "\"";
        rejection.status = status;
        rejection.resolvedAt = clock.now();
        rejection.resolvedBy = currentUser.username();
        audit.record(currentUser.username(), rejection.importBatch.entity, action, "ImportRejection",
                rejection.getId(), before, "\"" + status.name() + "\"");
    }

    /**
     * Loads a batch, enforcing the entity partition.
     *
     * @param batchId the batch id
     * @return the batch
     */
    private ImportBatch requireBatch(long batchId) {
        ImportBatch batch = ImportBatch.findById(batchId);
        if (batch == null || !scope.canSee(batch.entity.code)) {
            throw new ResourceNotFoundException("Import introuvable.");
        }
        return batch;
    }

    /**
     * Builds the view of a batch.
     *
     * @param batch the batch
     * @return its view
     */
    private static BatchView toView(ImportBatch batch) {
        return new BatchView(batch.getId(), batch.flowCode, batch.sourceSystem, batch.fileName, batch.fileHash,
                batch.receivedAt, batch.importedBy, batch.linesRead, batch.linesFiltered, batch.linesLoaded,
                batch.linesRejected, batch.linesOnHold, batch.status.name());
    }

    /**
     * Exchange view of an import batch and its five counters.
     *
     * @param id            the batch id
     * @param flowCode      the flow code
     * @param sourceSystem  the source system
     * @param fileName      the file name
     * @param fileHash      the file fingerprint
     * @param receivedAt    the reception time
     * @param importedBy    the importer
     * @param linesRead     lines read
     * @param linesFiltered lines filtered by the mapping
     * @param linesLoaded   lines integrated
     * @param linesRejected lines rejected
     * @param linesOnHold   lines put on hold
     * @param status        the batch status
     */
    public record BatchView(long id, String flowCode, String sourceSystem, String fileName, String fileHash,
                            OffsetDateTime receivedAt, String importedBy, Integer linesRead, Integer linesFiltered,
                            Integer linesLoaded, Integer linesRejected, Integer linesOnHold, String status) {
    }

    /**
     * Exchange view of a rejected line.
     *
     * @param id         the rejection id
     * @param lineNumber the line number
     * @param rawLine    the raw line
     * @param reasonCode the reason code
     * @param status     the rejection status
     */
    public record RejectionView(long id, Integer lineNumber, String rawLine, String reasonCode, String status) {
    }

    /**
     * Exchange view of a held event.
     *
     * @param sourceKey      the source key
     * @param contractNumber the contract number, if known
     * @param holdReason     the hold reason
     */
    public record HeldEventView(String sourceKey, String contractNumber, String holdReason) {
    }
}
