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
 * A non-overlapping tier of a scale (section 5.7).
 *
 * <p>Persistent entity backed by table {@code scale_tier}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "scale_tier")
public class ScaleTier extends BaseEntity {

    /** The scale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_id")
    public Scale scale;

    /** The tier's lower bound. */
    @Column(name = "lower_bound", precision = 14, scale = 2)
    public BigDecimal lowerBound;

    /** The tier's upper bound. */
    @Column(name = "upper_bound", precision = 14, scale = 2)
    public BigDecimal upperBound;

    /** The tier's rate, if it yields a rate. */
    @Column(name = "rate", precision = 9, scale = 6)
    public BigDecimal rate;

    /** The tier's amount, if it yields an amount. */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** Whether the rate applies from the first unit once the tier is reached. */
    @Column(name = "from_first_unit")
    public Boolean fromFirstUnit;
}
