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

/** A payment batch (section 5.5). */
@Entity
@Table(name = "payment_batch")
public class PaymentBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @Column(name = "batch_reference")
    public String batchReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", length = 30)
    public BatchType batchType;

    @Column(name = "execution_date")
    public LocalDate executionDate;

    @Column(name = "total_amount", precision = 14, scale = 2)
    public BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public BatchStatus status;

    @Column(name = "validated_by")
    public String validatedBy;

    @Column(name = "validated_at")
    public OffsetDateTime validatedAt;

    @Column(name = "sepa_file_ref")
    public String sepaFileRef;

    @Column(name = "sepa_file_hash", length = 64)
    public String sepaFileHash;
}
