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
 * The advance (discount) position of a coverage (section 5.5).
 *
 * <p>Persistent entity backed by table {@code advance_position}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "advance_position", uniqueConstraints = {
        @UniqueConstraint(name = "uq_advance_position_coverage", columnNames = {"coverage_id"})
})
public class AdvancePosition extends BaseEntity {

    /** The discount agreement; null for borrower and retirement discounts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_agreement_id")
    public AdvanceAgreement advanceAgreement;

    /** The coverage (garantie), the grain of calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public AdvanceStatus status;

    /** When the discount was paid. */
    @Column(name = "paid_at")
    public OffsetDateTime paidAt;

    /** The date by which the discount must be settled. */
    @Column(name = "settlement_due_date")
    public LocalDate settlementDueDate;

    /** Acquisition amount advanced. */
    @Column(name = "advanced_acquisition", precision = 14, scale = 2)
    public BigDecimal advancedAcquisition;

    /** Servicing amount advanced. */
    @Column(name = "advanced_servicing", precision = 14, scale = 2)
    public BigDecimal advancedServicing;

    /** Linear amount advanced. */
    @Column(name = "advanced_linear", precision = 14, scale = 2)
    public BigDecimal advancedLinear;

    /** Acquisition amount offset so far. */
    @Column(name = "offset_acquisition", precision = 14, scale = 2)
    public BigDecimal offsetAcquisition;

    /** Servicing amount offset so far. */
    @Column(name = "offset_servicing", precision = 14, scale = 2)
    public BigDecimal offsetServicing;

    /** Linear amount offset so far. */
    @Column(name = "offset_linear", precision = 14, scale = 2)
    public BigDecimal offsetLinear;

    /** The settlement adjustment, when settled or broken. */
    @Column(name = "adjustment_amount", precision = 14, scale = 2)
    public BigDecimal adjustmentAmount;

    /** When the position was closed. */
    @Column(name = "closed_at")
    public OffsetDateTime closedAt;

    /** Why the position was closed (a fixed code or the breaking event's code). */
    @Column(name = "close_reason")
    public String closeReason;

    /**
     * The total discount due over the position, versed or not (section 6.4). It equals the versed amount
     * for a single versement; for a fractioned convention it is the whole advance, so the apurement gap
     * is measured against the total while only the first part is paid (CAL-10).
     */
    @Column(name = "total_advance_due", precision = 14, scale = 2)
    public BigDecimal totalAdvanceDue;

    /** The due date of the balance versement of a fractioned discount, or null when not fractioned. */
    @Column(name = "balance_due_date")
    public LocalDate balanceDueDate;
}
