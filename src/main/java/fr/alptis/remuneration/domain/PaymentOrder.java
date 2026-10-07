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

/** A payment order within a batch (section 5.5). */
@Entity
@Table(name = "payment_order", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_order_e2e", columnNames = {"end_to_end_id"})
})
public class PaymentOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    public PaymentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    public Statement statement;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 30)
    public PaymentMode paymentMode;

    @Column(name = "iban")
    public String iban;

    @Column(name = "bic")
    public String bic;

    @Column(name = "creditor_name")
    public String creditorName;

    @Column(name = "end_to_end_id")
    public String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public PaymentOrderStatus status;

    @Column(name = "rejection_reason")
    public String rejectionReason;

    @Column(name = "settled_at")
    public OffsetDateTime settledAt;
}
