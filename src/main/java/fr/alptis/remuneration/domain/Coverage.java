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

/** A coverage line of a contract; the grain of calculation (section 5.2). */
@Entity
@Table(name = "coverage", uniqueConstraints = {
        @UniqueConstraint(name = "uq_coverage", columnNames = {"contract_id", "coverage_code"})
})
public class Coverage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    @Column(name = "coverage_code")
    public String coverageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    public ProductOption option;

    @Column(name = "effect_date")
    public LocalDate effectDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    public EndReason endReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ContractStatus status;

    @Column(name = "annual_premium", precision = 14, scale = 2)
    public BigDecimal annualPremium;

    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_type", length = 30)
    public TariffType tariffType;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_category", length = 30)
    public StaffCategory staffCategory;

    @Column(name = "single_premium")
    public Boolean singlePremium;

    @Column(name = "commissionable")
    public Boolean commissionable;
}
