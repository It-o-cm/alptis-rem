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

/** A received measure about an employee (section 5.7). */
@Entity
@Table(name = "employee_measure")
public class EmployeeMeasure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "measure_type", length = 30)
    public MeasureType measureType;

    @Column(name = "dimension")
    public String dimension;

    @Column(name = "period_from")
    public LocalDate periodFrom;

    @Column(name = "period_to")
    public LocalDate periodTo;

    @Column(name = "value", precision = 18, scale = 6)
    public BigDecimal value;
}
