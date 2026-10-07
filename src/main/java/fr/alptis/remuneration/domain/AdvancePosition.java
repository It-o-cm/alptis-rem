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

/** The advance (discount) position of a coverage (section 5.5). */
@Entity
@Table(name = "advance_position", uniqueConstraints = {
        @UniqueConstraint(name = "uq_advance_position_coverage", columnNames = {"coverage_id"})
})
public class AdvancePosition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_agreement_id")
    public AdvanceAgreement advanceAgreement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public AdvanceStatus status;

    @Column(name = "paid_at")
    public OffsetDateTime paidAt;

    @Column(name = "settlement_due_date")
    public LocalDate settlementDueDate;

    @Column(name = "advanced_acquisition", precision = 14, scale = 2)
    public BigDecimal advancedAcquisition;

    @Column(name = "advanced_servicing", precision = 14, scale = 2)
    public BigDecimal advancedServicing;

    @Column(name = "advanced_linear", precision = 14, scale = 2)
    public BigDecimal advancedLinear;

    @Column(name = "offset_acquisition", precision = 14, scale = 2)
    public BigDecimal offsetAcquisition;

    @Column(name = "offset_servicing", precision = 14, scale = 2)
    public BigDecimal offsetServicing;

    @Column(name = "offset_linear", precision = 14, scale = 2)
    public BigDecimal offsetLinear;

    @Column(name = "adjustment_amount", precision = 14, scale = 2)
    public BigDecimal adjustmentAmount;

    @Column(name = "closed_at")
    public OffsetDateTime closedAt;

    @Column(name = "close_reason")
    public String closeReason;
}
