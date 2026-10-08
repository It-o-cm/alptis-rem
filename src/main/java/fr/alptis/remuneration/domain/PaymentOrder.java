package fr.alptis.remuneration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A payment order within a batch (section 5.5).
 *
 * <p>Persistent entity backed by table {@code payment_order}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "payment_order", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_order_e2e", columnNames = {"end_to_end_id"})
})
public class PaymentOrder extends BaseEntity {

    /** The payment batch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    public PaymentBatch batch;

    /** The distributor account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    /** The statement carrying this, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    public Statement statement;

    /** The amount, in euros. */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** The means of payment (transfer or cheque). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 30)
    public PaymentMode paymentMode;

    /** The IBAN of the account. */
    @Column(name = "iban")
    public String iban;

    /** The BIC of the bank. */
    @Column(name = "bic")
    public String bic;

    /** The creditor name. */
    @Column(name = "creditor_name")
    public String creditorName;

    /** The SEPA end-to-end identifier (unique). */
    @Column(name = "end_to_end_id")
    public String endToEndId;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public PaymentOrderStatus status;

    /** The bank rejection reason, if rejected. */
    @Column(name = "rejection_reason")
    public String rejectionReason;

    /** When the order was settled. */
    @Column(name = "settled_at")
    public OffsetDateTime settledAt;
}
