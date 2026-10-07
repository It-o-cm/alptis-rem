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

/** A manual adjustment (section 5.5). */
@Entity
@Table(name = "adjustment")
public class Adjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", length = 30)
    public AdjustmentType adjustmentType;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_line_id")
    public CommissionLine correctedLine;

    @Column(name = "reason")
    public String reason;

    @Column(name = "attachment_ref")
    public String attachmentRef;

    @Column(name = "auto_reverse")
    public Boolean autoReverse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "repayment_schedule")
    public String repaymentSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterpart_account_id")
    public DistributorAccount counterpartAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public AdjustmentStatus status;

    @Column(name = "approved_by")
    public String approvedBy;

    @Column(name = "approved_at")
    public OffsetDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_line_id")
    public CommissionLine resultingLine;
}
