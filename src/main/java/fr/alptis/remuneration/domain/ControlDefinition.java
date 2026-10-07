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

/** A versioned control definition (section 5.7). */
@Entity
@Table(name = "control_definition", uniqueConstraints = {
        @UniqueConstraint(name = "uq_control_definition", columnNames = {"entity_id", "code", "valid_from"})
})
public class ControlDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "code")
    public String code;

    @Column(name = "label")
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "family", length = 30)
    public ControlFamily family;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30)
    public ControlSeverity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    @Column(name = "active")
    public Boolean active;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;
}
