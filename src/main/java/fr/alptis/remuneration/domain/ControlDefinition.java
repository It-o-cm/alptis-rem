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
 * A versioned control definition (section 5.7).
 *
 * <p>Persistent entity backed by table {@code control_definition}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "control_definition", uniqueConstraints = {
        @UniqueConstraint(name = "uq_control_definition", columnNames = {"entity_id", "code", "version_no"})
})
public class ControlDefinition extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The control family. */
    @Enumerated(EnumType.STRING)
    @Column(name = "family", length = 30)
    public ControlFamily family;

    /** The severity produced by the control. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30)
    public ControlSeverity severity;

    /** The control parameters, e.g. its threshold (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    /** Whether the control is active. */
    @Column(name = "active")
    public Boolean active;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;

    /** The version number of this control among its code's versions (CTL-04); null means version one. */
    @Column(name = "version_no")
    public Integer versionNo;

    /**
     * The version number, treating a null as the first version.
     *
     * @return the version number, at least one
     */
    public int version() {
        return versionNo == null ? 1 : versionNo;
    }
}
