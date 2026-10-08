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
 * A remuneration rule naming a calculation family (section 5.4).
 *
 * <p>Persistent entity backed by table {@code rule}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "rule", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rule", columnNames = {"plan_id", "code"})
})
public class Rule extends BaseEntity {

    /** The commission plan. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    public CommissionPlan plan;

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The calculation family of the rule. */
    @Enumerated(EnumType.STRING)
    @Column(name = "family", length = 30)
    public RuleFamily family;
}
