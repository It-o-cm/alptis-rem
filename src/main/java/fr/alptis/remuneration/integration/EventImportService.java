package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
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
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Imports a business-events pivot file (section 9.3). Each line is controlled and either loaded,
 * rejected with a reason code (section 8.2) or put on hold when a referenced object is not yet
 * known (ING-04, ING-06). The batch is journalled with its five counters (ING-05), idempotent by
 * file fingerprint and by source key (section 9.9, CR-68).
 */
@ApplicationScoped
public class EventImportService {

    /** Source of "today", for the future-date control. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public EventImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports an events file for an entity and source system.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw file content
     * @return the journalled import batch
     * @throws ConflictException if a file with the same fingerprint was already imported
     */
    @Transactional
    public ImportBatch importEvents(String entityCode, String sourceSystem, String fileName, String content) {
        CsvFile csv = CsvFile.parse(content);
        List<List<Map<String, String>>> groups = new java.util.ArrayList<>();
        List<Boolean> malformed = new java.util.ArrayList<>();
        for (int i = 0; i < csv.rows().size(); i++) {
            groups.add(java.util.List.of(csv.rows().get(i)));
            malformed.add(csv.malformed(i));
        }
        return importGroups(entityCode, sourceSystem, fileName, fileName + "\n" + content, groups, 0, malformed);
    }

    /**
     * Imports pre-mapped event groups: one group per kept source line, each with a primary row and
     * any derived rows (section 9.4). The derived rows are persisted with the primary but counted
     * once, so {@code lines_read = filtered + loaded + rejected + on_hold} stays at the source grain.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param hashSource   the text whose SHA-256 is the idempotency fingerprint (the original file)
     * @param groups       the pivot-row groups, one per kept source line
     * @param filtered     the number of source lines dropped by the mapping filter
     * @return the journalled batch
     * @throws ConflictException if the entity is unknown or the file was already imported
     */
    public ImportBatch importGroups(String entityCode, String sourceSystem, String fileName, String hashSource,
                                    List<List<Map<String, String>>> groups, int filtered) {
        return importGroups(entityCode, sourceSystem, fileName, hashSource, groups, filtered,
                java.util.Collections.nCopies(groups.size(), false));
    }

    /**
     * Imports pre-mapped event groups, rejecting as {@code STRUCTURE} the source lines flagged as
     * carrying more fields than the header (section 8.2).
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param hashSource   the text whose SHA-256 is the idempotency fingerprint (the original file)
     * @param groups       the pivot-row groups, one per kept source line
     * @param filtered     the number of source lines dropped by the mapping filter
     * @param malformed    per group, whether its source line is structurally malformed
     * @return the journalled batch
     * @throws ConflictException if the entity is unknown or the file was already imported
     */
    @Transactional
    public ImportBatch importGroups(String entityCode, String sourceSystem, String fileName, String hashSource,
                                    List<List<Map<String, String>>> groups, int filtered, List<Boolean> malformed) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ConflictException("Entité inconnue : " + entityCode);
        }
        String fileHash = HashUtil.sha256(hashSource);
        long already = ImportBatch.count("entity = ?1 and sourceSystem = ?2 and fileHash = ?3",
                entity, sourceSystem, fileHash);
        if (already > 0) {
            throw new ConflictException("Fichier déjà intégré : " + fileName);
        }

        ImportBatch batch = new ImportBatch();
        batch.entity = entity;
        batch.flowCode = FlowCodes.EVENTS;
        batch.sourceSystem = sourceSystem;
        batch.fileName = fileName;
        batch.fileHash = fileHash;
        batch.receivedAt = clock.now();
        batch.importedBy = "import";
        batch.linesFiltered = filtered;
        // Keep the header (column names in order) so a corrected rejected line can be reprocessed (INT-03).
        // Each group carries at least its primary row (see the loop below), so get(0).get(0) is safe.
        batch.header = groups.isEmpty() ? null : String.join(";", groups.get(0).get(0).keySet());
        batch.persist();

        LocalDate today = clock.today();
        int loaded = 0;
        int rejected = 0;
        int onHold = 0;
        int lineNumber = 1;
        for (int g = 0; g < groups.size(); g++) {
            List<Map<String, String>> group = groups.get(g);
            lineNumber++;
            Map<String, String> primary = group.get(0);
            if (Boolean.TRUE.equals(malformed.get(g))) {
                reject(batch, lineNumber, cleanLine(primary), RejectionReasonCode.STRUCTURE);
                rejected++;
                continue;
            }
            Outcome outcome = classify(entity, sourceSystem, primary, today);
            if (outcome.rejection != null) {
                reject(batch, lineNumber, cleanLine(primary), outcome.rejection);
                rejected++;
                continue;
            }
            persistEvent(batch, entity, sourceSystem, primary, outcome);
            for (int i = 1; i < group.size(); i++) {
                Map<String, String> derived = group.get(i);
                Outcome derivedOutcome = classify(entity, sourceSystem, derived, today);
                if (derivedOutcome.rejection == null) {
                    persistEvent(batch, entity, sourceSystem, derived, derivedOutcome);
                }
            }
            if (outcome.holdReason != null) {
                onHold++;
            } else {
                loaded++;
            }
        }

        batch.linesRead = groups.size() + filtered;
        batch.linesLoaded = loaded;
        batch.linesRejected = rejected;
        batch.linesOnHold = onHold;
        batch.status = rejected == 0 ? ImportBatchStatus.LOADED : ImportBatchStatus.PARTIALLY_LOADED;
        return batch;
    }

    /**
     * Controls one line and decides its outcome.
     *
     * @param entity       the importing entity
     * @param sourceSystem the source system
     * @param row          the parsed line
     * @param today        the current day
     * @return the line outcome
     */
    private Outcome classify(LegalEntity entity, String sourceSystem, Map<String, String> row, LocalDate today) {
        String sourceKey = value(row, "source_key");
        String contractNumber = value(row, "contract_number");
        String eventTypeRaw = value(row, "event_type");
        if (sourceKey.isEmpty() || contractNumber.isEmpty() || eventTypeRaw.isEmpty()) {
            return Outcome.rejected(RejectionReasonCode.MISSING_FIELD);
        }
        EventType eventType = parseEventType(eventTypeRaw);
        if (eventType == null) {
            return Outcome.rejected(RejectionReasonCode.TYPE);
        }
        LocalDate eventDate = parseDate(value(row, "event_date"));
        if (eventDate == null) {
            return Outcome.rejected(RejectionReasonCode.TYPE);
        }
        if (eventDate.isAfter(today)) {
            return Outcome.rejected(RejectionReasonCode.FUTURE_DATE);
        }
        BigDecimal gross = parseDecimal(value(row, "gross_amount"));
        if (isPremium(eventType) && (gross == null || gross.signum() == 0)) {
            return Outcome.rejected(RejectionReasonCode.AMOUNT);
        }
        boolean duplicate = BusinessEvent.count("entity = ?1 and sourceSystem = ?2 and sourceKey = ?3",
                entity, sourceSystem, sourceKey) > 0;
        if (duplicate) {
            return Outcome.rejected(RejectionReasonCode.DUPLICATE);
        }
        Contract contract = Contract.find("entity = ?1 and sourceSystem = ?2 and contractNumber = ?3",
                entity, sourceSystem, contractNumber).firstResult();
        if (contract == null) {
            return Outcome.onHold(HoldReason.UNKNOWN_REFERENCE, eventType, eventDate, gross, null);
        }
        Coverage coverage = resolveCoverage(contract, value(row, "coverage_code"));
        if (coverage == null && !value(row, "coverage_code").isEmpty()) {
            return Outcome.onHold(HoldReason.UNKNOWN_REFERENCE, eventType, eventDate, gross, contract);
        }
        // Negative-line reconciliation (section 9.3, INT-09): an explicit cancel key links the reversed
        // event; a bare negative line is matched by contract, type and opposite amount, else held; an
        // external/insurer negative (a third-party reprise) is integrated as received, without matching.
        String reversesKey = value(row, "reverses_source_key");
        BusinessEvent reversed = null;
        if (!reversesKey.isEmpty()) {
            reversed = BusinessEvent.find("entity = ?1 and sourceSystem = ?2 and sourceKey = ?3",
                    entity, sourceSystem, reversesKey).firstResult();
            if (reversed == null) {
                return Outcome.onHold(HoldReason.UNMATCHED_NEGATIVE, eventType, eventDate, gross, contract);
            }
        } else if (gross != null && gross.signum() < 0 && !isThirdParty(eventType)) {
            reversed = BusinessEvent.find(
                    "entity = ?1 and contract = ?2 and eventType = ?3 and grossAmount = ?4",
                    entity, contract, eventType, gross.negate()).firstResult();
            if (reversed == null) {
                return Outcome.onHold(HoldReason.UNMATCHED_NEGATIVE, eventType, eventDate, gross, contract);
            }
        }
        return Outcome.loaded(eventType, eventDate, gross, contract, coverage, reversed);
    }

    /** Whether a third party (not the application) decided this commission, so a negative is kept as-is. */
    private static boolean isThirdParty(EventType type) {
        return type == EventType.EXTERNAL_COMMISSION || type == EventType.INSURER_COMMISSION;
    }

    /**
     * Resolves a coverage within a contract by its code.
     *
     * @param contract     the contract
     * @param coverageCode the coverage code, possibly empty
     * @return the coverage, or {@code null} if absent or not found
     */
    private static Coverage resolveCoverage(Contract contract, String coverageCode) {
        if (coverageCode.isEmpty()) {
            return null;
        }
        return Coverage.find("contract = ?1 and coverageCode = ?2", contract, coverageCode).firstResult();
    }

    /**
     * Persists a loaded or held event.
     *
     * @param batch        the import batch
     * @param entity       the entity
     * @param sourceSystem the source system
     * @param row          the parsed line
     * @param outcome      the decided outcome
     */
    private void persistEvent(ImportBatch batch, LegalEntity entity, String sourceSystem,
                              Map<String, String> row, Outcome outcome) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = sourceSystem;
        event.sourceKey = value(row, "source_key");
        event.eventType = outcome.eventType;
        event.contract = outcome.contract;
        event.coverage = outcome.coverage;
        event.eventDate = outcome.eventDate;
        event.receivedDate = clock.today();
        event.periodFrom = parseDate(value(row, "period_from"));
        event.periodTo = parseDate(value(row, "period_to"));
        event.grossAmount = outcome.gross;
        event.taxAmount = parseDecimal(value(row, "tax_amount"));
        event.feeAmount = parseDecimal(value(row, "fee_amount"));
        event.basisAmount = basis(row, outcome.gross);
        event.fundCode = emptyToNull(value(row, "fund_code"));
        event.managementProfile = emptyToNull(value(row, "management_profile"));
        event.precomputedBasis = parseDecimal(value(row, "precomputed_basis"));
        event.precomputedRate = parseDecimal(value(row, "precomputed_rate"));
        event.precomputedAmount = parseDecimal(value(row, "precomputed_amount"));
        event.beneficiaryCode = emptyToNull(value(row, "beneficiary_code"));
        event.label = emptyToNull(value(row, "label"));
        event.processingStatus = outcome.holdReason == null ? ProcessingStatus.PENDING : ProcessingStatus.ON_HOLD;
        event.holdReason = outcome.holdReason;
        event.reversesEvent = outcome.reversed;
        event.importBatch = batch;
        event.persist();
    }

    /**
     * Computes the basis amount: explicit value, else gross minus tax minus fee.
     *
     * @param row   the parsed line
     * @param gross the gross amount
     * @return the basis amount, or {@code null}
     */
    private static BigDecimal basis(Map<String, String> row, BigDecimal gross) {
        BigDecimal explicit = parseDecimal(value(row, "basis_amount"));
        if (explicit != null) {
            return explicit;
        }
        if (gross == null) {
            return null;
        }
        BigDecimal tax = orZero(parseDecimal(value(row, "tax_amount")));
        BigDecimal fee = orZero(parseDecimal(value(row, "fee_amount")));
        return gross.subtract(tax).subtract(fee);
    }

    /**
     * Records a rejected line.
     *
     * @param batch      the import batch
     * @param lineNumber the line number
     * @param raw        the raw line content
     * @param reason     the reason code
     */
    private static void reject(ImportBatch batch, int lineNumber, String raw, RejectionReasonCode reason) {
        ImportRejection rejection = new ImportRejection();
        rejection.importBatch = batch;
        rejection.lineNumber = lineNumber;
        rejection.rawLine = raw;
        rejection.reasonCode = reason;
        rejection.status = ImportRejectionStatus.OPEN;
        rejection.persist();
    }

    /** Joins a parsed row's values in header order into a clean CSV line (section 9.9 separator). */
    private static String cleanLine(Map<String, String> row) {
        return String.join(";", row.values());
    }

    /** The outcome of reprocessing a corrected rejected line (INT-03). */
    public record ReprocessOutcome(boolean loaded, boolean onHold, RejectionReasonCode reason, String sourceKey) {
    }

    /**
     * Reprocesses a single corrected line against its original batch (INT-03): the line is re-parsed
     * with the batch header, re-controlled and, if it now passes, persisted as an event and counted in
     * the batch (loaded or on hold); otherwise its new rejection reason is returned. The caller resolves
     * the rejection and records the audit trace.
     *
     * @param batch         the batch the rejected line belongs to
     * @param correctedLine the corrected data line, in the batch's header column order
     * @return the reprocessing outcome
     */
    @Transactional
    public ReprocessOutcome reprocess(ImportBatch batch, String correctedLine) {
        if (batch.header == null) {
            return new ReprocessOutcome(false, false, RejectionReasonCode.STRUCTURE, null);
        }
        CsvFile csv = CsvFile.parse(batch.header + "\n" + correctedLine);
        if (csv.rows().isEmpty() || csv.malformed(0)) {
            return new ReprocessOutcome(false, false, RejectionReasonCode.STRUCTURE, null);
        }
        Map<String, String> row = csv.rows().get(0);
        Outcome outcome = classify(batch.entity, batch.sourceSystem, row, clock.today());
        if (outcome.rejection != null) {
            return new ReprocessOutcome(false, false, outcome.rejection, null);
        }
        // A reprocessable batch was created by importGroups, so its counters are set (header != null).
        persistEvent(batch, batch.entity, batch.sourceSystem, row, outcome);
        boolean onHold = outcome.holdReason != null;
        if (onHold) {
            batch.linesOnHold += 1;
        } else {
            batch.linesLoaded += 1;
        }
        batch.linesRejected = Math.max(0, batch.linesRejected - 1);
        return new ReprocessOutcome(true, onHold, null, value(row, "source_key"));
    }

    /**
     * Tells whether an event type carries a commissionable premium amount.
     *
     * @param type the event type
     * @return {@code true} for a premium collected or issued
     */
    private static boolean isPremium(EventType type) {
        return type == EventType.PREMIUM_COLLECTED || type == EventType.PREMIUM_ISSUED;
    }

    /**
     * Parses an event type, returning {@code null} when unknown.
     *
     * @param raw the raw value
     * @return the event type or {@code null}
     */
    private static EventType parseEventType(String raw) {
        try {
            return EventType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses an ISO date, returning {@code null} on empty or invalid input.
     *
     * @param raw the raw value
     * @return the date or {@code null}
     */
    private static LocalDate parseDate(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Parses a decimal, returning {@code null} on empty or invalid input.
     *
     * @param raw the raw value
     * @return the decimal or {@code null}
     */
    private static BigDecimal parseDecimal(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the value of a column, or an empty string when absent.
     *
     * @param row    the row
     * @param column the column name
     * @return the trimmed value, never null
     */
    private static String value(Map<String, String> row, String column) {
        String raw = row.get(column);
        return raw == null ? "" : raw;
    }

    /**
     * Returns the given amount, or zero when null.
     *
     * @param amount the amount, possibly null
     * @return the amount or zero
     */
    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * Returns null for an empty string, the value otherwise.
     *
     * @param value the value
     * @return null if empty, else the value
     */
    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /** The decided outcome of a line: a rejection, or a (possibly held) event to persist. */
    private static final class Outcome {
        private final RejectionReasonCode rejection;
        private final HoldReason holdReason;
        private final EventType eventType;
        private final LocalDate eventDate;
        private final BigDecimal gross;
        private final Contract contract;
        private final Coverage coverage;
        private final BusinessEvent reversed;

        /**
         * Creates an outcome.
         *
         * @param rejection  the reject reason, or {@code null}
         * @param holdReason the hold reason, or {@code null}
         * @param eventType  the event type, or {@code null}
         * @param eventDate  the event date, or {@code null}
         * @param gross      the gross amount, or {@code null}
         * @param contract   the resolved contract, or {@code null}
         * @param coverage   the resolved coverage, or {@code null}
         * @param reversed   the event this one reverses (section 9.3), or {@code null}
         */
        private Outcome(RejectionReasonCode rejection, HoldReason holdReason, EventType eventType,
                        LocalDate eventDate, BigDecimal gross, Contract contract, Coverage coverage,
                        BusinessEvent reversed) {
            this.rejection = rejection;
            this.holdReason = holdReason;
            this.eventType = eventType;
            this.eventDate = eventDate;
            this.gross = gross;
            this.contract = contract;
            this.coverage = coverage;
            this.reversed = reversed;
        }

        /**
         * Builds a rejected outcome.
         *
         * @param reason the reject reason
         * @return the outcome
         */
        private static Outcome rejected(RejectionReasonCode reason) {
            return new Outcome(reason, null, null, null, null, null, null, null);
        }

        /**
         * Builds a held outcome (event kept pending a missing reference).
         *
         * @param reason   the hold reason
         * @param type     the event type
         * @param date     the event date
         * @param gross    the gross amount
         * @param contract the resolved contract, or {@code null}
         * @return the outcome
         */
        private static Outcome onHold(HoldReason reason, EventType type, LocalDate date, BigDecimal gross,
                                      Contract contract) {
            return new Outcome(null, reason, type, date, gross, contract, null, null);
        }

        /**
         * Builds a loaded outcome (event ready to persist), optionally reversing another event (§9.3).
         *
         * @param type     the event type
         * @param date     the event date
         * @param gross    the gross amount
         * @param contract the resolved contract
         * @param coverage the resolved coverage, or {@code null}
         * @param reversed the event this one reverses, or {@code null}
         * @return the outcome
         */
        private static Outcome loaded(EventType type, LocalDate date, BigDecimal gross, Contract contract,
                                      Coverage coverage, BusinessEvent reversed) {
            return new Outcome(null, null, type, date, gross, contract, coverage, reversed);
        }
    }
}
