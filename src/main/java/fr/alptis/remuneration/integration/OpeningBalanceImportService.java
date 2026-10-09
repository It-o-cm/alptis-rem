package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * History reprise of opening balances (ING-11, section 14): imports an opening-balance pivot file and
 * posts an {@code OPENING_BALANCE} account entry per beneficiary, so the migrated account opens at
 * its carried balance and the computed balance is right from the first month. A line whose
 * beneficiary is unknown is kept on hold; a malformed amount or payee type is rejected.
 */
@ApplicationScoped
public class OpeningBalanceImportService {

    /** The opening-balance flow code. */
    public static final String OPENING_BALANCES = FlowCodes.OPENING_BALANCES;

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
