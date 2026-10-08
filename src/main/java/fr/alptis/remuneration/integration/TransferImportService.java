package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.AdvanceDebtHandling;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import fr.alptis.remuneration.domain.ReplacementOrder;
import fr.alptis.remuneration.domain.TransferStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * Imports the portfolio-transfer and replacement-order files received from the CRM (REF-09,
 * section 9.10). A row referencing an unknown distributor, contract or production code is put on
 * hold; each file is journalled with the five counters and idempotent by fingerprint.
 */
@ApplicationScoped
public class TransferImportService {

    /** Portfolio transfers file flow code. */
    public static final String PORTFOLIO_TRANSFERS = "portfolio_transfers";

    /** Replacement orders file flow code. */
    public static final String REPLACEMENT_ORDERS = "replacement_orders";

    /** The date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public TransferImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports a portfolio-transfers file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importPortfolioTransfers(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, PORTFOLIO_TRANSFERS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "from_partner_code").isEmpty() || value(row, "to_partner_code").isEmpty()
                            || value(row, "effect_date").isEmpty() || value(row, "advance_debt_handling").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parseDate(value(row, "effect_date")) == null
                            || parseDebtHandling(value(row, "advance_debt_handling")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    Distributor from = Distributor.find("partnerCode", value(row, "from_partner_code")).firstResult();
                    Distributor to = Distributor.find("partnerCode", value(row, "to_partner_code")).firstResult();
                    if (from == null || to == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    PortfolioTransfer transfer = new PortfolioTransfer();
                    transfer.entity = entity;
                    transfer.fromDistributor = from;
                    transfer.toDistributor = to;
                    transfer.effectDate = parseDate(value(row, "effect_date"));
                    transfer.scope = emptyToNull(value(row, "scope"));
                    transfer.advanceDebtHandling = parseDebtHandling(value(row, "advance_debt_handling"));
                    transfer.status = TransferStatus.PENDING;
                    transfer.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a replacement-orders file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importReplacementOrders(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, REPLACEMENT_ORDERS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "contract_number").isEmpty() || value(row, "from_production_code").isEmpty()
                            || value(row, "to_production_code").isEmpty() || value(row, "effect_date").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    return parseDate(value(row, "effect_date")) == null ? RejectionReasonCode.TYPE : null;
                },
                row -> {
                    Contract contract = Contract.find("entity = ?1 and sourceSystem = ?2 and contractNumber = ?3",
                            entity, value(row, "source_system"), value(row, "contract_number")).firstResult();
                    ProductionCode from = ProductionCode.find("entity = ?1 and code = ?2", entity,
                            value(row, "from_production_code")).firstResult();
                    ProductionCode to = ProductionCode.find("entity = ?1 and code = ?2", entity,
                            value(row, "to_production_code")).firstResult();
                    if (contract == null || from == null || to == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    ReplacementOrder order = new ReplacementOrder();
                    order.contract = contract;
                    order.fromProductionCode = from;
                    order.toProductionCode = to;
                    order.receivedDate = parseDate(value(row, "received_date"));
                    order.effectDate = parseDate(value(row, "effect_date"));
                    order.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Parses an advance-debt-handling code, returning {@code null} when unknown.
     *
     * @param raw the raw value
     * @return the handling, or {@code null}
     */
    private static AdvanceDebtHandling parseDebtHandling(String raw) {
        try {
            return AdvanceDebtHandling.valueOf(raw);
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
     * Returns null for an empty string, the value otherwise.
     *
     * @param value the value
     * @return null if empty, else the value
     */
    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
