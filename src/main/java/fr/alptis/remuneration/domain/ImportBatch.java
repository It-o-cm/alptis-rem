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

/** A single file import with its five counters (section 5.7). */
@Entity
@Table(name = "import_batch", uniqueConstraints = {
        @UniqueConstraint(name = "uq_import_batch", columnNames = {"entity_id", "source_system", "file_hash"})
})
public class ImportBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "flow_code")
    public String flowCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id")
    public ImportMapping mapping;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "file_name")
    public String fileName;

    @Column(name = "file_hash", length = 64)
    public String fileHash;

    @Column(name = "received_at")
    public OffsetDateTime receivedAt;

    @Column(name = "imported_by")
    public String importedBy;

    @Column(name = "lines_read")
    public Integer linesRead;

    @Column(name = "lines_filtered")
    public Integer linesFiltered;

    @Column(name = "lines_loaded")
    public Integer linesLoaded;

    @Column(name = "lines_rejected")
    public Integer linesRejected;

    @Column(name = "lines_on_hold")
    public Integer linesOnHold;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ImportBatchStatus status;
}
