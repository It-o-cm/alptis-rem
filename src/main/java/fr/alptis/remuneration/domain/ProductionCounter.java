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
 * A cumulative counter of a cumulative-rule holder (section 5.7).
 *
 * <p>Persistent entity backed by table {@code production_counter}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "production_counter", uniqueConstraints = {
        @UniqueConstraint(name = "uq_production_counter", columnNames = {"rule_id", "payee_type", "payee_id", "counter_year", "counter_month"})
})
public class ProductionCounter extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The rule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    public Rule rule;

    /** The kind of the cumulative holder (distributor or group). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", length = 30)
    public PayeeType payeeType;

    /** The technical id of the cumulative holder. */
    @Column(name = "payee_id")
    public Long payeeId;

    /** The enrollment, null for a rule without enrollment (retirement complement). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    public Enrollment enrollment;

    /** The counter's year. */
    @Column(name = "counter_year")
    public Short counterYear;

    /** The counter's month. */
    @Column(name = "counter_month")
    public Short counterMonth;

    /** The cumulative basis so far. */
    @Column(name = "cumulative_basis", precision = 14, scale = 2)
    public BigDecimal cumulativeBasis;

    /** The cumulative entitlement so far. */
    @Column(name = "cumulative_entitlement", precision = 14, scale = 2)
    public BigDecimal cumulativeEntitlement;
}
