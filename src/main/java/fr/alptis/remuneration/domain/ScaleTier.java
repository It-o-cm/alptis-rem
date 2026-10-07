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

/** A non-overlapping tier of a scale (section 5.7). */
@Entity
@Table(name = "scale_tier")
public class ScaleTier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_id")
    public Scale scale;

    @Column(name = "lower_bound", precision = 14, scale = 2)
    public BigDecimal lowerBound;

    @Column(name = "upper_bound", precision = 14, scale = 2)
    public BigDecimal upperBound;

    @Column(name = "rate", precision = 9, scale = 6)
    public BigDecimal rate;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "from_first_unit")
    public Boolean fromFirstUnit;
}
