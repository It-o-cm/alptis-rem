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
 * A received measure about an employee (section 5.7).
 *
 * <p>Persistent entity backed by table {@code employee_measure}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "employee_measure")
public class EmployeeMeasure extends BaseEntity {

    /** The sales-force employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    /** The kind of measure. */
    @Enumerated(EnumType.STRING)
    @Column(name = "measure_type", length = 30)
    public MeasureType measureType;

    /** The dimension (market or activity kind). */
    @Column(name = "dimension")
    public String dimension;

    /** Start of the covered period. */
    @Column(name = "period_from")
    public LocalDate periodFrom;

    /** End of the covered period. */
    @Column(name = "period_to")
    public LocalDate periodTo;

    /** The measured value. */
    @Column(name = "value", precision = 18, scale = 6)
    public BigDecimal value;
}
