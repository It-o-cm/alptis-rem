package fr.alptis.remuneration.payment;

import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentExclusion;
import fr.alptis.remuneration.domain.PaymentExclusionReason;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.StatementStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read model of the payment and documents screens (section 8.9, 8.10): the lots of a period, the
 * detail of a lot (its orders and exclusions) and the statements of a period.
 */
@ApplicationScoped
public class PaymentQueryService {

    /**
     * Lists the payment lots of an entity's period (PAY-01).
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the lot summaries
     */
    public List<LotView> lots(String entityCode, LocalDate periodMonth) {
        Period period = period(entityCode, periodMonth);
        List<LotView> views = new ArrayList<>();
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("period = ?1 order by batchType", period)) {
            views.add(new LotView(batch.getId(), batch.batchType, batch.status, batch.batchReference,
                    batch.totalAmount, PaymentOrder.count("batch = ?1", batch),
                    PaymentExclusion.count("batch = ?1", batch)));
        }
        return views;
    }

    /**
     * Returns the detail of a lot: its orders and exclusions (PAY-02).
     *
     * @param batchId the batch id
     * @return the lot detail
     */
    public LotDetail lot(Long batchId) {
        PaymentBatch batch = PaymentBatch.findById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("Lot inconnu : " + batchId);
        }
        List<OrderView> orders = new ArrayList<>();
        for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch = ?1 order by id", batch)) {
            orders.add(new OrderView(order.getId(), order.account.payeeType, order.account.payeeId, order.amount,
                    order.status, order.iban, order.endToEndId));
        }
        List<ExclusionView> exclusions = new ArrayList<>();
        for (PaymentExclusion exclusion : PaymentExclusion.<PaymentExclusion>list("batch = ?1 order by id", batch)) {
            exclusions.add(new ExclusionView(exclusion.account.payeeType, exclusion.account.payeeId,
                    exclusion.reason, exclusion.amount));
        }
        return new LotDetail(batch.getId(), batch.batchType, batch.status, batch.totalAmount, orders, exclusions);
    }

    /**
     * Lists the statements of an entity's period (BRD-01).
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the statement summaries
     */
    public List<StatementView> statements(String entityCode, LocalDate periodMonth) {
        Period period = period(entityCode, periodMonth);
        List<StatementView> views = new ArrayList<>();
        for (Statement statement : Statement.<Statement>list("period = ?1 order by id", period)) {
            views.add(new StatementView(statement.getId(), statement.statementNumber, statement.versionNo,
                    statement.netAmount, statement.status, statement.publicationDate, statement.gedReference,
                    statement.templateVersion));
        }
        return views;
    }

    /**
     * Returns an entity's period, or fails when unknown.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the period
     */
    private static Period period(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = entity == null ? null
                : Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + entityCode + " " + periodMonth);
        }
        return period;
    }

    /**
     * Summary of a payment lot.
     *
     * @param id         the batch id
     * @param batchType  the lot type
     * @param status     the lot status
     * @param reference  the lot reference
     * @param total      the lot total
     * @param orders     the number of orders
     * @param exclusions the number of exclusions
     */
    public record LotView(Long id, BatchType batchType, BatchStatus status, String reference, BigDecimal total,
                          long orders, long exclusions) {
    }

    /**
     * One order of a lot.
     *
     * @param id        the order id
     * @param payeeType the payee type
     * @param payeeId   the payee id
     * @param amount    the amount
     * @param status    the order status
     * @param iban      the creditor IBAN, or null
     * @param endToEndId the end-to-end reference
     */
    public record OrderView(Long id, PayeeType payeeType, Long payeeId, BigDecimal amount, PaymentOrderStatus status,
                            String iban, String endToEndId) {
    }

    /**
     * One exclusion of a lot.
     *
     * @param payeeType the payee type
     * @param payeeId   the payee id
     * @param reason    the exclusion reason
     * @param amount    the excluded amount
     */
    public record ExclusionView(PayeeType payeeType, Long payeeId, PaymentExclusionReason reason, BigDecimal amount) {
    }

    /**
     * Detail of a lot: its orders and exclusions.
     *
     * @param id         the batch id
     * @param batchType  the lot type
     * @param status     the lot status
     * @param total      the lot total
     * @param orders     the orders
     * @param exclusions the exclusions
     */
    public record LotDetail(Long id, BatchType batchType, BatchStatus status, BigDecimal total, List<OrderView> orders,
                            List<ExclusionView> exclusions) {
    }

    /**
     * Summary of a statement.
     *
     * @param id              the statement id
     * @param statementNumber the statement number
     * @param versionNo       the version
     * @param netAmount       the net amount
     * @param status          the status
     */
    public record StatementView(Long id, String statementNumber, Integer versionNo, BigDecimal netAmount,
                                StatementStatus status, java.time.LocalDate publicationDate, String gedReference,
                                Integer templateVersion) {
    }
}
