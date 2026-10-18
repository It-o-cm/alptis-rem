package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ProductionCounter;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import fr.alptis.remuneration.domain.Rule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * History reprise of the opening state at a perimeter switch-over (ING-11, section 14): opening
 * account balances (posted as {@code OPENING_BALANCE} entries so the migrated account opens at its
 * carried balance), open discount positions (so a discount's apurement continues in the first
 * period) and annual surcommission counters (so the first month's development remuneration starts
 * from the carried cumul). The acquired lines of the last months are replayed through the ordinary
 * events flow. A line whose beneficiary, guarantee or rule is unknown is kept on hold; a malformed
 * field is rejected.
 */
@ApplicationScoped
public class OpeningBalanceImportService {

    /** The opening-balance flow code. */
    public static final String OPENING_BALANCES = FlowCodes.OPENING_BALANCES;

    /** The opening discount-positions flow code. */
    public static final String OPENING_ADVANCES = FlowCodes.OPENING_ADVANCES;

    /** The opening annual-counters flow code. */
    public static final String OPENING_COUNTERS = FlowCodes.OPENING_COUNTERS;

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /** The distributor-account service, to find or open the account. */
    private final AccountService accounts;

    /**
     * Creates the service.
     *
     * @param clock    the application date/time provider
     * @param accounts the distributor-account service
     */
    @Inject
    public OpeningBalanceImportService(DateTimeProvider clock, AccountService accounts) {
        this.clock = clock;
        this.accounts = accounts;
    }

    /**
     * Imports an opening-balance file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importOpeningBalances(String entityCode, String sourceSystem, String fileName,
                                             String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, OPENING_BALANCES, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "payee_type").isEmpty() || value(row, "payee_code").isEmpty()
                            || value(row, "opening_balance").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parsePayeeType(value(row, "payee_type")) == null
                            || parseDecimal(value(row, "opening_balance")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    PayeeType type = parsePayeeType(value(row, "payee_type"));
                    Long payeeId = resolve(entity, type, value(row, "payee_code"));
                    if (payeeId == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    DistributorAccount account = accounts.accountFor(entity, type, payeeId);
                    AccountEntry entry = new AccountEntry();
                    entry.account = account;
                    entry.entryType = AccountEntryType.OPENING_BALANCE;
                    entry.amount = parseDecimal(value(row, "opening_balance"));
                    entry.entryDate = clock.now().toLocalDate();
                    entry.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports an opening discount-positions file (ING-11): each row opens an {@code ACTIVE} discount
     * position on a guarantee at its carried versed amount, backed by a single settled {@code ADVANCE}
     * line so the engine reconstructs the versed amount and continues the apurement in the first period.
     * Columns: {@code coverage_code}, {@code advanced}, {@code total_due}, {@code settlement_due_date}.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importOpeningAdvances(String entityCode, String sourceSystem, String fileName,
                                             String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, OPENING_ADVANCES, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "coverage_code").isEmpty() || value(row, "advanced").isEmpty()
                            || value(row, "total_due").isEmpty() || value(row, "settlement_due_date").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parseDecimal(value(row, "advanced")) == null || parseDecimal(value(row, "total_due")) == null
                            || parseDate(value(row, "settlement_due_date")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    Coverage coverage = Coverage.<Coverage>find("coverageCode = ?1 and contract.entity = ?2",
                            value(row, "coverage_code"), entity).firstResult();
                    if (coverage == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    BigDecimal advanced = parseDecimal(value(row, "advanced"));
                    AdvancePosition position = new AdvancePosition();
                    position.coverage = coverage;
                    position.status = AdvanceStatus.ACTIVE;
                    position.paidAt = clock.now();
                    position.settlementDueDate = parseDate(value(row, "settlement_due_date"));
                    position.advancedAcquisition = advanced;
                    position.advancedServicing = BigDecimal.ZERO;
                    position.advancedLinear = BigDecimal.ZERO;
                    position.offsetAcquisition = BigDecimal.ZERO;
                    position.offsetServicing = BigDecimal.ZERO;
                    position.offsetLinear = BigDecimal.ZERO;
                    position.totalAdvanceDue = parseDecimal(value(row, "total_due"));
                    position.setCreatedAt(clock.now());
                    position.persist();
                    // A single settled ADVANCE line carries the versed amount: the engine reconstructs the
                    // running state from it (D8) and reuses the position rather than versing again.
                    CommissionLine line = new CommissionLine();
                    line.entity = entity;
                    line.coverage = coverage;
                    line.contract = coverage.contract;
                    line.direction = Direction.OUTBOUND;
                    line.nature = LineNature.ADVANCE;
                    line.status = LineStatus.PAID;
                    line.amount = advanced;
                    line.advancePosition = position;
                    line.setCreatedAt(clock.now());
                    line.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports an opening annual-counters file (ING-11): each row carries a rule's cumulative basis and
     * entitlement for a payee at a month, so the first calculated month's development remuneration is
     * the gain over the carried cumul rather than the whole year. Columns: {@code rule_code},
     * {@code payee_type}, {@code payee_code}, {@code year}, {@code month}, {@code cumulative_basis},
     * {@code cumulative_entitlement}.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importOpeningCounters(String entityCode, String sourceSystem, String fileName,
                                             String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, OPENING_COUNTERS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "rule_code").isEmpty() || value(row, "payee_type").isEmpty()
                            || value(row, "payee_code").isEmpty() || value(row, "year").isEmpty()
                            || value(row, "month").isEmpty() || value(row, "cumulative_basis").isEmpty()
                            || value(row, "cumulative_entitlement").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parsePayeeType(value(row, "payee_type")) == null || parseShort(value(row, "year")) == null
                            || parseShort(value(row, "month")) == null
                            || parseDecimal(value(row, "cumulative_basis")) == null
                            || parseDecimal(value(row, "cumulative_entitlement")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    Rule rule = Rule.<Rule>find("code = ?1 and plan.entity = ?2", value(row, "rule_code"),
                            entity).firstResult();
                    PayeeType type = parsePayeeType(value(row, "payee_type"));
                    Long payeeId = resolve(entity, type, value(row, "payee_code"));
                    if (rule == null || payeeId == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    ProductionCounter counter = new ProductionCounter();
                    counter.entity = entity;
                    counter.rule = rule;
                    counter.payeeType = type;
                    counter.payeeId = payeeId;
                    counter.counterYear = parseShort(value(row, "year"));
                    counter.counterMonth = parseShort(value(row, "month"));
                    counter.cumulativeBasis = parseDecimal(value(row, "cumulative_basis"));
                    counter.cumulativeEntitlement = parseDecimal(value(row, "cumulative_entitlement"));
                    counter.setCreatedAt(clock.now());
                    counter.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Resolves a payee business code to its technical id within the entity.
     *
     * @param entity    the entity
     * @param type      the payee type
     * @param payeeCode the payee business code
     * @return the technical id, or null when unknown
     */
    private static Long resolve(LegalEntity entity, PayeeType type, String payeeCode) {
        return switch (type) {
            case PRODUCTION_CODE -> idOf(ProductionCode.<ProductionCode>find("entity = ?1 and code = ?2", entity,
                    payeeCode).firstResult());
            case DISTRIBUTOR -> idOf(Distributor.<Distributor>find("partnerCode", payeeCode).firstResult());
            case GROUP -> idOf(fr.alptis.remuneration.domain.DistributorGroup
                    .<fr.alptis.remuneration.domain.DistributorGroup>find("code", payeeCode).firstResult());
        };
    }

    /**
     * Returns the technical id of an entity, or null when it is null.
     *
     * @param entity the entity, possibly null
     * @return the id or null
     */
    private static Long idOf(fr.alptis.remuneration.domain.BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }

    /**
     * Parses a payee type, returning null when unknown.
     *
     * @param raw the raw value
     * @return the payee type or null
     */
    private static PayeeType parsePayeeType(String raw) {
        try {
            return PayeeType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a decimal, returning null when malformed.
     *
     * @param raw the raw value
     * @return the decimal or null
     */
    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses an ISO date, returning null when malformed.
     *
     * @param raw the raw value
     * @return the date or null
     */
    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parses a short integer, returning null when malformed.
     *
     * @param raw the raw value
     * @return the short or null
     */
    private static Short parseShort(String raw) {
        try {
            return Short.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads a column value from a row, trimmed, empty when absent.
     *
     * @param row    the row
     * @param column the column name
     * @return the value or an empty string
     */
    private static String value(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").trim();
    }
}
