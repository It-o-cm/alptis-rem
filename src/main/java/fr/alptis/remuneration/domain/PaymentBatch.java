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
 * A payment batch (section 5.5).
 *
 * <p>Persistent entity backed by table {@code payment_batch}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "payment_batch")
public class PaymentBatch extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The batch reference. */
    @Column(name = "batch_reference")
    public String batchReference;

    /** The kind of batch (cycle, savings, off-cycle, cheque). */
    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", length = 30)
    public BatchType batchType;

    /** The execution date. */
    @Column(name = "execution_date")
    public LocalDate executionDate;

    /** The total amount of the batch. */
    @Column(name = "total_amount", precision = 14, scale = 2)
    public BigDecimal totalAmount;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public BatchStatus status;

    /** Who validated the batch. */
    @Column(name = "validated_by")
    public String validatedBy;

    /** When the batch was validated. */
    @Column(name = "validated_at")
    public OffsetDateTime validatedAt;

    /** Reference of the SEPA file. */
    @Column(name = "sepa_file_ref")
    public String sepaFileRef;

    /** Fingerprint of the SEPA file. */
    @Column(name = "sepa_file_hash", length = 64)
    public String sepaFileHash;
}
