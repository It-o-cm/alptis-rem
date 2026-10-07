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

/** A state transition of a period (section 5.7). */
@Entity
@Table(name = "period_transition")
public class PeriodTransition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    public PeriodState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 30)
    public PeriodState toState;

    @Column(name = "occurred_at")
    public OffsetDateTime occurredAt;

    @Column(name = "user_id")
    public String userId;

    @Column(name = "reason")
    public String reason;
}
