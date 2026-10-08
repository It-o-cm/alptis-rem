package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.ImportRejectionStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import io.quarkus.runtime.util.HashUtil;

import java.util.Map;
import java.util.function.Function;

/**
 * Shared skeleton for CSV imports: resolves the entity, refuses an already-imported file by
 * fingerprint, creates the journalled batch, parses the file, runs a per-line processor and keeps
 * the five counters of ING-05 up to date. The processor returns the line's outcome.
 */
public final class ImportJournal {

    /** Outcome of processing one line. */
    public enum LineResult {
        /** Line integrated. */
        LOADED,
        /** Line kept pending a missing reference. */
        ON_HOLD
    }

    /** Prevents instantiation. */
    private ImportJournal() {
    }

    /**
     * Runs an import and journals it.
     *
     * @param clock        the date/time provider
     * @param entityCode   the entity code
     * @param flowCode     the flow code (object name)
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @param processor    processes one row, returning its result or throwing nothing; it records
     *                     a rejection itself by returning a reason through {@code rejector}
     * @param rejector     consulted first: returns a reject code for the row, or null to process it
     * @return the journalled batch
     * @throws ConflictException if the entity is unknown or the file was already imported
     */
    public static ImportBatch run(DateTimeProvider clock, String entityCode, String flowCode, String sourceSystem,
                                  String fileName, String content,
                                  Function<Map<String, String>, RejectionReasonCode> rejector,
                                  Function<Map<String, String>, LineResult> processor) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ConflictException("Entité inconnue : " + entityCode);
        }
        String fileHash = HashUtil.sha256(fileName + "\n" + content);
        if (ImportBatch.count("entity = ?1 and sourceSystem = ?2 and fileHash = ?3", entity, sourceSystem, fileHash) > 0) {
            throw new ConflictException("Fichier déjà intégré : " + fileName);
        }

        ImportBatch batch = new ImportBatch();
        batch.entity = entity;
        batch.flowCode = flowCode;
        batch.sourceSystem = sourceSystem;
        batch.fileName = fileName;
        batch.fileHash = fileHash;
        batch.receivedAt = clock.now();
        batch.importedBy = "import";
        batch.linesFiltered = 0;
        batch.persist();

        CsvFile csv = CsvFile.parse(content);
        int loaded = 0;
        int rejected = 0;
        int onHold = 0;
        int lineNumber = 1;
        for (Map<String, String> row : csv.rows()) {
            lineNumber++;
            RejectionReasonCode reason = rejector.apply(row);
            if (reason != null) {
                ImportRejection rejection = new ImportRejection();
                rejection.importBatch = batch;
                rejection.lineNumber = lineNumber;
                rejection.rawLine = row.toString();
                rejection.reasonCode = reason;
                rejection.status = ImportRejectionStatus.OPEN;
                rejection.persist();
                rejected++;
            } else if (processor.apply(row) == LineResult.ON_HOLD) {
                onHold++;
            } else {
                loaded++;
            }
        }

        batch.linesRead = csv.rows().size();
        batch.linesLoaded = loaded;
        batch.linesRejected = rejected;
        batch.linesOnHold = onHold;
        batch.status = rejected == 0 ? ImportBatchStatus.LOADED : ImportBatchStatus.PARTIALLY_LOADED;
        return batch;
    }
}
