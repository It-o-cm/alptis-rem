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
 * A scale of a rule version (section 5.7).
 *
 * <p>Persistent entity backed by table {@code scale}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "scale")
public class Scale extends BaseEntity {

    /** The rule version that produced this line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    public RuleVersion ruleVersion;

    /** Whether the scale is read marginally or on the whole measure. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 30)
    public ScaleMode mode;

    /** What the scale is measured against (amount or count). */
    @Enumerated(EnumType.STRING)
    @Column(name = "measure", length = 30)
    public ScaleMeasure measure;

    /** What a tier yields (rate or amount). */
    @Enumerated(EnumType.STRING)
    @Column(name = "output", length = 30)
    public ScaleOutput output;
}
