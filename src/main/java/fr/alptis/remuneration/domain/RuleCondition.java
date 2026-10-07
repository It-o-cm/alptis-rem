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

/** A payment condition of a rule version (section 5.4). */
@Entity
@Table(name = "rule_condition")
public class RuleCondition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    public RuleVersion ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", length = 30)
    public QualityIndicatorType indicatorType;

    @Column(name = "counter_code")
    public String counterCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", length = 30)
    public ConditionOperator operator;

    @Column(name = "threshold", precision = 14, scale = 6)
    public BigDecimal threshold;

    @Column(name = "measurement_period")
    public String measurementPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "on_failure", length = 30)
    public ConditionFailureMode onFailure;
}
