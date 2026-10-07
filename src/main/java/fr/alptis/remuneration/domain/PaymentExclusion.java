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

/** A payee excluded from a payment batch with its reason (section 5.7). */
@Entity
@Table(name = "payment_exclusion")
public class PaymentExclusion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    public PaymentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 30)
    public PaymentExclusionReason reason;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;
}
