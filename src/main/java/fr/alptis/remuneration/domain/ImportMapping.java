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
 * A versioned file mapping to the pivot format (section 5.7).
 *
 * <p>Persistent entity backed by table {@code import_mapping}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "import_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uq_import_mapping", columnNames = {"entity_id", "flow_code", "source_system", "version_no"})
})
public class ImportMapping extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The flow code (object name) the mapping applies to. */
    @Column(name = "flow_code")
    public String flowCode;

    /** The source system (CRM or management tool) this record comes from. */
    @Column(name = "source_system")
    public String sourceSystem;

    /** The version number; a version is never overwritten. */
    @Column(name = "version_no")
    public Integer versionNo;

    /** The mapping definition: column correspondence, defaults, transcoding, expressions (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition")
    public String definition;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
