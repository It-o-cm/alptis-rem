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

/** A marketed product (section 5.1). */
@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product", columnNames = {"entity_id", "code"})
})
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "code")
    public String code;

    @Column(name = "label")
    public String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    public ProductRange range;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurer_id")
    public Insurer insurer;

    @Enumerated(EnumType.STRING)
    @Column(name = "management_mode", length = 30)
    public ManagementMode managementMode;

    @Column(name = "reference_lag_months")
    public Short referenceLagMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "forecast_basis", length = 30)
    public ForecastBasis forecastBasis;

    @Column(name = "commissionable")
    public Boolean commissionable;

    @Column(name = "marketed_from")
    public LocalDate marketedFrom;

    @Column(name = "marketed_to")
    public LocalDate marketedTo;
}
