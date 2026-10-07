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

/** A line rejected during an import (section 5.7). */
@Entity
@Table(name = "import_rejection")
public class ImportRejection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    public ImportBatch importBatch;

    @Column(name = "line_number")
    public Integer lineNumber;

    @Column(name = "raw_line", length = 10000)
    public String rawLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    public RejectionReasonCode reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ImportRejectionStatus status;

    @Column(name = "resolved_at")
    public OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    public String resolvedBy;
}
