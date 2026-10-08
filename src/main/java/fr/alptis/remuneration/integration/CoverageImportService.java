package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.AssignmentOrigin;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ContractStatus;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.ImportRejectionStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Policyholder;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Imports a coverages pivot file (section 9.2): one line per coverage, creating or updating the
 * policyholder, contract, coverage, remuneration choice and main production-code assignment. An
 * unknown product or production code puts the line on hold; a known coverage is updated. Journalled
 * with the five counters and idempotent by file fingerprint.
 */
@ApplicationScoped
public class CoverageImportService {

    /** Source of "now", for historisation and received dates. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public CoverageImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports a coverages file for an entity and source system.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw file content
     * @return the journalled import batch
     * @throws ConflictException if the entity is unknown or the file was already imported
     */
    @Transactional
    public ImportBatch importCoverages(String entityCode, String sourceSystem, String fileName, String content) {
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
        batch.flowCode = FlowCodes.COVERAGES;
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
            RejectionReasonCode reject = control(entity, row);
            if (reject != null) {
                ImportRejection rejection = new ImportRejection();
                rejection.importBatch = batch;
                rejection.lineNumber = lineNumber;
                rejection.rawLine = row.toString();
                rejection.reasonCode = reject;
                rejection.status = ImportRejectionStatus.OPEN;
                rejection.persist();
                rejected++;
            } else if (held(entity, row)) {
                onHold++;
            } else {
                upsert(entity, sourceSystem, row);
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

    /**
     * Controls a line, returning a reject code or {@code null} when acceptable.
     *
     * @param entity the entity
     * @param row    the parsed line
     * @return the reject reason, or {@code null}
     */
    private static RejectionReasonCode control(LegalEntity entity, Map<String, String> row) {
        for (String required : new String[]{"contract_number", "coverage_code", "product_code", "policyholder_id",
                "registration_date", "effect_date", "status", "remuneration_mode", "production_code"}) {
            if (value(row, required).isEmpty()) {
                return RejectionReasonCode.MISSING_FIELD;
            }
        }
        if (parseStatus(value(row, "status")) == null || parseMode(value(row, "remuneration_mode")) == null) {
            return RejectionReasonCode.TYPE;
        }
        if (parseDate(value(row, "effect_date")) == null || parseDate(value(row, "registration_date")) == null) {
            return RejectionReasonCode.TYPE;
        }
        return null;
    }

    /**
     * Tells whether a line references an unknown product or production code (put on hold).
     *
     * @param entity the entity
     * @param row    the parsed line
     * @return {@code true} if the line must be held
     */
    private static boolean held(LegalEntity entity, Map<String, String> row) {
        return product(entity, value(row, "product_code")) == null
                || productionCode(entity, value(row, "production_code")) == null;
    }

    /**
     * Creates or updates the policyholder, contract, coverage, choice and assignment.
     *
     * @param entity       the entity
     * @param sourceSystem the source system
     * @param row          the parsed line
     */
    private void upsert(LegalEntity entity, String sourceSystem, Map<String, String> row) {
        Policyholder holder = Policyholder.find("entity = ?1 and sourceSystem = ?2 and externalId = ?3",
                entity, sourceSystem, value(row, "policyholder_id")).firstResult();
        if (holder == null) {
            holder = new Policyholder();
            holder.entity = entity;
            holder.sourceSystem = sourceSystem;
            holder.externalId = value(row, "policyholder_id");
            holder.persist();
        }
        holder.lastName = emptyToNull(value(row, "policyholder_name"));

        Contract contract = Contract.find("entity = ?1 and sourceSystem = ?2 and contractNumber = ?3",
                entity, sourceSystem, value(row, "contract_number")).firstResult();
        if (contract == null) {
            contract = new Contract();
            contract.entity = entity;
            contract.sourceSystem = sourceSystem;
            contract.contractNumber = value(row, "contract_number");
            contract.persist();
        }
        contract.product = product(entity, value(row, "product_code"));
        contract.policyholder = holder;
        contract.registrationDate = parseDate(value(row, "registration_date"));
        contract.effectDate = parseDate(value(row, "effect_date"));
        contract.status = parseStatus(value(row, "status"));

        Coverage coverage = Coverage.find("contract = ?1 and coverageCode = ?2",
                contract, value(row, "coverage_code")).firstResult();
        if (coverage == null) {
            coverage = new Coverage();
            coverage.contract = contract;
            coverage.coverageCode = value(row, "coverage_code");
            coverage.persist();
        }
        coverage.effectDate = parseDate(value(row, "effect_date"));
        coverage.status = parseStatus(value(row, "status"));
        coverage.annualPremium = parseDecimal(value(row, "annual_premium"));

        upsertChoice(coverage, row);
        upsertAssignment(entity, coverage, row);
    }

    /**
     * Sets the current remuneration choice of a coverage from the line.
     *
     * @param coverage the coverage
     * @param row      the parsed line
     */
    private void upsertChoice(Coverage coverage, Map<String, String> row) {
        RemunerationChoice choice = RemunerationChoice.find("coverage = ?1 and validTo is null", coverage).firstResult();
        if (choice == null) {
            choice = new RemunerationChoice();
            choice.coverage = coverage;
            choice.validFrom = coverage.effectDate;
            choice.persist();
        }
        choice.mode = parseMode(value(row, "remuneration_mode"));
        choice.linearRate = parseDecimal(value(row, "linear_rate"));
        choice.acquisitionRate = parseDecimal(value(row, "acquisition_rate"));
        choice.servicingRate = parseDecimal(value(row, "servicing_rate"));
        choice.advanceRequested = "true".equals(value(row, "advance_requested"));
    }

    /**
     * Sets the current main production-code assignment of a coverage from the line.
     *
     * @param entity   the entity
     * @param coverage the coverage
     * @param row      the parsed line
     */
    private void upsertAssignment(LegalEntity entity, Coverage coverage, Map<String, String> row) {
        CoverageAssignment assignment = CoverageAssignment.find("coverage = ?1 and validTo is null", coverage)
                .firstResult();
        if (assignment == null) {
            assignment = new CoverageAssignment();
            assignment.coverage = coverage;
            assignment.validFrom = coverage.effectDate;
            assignment.origin = AssignmentOrigin.SUBSCRIPTION;
            assignment.role = AssignmentRole.MAIN;
            assignment.share = BigDecimal.ONE;
            assignment.persist();
        }
        assignment.productionCode = productionCode(entity, value(row, "production_code"));
    }

    /**
     * Finds a product by entity and code.
     *
     * @param entity the entity
     * @param code   the product code
     * @return the product, or {@code null}
     */
    private static Product product(LegalEntity entity, String code) {
        return Product.find("entity = ?1 and code = ?2", entity, code).firstResult();
    }

    /**
     * Finds a production code by entity and code.
     *
     * @param entity the entity
     * @param code   the production code
     * @return the production code, or {@code null}
     */
    private static ProductionCode productionCode(LegalEntity entity, String code) {
        return ProductionCode.find("entity = ?1 and code = ?2", entity, code).firstResult();
    }

    /**
     * Parses a contract status, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the status, or {@code null}
     */
    private static ContractStatus parseStatus(String raw) {
        try {
            return ContractStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a remuneration mode, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the mode, or {@code null}
     */
    private static RemunerationMode parseMode(String raw) {
        try {
            return RemunerationMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses an ISO date, returning {@code null} on invalid input.
     *
     * @param raw the raw value
     * @return the date, or {@code null}
     */
    private static LocalDate parseDate(String raw) {
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
     * @return the decimal, or {@code null}
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
     * @param row    the parsed row
     * @param column the column name
     * @return the value, never null
     */
    private static String value(Map<String, String> row, String column) {
        String raw = row.get(column);
        return raw == null ? "" : raw;
    }

    /**
     * Returns {@code null} for an empty string, the value otherwise.
     *
     * @param value the value
     * @return {@code null} if empty, else the value
     */
    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
