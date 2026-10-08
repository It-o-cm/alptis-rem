package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentFrequency;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Imports the payment-rule and external-code referentials (sections 5.7, REF-06 and REF-04). A
 * payment rule carries its beneficiary shares inline in the {@code shares} column, as
 * {@code TYPE:CODE:FRACTION} segments separated by a pipe (so they do not clash with the CSV
 * semicolon) and summing to 1; an external code links an internal object to its identifier in a
 * source system. Both files are journalled with the five counters and are idempotent by
 * fingerprint; a line referencing an unknown object is kept on hold.
 */
@ApplicationScoped
public class PayeeExternalImportService {

    /** Payment-rule referential flow code (section 9.10). */
    public static final String PAYEE_RULES = "payee_rules";

    /** External-code correspondence flow code (section 9.10). */
    public static final String EXTERNAL_CODES = "external_codes";

    /** The date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public PayeeExternalImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports a payment-rules file (REF-06).
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importPayeeRules(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, PAYEE_RULES, sourceSystem, fileName, content,
                PayeeExternalImportService::rejectPayeeRule,
                row -> persistPayeeRule(entity, row));
    }

    /**
     * Imports an external-codes file (REF-04).
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importExternalCodes(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, EXTERNAL_CODES, sourceSystem, fileName, content,
                PayeeExternalImportService::rejectExternalCode,
                row -> persistExternalCode(entity, row));
    }

    /**
     * Controls one payment-rule line: perimeter present, frequency and dates well-typed, shares
     * well-formed and summing to 1.
     *
     * @param row the parsed line
     * @return a reject code, or null to process the line
     */
    private static RejectionReasonCode rejectPayeeRule(Map<String, String> row) {
        if (value(row, "payment_frequency").isEmpty() || value(row, "shares").isEmpty()
                || (value(row, "production_code").isEmpty() && value(row, "partner_code").isEmpty())) {
            return RejectionReasonCode.MISSING_FIELD;
        }
        if (parseFrequency(value(row, "payment_frequency")) == null
                || badDate(value(row, "valid_from")) || badDate(value(row, "valid_to"))) {
            return RejectionReasonCode.TYPE;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (String part : value(row, "shares").split("\\|")) {
            String[] cells = part.split(":");
            if (cells.length != 3 || parsePayeeType(cells[0]) == null) {
                return RejectionReasonCode.TYPE;
            }
            BigDecimal pct = parseDecimal(cells[2]);
            if (pct == null) {
                return RejectionReasonCode.TYPE;
            }
            sum = sum.add(pct);
        }
        return sum.compareTo(BigDecimal.ONE) == 0 ? null : RejectionReasonCode.AMOUNT;
    }

    /**
     * Controls one external-code line: object type, internal code, source system and external code
     * all present and the type recognised.
     *
     * @param row the parsed line
     * @return a reject code, or null to process the line
     */
    private static RejectionReasonCode rejectExternalCode(Map<String, String> row) {
        if (value(row, "object_type").isEmpty() || value(row, "internal_code").isEmpty()
                || value(row, "external_code").isEmpty()) {
            return RejectionReasonCode.MISSING_FIELD;
        }
        return parsePayeeType(value(row, "object_type")) == null ? RejectionReasonCode.TYPE : null;
    }

    /**
     * Persists a payment rule and its shares, holding the line if a referenced object is unknown.
     *
     * @param entity the importing entity
     * @param row    the parsed line
     * @return LOADED, or ON_HOLD when a perimeter or beneficiary reference is unknown
     */
    private static ImportJournal.LineResult persistPayeeRule(LegalEntity entity, Map<String, String> row) {
        ProductionCode productionCode = null;
        if (!value(row, "production_code").isEmpty()) {
            productionCode = ProductionCode.find("entity = ?1 and code = ?2", entity, value(row, "production_code"))
                    .firstResult();
            if (productionCode == null) {
                return ImportJournal.LineResult.ON_HOLD;
            }
        }
        Distributor distributor = null;
        if (!value(row, "partner_code").isEmpty()) {
            distributor = Distributor.find("partnerCode", value(row, "partner_code")).firstResult();
            if (distributor == null) {
                return ImportJournal.LineResult.ON_HOLD;
            }
        }
        ProductRange range = null;
        if (!value(row, "range_code").isEmpty()) {
            range = ProductRange.find("code", value(row, "range_code")).firstResult();
            if (range == null) {
                return ImportJournal.LineResult.ON_HOLD;
            }
        }

        // Resolve every beneficiary before persisting anything, so an unknown reference holds the
        // whole line without leaving a partial rule behind.
        String[] parts = value(row, "shares").split("\\|");
        PayeeType[] types = new PayeeType[parts.length];
        Long[] payeeIds = new Long[parts.length];
        BigDecimal[] percentages = new BigDecimal[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String[] cells = parts[i].split(":");
            types[i] = parsePayeeType(cells[0]);
            payeeIds[i] = resolvePayee(entity, types[i], cells[1]);
            percentages[i] = parseDecimal(cells[2]);
            if (payeeIds[i] == null) {
                return ImportJournal.LineResult.ON_HOLD;
            }
        }

        PayeeRule rule = new PayeeRule();
        rule.entity = entity;
        rule.productionCode = productionCode;
        rule.distributor = distributor;
        rule.range = range;
        rule.paymentFrequency = parseFrequency(value(row, "payment_frequency"));
        rule.validFrom = parseDate(value(row, "valid_from"));
        rule.validTo = parseDate(value(row, "valid_to"));
        rule.persist();

        for (int i = 0; i < parts.length; i++) {
            PayeeRuleShare share = new PayeeRuleShare();
            share.payeeRule = rule;
            share.payeeType = types[i];
            share.payeeId = payeeIds[i];
            share.share = percentages[i];
            share.persist();
        }
        return ImportJournal.LineResult.LOADED;
    }

    /**
     * Persists an external code, holding the line if the internal object is unknown.
     *
     * @param entity the importing entity
     * @param row    the parsed line
     * @return LOADED, or ON_HOLD when the internal object is unknown
     */
    private static ImportJournal.LineResult persistExternalCode(LegalEntity entity, Map<String, String> row) {
        PayeeType type = parsePayeeType(value(row, "object_type"));
        Long objectId = resolvePayee(entity, type, value(row, "internal_code"));
        if (objectId == null) {
            return ImportJournal.LineResult.ON_HOLD;
        }
        ExternalCode code = new ExternalCode();
        code.objectType = type.name();
        code.objectId = objectId;
        code.sourceSystem = value(row, "source_system");
        code.externalCode = value(row, "external_code");
        code.persist();
        return ImportJournal.LineResult.LOADED;
    }

    /**
     * Resolves a payee or object reference to its technical identifier.
     *
     * @param entity the entity (for production codes, which are partitioned)
     * @param type   the payee type
     * @param code   the business code
     * @return the technical identifier, or null if unknown
     */
    private static Long resolvePayee(LegalEntity entity, PayeeType type, String code) {
        return switch (type) {
            case PRODUCTION_CODE -> {
                ProductionCode pc = ProductionCode.find("entity = ?1 and code = ?2", entity, code).firstResult();
                yield pc == null ? null : pc.getId();
            }
            case DISTRIBUTOR -> {
                Distributor d = Distributor.find("partnerCode", code).firstResult();
                yield d == null ? null : d.getId();
            }
            case GROUP -> {
                DistributorGroup g = DistributorGroup.find("code", code).firstResult();
                yield g == null ? null : g.getId();
            }
        };
    }

    /**
     * Parses a payment frequency, returning null when unrecognised.
     *
     * @param raw the raw value
     * @return the frequency, or null
     */
    private static PaymentFrequency parseFrequency(String raw) {
        try {
            return PaymentFrequency.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a payee type, returning null when unrecognised.
     *
     * @param raw the raw value
     * @return the payee type, or null
     */
    private static PayeeType parsePayeeType(String raw) {
        try {
            return PayeeType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a decimal, returning null when malformed.
     *
     * @param raw the raw value
     * @return the decimal, or null
     */
    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Parses an ISO date, returning null when empty or malformed.
     *
     * @param raw the raw value
     * @return the date, or null
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
     * Tells whether a non-empty value fails to parse as an ISO date.
     *
     * @param raw the raw value
     * @return true if the value is present but not a valid date
     */
    private static boolean badDate(String raw) {
        return !raw.isEmpty() && parseDate(raw) == null;
    }

    /**
     * Reads a column, mapping a missing value to the empty string.
     *
     * @param row    the parsed line
     * @param column the column name
     * @return the value, never null
     */
    private static String value(Map<String, String> row, String column) {
        String raw = row.get(column);
        return raw == null ? "" : raw;
    }
}
