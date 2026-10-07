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

/** A cumulative counter of a cumulative-rule holder (section 5.7). */
@Entity
@Table(name = "production_counter", uniqueConstraints = {
        @UniqueConstraint(name = "uq_production_counter", columnNames = {"rule_id", "payee_type", "payee_id", "counter_year", "counter_month"})
})
public class ProductionCounter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    public Rule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", length = 30)
    public PayeeType payeeType;

    @Column(name = "payee_id")
    public Long payeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    public Enrollment enrollment;

    @Column(name = "counter_year")
    public Short counterYear;

    @Column(name = "counter_month")
    public Short counterMonth;

    @Column(name = "cumulative_basis", precision = 14, scale = 2)
    public BigDecimal cumulativeBasis;

    @Column(name = "cumulative_entitlement", precision = 14, scale = 2)
    public BigDecimal cumulativeEntitlement;
}
