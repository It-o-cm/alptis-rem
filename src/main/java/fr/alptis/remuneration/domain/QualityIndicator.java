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

/** A distributor quality indicator for a period (section 5.1). */
@Entity
@Table(name = "quality_indicator", uniqueConstraints = {
        @UniqueConstraint(name = "uq_quality_indicator", columnNames = {"entity_id", "distributor_id", "indicator_type", "range_id", "period_to"})
})
public class QualityIndicator extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", length = 30)
    public QualityIndicatorType indicatorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    public ProductRange range;

    @Column(name = "period_from")
    public LocalDate periodFrom;

    @Column(name = "period_to")
    public LocalDate periodTo;

    @Column(name = "value", precision = 12, scale = 6)
    public BigDecimal value;

    @Column(name = "source")
    public String source;
}
