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
 * A marketed product (section 5.1).
 *
 * <p>Persistent entity backed by table {@code product}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product", columnNames = {"entity_id", "code"})
})
public class Product extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The range. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    public ProductRange range;

    /** The insurer or delegated manager, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurer_id")
    public Insurer insurer;

    /** Whether the product is managed internally or by a delegated manager. */
    @Enumerated(EnumType.STRING)
    @Column(name = "management_mode", length = 30)
    public ManagementMode managementMode;

    /** Reference lag of principle P3, in months (0, 1 or 2). */
    @Column(name = "reference_lag_months")
    public Short referenceLagMonths;

    /** How the forecast line is built at subscription. */
    @Enumerated(EnumType.STRING)
    @Column(name = "forecast_basis", length = 30)
    public ForecastBasis forecastBasis;

    /** Whether the product gives rise to commission (principle P16). */
    @Column(name = "commissionable")
    public Boolean commissionable;

    /** Start of the marketing period. */
    @Column(name = "marketed_from")
    public LocalDate marketedFrom;

    /** End of the marketing period. */
    @Column(name = "marketed_to")
    public LocalDate marketedTo;
}
