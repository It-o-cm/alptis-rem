package fr.alptis.remuneration.payment;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PaymentExclusion;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the payment execution (sections 8.9, 9.6, step 10): the batch/order alignment
 * (CR-64), the SEPA file, the bank rejection that recredits and re-presents (CR-85), and the period
 * reaching PAID once every lot is executed (CR-86). Runs against UNEP, inside rolled-back transactions.
 */
@QuarkusTest
class PaymentServiceTest {

    @Inject
    PaymentService service;
    @Inject
    AccountService accounts;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);
    private long seq;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period in a state for the default month. */
    private static Period period(PeriodState state) {
        return period(M, state);
    }

    /** Creates a period in a state for a month. */
    private static Period period(LocalDate month, PeriodState state) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = state;
        period.persist();
        return period;
    }

    /** Creates a draft cycle batch for a period. */
    private static PaymentBatch batch(Period period, String total) {
        PaymentBatch batch = new PaymentBatch();
        batch.entity = unep();
        batch.period = period;
        batch.batchType = BatchType.CYCLE;
        batch.batchReference = "CYCLE-UNEP-" + period.periodMonth;
        batch.status = BatchStatus.DRAFT;
        batch.totalAmount = new BigDecimal(total);
        batch.persist();
        return batch;
    }

    /** Creates an order on an account with a payable line of the same amount. */
    private PaymentOrder order(PaymentBatch batch, String amount) {
        DistributorAccount account = accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, ++seq);
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = batch.period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.PAYABLE;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.persist();
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.account = account;
        order.amount = new BigDecimal(amount);
        order.status = PaymentOrderStatus.TO_VALIDATE;
        order.endToEndId = "2026-09-00" + seq;
        order.iban = "FR7630001007941234567890185";
        order.creditorName = "Cabinet " + seq;
        order.persist();
        return order;
    }

    /**
     * CR-64: a batch whose total matches the sum of its orders validates and emits a SEPA file; a
     * misaligned total is refused.
     */
    @Test
    @TestTransaction
    void cr64_alignment() {
        Period p = period(PeriodState.FINANCE_VALIDATED);
        PaymentBatch good = batch(p, "110.00");
        order(good, "110.00");
        PaymentBatch validated = service.validateFinance(good.getId(), "fin");
        assertEquals(BatchStatus.ISSUED, validated.status);
        assertTrue(service.sepaXml(good.getId()).contains("pain.001.001.03"));

        PaymentBatch bad = batch(period(LocalDate.of(2026, 10, 1), PeriodState.FINANCE_VALIDATED), "999.00");
        order(bad, "110.00");
        assertThrows(ValidationException.class, () -> service.validateFinance(bad.getId(), "fin"));
    }

    /**
     * CR-85: a rejected order recredits its account (PAYMENT_REJECTION), returns its lines to PAYABLE
     * and raises an anomaly; a settled one is confirmed.
     */
    @Test
    @TestTransaction
    void cr85_bank_rejection() {
        Period p = period(PeriodState.FINANCE_VALIDATED);
        PaymentBatch batch = batch(p, "110.00");
        PaymentOrder order = order(batch, "110.00");
        service.validateFinance(batch.getId(), "fin");
        // Emission paid the line and debited the account.
        assertEquals(0, accounts.balance(order.account).compareTo(BigDecimal.ZERO));

        service.recordBankResult(order.getId(), false, "IBAN invalide");

        assertEquals(PaymentOrderStatus.REJECTED,
                PaymentOrder.<PaymentOrder>findById(order.getId()).status);
        assertEquals(0, accounts.balance(order.account).compareTo(new BigDecimal("110.00")));
        assertEquals(1, AccountEntry.count("account = ?1 and entryType = ?2",
                order.account, AccountEntryType.PAYMENT_REJECTION));
        assertEquals(1, ControlResult.count("objectType = ?1 and objectId = ?2", "PAYMENT_ORDER", order.getId()));
        assertEquals(LineStatus.PAYABLE,
                CommissionLine.<CommissionLine>find("account = ?1", order.account).firstResult().status);
    }

    /**
     * CR-86: the period reaches PAID only once every order of every lot is settled or rejected.
     */
    @Test
    @TestTransaction
    void cr86_period_paid_when_all_lots_done() {
        Period p = period(PeriodState.FINANCE_VALIDATED);
        PaymentBatch cycle = batch(p, "110.00");
        PaymentOrder o1 = order(cycle, "110.00");
        PaymentBatch savings = new PaymentBatch();
        savings.entity = unep();
        savings.period = p;
        savings.batchType = BatchType.SAVINGS_CYCLE;
        savings.batchReference = "SAVINGS-UNEP-" + M;
        savings.status = BatchStatus.DRAFT;
        savings.totalAmount = new BigDecimal("50.00");
        savings.persist();
        PaymentOrder o2 = order(savings, "50.00");
        service.validateFinance(cycle.getId(), "fin");
        service.validateFinance(savings.getId(), "fin");

        service.recordBankResult(o1.getId(), true, null);
        assertEquals(PeriodState.FINANCE_VALIDATED, Period.<Period>findById(p.getId()).state);
        service.recordBankResult(o2.getId(), true, null);
        assertEquals(PeriodState.PAID, Period.<Period>findById(p.getId()).state);
    }

    /** Creates a distributor with an ORIAS status. */
    private static Distributor distributor(String partnerCode, OriasStatus orias) {
        Distributor d = new Distributor();
        d.partnerCode = partnerCode;
        d.legalName = partnerCode;
        d.oriasStatus = orias;
        d.persist();
        return d;
    }

    /** Creates a production-code account for a distributor. */
    private static DistributorAccount pcAccount(Distributor distributor, String code) {
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = code;
        pc.distributor = distributor;
        pc.persist();
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = pc.getId();
        account.persist();
        return account;
    }

    /** Posts an earned line on an account. */
    private static void earnedLine(Period period, DistributorAccount account, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /** Records bank details for an account. */
    private static void bank(DistributorAccount account, String iban) {
        BankAccount b = new BankAccount();
        b.entity = unep();
        b.holderType = account.payeeType;
        b.holderId = account.payeeId;
        b.iban = iban;
        b.bic = "AGRIFRPP";
        b.holderName = "Titulaire";
        b.validFrom = LocalDate.of(2020, 1, 1);
        b.persist();
    }

    /**
     * CR-62: a distributor struck off the ORIAS is excluded from the cycle lot, and an off-cycle lot
     * releases its commissions once it is re-registered.
     */
    @Test
    @TestTransaction
    void cr62_orias_suspension_and_off_cycle() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Period p = period(PeriodState.CONTROLLED);
        Distributor d = distributor("P13897", OriasStatus.STRUCK_OFF);
        DistributorAccount account = pcAccount(d, "PC-1");
        earnedLine(p, account, "110.00");
        bank(account, "FR7630001007941234567890185");

        PaymentBatch cycle = accounts.proposePayments(unep(), p);
        service.prepareOrderDetails(cycle.getId());
        assertEquals(1, PaymentExclusion.count("batch", cycle));
        assertEquals(1, PaymentOrder.count("batch = ?1 and status = ?2", cycle, PaymentOrderStatus.CANCELLED));

        d.oriasStatus = OriasStatus.REGISTERED;
        PaymentBatch off = service.offCycleLot("UNEP", "P13897", LocalDate.of(2026, 10, 1));
        assertEquals(0, off.totalAmount.compareTo(new BigDecimal("110.00")));
        assertEquals(1, PaymentOrder.count("batch", off));
    }

    /**
     * Preparation resolves the IBAN of a beneficiary with bank details and routes one without to the
     * quarterly cheque lot.
     */
    @Test
    @TestTransaction
    void prepare_resolves_iban_and_routes_cheque() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Period p = period(PeriodState.CONTROLLED);
        Distributor da = distributor("A", OriasStatus.REGISTERED);
        DistributorAccount acctA = pcAccount(da, "PC-A");
        earnedLine(p, acctA, "100.00");
        bank(acctA, "FR7630001007941234567890185");
        Distributor db = distributor("B", OriasStatus.REGISTERED);
        DistributorAccount acctB = pcAccount(db, "PC-B");
        earnedLine(p, acctB, "50.00");

        PaymentBatch cycle = accounts.proposePayments(unep(), p);
        service.prepareOrderDetails(cycle.getId());

        assertEquals(0, cycle.totalAmount.compareTo(new BigDecimal("100.00")));
        PaymentOrder kept = PaymentOrder.<PaymentOrder>find("batch = ?1 and account = ?2", cycle, acctA).firstResult();
        assertTrue(kept.iban != null && kept.iban.startsWith("FR76"));
        PaymentBatch cheque = PaymentBatch.<PaymentBatch>find("period = ?1 and batchType = ?2", p,
                fr.alptis.remuneration.domain.BatchType.CHEQUE).firstResult();
        assertEquals(0, cheque.totalAmount.compareTo(new BigDecimal("50.00")));
    }

    /** Creates a typed account. */
    private static DistributorAccount account(PayeeType type, Long id) {
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = type;
        account.payeeId = id;
        account.persist();
        return account;
    }

    /** Links a distributor to UNEP with a status. */
    private static void link(Distributor distributor, fr.alptis.remuneration.domain.PartnerStatus status) {
        fr.alptis.remuneration.domain.DistributorEntity link = new fr.alptis.remuneration.domain.DistributorEntity();
        link.distributor = distributor;
        link.entity = unep();
        link.status = status;
        link.validFrom = LocalDate.of(2020, 1, 1);
        link.persist();
    }

    /** Records bank details for a typed account. */
    private static void bank(PayeeType type, Long id) {
        BankAccount b = new BankAccount();
        b.entity = unep();
        b.holderType = type;
        b.holderId = id;
        b.iban = "FR7630001007941234567890185";
        b.bic = "AGRIFRPP";
        b.holderName = "Titulaire";
        b.validFrom = LocalDate.of(2020, 1, 1);
        b.validTo = LocalDate.of(2030, 1, 1);
        b.persist();
    }

    /**
     * Preparation resolves every account kind: an active distributor keeps its order, one without a
     * link or with an unknown production code is kept, a group account is kept, two without bank go to
     * the single cheque lot, and a suspended distributor is excluded.
     */
    @Test
    @TestTransaction
    void prepare_resolution_edges() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Period p = period(PeriodState.CONTROLLED);
        Distributor active = distributor("ACT", OriasStatus.REGISTERED);
        link(active, fr.alptis.remuneration.domain.PartnerStatus.ACTIVE);
        DistributorAccount a = pcAccount(active, "PC-ACT");
        earnedLine(p, a, "100.00");
        bank(a.payeeType, a.payeeId);
        DistributorAccount groupAcct = account(PayeeType.GROUP, 7L);
        earnedLine(p, groupAcct, "20.00");
        bank(PayeeType.GROUP, 7L);
        DistributorAccount distAcct = account(PayeeType.DISTRIBUTOR, distributor("D2", OriasStatus.REGISTERED).getId());
        earnedLine(p, distAcct, "30.00");
        bank(PayeeType.DISTRIBUTOR, distAcct.payeeId);
        DistributorAccount ghost = account(PayeeType.PRODUCTION_CODE, 999999L);
        earnedLine(p, ghost, "15.00");
        bank(PayeeType.PRODUCTION_CODE, 999999L);
        DistributorAccount noBank1 = pcAccount(distributor("NB1", OriasStatus.REGISTERED), "PC-NB1");
        earnedLine(p, noBank1, "40.00");
        DistributorAccount noBank2 = pcAccount(distributor("NB2", OriasStatus.REGISTERED), "PC-NB2");
        earnedLine(p, noBank2, "60.00");
        Distributor suspended = distributor("SUS", OriasStatus.REGISTERED);
        link(suspended, fr.alptis.remuneration.domain.PartnerStatus.SUSPENDED);
        DistributorAccount susAcct = pcAccount(suspended, "PC-SUS");
        earnedLine(p, susAcct, "25.00");

        PaymentBatch cycle = accounts.proposePayments(unep(), p);
        service.prepareOrderDetails(cycle.getId());

        PaymentBatch cheque = PaymentBatch.<PaymentBatch>find("period = ?1 and batchType = ?2", p,
                fr.alptis.remuneration.domain.BatchType.CHEQUE).firstResult();
        assertEquals(0, cheque.totalAmount.compareTo(new BigDecimal("100.00")));
        assertEquals(1, PaymentOrder.count("batch = ?1 and account = ?2 and status = ?3",
                cycle, susAcct, PaymentOrderStatus.CANCELLED));
        assertEquals(0, cycle.totalAmount.compareTo(new BigDecimal("165.00")));
    }

    /**
     * The off-cycle lot pays nothing for a struck-off distributor, skips a non-positive account and
     * reuses an existing period; a rejected order reuses the rejection control, and a result on a
     * non-finance-validated period does not close it.
     */
    @Test
    @TestTransaction
    void offcycle_and_result_edges() {
        Period open = period(LocalDate.of(2026, 11, 1), PeriodState.OPEN);
        Distributor struck = distributor("STR", OriasStatus.STRUCK_OFF);
        PaymentBatch none = service.offCycleLot("UNEP", "STR", LocalDate.of(2026, 11, 1));
        assertEquals(0, none.totalAmount.compareTo(BigDecimal.ZERO));
        assertEquals(open.getId(), none.period.getId());

        Distributor payable = distributor("PAY", OriasStatus.REGISTERED);
        DistributorAccount zero = pcAccount(payable, "PC-ZERO");
        earnedLine(open, zero, "0.00");
        PaymentBatch empty = service.offCycleLot("UNEP", "PAY", LocalDate.of(2026, 11, 1));
        assertEquals(0, empty.totalAmount.compareTo(BigDecimal.ZERO));

        // Two orders on an OPEN-period off-cycle batch, both rejected: the control is reused and the
        // period stays OPEN.
        PaymentBatch batch = batch(open, "0");
        PaymentOrder o1 = order(batch, "10.00");
        PaymentOrder o2 = order(batch, "20.00");
        service.recordBankResult(o1.getId(), false, "rejet 1");
        service.recordBankResult(o2.getId(), false, "rejet 2");
        assertEquals(PeriodState.OPEN, Period.<Period>findById(open.getId()).state);
        assertEquals(1, fr.alptis.remuneration.domain.ControlDefinition.count("entity = ?1 and code = ?2",
                unep(), "PAY-REJECT"));

        // A payable distributor with an earned balance and no bank: its lines pass payable and a
        // cheque-bound off-cycle order is created.
        Distributor pos = distributor("POS", OriasStatus.REGISTERED);
        DistributorAccount acct = pcAccount(pos, "PC-POS");
        earnedLine(open, acct, "77.00");
        PaymentBatch off = service.offCycleLot("UNEP", "POS", LocalDate.of(2026, 11, 1));
        assertEquals(0, off.totalAmount.compareTo(new BigDecimal("77.00")));
        assertEquals(LineStatus.PAYABLE,
                CommissionLine.<CommissionLine>find("account = ?1", acct).firstResult().status);
    }

    /**
     * An unknown batch or order is reported as not found.
     */
    @Test
    @TestTransaction
    void unknown_batch_or_order() {
        assertThrows(ResourceNotFoundException.class, () -> service.validateFinance(-1L, "fin"));
        assertThrows(ResourceNotFoundException.class, () -> service.sepaXml(-1L));
        assertThrows(ResourceNotFoundException.class, () -> service.recordBankResult(-1L, true, null));
        assertThrows(ResourceNotFoundException.class, () -> service.prepareOrderDetails(-1L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.offCycleLot("NOPE", "X", LocalDate.of(2026, 11, 1)));
        assertThrows(ResourceNotFoundException.class,
                () -> service.offCycleLot("UNEP", "NODIST", LocalDate.of(2026, 11, 1)));
    }
}
