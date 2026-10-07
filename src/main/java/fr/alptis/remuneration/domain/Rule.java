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

/** A remuneration rule naming a calculation family (section 5.4). */
@Entity
@Table(name = "rule", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rule", columnNames = {"plan_id", "code"})
})
public class Rule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    public CommissionPlan plan;

    @Column(name = "code")
    public String code;

    @Column(name = "label")
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "family", length = 30)
    public RuleFamily family;
}
