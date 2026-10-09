package fr.alptis.remuneration.payment;

import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentExclusion;
import fr.alptis.remuneration.domain.PaymentExclusionReason;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment service (section 8.9, 7.1): the Finance validation of a payment batch, which checks the
 * alignment of the batch total with the sum of its orders (CR-64), emits the SEPA file (pain.001),
 * commits the payment (the batch's lines pass {@code PAID} and a payment entry debits each account),
 * then the recording of the bank result — a settled order is confirmed, a rejected one recredits the
 * account with a {@code PAYMENT_REJECTION} entry, frees its lines for the next lot and raises an
 * anomaly (CR-85). The period reaches {@code PAID} once every order of every lot is settled or
 * rejected (CR-86).
 */
@ApplicationScoped
public class PaymentService {

    private final DateTimeProvider clock;
    private final fr.alptis.remuneration.accounts.AccountService accounts;

    /**
     * Creates the service.
     *
     * @param clock    the application date/time provider
     * @param accounts the distributor-account service
     */
    @Inject
    public PaymentService(DateTimeProvider clock, fr.alptis.remuneration.accounts.AccountService accounts) {
        this.clock = clock;
        this.accounts = accounts;
    }

    /**
     * Prepares a cycle batch for emission (PAY-02, PAY-04): a beneficiary struck off the ORIAS or no
     * longer active in the entity has its order cancelled and listed as an exclusion; a beneficiary
     * without bank details is moved to the quarterly cheque lot; the others receive their IBAN, name
     * and end-to-end reference (the bordereau number). The batch total is realigned to its remaining
     * transfer orders.
     *
     * @param batchId the cycle batch id
     * @return the prepared batch
     */
    @Transactional
    public PaymentBatch prepareOrderDetails(Long batchId) {
        PaymentBatch batch = PaymentBatch.findById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("Lot inconnu : " + batchId);
        }
        PaymentBatch cheque = null;
        BigDecimal transferTotal = BigDecimal.ZERO;
        BigDecimal chequeTotal = BigDecimal.ZERO;
        for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch = ?1 and status = ?2", batch,
                PaymentOrderStatus.TO_VALIDATE)) {
            Distributor distributor = resolveDistributor(order.account);
            if (distributor != null && !payable(distributor, batch.entity)) {
                order.status = PaymentOrderStatus.CANCELLED;
                exclusion(batch, order.account, PaymentExclusionReason.PAYEE_INACTIVE, order.amount);
                continue;
            }
            BankAccount bank = findBank(batch.entity, order.account);
            if (bank == null) {
                if (cheque == null) {
                    cheque = chequeBatch(batch);
                }
                order.batch = cheque;
                chequeTotal = chequeTotal.add(order.amount);
            } else {
                order.iban = bank.iban;
                order.bic = bank.bic;
                order.creditorName = bank.holderName;
                order.endToEndId = order.statement.statementNumber;
                transferTotal = transferTotal.add(order.amount);
            }
        }
        batch.totalAmount = transferTotal;
        if (cheque != null) {
            cheque.totalAmount = chequeTotal;
        }
        return batch;
    }

    /**
     * Builds an off-cycle lot paying a distributor's positive balances now that it is payable again
     * (PAY-07, CR-62): for example to release the commissions of a distributor re-registered with the
     * ORIAS without waiting for the next cycle.
     *
     * @param entityCode  the entity code
     * @param partnerCode the distributor partner code
     * @param periodMonth the period the lot attaches to
     * @return the off-cycle batch
     */
    @Transactional
    public PaymentBatch offCycleLot(String entityCode, String partnerCode, java.time.LocalDate periodMonth) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Distributor distributor = Distributor.<Distributor>find("partnerCode", partnerCode).firstResult();
        if (entity == null || distributor == null) {
            throw new ResourceNotFoundException("Entité ou distributeur inconnu");
        }
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            period = new Period();
            period.entity = entity;
            period.periodMonth = periodMonth;
            period.state = PeriodState.OPEN;
            period.setCreatedAt(clock.now());
            period.persist();
        }
        PaymentBatch batch = new PaymentBatch();
        batch.entity = entity;
        batch.period = period;
        batch.batchType = BatchType.OFF_CYCLE;
        batch.batchReference = "OFFCYCLE-" + entityCode + "-" + partnerCode + "-" + periodMonth;
        batch.status = BatchStatus.DRAFT;
        batch.totalAmount = BigDecimal.ZERO;
        batch.setCreatedAt(clock.now());
        batch.persist();
        BigDecimal total = BigDecimal.ZERO;
        if (payable(distributor, entity)) {
            for (DistributorAccount account : accounts.accountsOfDistributor(entity, distributor)) {
                BigDecimal balance = accounts.balance(account);
                if (balance.signum() > 0) {
                    for (CommissionLine line : CommissionLine.<CommissionLine>list("account = ?1 and status = ?2",
                            account, LineStatus.EARNED)) {
                        line.status = LineStatus.PAYABLE;
                    }
                    offCycleOrder(batch, account, balance);
                    total = total.add(balance);
                }
            }
        }
        batch.totalAmount = total;
        return batch;
    }

    /**
     * Creates an off-cycle order for an account, resolving its bank details.
     *
     * @param batch   the off-cycle batch
     * @param account the account
     * @param amount  the balance to pay
     */
    private void offCycleOrder(PaymentBatch batch, DistributorAccount account, BigDecimal amount) {
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.account = account;
        order.amount = amount;
        order.status = PaymentOrderStatus.TO_VALIDATE;
        order.endToEndId = batch.batchReference + "-" + account.getId();
        BankAccount bank = findBank(batch.entity, account);
        if (bank != null) {
            order.iban = bank.iban;
            order.bic = bank.bic;
            order.creditorName = bank.holderName;
        }
        order.setCreatedAt(clock.now());
        order.persist();
    }

    /**
     * Resolves the distributor behind an account, or null for a group account.
     *
     * @param account the account
     * @return the distributor, or null
     */
    private static Distributor resolveDistributor(DistributorAccount account) {
        if (account.payeeType == PayeeType.PRODUCTION_CODE) {
            ProductionCode pc = ProductionCode.findById(account.payeeId);
            return pc == null ? null : pc.distributor;
        }
        if (account.payeeType == PayeeType.DISTRIBUTOR) {
            return Distributor.findById(account.payeeId);
        }
        return null;
    }

    /**
     * Tells whether a distributor may be paid in an entity: registered with the ORIAS and not
     * suspended or terminated in that entity.
     *
     * @param distributor the distributor
     * @param entity      the entity
     * @return {@code true} when payable
     */
    private static boolean payable(Distributor distributor, LegalEntity entity) {
        if (distributor.oriasStatus == OriasStatus.STRUCK_OFF) {
            return false;
        }
        DistributorEntity link = DistributorEntity.find("distributor = ?1 and entity = ?2", distributor, entity)
                .firstResult();
        return link == null || link.status == PartnerStatus.ACTIVE;
    }

    /**
     * Finds the account's current bank details in the entity, or null.
     *
     * @param entity  the entity
     * @param account the account
     * @return the bank account, or null
     */
    private BankAccount findBank(LegalEntity entity, DistributorAccount account) {
        return BankAccount.find(
                "entity = ?1 and holderType = ?2 and holderId = ?3 and (validTo is null or validTo >= ?4)",
                entity, account.payeeType, account.payeeId, clock.now().toLocalDate()).firstResult();
    }

    /**
     * Returns the entity's cheque batch for a period, creating it when absent.
     *
     * @param cycle the cycle batch giving the entity and period
     * @return the cheque batch
     */
    private PaymentBatch chequeBatch(PaymentBatch cycle) {
        PaymentBatch cheque = new PaymentBatch();
        cheque.entity = cycle.entity;
        cheque.period = cycle.period;
        cheque.batchType = BatchType.CHEQUE;
        cheque.batchReference = "CHEQUE-" + cycle.entity.code + "-" + cycle.period.periodMonth;
        cheque.status = BatchStatus.DRAFT;
        cheque.totalAmount = BigDecimal.ZERO;
        cheque.setCreatedAt(clock.now());
        cheque.persist();
        return cheque;
    }

    /**
     * Records a payment exclusion with its reason.
     *
     * @param batch   the batch
     * @param account the excluded account
     * @param reason  the exclusion reason
     * @param amount  the excluded amount
     */
    private void exclusion(PaymentBatch batch, DistributorAccount account, PaymentExclusionReason reason,
                           BigDecimal amount) {
        PaymentExclusion exclusion = new PaymentExclusion();
        exclusion.batch = batch;
        exclusion.account = account;
        exclusion.reason = reason;
        exclusion.amount = amount;
        exclusion.setCreatedAt(clock.now());
        exclusion.persist();
    }

    /**
     * Validates a batch for Finance and emits its SEPA file (PAY-03, PAY-05): the batch total must
     * equal the sum of its orders (CR-64); the orders are issued, their lines pass {@code PAID} and a
     * payment entry debits each account.
     *
     * @param batchId the batch id
     * @param userId  the validating Finance controller
     * @return the validated batch
     */
    @Transactional
    public PaymentBatch validateFinance(Long batchId, String userId) {
        PaymentBatch batch = PaymentBatch.findById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("Lot inconnu : " + batchId);
        }
        List<PaymentOrder> orders = PaymentOrder.list("batch = ?1", batch);
        BigDecimal orderSum = BigDecimal.ZERO;
        for (PaymentOrder order : orders) {
            orderSum = orderSum.add(order.amount);
        }
        if (batch.totalAmount.compareTo(orderSum) != 0) {
            throw new ValidationException("Le total du lot ne correspond pas à la somme des ordres");
        }
        batch.status = BatchStatus.ISSUED;
        batch.validatedBy = userId;
        batch.validatedAt = clock.now();
        batch.sepaFileRef = batch.entity.code + "_SEPA_" + batch.period.periodMonth + "_" + batch.batchReference;
        batch.sepaFileHash = HashUtil.sha256(sepaXml(batch, orders));
        for (PaymentOrder order : orders) {
            order.status = PaymentOrderStatus.ISSUED;
            pay(order);
        }
        return batch;
    }

    /**
     * Debits an order's account and passes its payable lines to paid (payment committed on emission).
     *
     * @param order the issued order
     */
    private void pay(PaymentOrder order) {
        for (CommissionLine line : CommissionLine.<CommissionLine>list("account = ?1 and status = ?2",
                order.account, LineStatus.PAYABLE)) {
            line.status = LineStatus.PAID;
            line.paidAt = clock.now();
        }
        entry(order.account, AccountEntryType.PAYMENT, order.amount.negate());
    }

    /**
     * Returns the SEPA pain.001.001.03 file of a batch (section 9.6): one payment block for the lot,
     * one transaction per order with the bordereau reference as the end-to-end id and a {@code COM}
     * label.
     *
     * @param batchId the batch id
     * @return the SEPA XML
     */
    public String sepaXml(Long batchId) {
        PaymentBatch batch = PaymentBatch.findById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("Lot inconnu : " + batchId);
        }
        return sepaXml(batch, PaymentOrder.list("batch = ?1", batch));
    }

    /**
     * Builds the SEPA XML of a batch and its orders.
     *
     * @param batch  the batch
     * @param orders the batch orders
     * @return the pain.001.001.03 XML
     */
    private String sepaXml(PaymentBatch batch, List<PaymentOrder> orders) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">\n");
        xml.append("<CstmrCdtTrfInitn><PmtInf>");
        xml.append("<PmtInfId>").append(batch.batchReference).append("</PmtInfId>");
        xml.append("<Dbtr><Nm>").append(batch.entity.name).append("</Nm></Dbtr>");
        xml.append("<DbtrAcct><Id><IBAN>").append(nullSafe(batch.entity.debtorIban)).append("</IBAN></Id></DbtrAcct>");
        for (PaymentOrder order : orders) {
            String label = "COM " + batch.entity.code + " " + batch.period.periodMonth + " " + order.endToEndId;
            xml.append("<CdtTrfTxInf>");
            xml.append("<PmtId><EndToEndId>").append(nullSafe(order.endToEndId)).append("</EndToEndId></PmtId>");
            xml.append("<Amt><InstdAmt Ccy=\"EUR\">").append(order.amount.toPlainString()).append("</InstdAmt></Amt>");
            xml.append("<Cdtr><Nm>").append(nullSafe(order.creditorName)).append("</Nm></Cdtr>");
            xml.append("<CdtrAcct><Id><IBAN>").append(nullSafe(order.iban)).append("</IBAN></Id></CdtrAcct>");
            xml.append("<RmtInf><Ustrd>").append(label).append("</Ustrd></RmtInf>");
            xml.append("</CdtTrfTxInf>");
        }
        xml.append("</PmtInf></CstmrCdtTrfInitn></Document>");
        return xml.toString();
    }

    /**
     * Records the bank result of one order (PAY-06): a settled order is confirmed; a rejected one is
     * recredited with a {@code PAYMENT_REJECTION} entry, its lines return to {@code PAYABLE} for the
     * next lot and an anomaly is raised. The period passes {@code PAID} once every order of its lots
     * is settled or rejected.
     *
     * @param orderId  the order id
     * @param settled  whether the transfer settled
     * @param reason   the rejection reason, when not settled
     * @return the updated order
     */
    @Transactional
    public PaymentOrder recordBankResult(Long orderId, boolean settled, String reason) {
        PaymentOrder order = PaymentOrder.findById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Ordre inconnu : " + orderId);
        }
        if (settled) {
            order.status = PaymentOrderStatus.SETTLED;
            order.settledAt = clock.now();
        } else {
            order.status = PaymentOrderStatus.REJECTED;
            order.rejectionReason = reason;
            for (CommissionLine line : CommissionLine.<CommissionLine>list("account = ?1 and status = ?2",
                    order.account, LineStatus.PAID)) {
                line.status = LineStatus.PAYABLE;
                line.paidAt = null;
            }
            entry(order.account, AccountEntryType.PAYMENT_REJECTION, order.amount);
            rejectionAnomaly(order);
        }
        closeIfDone(order.batch.period);
        return order;
    }

    /**
     * Passes the period to {@code PAID} once every order of every lot of the period is settled or
     * rejected (CR-86).
     *
     * @param period the period
     */
    private void closeIfDone(Period period) {
        long pending = PaymentOrder.count(
                "batch.period = ?1 and status not in ?2", period,
                List.of(PaymentOrderStatus.SETTLED, PaymentOrderStatus.REJECTED, PaymentOrderStatus.CANCELLED));
        if (pending == 0 && period.state == PeriodState.FINANCE_VALIDATED) {
            period.state = PeriodState.PAID;
        }
    }

    /**
     * Writes a signed account entry.
     *
     * @param account the account
     * @param type    the entry type
     * @param amount  the signed amount
     */
    private void entry(DistributorAccount account, AccountEntryType type, BigDecimal amount) {
        AccountEntry entry = new AccountEntry();
        entry.account = account;
        entry.period = null;
        entry.entryDate = clock.now().toLocalDate();
        entry.entryType = type;
        entry.amount = amount;
        entry.setCreatedAt(clock.now());
        entry.persist();
    }

    /**
     * Raises the bank-rejection anomaly for an order.
     *
     * @param order the rejected order
     */
    private void rejectionAnomaly(PaymentOrder order) {
        LegalEntity entity = order.batch.entity;
        ControlDefinition control = ControlDefinition.find("entity = ?1 and code = ?2", entity, "PAY-REJECT")
                .firstResult();
        if (control == null) {
            control = new ControlDefinition();
            control.entity = entity;
            control.code = "PAY-REJECT";
            control.label = "Rejet bancaire";
            control.family = ControlFamily.PAYMENT;
            control.severity = ControlSeverity.WARNING;
            control.active = Boolean.TRUE;
            control.setCreatedAt(clock.now());
            control.persist();
        }
        ControlResult result = new ControlResult();
        result.period = order.batch.period;
        result.control = control;
        result.objectType = "PAYMENT_ORDER";
        result.objectId = order.getId();
        result.severity = ControlSeverity.WARNING;
        result.observedValue = order.rejectionReason;
        result.expectedValue = order.amount.toPlainString();
        result.decision = ControlDecision.OPEN;
        result.setCreatedAt(clock.now());
        result.persist();
    }

    /**
     * Returns an empty string for a null value.
     *
     * @param value the value, possibly null
     * @return the value or an empty string
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
