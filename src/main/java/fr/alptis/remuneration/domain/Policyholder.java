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
 * An insured person or subscribing company (section 5.2).
 *
 * <p>Persistent entity backed by table {@code policyholder}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "policyholder", uniqueConstraints = {
        @UniqueConstraint(name = "uq_policyholder", columnNames = {"entity_id", "source_system", "external_id"})
})
public class Policyholder extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The source system (CRM or management tool) this record comes from. */
    @Column(name = "source_system")
    public String sourceSystem;

    /** The identifier of the object in the source system. */
    @Column(name = "external_id")
    public String externalId;

    /** Last name or company name. */
    @Column(name = "last_name")
    public String lastName;

    /** First name. */
    @Column(name = "first_name")
    public String firstName;

    /** Birth date. */
    @Column(name = "birth_date")
    public LocalDate birthDate;

    /** Whether the policyholder is a natural or legal person. */
    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", length = 30)
    public PersonType personType;
}
