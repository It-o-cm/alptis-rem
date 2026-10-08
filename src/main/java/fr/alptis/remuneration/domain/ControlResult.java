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
 * The result of a control on a period (section 5.5).
 *
 * <p>Persistent entity backed by table {@code control_result}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "control_result")
public class ControlResult extends BaseEntity {

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The control that produced this result. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "control_id")
    public ControlDefinition control;

    /** The type of the controlled object. */
    @Column(name = "object_type")
    public String objectType;

    /** The id of the controlled object. */
    @Column(name = "object_id")
    public Long objectId;

    /** The severity of this result. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30)
    public ControlSeverity severity;

    /** The observed value. */
    @Column(name = "observed_value")
    public String observedValue;

    /** The expected value. */
    @Column(name = "expected_value")
    public String expectedValue;

    /** The decision taken. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 30)
    public ControlDecision decision;

    /** Who decided. */
    @Column(name = "decided_by")
    public String decidedBy;

    /** When the decision was taken. */
    @Column(name = "decided_at")
    public OffsetDateTime decidedAt;

    /** The justification of the decision. */
    @Column(name = "justification")
    public String justification;
}
