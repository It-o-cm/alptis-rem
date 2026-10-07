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

/** A computed variable-pay result (section 5.7). */
@Entity
@Table(name = "variable_result")
public class VariableResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    public VariablePlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", length = 30)
    public VariableComponent component;

    @Column(name = "achievement_rate", precision = 9, scale = 6)
    public BigDecimal achievementRate;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "adjustment_reason")
    public String adjustmentReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public VariableResultStatus status;

    @Column(name = "validated_by")
    public String validatedBy;

    @Column(name = "validated_at")
    public OffsetDateTime validatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation")
    public String explanation;
}
