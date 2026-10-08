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
 * A manual adjustment (section 5.5).
 *
 * <p>Persistent entity backed by table {@code adjustment}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "adjustment")
public class Adjustment extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The type of adjustment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", length = 30)
    public AdjustmentType adjustmentType;

    /** The amount, in euros. */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** The distributor account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    /** The coverage (garantie), the grain of calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    /** The line corrected by this adjustment, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_line_id")
    public CommissionLine correctedLine;

    /** Free-text reason. */
    @Column(name = "reason")
    public String reason;

    /** Reference of an attached document. */
    @Column(name = "attachment_ref")
    public String attachmentRef;

    /** Whether the adjustment is automatically reversed next period. */
    @Column(name = "auto_reverse")
    public Boolean autoReverse;

    /** The repayment schedule of an exceptional advance (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "repayment_schedule")
    public String repaymentSchedule;

    /** The second account of a transfer adjustment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterpart_account_id")
    public DistributorAccount counterpartAccount;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public AdjustmentStatus status;

    /** Who approved the adjustment (must differ from its creator). */
    @Column(name = "approved_by")
    public String approvedBy;

    /** When the adjustment was approved. */
    @Column(name = "approved_at")
    public OffsetDateTime approvedAt;

    /** The commission line produced when the adjustment is applied. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_line_id")
    public CommissionLine resultingLine;
}
