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

/** An individual or collective sales objective, versioned (section 5.7). */
@Entity
@Table(name = "sales_objective")
public class SalesObjective extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_code", length = 30)
    public JobCode jobCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "objective_type", length = 30)
    public ObjectiveType objectiveType;

    @Column(name = "period_from")
    public LocalDate periodFrom;

    @Column(name = "period_to")
    public LocalDate periodTo;

    @Column(name = "target_value", precision = 18, scale = 6)
    public BigDecimal targetValue;

    @Column(name = "version_no")
    public Integer versionNo;
}
