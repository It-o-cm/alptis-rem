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
 * A discount (advance) agreement (section 5.4).
 *
 * <p>Persistent entity backed by table {@code advance_agreement}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "advance_agreement")
public class AdvanceAgreement extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The agreement that founds this, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    public Agreement agreement;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;

    /** How many days before the effect date the discount may be calculated (H-08). */
    @Column(name = "anticipation_days")
    public Integer anticipationDays;

    /** Maximum number of discounted memberships, if capped (section 6.4). */
    @Column(name = "max_contracts")
    public Integer maxContracts;

    /** The unpaid rank that breaks the discount (default 2, H-07). */
    @Column(name = "unpaid_rank_threshold")
    public Short unpaidRankThreshold;

    /** How the discount is split between beneficiaries (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "split")
    public String split;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public AdvanceAgreementStatus status;
}
