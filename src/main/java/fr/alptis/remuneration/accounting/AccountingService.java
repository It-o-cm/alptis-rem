package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.AccountMapping;
import fr.alptis.remuneration.domain.AccountRole;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AccountingScheme;
import fr.alptis.remuneration.domain.AccountingVariant;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates the auxiliary accounting entries of a period (requirement FIN-01, section 10.2). All the
 * entries of a period are produced at its business validation — the forecast, the earning, the
 * offsetting and the escompte residual — so a recalculation before validation has nothing to
 * reverse; only the payment and the bank-rejection entries are posted later, when the bank result is
 * recorded. Every entry carries a positive amount; the direction comes from the schema of the
 * entity, the variant from the sign of the line, and a reversal line swaps the debit and the credit.
 * A family never books anything: this service reads already-decided lines and orders and writes.
 */
@ApplicationScoped
public class AccountingService {

    /** The commission natures born of a flux: they earn then become payable (section 10.2). */
    private static final Set<LineNature> FLUX = Set.of(LineNature.LINEAR, LineNature.ACQUISITION,
            LineNature.SERVICING, LineNature.OUTSTANDING, LineNature.ARBITRAGE, LineNature.RETROCESSION,
            LineNature.EXTERNAL);

    /** The line statuses present at business validation, before anything is paid. */
    private static final List<LineStatus> AT_VALIDATION = List.of(LineStatus.FORECAST, LineStatus.EARNED,
            LineStatus.OFFSET);

    /** How a non-flux nature's variant is decided at generation time. */
    private enum VariantPolicy {
        /** Always {@link AccountingVariant#STANDARD}. */
        STANDARD_ONLY,
        /** {@link AccountingVariant#NEGATIVE} when the line amount is negative, else standard. */
        SIGNED,
        /** {@link AccountingVariant#SAVINGS} for a retirement escompte (no convention), else standard. */
        SAVINGS_OR_STANDARD
    }

    /** The accounting event and variant policy of a non-flux nature. */
    private record OwnEvent(AccountingEvent event, VariantPolicy policy) {
    }

    /** The own accounting event of each non-flux nature booked directly to the distributor account. */
    private static final Map<LineNature, OwnEvent> OWN = Map.of(
            LineNature.CLAWBACK, new OwnEvent(AccountingEvent.CLAWBACK, VariantPolicy.STANDARD_ONLY),
            LineNature.RCD, new OwnEvent(AccountingEvent.SUPPLEMENT, VariantPolicy.SIGNED),
            LineNature.RCP, new OwnEvent(AccountingEvent.SUPPLEMENT, VariantPolicy.SIGNED),
            LineNature.COMPLEMENT, new OwnEvent(AccountingEvent.SUPPLEMENT, VariantPolicy.SIGNED),
            LineNature.FEE, new OwnEvent(AccountingEvent.SUPPLEMENT, VariantPolicy.SIGNED),
            LineNature.MANUAL, new OwnEvent(AccountingEvent.SUPPLEMENT, VariantPolicy.SIGNED),
            LineNature.ADVANCE, new OwnEvent(AccountingEvent.ADVANCE_GRANTED, VariantPolicy.SAVINGS_OR_STANDARD),
            LineNature.ADVANCE_ADJUSTMENT, new OwnEvent(AccountingEvent.ADVANCE_SETTLEMENT, VariantPolicy.SIGNED),
            LineNature.TRANSFER, new OwnEvent(AccountingEvent.ACCOUNT_TRANSFER, VariantPolicy.STANDARD_ONLY));

    /**
     * Generates every accounting entry of a period at its business validation, after the lines have
     * been posted to the accounts. Any non-closed entry already produced for the period is dropped
     * first, so a re-validation after a Finance refusal is idempotent.
     *
     * @param entity the entity
     * @param period the validated period
     */
    public void generateForValidation(LegalEntity entity, Period period) {
        discardForPeriod(period);
        LocalDate month = period.periodMonth;
        LocalDate entryDate = month.withDayOfMonth(month.lengthOfMonth());
        for (CommissionLine line : CommissionLine.<CommissionLine>list("period = ?1 and status in ?2",
                period, AT_VALIDATION)) {
            generateForLine(entity, period, line, month, entryDate);
        }
        // A discount residual written off under the settlement tolerance produces no commission line, so
        // its ADVANCE_WRITE_OFF entry is booked here, once, from the position (section 10.2, CR-78).
        for (AdvancePosition position : AdvancePosition.<AdvancePosition>list(
                "closeReason = ?1 and adjustmentAmount is not null and coverage.contract.entity = ?2",
                "WRITE_OFF", entity)) {
            if (AccountingEntry.count("accountingEvent = ?1 and advancePosition = ?2",
                    AccountingEvent.ADVANCE_WRITE_OFF, position) == 0) {
                emitPosition(entity, period, AccountingEvent.ADVANCE_WRITE_OFF, position,
                        position.adjustmentAmount, month, entryDate);
            }
        }
    }

    /**
     * Emits one position-level entry (an escompte write-off that carries no commission line): the
     * accounts come from the schema and the mapping, the amount from the position.
     *
     * @param entity    the entity
     * @param period    the period
     * @param event     the accounting event
     * @param position  the discount position
     * @param amount    the entry amount
     * @param month     the accounting month (first day)
     * @param entryDate the entry date (last day of the month)
     */
    private void emitPosition(LegalEntity entity, Period period, AccountingEvent event, AdvancePosition position,
                              BigDecimal amount, LocalDate month, LocalDate entryDate) {
        AccountingScheme scheme = scheme(entity, event, AccountingVariant.STANDARD);
        AccountMapping debit = mapping(entity, scheme.debitRole);
        AccountMapping credit = mapping(entity, scheme.creditRole);
        AccountingEntry entry = new AccountingEntry();
        entry.entity = entity;
        entry.period = period;
        entry.accountingEvent = event;
        entry.journal = debit.journal;
        entry.entryDate = entryDate;
        entry.debitAccount = debit.accountNumber;
        entry.creditAccount = credit.accountNumber;
        entry.amount = amount.abs();
        entry.fiscalYear = (short) month.getYear();
        entry.accountingMonth = month.withDayOfMonth(1);
        entry.advancePosition = position;
        entry.persist();
    }

    /**
     * Generates the entries of a single line, dispatching on its status and nature.
     *
     * @param entity    the entity
     * @param period    the period
     * @param line      the line
     * @param month     the accounting month (first day)
     * @param entryDate the entry date (last day of the month)
     */
    private void generateForLine(LegalEntity entity, Period period, CommissionLine line, LocalDate month,
                                 LocalDate entryDate) {
        if (line.status == LineStatus.FORECAST) {
            if (line.originLine == null) {
                emit(entity, period, AccountingEvent.FORECAST_RECOGNITION, AccountingVariant.STANDARD, line,
                        month, entryDate, false);
            } else {
                emit(entity, period, AccountingEvent.FORECAST_ADJUSTMENT, signed(line), line, month, entryDate,
                        false);
            }
        } else if (line.status == LineStatus.OFFSET) {
            emit(entity, period, AccountingEvent.EARNING, earningVariant(line), line, month, entryDate, false);
            emit(entity, period, AccountingEvent.ADVANCE_OFFSETTING, AccountingVariant.STANDARD, line, month,
                    entryDate, false);
        } else if (line.nature == LineNature.INSURER) {
            // A received insurer commission (INBOUND, section 6.10, FIN-01): its receivable entry is
            // booked at validation — insurer receivable against commission income — and it enters no
            // payment; it is settled when the insurer's payment is reconciled (FIN-08).
            emit(entity, period, AccountingEvent.INSURER_RECEIVABLE, AccountingVariant.STANDARD, line, month,
                    entryDate, false);
        } else if (FLUX.contains(line.nature)) {
            boolean reversal = line.originLine != null && line.amount.signum() < 0;
            emit(entity, period, AccountingEvent.EARNING, earningVariant(line), line, month, entryDate, reversal);
            emit(entity, period, AccountingEvent.PAYABLE_RECOGNITION, AccountingVariant.STANDARD, line, month,
                    entryDate, reversal);
        } else {
            OwnEvent own = OWN.get(line.nature);
            emit(entity, period, own.event(), variantOf(own.policy(), line), line, month, entryDate, false);
        }
    }

    /**
     * Records the payment entry of a settled order: distributor payable to bank, carrying the month of
     * the execution date (section 10.3, hypothesis H-43).
     *
     * @param order         the settled order
     * @param executionDate the date the transfer was executed
     */
    public void recordPayment(PaymentOrder order, LocalDate executionDate) {
        LegalEntity entity = order.batch.period.entity;
        emitOrder(entity, order, AccountingEvent.PAYMENT, executionDate);
    }

    /**
     * Records the rejection entry of a rejected order: bank back to distributor payable.
     *
     * @param order        the rejected order
     * @param rejectionDate the date the rejection was recorded
     */
    public void recordRejection(PaymentOrder order, LocalDate rejectionDate) {
        LegalEntity entity = order.batch.period.entity;
        emitOrder(entity, order, AccountingEvent.PAYMENT_REJECTION, rejectionDate);
    }

    /**
     * Drops the non-closed entries of a period (those not yet carried by a cut-off extract), used when
     * a Finance refusal takes the period back before its validation.
     *
     * @param period the period
     */
    public void discardForPeriod(Period period) {
        AccountingEntry.delete("period = ?1 and cutoffExtract is null", period);
    }

    /**
     * Emits an order-level entry (payment or rejection) at a given date.
     *
     * @param entity the entity
     * @param order  the order
     * @param event  the accounting event
     * @param date   the execution or rejection date
     */
    private void emitOrder(LegalEntity entity, PaymentOrder order, AccountingEvent event, LocalDate date) {
        AccountingScheme scheme = scheme(entity, event, AccountingVariant.STANDARD);
        AccountMapping debit = mapping(entity, scheme.debitRole);
        AccountMapping credit = mapping(entity, scheme.creditRole);
        AccountingEntry entry = new AccountingEntry();
        entry.entity = entity;
        entry.period = order.batch.period;
        entry.accountingEvent = event;
        entry.journal = debit.journal;
        entry.entryDate = date;
        entry.debitAccount = debit.accountNumber;
        entry.creditAccount = credit.accountNumber;
        entry.amount = order.amount.abs();
        entry.fiscalYear = (short) date.getYear();
        entry.accountingMonth = date.withDayOfMonth(1);
        entry.paymentOrder = order;
        entry.persist();
    }

    /**
     * Emits one line-level entry, resolving the accounts from the schema and the mapping and swapping
     * the debit and the credit when the line is a reversal.
     *
     * @param entity    the entity
     * @param period    the period
     * @param event     the accounting event
     * @param variant   the variant
     * @param line      the line
     * @param month     the accounting month (first day)
     * @param entryDate the entry date
     * @param swap      whether to swap the debit and the credit (reversal line)
     */
    private void emit(LegalEntity entity, Period period, AccountingEvent event, AccountingVariant variant,
                      CommissionLine line, LocalDate month, LocalDate entryDate, boolean swap) {
        AccountingScheme scheme = scheme(entity, event, variant);
        AccountRole debitRole = swap ? scheme.creditRole : scheme.debitRole;
        AccountRole creditRole = swap ? scheme.debitRole : scheme.creditRole;
        AccountMapping debit = mapping(entity, debitRole);
        AccountMapping credit = mapping(entity, creditRole);
        AccountingEntry entry = new AccountingEntry();
        entry.entity = entity;
        entry.period = period;
        entry.accountingEvent = event;
        entry.journal = debit.journal;
        entry.entryDate = entryDate;
        entry.debitAccount = debit.accountNumber;
        entry.creditAccount = credit.accountNumber;
        entry.amount = line.amount.abs();
        entry.fiscalYear = (short) month.getYear();
        entry.accountingMonth = month.withDayOfMonth(1);
        entry.premiumFiscalYear = line.premiumFiscalYear;
        entry.commissionLine = line;
        entry.advancePosition = line.advancePosition;
        entry.persist();
    }

    /**
     * The variant of a forecast adjustment or any signed event: negative when the amount is negative.
     *
     * @param line the line
     * @return the variant
     */
    private AccountingVariant signed(CommissionLine line) {
        return line.amount.signum() < 0 ? AccountingVariant.NEGATIVE : AccountingVariant.STANDARD;
    }

    /**
     * The variant of a non-flux own event, from its policy.
     *
     * @param policy the variant policy
     * @param line   the line
     * @return the variant
     */
    private AccountingVariant variantOf(VariantPolicy policy, CommissionLine line) {
        return switch (policy) {
            case STANDARD_ONLY -> AccountingVariant.STANDARD;
            case SIGNED -> signed(line);
            case SAVINGS_OR_STANDARD -> line.advancePosition == null
                    ? AccountingVariant.SAVINGS : AccountingVariant.STANDARD;
        };
    }

    /**
     * The earning variant: a product without a forecast earns to the commission expense instead of the
     * provision ({@link AccountingVariant#NO_FORECAST}).
     *
     * @param line the line
     * @return the variant
     */
    private AccountingVariant earningVariant(CommissionLine line) {
        return hasForecast(line) ? AccountingVariant.STANDARD : AccountingVariant.NO_FORECAST;
    }

    /**
     * Tells whether the line's product constitutes a forecast (its forecast basis is set).
     *
     * @param line the line
     * @return true when the product constitutes a forecast
     */
    private boolean hasForecast(CommissionLine line) {
        Coverage coverage = line.coverage;
        if (coverage == null || coverage.option == null) {
            return true;
        }
        ProductOption option = coverage.option;
        Product product = option.product;
        return product == null || product.forecastBasis != null;
    }

    /**
     * Resolves the schema row of an entity for an event and a variant.
     *
     * @param entity  the entity
     * @param event   the accounting event
     * @param variant the variant
     * @return the schema row
     */
    private AccountingScheme scheme(LegalEntity entity, AccountingEvent event, AccountingVariant variant) {
        return AccountingScheme.<AccountingScheme>find(
                "entity = ?1 and accountingEvent = ?2 and variant = ?3", entity, event, variant).firstResult();
    }

    /**
     * Resolves the account mapping of an entity for a role.
     *
     * @param entity the entity
     * @param role   the account role
     * @return the mapping row
     */
    private AccountMapping mapping(LegalEntity entity, AccountRole role) {
        return AccountMapping.<AccountMapping>find("entity = ?1 and accountRole = ?2", entity, role)
                .firstResult();
    }
}
