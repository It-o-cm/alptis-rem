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
 * A coherent set of rules (section 5.4).
 *
 * <p>Persistent entity backed by table {@code commission_plan}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "commission_plan", uniqueConstraints = {
        @UniqueConstraint(name = "uq_commission_plan", columnNames = {"entity_id", "code"})
})
public class CommissionPlan extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** Whether the plan's commissions are received or owed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 30)
    public Direction direction;

    /** The vintage (millésime) year. */
    @Column(name = "vintage")
    public Short vintage;
}
