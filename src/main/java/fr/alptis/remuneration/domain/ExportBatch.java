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
 * The journal of an emitted, scheduled or on-demand file (section 5.7).
 *
 * <p>Persistent entity backed by table {@code export_batch}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "export_batch")
public class ExportBatch extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The flow code of the emitted file. */
    @Column(name = "flow_code")
    public String flowCode;

    /** The emitted file name. */
    @Column(name = "file_name")
    public String fileName;

    /** The emitted file fingerprint. */
    @Column(name = "file_hash", length = 64)
    public String fileHash;

    /** When the file was generated. */
    @Column(name = "generated_at")
    public OffsetDateTime generatedAt;

    /** Who or what generated the file. */
    @Column(name = "generated_by")
    public String generatedBy;

    /** Number of lines written. */
    @Column(name = "lines_written")
    public Integer linesWritten;

    /** Total amount of the file. */
    @Column(name = "total_amount", precision = 14, scale = 2)
    public BigDecimal totalAmount;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ExportBatchStatus status;
}
