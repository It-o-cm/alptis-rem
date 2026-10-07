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

/** A mapping of an internal object to an external code of a source system (section 5.1). */
@Entity
@Table(name = "external_code", uniqueConstraints = {
        @UniqueConstraint(name = "uq_external_code", columnNames = {"object_type", "source_system", "external_code"})
})
public class ExternalCode extends BaseEntity {

    @Column(name = "object_type")
    public String objectType;

    @Column(name = "object_id")
    public Long objectId;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "external_code")
    public String externalCode;
}
