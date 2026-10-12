package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.cycle.AdjustmentService;
import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.AdjustmentType;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bulk import of adjustments (REG-04, section 9.4): parses the pivot adjustments file and creates one
 * submitted adjustment per well-formed line, resolving each payee code to its production-code
 * account. A line missing a required field, carrying an unknown type, a non-numeric amount or an
 * unknown payee is skipped; the rest are created through {@link AdjustmentService}.
 */
@ApplicationScoped
public class AdjustmentImportService {

    /** The distributor-account service, to resolve a payee code to its account. */
    private final AccountService accounts;

    /** The adjustment service, to create the submitted adjustments. */
    private final AdjustmentService adjustments;

    /**
     * Creates the service.
     *
     * @param accounts    the distributor-account service
     * @param adjustments the adjustment service
     */
    @Inject
    public AdjustmentImportService(AccountService accounts, AdjustmentService adjustments) {
        this.accounts = accounts;
        this.adjustments = adjustments;
    }

    /**
     * Imports the adjustments of a pivot file into a period (REG-04).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param content    the raw CSV content
     * @param userId     the operator
     * @return the number of adjustments created
     */
    @Transactional
    public int importAdjustments(String entityCode, LocalDate month, String content, String userId) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, month).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + month);
        }
        List<Adjustment> toCreate = new ArrayList<>();
        for (Map<String, String> row : CsvFile.parse(content).rows()) {
            Adjustment adjustment = build(entity, period, row, userId);
            if (adjustment != null) {
                toCreate.add(adjustment);
            }
        }
        return adjustments.importBulk(toCreate);
    }

    /**
     * Builds one adjustment stub from a pivot row, or null when the row is not usable.
     *
     * @param entity the entity
     * @param period the period
     * @param row    the parsed line
     * @param userId the operator
     * @return the adjustment stub, or null to skip the line
     */
    private Adjustment build(LegalEntity entity, Period period, Map<String, String> row, String userId) {
        for (String required : new String[]{"adjustment_type", "payee_code", "amount", "reason"}) {
            if (value(row, required).isBlank()) {
                return null;
            }
        }
        AdjustmentType type = parseType(value(row, "adjustment_type"));
        BigDecimal amount = parseAmount(value(row, "amount"));
        DistributorAccount account = resolve(entity, value(row, "payee_code"));
        if (type == null || amount == null || account == null) {
            return null;
        }
        DistributorAccount counterpart = null;
        String counterpartCode = value(row, "counterpart_payee_code");
        if (!counterpartCode.isBlank()) {
            counterpart = resolve(entity, counterpartCode);
            if (counterpart == null) {
                return null;
            }
        }
        Adjustment adjustment = new Adjustment();
        adjustment.entity = entity;
        adjustment.period = period;
        adjustment.adjustmentType = type;
        adjustment.amount = amount;
        adjustment.account = account;
        adjustment.reason = value(row, "reason");
        adjustment.counterpartAccount = counterpart;
        adjustment.autoReverse = "true".equalsIgnoreCase(value(row, "auto_reverse"));
        adjustment.setCreatedBy(userId);
        return adjustment;
    }

    /**
     * Resolves a payee code to its production-code account within an entity, or null when unknown.
     *
     * @param entity the entity
     * @param code   the production code
     * @return the account, or null
     */
    private DistributorAccount resolve(LegalEntity entity, String code) {
        ProductionCode productionCode = ProductionCode.<ProductionCode>find("entity = ?1 and code = ?2", entity, code)
                .firstResult();
        if (productionCode == null) {
            return null;
        }
        return accounts.accountFor(entity, PayeeType.PRODUCTION_CODE, productionCode.getId());
    }

    /**
     * Parses an adjustment type, null when unknown.
     *
     * @param raw the type name
     * @return the type, or null
     */
    private static AdjustmentType parseType(String raw) {
        try {
            return AdjustmentType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a decimal amount, null when not numeric.
     *
     * @param raw the amount text
     * @return the amount, or null
     */
    private static BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns a trimmed cell value, empty string when absent.
     *
     * @param row the row
     * @param key the column
     * @return the value, never null
     */
    private static String value(Map<String, String> row, String key) {
        String raw = row.get(key);
        return raw == null ? "" : raw.trim();
    }
}
