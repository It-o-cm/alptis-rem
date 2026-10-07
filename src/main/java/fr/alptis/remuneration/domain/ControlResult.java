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

/** The result of a control on a period (section 5.5). */
@Entity
@Table(name = "control_result")
public class ControlResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "control_id")
    public ControlDefinition control;

    @Column(name = "object_type")
    public String objectType;

    @Column(name = "object_id")
    public Long objectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30)
    public ControlSeverity severity;

    @Column(name = "observed_value")
    public String observedValue;

    @Column(name = "expected_value")
    public String expectedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 30)
    public ControlDecision decision;

    @Column(name = "decided_by")
    public String decidedBy;

    @Column(name = "decided_at")
    public OffsetDateTime decidedAt;

    @Column(name = "justification")
    public String justification;
}
