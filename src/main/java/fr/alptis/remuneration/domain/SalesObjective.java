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
 * An individual or collective sales objective, versioned (section 5.7).
 *
 * <p>Persistent entity backed by table {@code sales_objective}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "sales_objective")
public class SalesObjective extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The sales-force employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    /** The sales-force job code (2026 grid). */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_code", length = 30)
    public JobCode jobCode;

    /** The kind of objective. */
    @Enumerated(EnumType.STRING)
    @Column(name = "objective_type", length = 30)
    public ObjectiveType objectiveType;

    /** Start of the covered period. */
    @Column(name = "period_from")
    public LocalDate periodFrom;

    /** End of the covered period. */
    @Column(name = "period_to")
    public LocalDate periodTo;

    /** The target value. */
    @Column(name = "target_value", precision = 18, scale = 6)
    public BigDecimal targetValue;

    /** The version number; a version is never overwritten. */
    @Column(name = "version_no")
    public Integer versionNo;
}
