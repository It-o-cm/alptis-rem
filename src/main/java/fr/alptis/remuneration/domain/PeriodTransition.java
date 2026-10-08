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
 * A state transition of a period (section 5.7).
 *
 * <p>Persistent entity backed by table {@code period_transition}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "period_transition")
public class PeriodTransition extends BaseEntity {

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The state before the transition. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    public PeriodState fromState;

    /** The state after the transition. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 30)
    public PeriodState toState;

    /** When the transition occurred. */
    @Column(name = "occurred_at")
    public OffsetDateTime occurredAt;

    /** Who made the transition. */
    @Column(name = "user_id")
    public String userId;

    /** Free-text reason. */
    @Column(name = "reason")
    public String reason;
}
