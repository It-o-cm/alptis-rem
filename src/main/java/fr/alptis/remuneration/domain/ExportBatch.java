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

/** The journal of an emitted, scheduled or on-demand file (section 5.7). */
@Entity
@Table(name = "export_batch")
public class ExportBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "flow_code")
    public String flowCode;

    @Column(name = "file_name")
    public String fileName;

    @Column(name = "file_hash", length = 64)
    public String fileHash;

    @Column(name = "generated_at")
    public OffsetDateTime generatedAt;

    @Column(name = "generated_by")
    public String generatedBy;

    @Column(name = "lines_written")
    public Integer linesWritten;

    @Column(name = "total_amount", precision = 14, scale = 2)
    public BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ExportBatchStatus status;
}
