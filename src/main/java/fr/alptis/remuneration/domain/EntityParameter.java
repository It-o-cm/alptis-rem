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

/** A dated parameter of an entity (section 5.7). */
@Entity
@Table(name = "entity_parameter", uniqueConstraints = {
        @UniqueConstraint(name = "uq_entity_parameter", columnNames = {"entity_id", "param_key", "valid_from"})
})
public class EntityParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "param_key")
    public String paramKey;

    @Column(name = "param_value")
    public String paramValue;

    @Column(name = "valid_from")
    public LocalDate validFrom;
}
