package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.ForecastBasis;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of the accounting generation (section 10.2, FIN-01): the accounting cycle of the
 * worked example (CR-65), the termination forecast adjustment (CR-66) and every branch of the schema
 * — the flux events, the reversal swap, the direct-to-account events, the variants and the payment
 * and rejection entries. Amounts are copied by hand from the specification. Runs against UNEP, inside
 * rolled-back transactions, on the schema and mapping seeded at startup.
 */
@QuarkusTest
class AccountingServiceTest {

    @Inject
    AccountingService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    private static final String FORECAST_EXPENSE = "609100";
    private static final String PROVISION = "428600";
    private static final String EARNED = "467200";
    private static final String DISTRIBUTOR = "401100";
    private static final String BANK = "512000";
    private static final String ADVANCES = "409100";
    private static final String COMMISSION_EXPENSE = "622000";
    private static final String CLAWBACK_INCOME = "709100";

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period in a state for the default month. */
    private static Period period(PeriodState state) {
        return period(state, M);
    }

    /** Creates a period in a state for a given month. */
    private static Period period(PeriodState state, LocalDate month) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = state;
        period.persist();
        return period;
    }

    /** Creates and persists a commission line of a nature, status and amount. */
    private static CommissionLine line(Period period, LineNature nature, LineStatus status, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = nature;
        line.status = status;
        line.amount = new BigDecimal(amount);
        line.persist();
        return line;
    }

    /** Returns the single entry of an event for a period. */
    private static AccountingEntry entry(Period period, AccountingEvent event) {
        return AccountingEntry.<AccountingEntry>find("period = ?1 and accountingEvent = ?2", period, event)
                .firstResult();
    }

    /** Asserts an entry's amount, debit account and credit account. */
    private static void assertEntry(Period period, AccountingEvent event, String amount, String debit,
                                    String credit) {
        AccountingEntry e = entry(period, event);
        assertEquals(0, new BigDecimal(amount).compareTo(e.amount), event + " amount");
        assertEquals(debit, e.debitAccount, event + " debit");
        assertEquals(credit, e.creditAccount, event + " credit");
    }

    /**
     * CR-65: the accounting cycle of the section 10.2 example — a forecast of 180, then 15 per
     * collection (earning then payable recognition), then the payment of 15, all balanced.
     */
    @Test
    @TestTransaction
    void cr65_accounting_cycle() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.LINEAR, LineStatus.FORECAST, "180.00");
        line(p, LineNature.LINEAR, LineStatus.EARNED, "15.00");
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.FORECAST_RECOGNITION, "180.00", FORECAST_EXPENSE, PROVISION);
        assertEntry(p, AccountingEvent.EARNING, "15.00", PROVISION, EARNED);
        assertEntry(p, AccountingEvent.PAYABLE_RECOGNITION, "15.00", EARNED, DISTRIBUTOR);
        // The three line entries carry the period month as accounting month, dated its last day.
        AccountingEntry earning = entry(p, AccountingEvent.EARNING);
        assertEquals(LocalDate.of(2026, 9, 1), earning.accountingMonth);
        assertEquals(LocalDate.of(2026, 9, 30), earning.entryDate);
        assertEquals((short) 2026, earning.fiscalYear);

        PaymentBatch batch = new PaymentBatch();
        batch.entity = unep();
        batch.period = p;
        batch.batchType = BatchType.CYCLE;
        batch.batchReference = "CYCLE-UNEP-" + M;
        batch.status = BatchStatus.ISSUED;
        batch.totalAmount = new BigDecimal("15.00");
        batch.persist();
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.amount = new BigDecimal("15.00");
        order.status = PaymentOrderStatus.ISSUED;
        order.persist();
        service.recordPayment(order, LocalDate.of(2026, 10, 7));

        assertEntry(p, AccountingEvent.PAYMENT, "15.00", DISTRIBUTOR, BANK);
        // The payment entry carries the execution month, not the period month (H-43).
        AccountingEntry payment = entry(p, AccountingEvent.PAYMENT);
        assertEquals(LocalDate.of(2026, 10, 1), payment.accountingMonth);
        assertEquals(LocalDate.of(2026, 10, 7), payment.entryDate);
    }

    /**
     * CR-66: a termination at the sixth month posts a forecast adjustment of −90 €, the provision
     * debited back against the forecast expense.
     */
    @Test
    @TestTransaction
    void cr66_termination_forecast_adjustment() {
        Period p = period(PeriodState.CONTROLLED);
        CommissionLine origin = line(p, LineNature.LINEAR, LineStatus.FORECAST, "180.00");
        CommissionLine regul = line(p, LineNature.LINEAR, LineStatus.FORECAST, "-90.00");
        regul.originLine = origin;
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.FORECAST_ADJUSTMENT, "90.00", PROVISION, FORECAST_EXPENSE);
    }

    /**
     * A positive forecast adjustment (an endorsement raising the premium) keeps the standard
     * direction: forecast expense debited, provision credited.
     */
    @Test
    @TestTransaction
    void positive_forecast_adjustment_is_standard() {
        Period p = period(PeriodState.CONTROLLED);
        CommissionLine origin = line(p, LineNature.LINEAR, LineStatus.FORECAST, "180.00");
        CommissionLine regul = line(p, LineNature.LINEAR, LineStatus.FORECAST, "45.00");
        regul.originLine = origin;
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.FORECAST_ADJUSTMENT, "45.00", FORECAST_EXPENSE, PROVISION);
    }

    /**
     * An offset line (collection imputed on an escompte) earns then offsets the advance.
     */
    @Test
    @TestTransaction
    void offset_line_earns_then_offsets() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.LINEAR, LineStatus.OFFSET, "40.00");
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.EARNING, "40.00", PROVISION, EARNED);
        assertEntry(p, AccountingEvent.ADVANCE_OFFSETTING, "40.00", EARNED, ADVANCES);
    }

    /**
     * FIN-01 / section 6.10: a received insurer commission (INSURER line) books an INSURER_RECEIVABLE
     * entry at validation — insurer receivable (467100) against commission income (706000) — and no
     * payable or payment entry.
     */
    @Test
    @TestTransaction
    void insurer_line_books_a_receivable() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.INSURER, LineStatus.EARNED, "451.54");
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.INSURER_RECEIVABLE, "451.54", "467100", "706000");
        assertEquals(0, AccountingEntry.count("period = ?1 and accountingEvent = ?2", p,
                AccountingEvent.PAYABLE_RECOGNITION));
    }

    /**
     * A reversal flux line (a cancelled collection) swaps the debit and the credit of its earning and
     * payable recognition; a non-reversal line with a positive amount and an origin line does not.
     */
    @Test
    @TestTransaction
    void reversal_flux_line_swaps_debit_and_credit() {
        Period p = period(PeriodState.CONTROLLED);
        CommissionLine origin = line(p, LineNature.LINEAR, LineStatus.EARNED, "15.00");
        CommissionLine reversal = line(p, LineNature.ACQUISITION, LineStatus.EARNED, "-15.00");
        reversal.originLine = origin;
        service.generateForValidation(unep(), p);

        // The reversal's entries are swapped; the origin's are standard. Both exist, so query by amount.
        AccountingEntry swapped = AccountingEntry.<AccountingEntry>find(
                "period = ?1 and accountingEvent = ?2 and debitAccount = ?3", p, AccountingEvent.EARNING, EARNED)
                .firstResult();
        assertEquals(0, new BigDecimal("15.00").compareTo(swapped.amount));
        assertEquals(PROVISION, swapped.creditAccount);
    }

    /**
     * A flux line with an origin line but a positive amount is not a reversal: its direction stays
     * standard (the second jamb of the reversal condition is false).
     */
    @Test
    @TestTransaction
    void origin_line_with_positive_amount_is_not_a_reversal() {
        Period p = period(PeriodState.CONTROLLED);
        CommissionLine origin = line(p, LineNature.LINEAR, LineStatus.EARNED, "15.00");
        CommissionLine other = line(p, LineNature.SERVICING, LineStatus.EARNED, "20.00");
        other.originLine = origin;
        service.generateForValidation(unep(), p);

        AccountingEntry twenty = AccountingEntry.<AccountingEntry>find(
                "period = ?1 and accountingEvent = ?2 and amount = ?3", p, AccountingEvent.EARNING,
                new BigDecimal("20.00")).firstResult();
        assertEquals(PROVISION, twenty.debitAccount);
        assertEquals(EARNED, twenty.creditAccount);
    }

    /**
     * A clawback posts its reprise directly: distributor payable debited, clawback income credited.
     */
    @Test
    @TestTransaction
    void clawback_posts_to_clawback_income() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.CLAWBACK, LineStatus.EARNED, "-30.00");
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.CLAWBACK, "30.00", DISTRIBUTOR, CLAWBACK_INCOME);
    }

    /**
     * A positive supplement (RCD) is a commission expense; a negative one reverses it.
     */
    @Test
    @TestTransaction
    void supplement_follows_its_sign() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.RCD, LineStatus.EARNED, "120.00");
        service.generateForValidation(unep(), p);
        assertEntry(p, AccountingEvent.SUPPLEMENT, "120.00", COMMISSION_EXPENSE, DISTRIBUTOR);

        Period n = period(PeriodState.CONTROLLED, LocalDate.of(2026, 10, 1));
        line(n, LineNature.FEE, LineStatus.EARNED, "-15.00");
        service.generateForValidation(unep(), n);
        assertEntry(n, AccountingEvent.SUPPLEMENT, "15.00", DISTRIBUTOR, COMMISSION_EXPENSE);
    }

    /**
     * A convention escompte is granted as an advance; a retirement escompte (no position) is a savings
     * grant booked straight to the commission expense.
     */
    @Test
    @TestTransaction
    void advance_granted_standard_and_savings() {
        Period p = period(PeriodState.CONTROLLED);
        CommissionLine convention = line(p, LineNature.ADVANCE, LineStatus.EARNED, "480.00");
        AdvancePosition position = new AdvancePosition();
        position.persist();
        convention.advancePosition = position;
        service.generateForValidation(unep(), p);
        AccountingEntry standard = AccountingEntry.<AccountingEntry>find(
                "period = ?1 and accountingEvent = ?2 and amount = ?3", p, AccountingEvent.ADVANCE_GRANTED,
                new BigDecimal("480.00")).firstResult();
        assertEquals(ADVANCES, standard.debitAccount);
        assertEquals(DISTRIBUTOR, standard.creditAccount);

        Period n = period(PeriodState.CONTROLLED, LocalDate.of(2026, 10, 1));
        line(n, LineNature.ADVANCE, LineStatus.EARNED, "200.00");
        service.generateForValidation(unep(), n);
        AccountingEntry savings = AccountingEntry.<AccountingEntry>find(
                "period = ?1 and accountingEvent = ?2 and amount = ?3", n, AccountingEvent.ADVANCE_GRANTED,
                new BigDecimal("200.00")).firstResult();
        assertEquals(COMMISSION_EXPENSE, savings.debitAccount);
        assertEquals(DISTRIBUTOR, savings.creditAccount);
    }

    /**
     * An escompte settlement follows its sign; an account transfer books distributor to distributor.
     */
    @Test
    @TestTransaction
    void advance_settlement_and_transfer() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.ADVANCE_ADJUSTMENT, LineStatus.EARNED, "-25.00");
        line(p, LineNature.TRANSFER, LineStatus.EARNED, "50.00");
        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.ADVANCE_SETTLEMENT, "25.00", DISTRIBUTOR, ADVANCES);
        assertEntry(p, AccountingEvent.ACCOUNT_TRANSFER, "50.00", DISTRIBUTOR, DISTRIBUTOR);
    }

    /**
     * A flux line on a product without a forecast basis earns to the commission expense (variant
     * {@code NO_FORECAST}); a product with a forecast basis, a missing product, a missing option and a
     * missing coverage all earn to the provision.
     */
    @Test
    @TestTransaction
    void earning_variant_depends_on_the_product_forecast() {
        Period p = period(PeriodState.CONTROLLED);
        CommissionLine noForecast = line(p, LineNature.LINEAR, LineStatus.EARNED, "10.00");
        noForecast.coverage = coverage(product(null), true);
        CommissionLine withForecast = line(p, LineNature.LINEAR, LineStatus.EARNED, "11.00");
        withForecast.coverage = coverage(product(ForecastBasis.ISSUED_PREMIUMS), true);
        CommissionLine noProduct = line(p, LineNature.LINEAR, LineStatus.EARNED, "12.00");
        noProduct.coverage = coverage(null, true);
        CommissionLine noOption = line(p, LineNature.LINEAR, LineStatus.EARNED, "13.00");
        noOption.coverage = coverage(null, false);
        service.generateForValidation(unep(), p);

        assertEquals(COMMISSION_EXPENSE, earningDebit(p, "10.00"));
        assertEquals(PROVISION, earningDebit(p, "11.00"));
        assertEquals(PROVISION, earningDebit(p, "12.00"));
        assertEquals(PROVISION, earningDebit(p, "13.00"));
    }

    /**
     * A re-validation drops the non-closed entries first, so the entries are not duplicated.
     */
    @Test
    @TestTransaction
    void regeneration_is_idempotent() {
        Period p = period(PeriodState.CONTROLLED);
        line(p, LineNature.LINEAR, LineStatus.FORECAST, "180.00");
        service.generateForValidation(unep(), p);
        service.generateForValidation(unep(), p);

        assertEquals(1, AccountingEntry.count("period = ?1 and accountingEvent = ?2", p,
                AccountingEvent.FORECAST_RECOGNITION));
    }

    /**
     * A rejected order posts the rejection entry: bank debited, distributor payable credited.
     */
    @Test
    @TestTransaction
    void rejection_posts_bank_to_distributor() {
        Period p = period(PeriodState.CONTROLLED);
        PaymentBatch batch = new PaymentBatch();
        batch.entity = unep();
        batch.period = p;
        batch.batchType = BatchType.CYCLE;
        batch.batchReference = "CYCLE-UNEP-" + M;
        batch.status = BatchStatus.ISSUED;
        batch.totalAmount = new BigDecimal("110.00");
        batch.persist();
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.amount = new BigDecimal("110.00");
        order.status = PaymentOrderStatus.REJECTED;
        order.persist();
        service.recordRejection(order, LocalDate.of(2026, 10, 9));

        assertEntry(p, AccountingEvent.PAYMENT_REJECTION, "110.00", BANK, DISTRIBUTOR);
    }

    /** Builds a settled, written-off discount position carrying the given residual, in UNEP. */
    private static AdvancePosition writtenOffPosition(String residual) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "WO-" + residual;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "WO-C-" + residual;
        coverage.persist();
        AdvancePosition position = new AdvancePosition();
        position.coverage = coverage;
        position.status = AdvanceStatus.SETTLED;
        position.closeReason = "WRITE_OFF";
        position.adjustmentAmount = new BigDecimal(residual);
        position.persist();
        return position;
    }

    /**
     * CR-78: a discount residual written off under the settlement tolerance is booked as an
     * ADVANCE_WRITE_OFF entry of 0,04 €, commission expense debited against distributor advances.
     */
    @Test
    @TestTransaction
    void cr78_write_off_books_an_advance_write_off_entry() {
        Period p = period(PeriodState.CONTROLLED);
        writtenOffPosition("0.04");

        service.generateForValidation(unep(), p);

        assertEntry(p, AccountingEvent.ADVANCE_WRITE_OFF, "0.04", COMMISSION_EXPENSE, ADVANCES);
    }

    /**
     * A write-off entry is booked only once, even when a later period is validated after the one that
     * first booked it.
     */
    @Test
    @TestTransaction
    void write_off_entry_is_booked_only_once() {
        writtenOffPosition("0.04");
        service.generateForValidation(unep(), period(PeriodState.CONTROLLED));
        service.generateForValidation(unep(), period(PeriodState.CONTROLLED, LocalDate.of(2026, 10, 1)));

        assertEquals(1, AccountingEntry.count("accountingEvent = ?1", AccountingEvent.ADVANCE_WRITE_OFF));
    }

    /** Builds a product with a given forecast basis (possibly null). */
    private static Product product(ForecastBasis basis) {
        Product product = new Product();
        product.entity = unep();
        product.code = "PRD-" + (basis == null ? "NONE" : basis.name());
        product.label = "Produit";
        product.forecastBasis = basis;
        product.persist();
        return product;
    }

    /**
     * Builds a coverage; when {@code withOption} is true it carries an option on the given product,
     * otherwise it has no option.
     */
    private static Coverage coverage(Product product, boolean withOption) {
        Coverage coverage = new Coverage();
        if (withOption) {
            ProductOption option = new ProductOption();
            option.product = product;
            option.persist();
            coverage.option = option;
        }
        coverage.persist();
        return coverage;
    }

    /** Returns the debit account of the earning entry of a given amount. */
    private static String earningDebit(Period period, String amount) {
        return AccountingEntry.<AccountingEntry>find(
                "period = ?1 and accountingEvent = ?2 and amount = ?3", period, AccountingEvent.EARNING,
                new BigDecimal(amount)).<AccountingEntry>firstResult().debitAccount;
    }
}
