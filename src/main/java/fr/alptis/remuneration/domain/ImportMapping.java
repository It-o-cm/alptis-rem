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

/** A versioned file mapping to the pivot format (section 5.7). */
@Entity
@Table(name = "import_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uq_import_mapping", columnNames = {"entity_id", "flow_code", "source_system", "version_no"})
})
public class ImportMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "flow_code")
    public String flowCode;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "version_no")
    public Integer versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition")
    public String definition;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;
}
