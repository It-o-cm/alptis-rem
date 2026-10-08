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
 * A sales-force employee (section 5.7).
 *
 * <p>Persistent entity backed by table {@code employee}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "employee")
public class Employee extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The identifier of the object in the source system. */
    @Column(name = "external_id")
    public String externalId;

    /** Last name. */
    @Column(name = "last_name")
    public String lastName;

    /** First name. */
    @Column(name = "first_name")
    public String firstName;

    /** The sales-force job code (2026 grid). */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_code", length = 30)
    public JobCode jobCode;

    /** The employee's team. */
    @Column(name = "team")
    public String team;

    /** The employee's region. */
    @Column(name = "region")
    public String region;

    /** The employee's manager. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    public Employee manager;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
