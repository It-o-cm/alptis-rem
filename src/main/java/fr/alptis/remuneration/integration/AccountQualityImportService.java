package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Imports bank-account (REF-05, with IBAN structure control) and quality-indicator (REF-07)
 * referentials (section 9.10). A row referencing an unknown holder or distributor is put on hold;
 * a malformed IBAN is rejected.
 */
@ApplicationScoped
public class AccountQualityImportService {

    /** Bank accounts file flow code. */
    public static final String BANK_ACCOUNTS = FlowCodes.BANK_ACCOUNTS;

    /** Quality indicators file flow code. */
    public static final String QUALITY_INDICATORS = FlowCodes.QUALITY_INDICATORS;

    /** The date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public AccountQualityImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports a bank-accounts file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importBankAccounts(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, BANK_ACCOUNTS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "holder_type").isEmpty() || value(row, "holder_code").isEmpty()
                            || value(row, "iban").isEmpty() || value(row, "holder_name").isEmpty()
                            || value(row, "valid_from").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parsePayeeType(value(row, "holder_type")) == null
                            || parseDate(value(row, "valid_from")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return validIban(value(row, "iban")) ? null : RejectionReasonCode.STRUCTURE;
                },
                row -> {
                    Long holderId = resolveHolder(entity, parsePayeeType(value(row, "holder_type")),
                            value(row, "holder_code"));
                    if (holderId == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    BankAccount account = new BankAccount();
                    account.entity = entity;
                    account.holderType = parsePayeeType(value(row, "holder_type"));
                    account.holderId = holderId;
                    account.iban = value(row, "iban");
                    account.bic = emptyToNull(value(row, "bic"));
                    account.holderName = value(row, "holder_name");
                    account.validFrom = parseDate(value(row, "valid_from"));
                    account.validTo = parseDate(value(row, "valid_to"));
                    account.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a quality-indicators file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importQualityIndicators(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, QUALITY_INDICATORS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "partner_code").isEmpty() || value(row, "indicator_type").isEmpty()
                            || value(row, "period_from").isEmpty() || value(row, "period_to").isEmpty()
                            || value(row, "value").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parseIndicatorType(value(row, "indicator_type")) == null
                            || parseDate(value(row, "period_to")) == null
                            || parseDecimal(value(row, "value")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    Distributor distributor = Distributor.find("partnerCode", value(row, "partner_code")).firstResult();
                    if (distributor == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    ProductRange range = value(row, "range_code").isEmpty() ? null
                            : ProductRange.find("code", value(row, "range_code")).firstResult();
                    QualityIndicator indicator = new QualityIndicator();
                    indicator.entity = entity;
                    indicator.distributor = distributor;
                    indicator.indicatorType = parseIndicatorType(value(row, "indicator_type"));
                    indicator.range = range;
                    indicator.periodFrom = parseDate(value(row, "period_from"));
                    indicator.periodTo = parseDate(value(row, "period_to"));
                    indicator.value = parseDecimal(value(row, "value"));
                    indicator.source = emptyToNull(value(row, "source"));
                    indicator.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Resolves a bank-account holder code to its technical id within the entity.
     *
     * @param entity     the entity
     * @param holderType the holder type
     * @param holderCode the holder business code
     * @return the holder technical id, or {@code null} if unknown
     */
    private static Long resolveHolder(LegalEntity entity, PayeeType holderType, String holderCode) {
        return switch (holderType) {
            case PRODUCTION_CODE -> idOf(ProductionCode.find("entity = ?1 and code = ?2", entity, holderCode).firstResult());
            case DISTRIBUTOR -> idOf(Distributor.find("partnerCode", holderCode).firstResult());
            case GROUP -> idOf(DistributorGroup.find("code", holderCode).firstResult());
        };
    }

    /**
     * Returns the technical id of an entity, or {@code null}.
     *
     * @param entity the entity, possibly null
     * @return its id or null
     */
    private static Long idOf(fr.alptis.remuneration.domain.BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }

    /**
     * Checks the structure of an IBAN (length and ISO 7064 mod-97).
     *
     * @param iban the IBAN
     * @return {@code true} if the structure is valid
     */
    static boolean validIban(String iban) {
        String compact = iban.replace(" ", "").toUpperCase();
        if (compact.length() < 15 || compact.length() > 34 || !compact.matches("[A-Z0-9]+")) {
            return false;
        }
        String rearranged = compact.substring(4) + compact.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < rearranged.length(); i++) {
            char ch = rearranged.charAt(i);
            digits.append(Character.isLetter(ch) ? Integer.toString(ch - 'A' + 10) : ch);
        }
        BigDecimal number = new BigDecimal(digits.toString());
        return number.remainder(BigDecimal.valueOf(97)).intValueExact() == 1;
    }

    /**
     * Parses a payee (holder) type, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the type, or {@code null}
     */
    private static PayeeType parsePayeeType(String raw) {
        try {
            return PayeeType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a quality-indicator type, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the type, or {@code null}
     */
    private static QualityIndicatorType parseIndicatorType(String raw) {
        try {
            return QualityIndicatorType.valueOf(raw);
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
     * Parses a decimal, returning {@code null} on invalid input.
     *
     * @param raw the raw value
     * @return the decimal, or {@code null}
     */
    private static BigDecimal parseDecimal(String raw) {
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
