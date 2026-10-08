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
 * A coverage line of a contract; the grain of calculation (section 5.2).
 *
 * <p>Persistent entity backed by table {@code coverage}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "coverage", uniqueConstraints = {
        @UniqueConstraint(name = "uq_coverage", columnNames = {"contract_id", "coverage_code"})
})
public class Coverage extends BaseEntity {

    /** The membership contract (adhésion). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    /** The coverage code within the contract. */
    @Column(name = "coverage_code")
    public String coverageCode;

    /** The product option (level, formula, fund). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    public ProductOption option;

    /** The effect date. */
    @Column(name = "effect_date")
    public LocalDate effectDate;

    /** The end date; null while in force. */
    @Column(name = "end_date")
    public LocalDate endDate;

    /** The reason the object ended. */
    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    public EndReason endReason;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ContractStatus status;

    /** The annualised premium, hors taxes. */
    @Column(name = "annual_premium", precision = 14, scale = 2)
    public BigDecimal annualPremium;

    /** The tariff band. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_type", length = 30)
    public TariffType tariffType;

    /** The staff college for a collective coverage; null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "staff_category", length = 30)
    public StaffCategory staffCategory;

    /** Whether the coverage is a single-premium one. */
    @Column(name = "single_premium")
    public Boolean singlePremium;

    /** Whether the coverage gives rise to commission. */
    @Column(name = "commissionable")
    public Boolean commissionable;
}
