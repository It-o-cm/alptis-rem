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
 * A monthly accounting extraction (section 5.5).
 *
 * <p>Persistent entity backed by table {@code cutoff_extract}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "cutoff_extract", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cutoff_extract_period", columnNames = {"period_id"})
})
public class CutoffExtract extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The cut-off date. */
    @Column(name = "cutoff_date")
    public LocalDate cutoffDate;

    /** Reference of the extraction file. */
    @Column(name = "file_ref")
    public String fileRef;

    /** Fingerprint of the extraction file. */
    @Column(name = "file_hash", length = 64)
    public String fileHash;

    /** When the extraction was generated. */
    @Column(name = "generated_at")
    public OffsetDateTime generatedAt;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public CutoffStatus status;
}
