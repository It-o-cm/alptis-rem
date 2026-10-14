package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DistributorType;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import fr.alptis.remuneration.security.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * Imports the distributor hierarchy referentials (section 9.10): distributors with their per-entity
 * status, distributor groups and production codes. Each file is journalled with the five counters
 * and idempotent by fingerprint.
 */
@ApplicationScoped
public class ReferentialImportService {

    /** The date/time provider. */
    private final DateTimeProvider clock;

    /** The audit journal, used to historise hierarchy changes (REF-03, ADM-04). */
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     * @param audit the audit journal used to historise hierarchy changes
     */
    @Inject
    public ReferentialImportService(DateTimeProvider clock, AuditService audit) {
        this.clock = clock;
        this.audit = audit;
    }

    /**
     * Imports a distributors file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importDistributors(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, FlowCodes.DISTRIBUTORS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "partner_code").isEmpty() || value(row, "legal_name").isEmpty()
                            || value(row, "status").isEmpty() || value(row, "distributor_type").isEmpty()
                            || value(row, "valid_from").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parseType(value(row, "distributor_type")) == null
                            || parseStatus(value(row, "status")) == null
                            || parseDate(value(row, "valid_from")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    upsertDistributor(entity, row);
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a distributor-groups file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importGroups(String entityCode, String sourceSystem, String fileName, String content) {
        return ImportJournal.run(clock, entityCode, FlowCodes.DISTRIBUTOR_GROUPS, sourceSystem, fileName, content,
                row -> (value(row, "group_code").isEmpty() || value(row, "label").isEmpty())
                        ? RejectionReasonCode.MISSING_FIELD : null,
                row -> {
                    upsertGroup(row);
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a production-codes file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importProductionCodes(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, FlowCodes.PRODUCTION_CODES, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "production_code").isEmpty() || value(row, "partner_code").isEmpty()
                            || value(row, "status").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    return parseStatus(value(row, "status")) == null ? RejectionReasonCode.TYPE : null;
                },
                row -> {
                    upsertProductionCode(entity, row);
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Creates or updates a distributor (shared referential) and its status within the entity.
     *
     * @param entity the importing entity
     * @param row    the parsed line
     */
    private void upsertDistributor(LegalEntity entity, Map<String, String> row) {
        String partnerCode = value(row, "partner_code");
        Distributor distributor = Distributor.find("partnerCode", partnerCode).firstResult();
        boolean existed = distributor != null;
        if (!existed) {
            distributor = new Distributor();
            distributor.partnerCode = partnerCode;
            distributor.persist();
        }
        distributor.legalName = value(row, "legal_name");
        distributor.distributorType = parseType(value(row, "distributor_type"));
        String previousGroup = distributor.group == null ? null : distributor.group.code;
        DistributorGroup newGroup = groupByCode(value(row, "group_code"));
        String nextGroup = newGroup == null ? null : newGroup.code;
        // Historise a change of grouping (the distributor's place in the hierarchy, REF-03).
        if (existed && !java.util.Objects.equals(previousGroup, nextGroup)) {
            audit.record("import", entity, "REASSIGN_DISTRIBUTOR_GROUP", "Distributor",
                    distributor.getId(), json(previousGroup), json(nextGroup));
        }
        distributor.group = newGroup;
        distributor.commissionable = !"false".equals(value(row, "commissionable"));
        distributor.advanceExcluded = "true".equals(value(row, "advance_excluded"));
        // ORIAS registration status, carried by the distributor referential (default registered); a
        // struck-off then re-registered distributor is reinstated by re-importing it (CR-62).
        distributor.oriasStatus = parseOrias(value(row, "orias_status"));

        LocalDate validFrom = parseDate(value(row, "valid_from"));
        DistributorEntity link = DistributorEntity.find(
                "distributor = ?1 and entity = ?2 and validFrom = ?3", distributor, entity, validFrom).firstResult();
        if (link == null) {
            link = new DistributorEntity();
            link.distributor = distributor;
            link.entity = entity;
            link.validFrom = validFrom;
            link.persist();
        }
        link.status = parseStatus(value(row, "status"));
        link.validTo = parseDate(value(row, "valid_to"));
    }

    /**
     * Creates or updates a distributor group.
     *
     * @param row the parsed line
     */
    private static void upsertGroup(Map<String, String> row) {
        String code = value(row, "group_code");
        DistributorGroup group = DistributorGroup.find("code", code).firstResult();
        if (group == null) {
            group = new DistributorGroup();
            group.code = code;
            group.persist();
        }
        group.label = value(row, "label");
        group.headDistributor = distributorByCode(value(row, "head_partner_code"));
    }

    /**
     * Creates or updates a production code within the entity.
     *
     * @param entity the entity
     * @param row    the parsed line
     */
    private void upsertProductionCode(LegalEntity entity, Map<String, String> row) {
        String code = value(row, "production_code");
        ProductionCode pc = ProductionCode.find("entity = ?1 and code = ?2", entity, code).firstResult();
        boolean existed = pc != null;
        if (!existed) {
            pc = new ProductionCode();
            pc.entity = entity;
            pc.code = code;
            pc.persist();
        }
        String previousOwner = pc.distributor == null ? null : pc.distributor.partnerCode;
        Distributor newOwner = distributorByCode(value(row, "partner_code"));
        String nextOwner = newOwner == null ? null : newOwner.partnerCode;
        // Historise a reassignment of the production code to another distributor (REF-03).
        if (existed && !java.util.Objects.equals(previousOwner, nextOwner)) {
            audit.record("import", entity, "REASSIGN_PRODUCTION_CODE", "ProductionCode",
                    pc.getId(), json(previousOwner), json(nextOwner));
        }
        pc.distributor = newOwner;
        pc.label = emptyToNull(value(row, "label"));
        pc.status = parseStatus(value(row, "status"));
    }

    /**
     * Finds a distributor by partner code, or {@code null} when the code is empty or unknown.
     *
     * @param partnerCode the partner code, possibly empty
     * @return the distributor, or {@code null}
     */
    private static Distributor distributorByCode(String partnerCode) {
        return partnerCode.isEmpty() ? null : Distributor.find("partnerCode", partnerCode).firstResult();
    }

    /**
     * Finds a distributor group by code, or {@code null} when the code is empty or unknown.
     *
     * @param code the group code, possibly empty
     * @return the group, or {@code null}
     */
    private static DistributorGroup groupByCode(String code) {
        return code.isEmpty() ? null : DistributorGroup.find("code", code).firstResult();
    }

    /**
     * Parses a distributor type, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the type, or {@code null}
     */
    private static DistributorType parseType(String raw) {
        try {
            return DistributorType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses an ORIAS status: {@code STRUCK_OFF} when so stated, registered otherwise (the default when
     * the column is absent).
     *
     * @param raw the raw value
     * @return the ORIAS status
     */
    private static OriasStatus parseOrias(String raw) {
        return "STRUCK_OFF".equals(raw) ? OriasStatus.STRUCK_OFF : OriasStatus.REGISTERED;
    }

    /**
     * Parses a partner status, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the status, or {@code null}
     */
    private static PartnerStatus parseStatus(String raw) {
        try {
            return PartnerStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses an ISO date, returning {@code null} on empty or invalid input.
     *
     * @param raw the raw value
     * @return the date, or {@code null}
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

    /**
     * Encodes a code as a JSON string literal for the audit journal's JSON before/after columns.
     *
     * @param code the code, possibly null
     * @return the JSON string literal, or {@code null} when the code is null
     */
    private static String json(String code) {
        return code == null ? null : "\"" + code + "\"";
    }
}
