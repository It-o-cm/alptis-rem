package fr.alptis.remuneration.accounts;

import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentExclusion;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.accounts.AccountService.MonthBalance;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the distributor account (sections 8.8, 10.5, step 9): the debtor balance
 * carried forward and paid once positive (CR-60), the compensation between a distributor's accounts
 * (CR-61), and the payment held under the entity's minimum (CR-63). Runs against UNEP, inside
 * rolled-back transactions.
 */
@QuarkusTest
class AccountServiceTest {

    @Inject
    AccountService service;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period of the entity for a month. */
    private static Period period(LocalDate month) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = PeriodState.OPEN;
        period.persist();
        return period;
    }

    /** Creates a production-code account for a distributor. */
    private DistributorAccount accountOf(Distributor distributor, String code) {
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = code;
        pc.distributor = distributor;
        pc.persist();
        return service.accountFor(unep(), PayeeType.PRODUCTION_CODE, pc.getId());
    }

    /** Creates a distributor. */
    private static Distributor distributor(String partnerCode) {
        Distributor d = new Distributor();
        d.partnerCode = partnerCode;
        d.legalName = partnerCode;
        d.persist();
        return d;
    }

    /** Posts an earned commission line on an account for a period. */
    private static void line(DistributorAccount account, Period period, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.attachmentMonth = period.periodMonth;
        line.persist();
    }

    /** Posts an earned cycle line carrying a given contract (for the savings-classification null legs). */
    private static void contractLine(DistributorAccount account, Period period, String amount, Contract contract) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.contract = contract;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.attachmentMonth = period.periodMonth;
        line.persist();
    }

    /** Posts an opening-balance entry on an account. */
    private static void opening(DistributorAccount account, String amount) {
        AccountEntry entry = new AccountEntry();
        entry.account = account;
        entry.entryType = AccountEntryType.OPENING_BALANCE;
        entry.amount = new BigDecimal(amount);
        entry.persist();
    }

    /**
     * Posts an earned commission line on a retirement-savings-range contract, so it is swept into the
     * distinct savings lot (section 10.5). The suffix keys the unique range/product/contract.
     */
    private static void savingsLine(DistributorAccount account, Period period, String amount, String suffix) {
        ProductRange range = new ProductRange();
        range.code = "EPARGNE-" + suffix;
        range.label = "Épargne retraite";
        range.retirementSavings = Boolean.TRUE;
        range.persist();
        Product product = new Product();
        product.entity = unep();
        product.code = "PER-" + suffix;
        product.label = "PER";
        product.range = range;
        product.persist();
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "C-" + suffix;
        contract.product = product;
        contract.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.contract = contract;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.ADVANCE;
        line.amount = new BigDecimal(amount);
        line.attachmentMonth = period.periodMonth;
        line.persist();
    }

    /** Returns the sole order of a period's lot of the given type, or null when the lot has none. */
    private static PaymentOrder orderOf(Period period, BatchType type) {
        PaymentBatch batch = PaymentBatch.find("period = ?1 and batchType = ?2", period, type).firstResult();
        return batch == null ? null : PaymentOrder.<PaymentOrder>find("batch = ?1", batch).firstResult();
    }

    /**
     * CR-86: retirement-savings lines are paid in a distinct SAVINGS_CYCLE lot. Two accounts, one with a
     * savings line only, one with both a savings and a cycle line, produce a savings lot (dated the
     * 20th) carrying each account's savings net and a cycle lot carrying the rest.
     */
    @Test
    @TestTransaction
    void cr86_savings_lines_go_to_a_distinct_savings_lot() {
        Period p = period(LocalDate.of(2026, 1, 1));
        DistributorAccount savingsOnly = accountOf(distributor("DS"), "PC-S");
        savingsLine(savingsOnly, p, "50.00", "A");
        DistributorAccount mixed = accountOf(distributor("DM"), "PC-M");
        savingsLine(mixed, p, "30.00", "B");
        line(mixed, p, "100.00");

        service.proposePayments(unep(), p);

        // The savings lot (dated the 20th) pays each account's savings net.
        PaymentBatch savings = PaymentBatch.find("period = ?1 and batchType = ?2", p,
                BatchType.SAVINGS_CYCLE).firstResult();
        assertNotNull(savings);
        assertEquals(LocalDate.of(2026, 1, 20), savings.executionDate);
        assertEquals(0, savings.totalAmount.compareTo(new BigDecimal("80.00")));
        assertNotNull(orderOf(p, BatchType.SAVINGS_CYCLE));
        // The savings-only account has no cycle order; the mixed account's cycle order is its 100 €.
        PaymentBatch cycle = PaymentBatch.find("period = ?1 and batchType = ?2", p, BatchType.CYCLE).firstResult();
        assertEquals(0, cycle.totalAmount.compareTo(new BigDecimal("100.00")));
    }

    /**
     * A negative savings net is imputed on the cycle lot (section 10.5): a −30 € savings line and a
     * +100 € cycle line pay 70,00 € in the cycle lot and create no savings lot.
     */
    @Test
    @TestTransaction
    void a_negative_savings_net_is_imputed_on_the_cycle_lot() {
        Period p = period(LocalDate.of(2026, 1, 1));
        DistributorAccount a = accountOf(distributor("DN"), "PC-N");
        savingsLine(a, p, "-30.00", "C");
        line(a, p, "100.00");

        service.proposePayments(unep(), p);

        assertNull(PaymentBatch.find("period = ?1 and batchType = ?2", p, BatchType.SAVINGS_CYCLE).firstResult());
        PaymentOrder cycle = orderOf(p, BatchType.CYCLE);
        assertNotNull(cycle);
        assertEquals(0, cycle.amount.compareTo(new BigDecimal("70.00")));
        // The order equals the cycle bordereau net, and the imputed −30 is excluded from the carried
        // balance (it shows as one imputation line instead): the carried balance is the prior balance, 0.
        assertEquals(0, cycle.statement.netAmount.compareTo(new BigDecimal("70.00")));
        assertEquals(0, cycle.statement.carriedBalance.compareTo(BigDecimal.ZERO));
    }

    /**
     * A line whose contract has no product, or whose product has no range, is not a savings line: it
     * stays on the cycle lot (covers the null short-circuits of the savings classification).
     */
    @Test
    @TestTransaction
    void a_line_without_product_or_range_is_not_savings() {
        Period p = period(LocalDate.of(2026, 1, 1));
        DistributorAccount a = accountOf(distributor("DX"), "PC-X");
        Contract noProduct = new Contract();
        noProduct.entity = unep();
        noProduct.sourceSystem = "SYS";
        noProduct.contractNumber = "C-NOPROD";
        noProduct.persist();
        Product noRange = new Product();
        noRange.entity = unep();
        noRange.code = "P-NORANGE";
        noRange.label = "No range";
        noRange.persist();
        Contract withProduct = new Contract();
        withProduct.entity = unep();
        withProduct.sourceSystem = "SYS";
        withProduct.contractNumber = "C-NORANGE";
        withProduct.product = noRange;
        withProduct.persist();
        contractLine(a, p, "40.00", noProduct);
        contractLine(a, p, "60.00", withProduct);

        service.proposePayments(unep(), p);

        // Both lines are cycle lines (no savings range), so there is no savings lot and the cycle pays 100 €.
        assertNull(PaymentBatch.find("period = ?1 and batchType = ?2", p, BatchType.SAVINGS_CYCLE).firstResult());
        PaymentOrder cycle = orderOf(p, BatchType.CYCLE);
        assertNotNull(cycle);
        assertEquals(0, cycle.amount.compareTo(new BigDecimal("100.00")));
    }

    /**
     * A negative cycle net is imputed on the savings lot: a +100 € savings line and a −30 € cycle line
     * pay 70,00 € in the savings lot and create no cycle order.
     */
    @Test
    @TestTransaction
    void a_negative_cycle_net_is_imputed_on_the_savings_lot() {
        Period p = period(LocalDate.of(2026, 1, 1));
        DistributorAccount a = accountOf(distributor("DC"), "PC-C");
        savingsLine(a, p, "100.00", "D");
        line(a, p, "-30.00");

        service.proposePayments(unep(), p);

        PaymentOrder savings = orderOf(p, BatchType.SAVINGS_CYCLE);
        assertNotNull(savings);
        assertEquals(0, savings.amount.compareTo(new BigDecimal("70.00")));
        PaymentBatch cycle = PaymentBatch.find("period = ?1 and batchType = ?2", p, BatchType.CYCLE).firstResult();
        assertEquals(0, cycle.totalAmount.compareTo(BigDecimal.ZERO));
        // The order equals the savings bordereau net; the imputed −30 cycle net stays out of the carried
        // balance (shown as one imputation line instead), so the carried balance is the prior balance, 0.
        assertEquals(0, savings.statement.netAmount.compareTo(new BigDecimal("70.00")));
        assertEquals(0, savings.statement.carriedBalance.compareTo(BigDecimal.ZERO));
    }

    /**
     * CR-60: a −1 000 € opening balance holds payment in January and February (600 € brings it to
     * −400 €), and pays 300,00 € in March (700 € brings it to +300 €), the prior balance carried.
     */
    @Test
    @TestTransaction
    void cr60_debtor_carried_forward() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Distributor d = distributor("D1");
        DistributorAccount a = accountOf(d, "PC-1");
        opening(a, "-1000");
        Period february = period(LocalDate.of(2026, 2, 1));
        line(a, february, "600");

        PaymentBatch febBatch = service.proposePayments(unep(), february);
        assertEquals(0, PaymentOrder.count("batch", febBatch));
        assertEquals(0, service.balance(a).compareTo(new BigDecimal("-400")));
        // The debtor account still gets a bordereau carrying its reported balance, with no order (CPT-01,
        // §9.5): period total 600, carried −1 000 (the opening balance), net −400.
        fr.alptis.remuneration.domain.Statement febStatement = fr.alptis.remuneration.domain.Statement
                .<fr.alptis.remuneration.domain.Statement>find("account = ?1 and period = ?2", a, february)
                .firstResult();
        assertNotNull(febStatement);
        assertEquals(0, febStatement.netAmount.compareTo(new BigDecimal("-400")));
        assertEquals(0, febStatement.carriedBalance.compareTo(new BigDecimal("-1000")));

        Period march = period(LocalDate.of(2026, 3, 1));
        line(a, march, "700");
        PaymentBatch marBatch = service.proposePayments(unep(), march);
        assertEquals(1, PaymentOrder.count("batch", marBatch));
        PaymentOrder order = PaymentOrder.<PaymentOrder>find("batch", marBatch).firstResult();
        assertEquals(0, order.amount.compareTo(new BigDecimal("300")));
    }

    /**
     * The balance series accumulates the account's lines (by period month) and entries (by entry
     * month) into a running balance, month by month.
     */
    @Test
    @TestTransaction
    void balance_series_runs_month_by_month() {
        Distributor d = distributor("DS");
        DistributorAccount a = accountOf(d, "PC-S");
        line(a, period(LocalDate.of(2026, 1, 1)), "100");
        line(a, period(LocalDate.of(2026, 2, 1)), "50");
        AccountEntry payment = new AccountEntry();
        payment.account = a;
        payment.entryType = AccountEntryType.PAYMENT;
        payment.amount = new BigDecimal("-30");
        payment.entryDate = LocalDate.of(2026, 2, 15);
        payment.persist();

        List<MonthBalance> series = service.balanceSeries(unep(), d);
        assertEquals(2, series.size());
        assertEquals(LocalDate.of(2026, 1, 1), series.get(0).month());
        assertEquals(0, new BigDecimal("100").compareTo(series.get(0).balance()));
        assertEquals(0, new BigDecimal("120").compareTo(series.get(1).balance()));
    }

    /**
     * CR-61: a distributor with a code at −200 € and a code at +500 € is compensated by two TRANSFER
     * lines, leaving 300,00 € paid on the creditor code.
     */
    @Test
    @TestTransaction
    void cr61_compensation() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Distributor d = distributor("D1");
        DistributorAccount debtor = accountOf(d, "PC-DEBIT");
        DistributorAccount creditor = accountOf(d, "PC-CREDIT");
        Period p = period(LocalDate.of(2026, 3, 1));
        line(debtor, p, "-200");
        line(creditor, p, "500");

        int transfers = service.compensate(unep(), d, p);
        assertEquals(2, transfers);
        assertEquals(0, service.balance(debtor).compareTo(BigDecimal.ZERO));
        assertEquals(0, service.balance(creditor).compareTo(new BigDecimal("300")));

        PaymentBatch batch = service.proposePayments(unep(), p);
        assertEquals(1, PaymentOrder.count("batch", batch));
        assertEquals(0, PaymentOrder.<PaymentOrder>find("batch", batch).firstResult()
                .amount.compareTo(new BigDecimal("300")));
    }

    /**
     * CR-63: a positive balance under the entity's minimum is not ordered and stays on the account.
     */
    @Test
    @TestTransaction
    void cr63_below_threshold() {
        unep().minPaymentAmount = new BigDecimal("100");
        Distributor d = distributor("D1");
        DistributorAccount a = accountOf(d, "PC-1");
        Period p = period(LocalDate.of(2026, 3, 1));
        line(a, p, "50");

        PaymentBatch batch = service.proposePayments(unep(), p);
        assertEquals(0, PaymentOrder.count("batch", batch));
        assertEquals(1, PaymentExclusion.count("batch", batch));
        assertEquals(0, service.balance(a).compareTo(new BigDecimal("50")));
    }

    /**
     * Asking twice for the same account returns the same one.
     */
    @Test
    @TestTransaction
    void account_reused() {
        DistributorAccount first = service.accountFor(unep(), PayeeType.DISTRIBUTOR, 42L);
        DistributorAccount second = service.accountFor(unep(), PayeeType.DISTRIBUTOR, 42L);
        assertEquals(first.getId(), second.getId());
    }

    /** Creates a production code (optionally with a distributor) and its account. */
    private DistributorAccount pcAccount(String code, Distributor distributor) {
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = code;
        pc.distributor = distributor;
        pc.persist();
        return service.accountFor(unep(), PayeeType.PRODUCTION_CODE, pc.getId());
    }

    /**
     * Compensation nets a distributor's debtor accounts against its creditor accounts, pulling from
     * several creditors, stopping once a debtor is covered, skipping a drained creditor and a
     * nil-balance account, and ignoring accounts of other distributors or without a resolvable code.
     */
    @Test
    @TestTransaction
    void compensation_full() {
        Distributor d = distributor("D1");
        Distributor other = distributor("E1");
        Period p = period(LocalDate.of(2026, 3, 1));
        DistributorAccount d1 = pcAccount("D1-1", d);
        DistributorAccount d2 = pcAccount("D1-2", d);
        DistributorAccount c1 = pcAccount("C1", d);
        DistributorAccount c2 = pcAccount("C2", d);
        DistributorAccount zero = pcAccount("Z", d);
        line(d1, p, "-100");
        line(d2, p, "-100");
        line(c1, p, "100");
        line(c2, p, "50");
        // Noise accounts exercised by the account resolution.
        service.accountFor(unep(), PayeeType.DISTRIBUTOR, d.getId());
        service.accountFor(unep(), PayeeType.DISTRIBUTOR, other.getId());
        service.accountFor(unep(), PayeeType.PRODUCTION_CODE, 999999L);
        pcAccount("NODIST", null);
        pcAccount("OTHER", other);

        int transfers = service.compensate(unep(), d, p);

        assertEquals(4, transfers);
        assertEquals(0, service.balance(d1).compareTo(BigDecimal.ZERO));
        assertEquals(0, service.balance(c1).compareTo(BigDecimal.ZERO));
        assertEquals(0, service.balance(c2).compareTo(BigDecimal.ZERO));
        assertEquals(0, service.balance(d2).compareTo(new BigDecimal("-50")));
        assertEquals(0, service.balance(zero).compareTo(BigDecimal.ZERO));
    }

    /**
     * With no minimum set on the entity, any positive balance is ordered.
     */
    @Test
    @TestTransaction
    void payment_without_minimum() {
        unep().minPaymentAmount = null;
        Distributor d = distributor("D1");
        DistributorAccount a = accountOf(d, "PC-1");
        Period p = period(LocalDate.of(2026, 3, 1));
        line(a, p, "10");

        PaymentBatch batch = service.proposePayments(unep(), p);
        assertEquals(1, PaymentOrder.count("batch", batch));
    }

    /**
     * Blocking or unblocking an unknown account is reported as not found.
     */
    @Test
    @TestTransaction
    void block_unknown_account() {
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class, () -> service.block(-1L, "x", null));
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class, () -> service.unblock(-1L));
    }

    /**
     * A blocked account is excluded from payment with its reason, even with a payable balance.
     */
    @Test
    @TestTransaction
    void blocked_account_excluded() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Distributor d = distributor("D1");
        DistributorAccount a = accountOf(d, "PC-1");
        a.blocked = Boolean.TRUE;
        Period p = period(LocalDate.of(2026, 3, 1));
        line(a, p, "500");

        PaymentBatch batch = service.proposePayments(unep(), p);
        assertEquals(0, PaymentOrder.count("batch", batch));
        assertEquals(1, PaymentExclusion.count("batch", batch));
    }

    /**
     * A block holds until its lifting date (CPT-04): a block still in force excludes the account, one
     * whose date has passed no longer does.
     */
    @Test
    @TestTransaction
    void blocked_account_respects_the_lifting_date() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Period p = period(LocalDate.of(2026, 3, 1));
        DistributorAccount stillBlocked = accountOf(distributor("DF"), "PC-F");
        stillBlocked.blocked = Boolean.TRUE;
        stillBlocked.blockedUntil = LocalDate.of(2099, 1, 1);
        line(stillBlocked, p, "500");
        DistributorAccount lifted = accountOf(distributor("DP"), "PC-P");
        lifted.blocked = Boolean.TRUE;
        lifted.blockedUntil = LocalDate.of(2000, 1, 1);
        line(lifted, p, "500");

        PaymentBatch batch = service.proposePayments(unep(), p);
        assertEquals(1, PaymentExclusion.count("batch = ?1 and account = ?2", batch, stillBlocked));
        assertEquals(0, PaymentExclusion.count("batch = ?1 and account = ?2", batch, lifted));
        assertEquals(1, PaymentOrder.count("batch = ?1 and account = ?2", batch, lifted));
    }

    /**
     * A manually blocked commission line (CPT-05) is held: the order pays only the unblocked lines,
     * the blocked line stays {@code EARNED}, and its amount stays at the account balance.
     */
    @Test
    @TestTransaction
    void blocked_line_is_held_from_payment() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Distributor d = distributor("DH");
        DistributorAccount a = accountOf(d, "PC-H");
        Period p = period(LocalDate.of(2026, 3, 1));
        line(a, p, "500");
        CommissionLine blocked = blockedLine(a, p, "200");

        PaymentBatch batch = service.proposePayments(unep(), p);
        assertEquals(1, PaymentOrder.count("batch", batch));
        PaymentOrder order = PaymentOrder.<PaymentOrder>find("batch", batch).firstResult();
        assertEquals(0, order.amount.compareTo(new BigDecimal("500")));
        // The blocked line is not made payable; its amount stays at the account.
        assertEquals(LineStatus.EARNED, CommissionLine.<CommissionLine>findById(blocked.getId()).status);
        assertEquals(0, service.balance(a).compareTo(new BigDecimal("700")));
        // The swept line links to the bordereau it is carried by (REP-02); the held line does not.
        CommissionLine payable = CommissionLine.<CommissionLine>find(
                "account = ?1 and status = ?2", a, LineStatus.PAYABLE).firstResult();
        assertNotNull(payable.statement);
        assertEquals(order.statement.getId(), payable.statement.getId());
        assertNull(CommissionLine.<CommissionLine>findById(blocked.getId()).statement);
    }

    /**
     * Lists a distributor's lines and blocks then unblocks one by id (CPT-05); an unknown line is
     * reported as not found.
     */
    @Test
    @TestTransaction
    void blocks_and_unblocks_a_line() {
        Distributor d = distributor("DL");
        DistributorAccount a = accountOf(d, "PC-L");
        Period p = period(LocalDate.of(2026, 3, 1));
        line(a, p, "120");
        CommissionLine line = CommissionLine.<CommissionLine>find("account", a).firstResult();

        assertEquals(1, service.linesOfDistributor(unep(), d).size());

        CommissionLine held = service.blockLine(line.getId(), "enquête", LocalDate.of(2026, 6, 1));
        assertEquals(Boolean.TRUE, held.blocked);
        assertEquals("enquête", held.blockReason);
        assertEquals(LocalDate.of(2026, 6, 1), held.blockedUntil);

        CommissionLine freed = service.unblockLine(line.getId());
        assertEquals(Boolean.FALSE, freed.blocked);
        assertNull(freed.blockReason);
        assertNull(freed.blockedUntil);

        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class,
                () -> service.blockLine(-1L, "x", null));
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class,
                () -> service.unblockLine(-1L));
    }

    /**
     * CPT-07: a debtor account is signalled for a repayment demand only when it has no active portfolio —
     * a production-code account whose code is not active, or whose code is unknown; an active code or a
     * distributor-type account is not signalled.
     */
    @Test
    @TestTransaction
    void signals_a_debtor_without_an_active_portfolio() {
        Distributor d = distributor("WAP");
        // A production-code account whose code is not active (no status) is signalled.
        DistributorAccount inactive = accountOf(d, "PC-INACTIVE");
        assertTrue(service.withoutActivePortfolio(inactive));
        // A production-code account whose code is active is not signalled.
        DistributorAccount active = accountOf(d, "PC-ACTIVE");
        ProductionCode activeCode = ProductionCode.findById(active.payeeId);
        activeCode.status = PartnerStatus.ACTIVE;
        assertFalse(service.withoutActivePortfolio(active));
        // A production-code account whose code is unknown is signalled.
        DistributorAccount orphan = new DistributorAccount();
        orphan.entity = unep();
        orphan.payeeType = PayeeType.PRODUCTION_CODE;
        orphan.payeeId = -1L;
        orphan.persist();
        assertTrue(service.withoutActivePortfolio(orphan));
        // A distributor-type account is never signalled.
        DistributorAccount distributorAccount = service.accountFor(unep(), PayeeType.DISTRIBUTOR, d.getId());
        assertFalse(service.withoutActivePortfolio(distributorAccount));
    }

    /** Posts a blocked earned commission line on an account for a period and returns it. */
    private static CommissionLine blockedLine(DistributorAccount account, Period period, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.attachmentMonth = period.periodMonth;
        line.blocked = Boolean.TRUE;
        line.persist();
        return line;
    }
}
