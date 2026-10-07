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

/** A variable-pay plan per job code (section 5.7). */
@Entity
@Table(name = "variable_plan")
public class VariablePlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_code", length = 30)
    public JobCode jobCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", length = 30)
    public VariableComponent component;

    @Column(name = "envelope_amount", precision = 14, scale = 2)
    public BigDecimal envelopeAmount;

    @Column(name = "instalment_amount", precision = 14, scale = 2)
    public BigDecimal instalmentAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;
}
