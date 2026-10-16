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
 * A single file import with its five counters (section 5.7).
 *
 * <p>Persistent entity backed by table {@code import_batch}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "import_batch", uniqueConstraints = {
        @UniqueConstraint(name = "uq_import_batch", columnNames = {"entity_id", "source_system", "file_hash"})
})
public class ImportBatch extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The flow code (object name) of the imported file. */
    @Column(name = "flow_code")
    public String flowCode;

    /** The file mapping applied, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id")
    public ImportMapping mapping;

    /** The source system (CRM or management tool) this record comes from. */
    @Column(name = "source_system")
    public String sourceSystem;

    /** The received file name. */
    @Column(name = "file_name")
    public String fileName;

    /** The file fingerprint; a second deposit of the same file is refused. */
    @Column(name = "file_hash", length = 64)
    public String fileHash;

    /** When the file or object was received. */
    @Column(name = "received_at")
    public OffsetDateTime receivedAt;

    /** Who or what imported the file. */
    @Column(name = "imported_by")
    public String importedBy;

    /** The CSV header line (column names, in file order), kept to reprocess a corrected line (INT-03). */
    @Column(name = "header", length = 4000)
    public String header;

    /** Lines read (= filtered + loaded + rejected + on hold). */
    @Column(name = "lines_read")
    public Integer linesRead;

    /** Lines dropped by the mapping. */
    @Column(name = "lines_filtered")
    public Integer linesFiltered;

    /** Lines integrated. */
    @Column(name = "lines_loaded")
    public Integer linesLoaded;

    /** Lines rejected with a reason code. */
    @Column(name = "lines_rejected")
    public Integer linesRejected;

    /** Lines integrated but held pending a missing reference. */
    @Column(name = "lines_on_hold")
    public Integer linesOnHold;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ImportBatchStatus status;
}
