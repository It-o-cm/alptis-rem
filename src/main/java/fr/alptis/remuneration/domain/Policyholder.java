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

/** An insured person or subscribing company (section 5.2). */
@Entity
@Table(name = "policyholder", uniqueConstraints = {
        @UniqueConstraint(name = "uq_policyholder", columnNames = {"entity_id", "source_system", "external_id"})
})
public class Policyholder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "external_id")
    public String externalId;

    @Column(name = "last_name")
    public String lastName;

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "birth_date")
    public LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", length = 30)
    public PersonType personType;
}
