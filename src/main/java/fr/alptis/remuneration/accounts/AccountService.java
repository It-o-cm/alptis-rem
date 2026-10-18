package fr.alptis.remuneration.accounts;

import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentExclusion;
import fr.alptis.remuneration.domain.PaymentExclusionReason;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.StatementLevel;
import fr.alptis.remuneration.domain.StatementStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Distributor-account service (section 8.8, 10.5): the balance of a beneficiary's account, the
 * automatic compensation between a distributor's debtor and creditor accounts in the same entity
 * (CPT-04), and the payment proposal that carries a debtor balance forward, holds a balance under the
 * minimum, and proposes a positive balance in full (CR-60, CR-61, CR-63). An account's balance is the
 * sum of its non-forecast, non-offset, non-cancelled commission lines plus its signed account entries
 * (opening balance, repayments, write-offs, minus payments).
 */
@ApplicationScoped
public class AccountService {

    /** The commission-line statuses that count toward the balance. */
    private static final List<LineStatus> COUNTED = List.of(LineStatus.EARNED, LineStatus.PAYABLE, LineStatus.PAID);

    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public AccountService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Returns the account for an entity and payee, creating it when absent (dev profile).
     *
     * @param entity    the entity
     * @param payeeType the payee type
     * @param payeeId   the payee id
     * @return the account
     */
    @Transactional
    public DistributorAccount accountFor(LegalEntity entity, PayeeType payeeType, Long payeeId) {
        DistributorAccount account = DistributorAccount.find(
                "entity = ?1 and payeeType = ?2 and payeeId = ?3", entity, payeeType, payeeId).firstResult();
        if (account == null) {
            account = new DistributorAccount();
            account.entity = entity;
            account.payeeType = payeeType;
            account.payeeId = payeeId;
            account.setCreatedAt(clock.now());
            account.persist();
        }
        return account;
    }

    /**
     * Returns the balance of an account (section 10.5): its counted lines plus its signed entries.
     *
     * @param account the account
     * @return the balance
     */
    public BigDecimal balance(DistributorAccount account) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list("account = ?1 and status in ?2",
                account, COUNTED)) {
            sum = sum.add(line.amount);
        }
        for (AccountEntry entry : AccountEntry.<AccountEntry>list("account = ?1", account)) {
            sum = sum.add(entry.amount);
        }
        return sum;
    }

    /**
     * Returns the part of an account's balance attached to a period's counted lines.
     *
     * @param account the account
     * @param period  the period
     * @return the period total
     */
    public BigDecimal periodTotal(DistributorAccount account, Period period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "account = ?1 and period = ?2 and status in ?3", account, period, COUNTED)) {
            sum = sum.add(line.amount);
        }
        return sum;
    }

    /**
     * The month-by-month running balance of a distributor across all its accounts: the cumulative sum
     * of its counted commission lines (by period month) and its account entries (by entry month). For
     * the account-balance chart; the interface only displays it.
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @return the running balance per month, ascending
     */
    public List<MonthBalance> balanceSeries(LegalEntity entity, Distributor distributor) {
        Map<LocalDate, BigDecimal> delta = new java.util.TreeMap<>();
        for (DistributorAccount account : accountsOfDistributor(entity, distributor)) {
            for (CommissionLine line : CommissionLine.<CommissionLine>list(
                    "account = ?1 and status in ?2 and period is not null", account, COUNTED)) {
                delta.merge(line.period.periodMonth, line.amount, BigDecimal::add);
            }
            for (AccountEntry entry : AccountEntry.<AccountEntry>list("account = ?1", account)) {
                delta.merge(entry.entryDate.withDayOfMonth(1), entry.amount, BigDecimal::add);
            }
        }
        List<MonthBalance> series = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> month : delta.entrySet()) {
            running = running.add(month.getValue());
            series.add(new MonthBalance(month.getKey(), running));
        }
        return series;
    }

    /**
     * Attaches the period's acquired lines that bear no account yet to their production code's account
     * (the account-posting time of the cycle, D10).
     *
     * @param entity the entity
     * @param period the period
     */
    @Transactional
    public void postToAccounts(LegalEntity entity, Period period) {
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period = ?2 and status = ?3 and account is null and originProductionCode is not null",
                entity, period, LineStatus.EARNED)) {
            line.account = accountFor(entity, PayeeType.PRODUCTION_CODE, line.originProductionCode.getId());
        }
    }

    /**
     * Compensates a distributor's debtor accounts with its creditor accounts in an entity (CPT-04):
     * for each debtor, transfers are pulled from the creditors until the debt is covered, each
     * transfer a pair of {@code TRANSFER} lines. Compensation never crosses two distributors.
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @param period      the period
     * @return the number of transfer lines written
     */
    @Transactional
    public int compensate(LegalEntity entity, Distributor distributor, Period period) {
        List<DistributorAccount> accounts = accountsOf(entity, distributor);
        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();
        for (DistributorAccount account : accounts) {
            BigDecimal balance = balance(account);
            if (balance.signum() < 0) {
                debtors.add(new Balance(account, balance.negate()));
            } else if (balance.signum() > 0) {
                creditors.add(new Balance(account, balance));
            }
        }
        int count = 0;
        for (Balance debtor : debtors) {
            for (Balance creditor : creditors) {
                if (debtor.amount.signum() == 0) {
                    break;
                }
                if (creditor.amount.signum() == 0) {
                    continue;
                }
                BigDecimal moved = debtor.amount.min(creditor.amount);
                transfer(entity, period, debtor.account, moved, distributor);
                transfer(entity, period, creditor.account, moved.negate(), distributor);
                debtor.amount = debtor.amount.subtract(moved);
                creditor.amount = creditor.amount.subtract(moved);
                count += 2;
            }
        }
        return count;
    }

    /**
     * Writes one {@code TRANSFER} line of a signed amount on an account (compensation).
     *
     * @param entity      the entity
     * @param period      the period
     * @param account     the account
     * @param amount      the signed amount
     * @param distributor the distributor
     */
    private void transfer(LegalEntity entity, Period period, DistributorAccount account, BigDecimal amount,
                          Distributor distributor) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.TRANSFER;
        line.amount = amount;
        line.account = account;
        line.originDistributor = distributor;
        line.attachmentMonth = period.periodMonth;
        line.explanation = "{\"family\":\"TRANSFER\",\"compensation\":true,\"amount\":" + amount.toPlainString() + "}";
        line.setCreatedAt(clock.now());
        line.persist();
    }

    /**
     * Prepares the cycle payment of an entity's period (section 10.5): for each account a statement is
     * produced with its carried balance and period total; a positive balance reaching the entity's
     * minimum is proposed in full as an order, a balance under the minimum or not positive is held and
     * carried, each hold listed with its reason.
     *
     * @param entity the entity
     * @param period the period
     * @return the created draft payment batch
     */
    @Transactional
    public PaymentBatch proposePayments(LegalEntity entity, Period period) {
        PaymentBatch cycle = new PaymentBatch();
        cycle.entity = entity;
        cycle.period = period;
        cycle.batchType = BatchType.CYCLE;
        cycle.batchReference = "CYCLE-" + entity.code + "-" + period.periodMonth;
        cycle.status = BatchStatus.DRAFT;
        cycle.totalAmount = BigDecimal.ZERO;
        cycle.setCreatedAt(clock.now());
        cycle.persist();
        // The retirement-savings lot is created only when a savings line is actually payable, so a
        // period with no savings range behaves exactly as before (one cycle lot).
        PaymentBatch[] savingsHolder = {null};
        BigDecimal threshold = entity.minPaymentAmount == null ? BigDecimal.ZERO : entity.minPaymentAmount;
        BigDecimal cycleTotal = BigDecimal.ZERO;
        BigDecimal savingsTotal = BigDecimal.ZERO;
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            List<CommissionLine> earned = CommissionLine.<CommissionLine>list(
                    "account = ?1 and status = ?2", account, LineStatus.EARNED);
            // A manually blocked line (CPT-05) is held: its amount stays at the account and is not
            // swept into the payment order. With no blocked line the payable equals the full balance.
            BigDecimal held = earned.stream()
                    .filter(line -> Boolean.TRUE.equals(line.blocked))
                    .map(line -> line.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal payable = balance(account).subtract(held);
            PaymentExclusionReason reason = exclusionReason(account, payable, threshold, clock.today());
            if (reason != null) {
                PaymentExclusion exclusion = new PaymentExclusion();
                exclusion.batch = cycle;
                exclusion.account = account;
                exclusion.reason = reason;
                exclusion.amount = payable;
                exclusion.setCreatedAt(clock.now());
                exclusion.persist();
                // A debtor account (non-positive carried balance) still gets a bordereau carrying its
                // reported balance, with no order emitted (section 9.5 p.1720, section 10.5 footer, BRD-01,
                // CPT-01): the bordereau is produced whenever a line or a carried balance exists.
                if (reason == PaymentExclusionReason.NON_POSITIVE_BALANCE) {
                    statement(entity, period, account, BatchType.CYCLE, payable, periodTotal(account, period),
                            BigDecimal.ZERO);
                }
                continue;
            }
            // Split the payable between the retirement-savings lot and the cycle lot (section 10.5): the
            // savings range takes its own positive net, the cycle lot carries the rest (prior balance,
            // compensation, threshold). A negative net of either lot is imputed on the other, so the two
            // orders still sum to the account's payable and no payment exceeds a positive balance (CPT-03).
            List<CommissionLine> nonBlocked = earned.stream()
                    .filter(line -> !Boolean.TRUE.equals(line.blocked)).toList();
            List<CommissionLine> savingsLines = nonBlocked.stream().filter(this::savingsLine).toList();
            List<CommissionLine> cycleLines = nonBlocked.stream().filter(line -> !savingsLine(line)).toList();
            BigDecimal savingsNet = savingsLines.stream()
                    .map(line -> line.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cycleNet = cycleLines.stream()
                    .map(line -> line.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal savingsPay = savingsNet.signum() > 0 ? savingsNet.min(payable) : BigDecimal.ZERO;
            BigDecimal cyclePay = payable.subtract(savingsPay);
            BigDecimal savingsPeriodTotal = savingsPeriodTotal(account, period);
            BigDecimal cyclePeriodTotal = periodTotal(account, period).subtract(savingsPeriodTotal);
            boolean hasCycle = cyclePay.signum() > 0;
            boolean hasSavings = savingsPay.signum() > 0;
            // A lot with no positive net of its own is imputed on the paying lot (section 10.5, PAI-04): its
            // net becomes a single imputation line on the paying bordereau, so the two orders still sum to
            // the account's payable and no payment exceeds a positive balance (CPT-03).
            BigDecimal imputedOnCycle = hasSavings ? BigDecimal.ZERO : savingsNet;
            BigDecimal imputedOnSavings = hasCycle ? BigDecimal.ZERO : cycleNet;
            Statement cycleStmt = hasCycle
                    ? statement(entity, period, account, BatchType.CYCLE, cyclePay, cyclePeriodTotal, imputedOnCycle)
                    : null;
            Statement savingsStmt = hasSavings
                    ? statement(entity, period, account, BatchType.SAVINGS_CYCLE, savingsPay, savingsPeriodTotal,
                            imputedOnSavings)
                    : null;
            if (cycleStmt != null) {
                order(cycle, account, cycleStmt, cyclePay);
                cycleTotal = cycleTotal.add(cyclePay);
            }
            if (savingsStmt != null) {
                if (savingsHolder[0] == null) {
                    savingsHolder[0] = savingsBatch(entity, period);
                }
                order(savingsHolder[0], account, savingsStmt, savingsPay);
                savingsTotal = savingsTotal.add(savingsPay);
            }
            // Attach each line to the bordereau of the lot that pays it; an imputed lot (no own statement)
            // has its lines follow the paying lot, where they render as one imputation line (REP-02).
            Statement savingsHome = savingsStmt != null ? savingsStmt : cycleStmt;
            Statement cycleHome = cycleStmt != null ? cycleStmt : savingsStmt;
            for (CommissionLine line : savingsLines) {
                line.status = LineStatus.PAYABLE;
                line.statement = savingsHome;
            }
            for (CommissionLine line : cycleLines) {
                line.status = LineStatus.PAYABLE;
                line.statement = cycleHome;
            }
        }
        cycle.totalAmount = cycleTotal;
        if (savingsHolder[0] != null) {
            savingsHolder[0].totalAmount = savingsTotal;
        }
        return cycle;
    }

    /**
     * Creates the draft retirement-savings lot of a period, dated the 20th of the period month (section
     * 10.6: the savings transfers go out between the 20th and the 25th).
     *
     * @param entity the entity
     * @param period the period
     * @return the created savings batch
     */
    private PaymentBatch savingsBatch(LegalEntity entity, Period period) {
        PaymentBatch savings = new PaymentBatch();
        savings.entity = entity;
        savings.period = period;
        savings.batchType = BatchType.SAVINGS_CYCLE;
        savings.batchReference = "SAVINGS-" + entity.code + "-" + period.periodMonth;
        savings.status = BatchStatus.DRAFT;
        savings.executionDate = period.periodMonth.withDayOfMonth(20);
        savings.totalAmount = BigDecimal.ZERO;
        savings.setCreatedAt(clock.now());
        savings.persist();
        return savings;
    }

    /**
     * Creates a to-validate payment order on a lot for an account.
     *
     * @param batch     the lot
     * @param account   the account
     * @param statement the statement carrying the order
     * @param amount    the order amount
     */
    private void order(PaymentBatch batch, DistributorAccount account, Statement statement, BigDecimal amount) {
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.account = account;
        order.statement = statement;
        order.amount = amount;
        order.status = PaymentOrderStatus.TO_VALIDATE;
        order.setCreatedAt(clock.now());
        order.persist();
    }

    /**
     * Whether a commission line belongs to a retirement-savings range (section 10.5).
     *
     * @param line the commission line
     * @return true when its product's range is a retirement-savings range
     */
    private boolean savingsLine(CommissionLine line) {
        return line.contract != null && line.contract.product != null
                && line.contract.product.range != null
                && Boolean.TRUE.equals(line.contract.product.range.retirementSavings);
    }

    /**
     * Returns the part of an account's period total carried by retirement-savings lines.
     *
     * @param account the account
     * @param period  the period
     * @return the savings period total
     */
    private BigDecimal savingsPeriodTotal(DistributorAccount account, Period period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "account = ?1 and period = ?2 and status in ?3", account, period, COUNTED)) {
            if (savingsLine(line)) {
                sum = sum.add(line.amount);
            }
        }
        return sum;
    }

    /**
     * Creates the account's statement for the period and lot, carrying the balance before the period and
     * its period total.
     *
     * @param entity      the entity
     * @param period      the period
     * @param account     the account
     * @param batchType   the lot type the statement belongs to
     * @param net         the net balance of the lot
     * @param periodTotal the lot's part of the period total
     * @param imputedNet  the net imputed from the other lot (shown as one imputation line), or zero
     * @return the statement
     */
    private Statement statement(LegalEntity entity, Period period, DistributorAccount account, BatchType batchType,
                                BigDecimal net, BigDecimal periodTotal, BigDecimal imputedNet) {
        Statement statement = new Statement();
        statement.entity = entity;
        statement.period = period;
        statement.account = account;
        statement.batchType = batchType;
        statement.statementNumber = "ST-" + entity.code + "-" + period.periodMonth + "-" + account.getId()
                + (batchType == BatchType.SAVINGS_CYCLE ? "-S" : "");
        statement.versionNo = 1;
        // The bordereau is produced at its beneficiary's level (BRD-03): production code, distributor or
        // grouping, taken from the account's payee type.
        statement.level = switch (account.payeeType) {
            case DISTRIBUTOR -> StatementLevel.DISTRIBUTOR;
            case GROUP -> StatementLevel.GROUP;
            case PRODUCTION_CODE -> StatementLevel.PRODUCTION_CODE;
        };
        // The carried balance holds the prior-period balance only; the net imputed from the other lot is
        // shown separately as one imputation line (section 10.5, PAI-04), so it is excluded here.
        statement.carriedBalance = net.subtract(periodTotal).subtract(imputedNet);
        statement.periodTotal = periodTotal;
        statement.netAmount = net;
        statement.status = StatementStatus.DRAFT;
        statement.setCreatedAt(clock.now());
        statement.persist();
        return statement;
    }

    /**
     * Returns the reason an account is excluded from payment, or null when it is payable (section
     * 10.5): a blocked account, a non-positive (carried) balance, or a balance under the minimum.
     *
     * @param account   the account
     * @param net       the net balance
     * @param threshold the entity minimum
     * @param today     the application day, for the block's lifting date
     * @return the exclusion reason, or null
     */
    private static PaymentExclusionReason exclusionReason(DistributorAccount account, BigDecimal net,
                                                          BigDecimal threshold, LocalDate today) {
        // A block holds until its lifting date (CPT-04): once that date is reached it no longer excludes.
        if (Boolean.TRUE.equals(account.blocked)
                && (account.blockedUntil == null || today.isBefore(account.blockedUntil))) {
            return PaymentExclusionReason.ACCOUNT_BLOCKED;
        }
        if (net.signum() <= 0) {
            return PaymentExclusionReason.NON_POSITIVE_BALANCE;
        }
        if (net.compareTo(threshold) < 0) {
            return PaymentExclusionReason.BELOW_THRESHOLD;
        }
        return null;
    }

    /**
     * Returns the accounts of a distributor in an entity (its production codes' accounts and its own),
     * for the distributor-account view (CPT-02, CPT-06).
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @return the distributor's accounts
     */
    public List<DistributorAccount> accountsOfDistributor(LegalEntity entity, Distributor distributor) {
        return accountsOf(entity, distributor);
    }

    /**
     * Blocks an account with a reason and an optional lifting date (CPT-05).
     *
     * @param accountId the account id
     * @param reason    the block reason
     * @param until     the lifting date, or null
     * @return the blocked account
     */
    @Transactional
    public DistributorAccount block(Long accountId, String reason, LocalDate until) {
        DistributorAccount account = DistributorAccount.findById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Compte inconnu : " + accountId);
        }
        account.blocked = Boolean.TRUE;
        account.blockReason = reason;
        account.blockedUntil = until;
        return account;
    }

    /**
     * Returns a distributor's commission lines in an entity, most recent attachment month first, for
     * the line-blocking screen (CPT-05, CPT-06).
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @return the distributor's commission lines
     */
    public List<CommissionLine> linesOfDistributor(LegalEntity entity, Distributor distributor) {
        List<CommissionLine> lines = new ArrayList<>();
        for (DistributorAccount account : accountsOf(entity, distributor)) {
            lines.addAll(CommissionLine.<CommissionLine>list("account = ?1 order by attachmentMonth desc, id", account));
        }
        return lines;
    }

    /**
     * Blocks a single commission line with a reason and an optional lifting date (CPT-05). A blocked
     * line is held from payment; its amount stays at the account.
     *
     * @param lineId the commission-line id
     * @param reason the block reason
     * @param until  the lifting date, or null
     * @return the blocked line
     */
    @Transactional
    public CommissionLine blockLine(Long lineId, String reason, LocalDate until) {
        CommissionLine line = CommissionLine.findById(lineId);
        if (line == null) {
            throw new ResourceNotFoundException("Ligne inconnue : " + lineId);
        }
        line.blocked = Boolean.TRUE;
        line.blockReason = reason;
        line.blockedUntil = until;
        return line;
    }

    /**
     * Lifts the block on a commission line (CPT-05).
     *
     * @param lineId the commission-line id
     * @return the unblocked line
     */
    @Transactional
    public CommissionLine unblockLine(Long lineId) {
        CommissionLine line = CommissionLine.findById(lineId);
        if (line == null) {
            throw new ResourceNotFoundException("Ligne inconnue : " + lineId);
        }
        line.blocked = Boolean.FALSE;
        line.blockReason = null;
        line.blockedUntil = null;
        return line;
    }

    /**
     * Lifts the block on an account (CPT-05).
     *
     * @param accountId the account id
     * @return the unblocked account
     */
    @Transactional
    public DistributorAccount unblock(Long accountId) {
        DistributorAccount account = DistributorAccount.findById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Compte inconnu : " + accountId);
        }
        account.blocked = Boolean.FALSE;
        account.blockReason = null;
        account.blockedUntil = null;
        return account;
    }

    /**
     * Returns the entity's debtor accounts, for the demand-for-repayment list (CPT-07).
     *
     * @param entity the entity
     * @return the accounts with a debtor balance
     */
    public List<DistributorAccount> debtors(LegalEntity entity) {
        List<DistributorAccount> debtors = new ArrayList<>();
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            if (balance(account).signum() < 0) {
                debtors.add(account);
            }
        }
        return debtors;
    }

    /**
     * Whether a debtor account has no active portfolio (CPT-07, section 6.4 point 7): a production-code
     * account whose production code is no longer active can no longer absorb its debt through future
     * commissions, so the distributor is signalled for a repayment demand. Any other account, or one on
     * an active production code, is not signalled.
     *
     * @param account the account
     * @return true when the account has no active portfolio
     */
    public boolean withoutActivePortfolio(DistributorAccount account) {
        if (account.payeeType != PayeeType.PRODUCTION_CODE) {
            return false;
        }
        ProductionCode pc = ProductionCode.findById(account.payeeId);
        return pc == null || pc.status != PartnerStatus.ACTIVE;
    }

    /**
     * Returns the accounts of a distributor in an entity (its production codes' accounts and its own).
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @return the distributor's accounts
     */
    private static List<DistributorAccount> accountsOf(LegalEntity entity, Distributor distributor) {
        Map<Long, DistributorAccount> byId = new LinkedHashMap<>();
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            if (account.payeeType == PayeeType.DISTRIBUTOR && account.payeeId.equals(distributor.getId())) {
                byId.put(account.getId(), account);
            } else if (account.payeeType == PayeeType.PRODUCTION_CODE) {
                ProductionCode pc = ProductionCode.findById(account.payeeId);
                if (pc != null && pc.distributor != null && pc.distributor.getId().equals(distributor.getId())) {
                    byId.put(account.getId(), account);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** A mutable account balance pairing used during compensation. */
    private static final class Balance {
        /** The account. */
        private final DistributorAccount account;
        /** The remaining absolute amount to compensate. */
        private BigDecimal amount;

        /**
         * Creates a balance pairing.
         *
         * @param account the account
         * @param amount  the absolute amount
         */
        private Balance(DistributorAccount account, BigDecimal amount) {
            this.account = account;
            this.amount = amount;
        }
    }

    /**
     * One month and the distributor's running account balance at the end of it.
     *
     * @param month   the month
     * @param balance the running balance
     */
    public record MonthBalance(LocalDate month, BigDecimal balance) {
    }
}
