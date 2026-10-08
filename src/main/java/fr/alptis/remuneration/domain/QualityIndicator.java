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
 * A distributor quality indicator for a period (section 5.1).
 *
 * <p>Persistent entity backed by table {@code quality_indicator}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "quality_indicator", uniqueConstraints = {
        @UniqueConstraint(name = "uq_quality_indicator", columnNames = {"entity_id", "distributor_id", "indicator_type", "range_id", "period_to"})
})
public class QualityIndicator extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The kind of quality indicator. */
    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", length = 30)
    public QualityIndicatorType indicatorType;

    /** The measured range (health, provident); null if global. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    public ProductRange range;

    /** Start of the covered period. */
    @Column(name = "period_from")
    public LocalDate periodFrom;

    /** End of the covered period. */
    @Column(name = "period_to")
    public LocalDate periodTo;

    /** The measured value, as a decimal fraction (0.82 for 82%). */
    @Column(name = "value", precision = 12, scale = 6)
    public BigDecimal value;

    /** The service or system that produced the measure. */
    @Column(name = "source")
    public String source;
}
