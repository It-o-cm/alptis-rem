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
 * A payment condition of a rule version (section 5.4).
 *
 * <p>Persistent entity backed by table {@code rule_condition}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "rule_condition")
public class RuleCondition extends BaseEntity {

    /** The rule version that produced this line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    public RuleVersion ruleVersion;

    /** The quality indicator the condition tests, if any. */
    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", length = 30)
    public QualityIndicatorType indicatorType;

    /** A free counter code defined by the rule, if the condition tests a counter. */
    @Column(name = "counter_code")
    public String counterCode;

    /** The comparison operator. */
    @Enumerated(EnumType.STRING)
    @Column(name = "operator", length = 30)
    public ConditionOperator operator;

    /** The threshold compared against. */
    @Column(name = "threshold", precision = 14, scale = 6)
    public BigDecimal threshold;

    /** Over which period the indicator or counter is measured. */
    @Column(name = "measurement_period")
    public String measurementPeriod;

    /** What happens to the month when the condition is not met. */
    @Enumerated(EnumType.STRING)
    @Column(name = "on_failure", length = 30)
    public ConditionFailureMode onFailure;
}
