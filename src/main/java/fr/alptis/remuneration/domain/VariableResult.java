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
 * A computed variable-pay result (section 5.7).
 *
 * <p>Persistent entity backed by table {@code variable_result}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "variable_result")
public class VariableResult extends BaseEntity {

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The sales-force employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    /** The commission plan. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    public VariablePlan plan;

    /** The variable-pay component. */
    @Enumerated(EnumType.STRING)
    @Column(name = "component", length = 30)
    public VariableComponent component;

    /** The achievement rate, as a fraction. */
    @Column(name = "achievement_rate", precision = 9, scale = 6)
    public BigDecimal achievementRate;

    /** The amount to pay. */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** The reason of a manual adjustment component. */
    @Column(name = "adjustment_reason")
    public String adjustmentReason;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public VariableResultStatus status;

    /** Who validated the result. */
    @Column(name = "validated_by")
    public String validatedBy;

    /** When the result was validated. */
    @Column(name = "validated_at")
    public OffsetDateTime validatedAt;

    /** The inputs of the computation (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation")
    public String explanation;
}
