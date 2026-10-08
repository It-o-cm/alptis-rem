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
 * The split of a surcommission line across contracts (section 5.7).
 *
 * <p>Persistent entity backed by table {@code surcommission_allocation}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "surcommission_allocation")
public class SurcommissionAllocation extends BaseEntity {

    /** The commission line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_line_id")
    public CommissionLine commissionLine;

    /** The membership contract (adhésion). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    /** The membership's annualised premium. */
    @Column(name = "annualised_premium", precision = 14, scale = 2)
    public BigDecimal annualisedPremium;

    /** The part of the surcommission allocated to this membership. */
    @Column(name = "allocated_amount", precision = 14, scale = 2)
    public BigDecimal allocatedAmount;
}
