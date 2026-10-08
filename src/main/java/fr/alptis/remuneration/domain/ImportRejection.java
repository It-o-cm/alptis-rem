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
 * A line rejected during an import (section 5.7).
 *
 * <p>Persistent entity backed by table {@code import_rejection}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "import_rejection")
public class ImportRejection extends BaseEntity {

    /** The import batch this line came from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    public ImportBatch importBatch;

    /** The 1-based line number in the file. */
    @Column(name = "line_number")
    public Integer lineNumber;

    /** The raw rejected line. */
    @Column(name = "raw_line", length = 10000)
    public String rawLine;

    /** The rejection reason code. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    public RejectionReasonCode reasonCode;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ImportRejectionStatus status;

    /** When the rejection was resolved. */
    @Column(name = "resolved_at")
    public OffsetDateTime resolvedAt;

    /** Who resolved the rejection. */
    @Column(name = "resolved_by")
    public String resolvedBy;
}
