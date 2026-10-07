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

/** A monthly accounting extraction (section 5.5). */
@Entity
@Table(name = "cutoff_extract", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cutoff_extract_period", columnNames = {"period_id"})
})
public class CutoffExtract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @Column(name = "cutoff_date")
    public LocalDate cutoffDate;

    @Column(name = "file_ref")
    public String fileRef;

    @Column(name = "file_hash", length = 64)
    public String fileHash;

    @Column(name = "generated_at")
    public OffsetDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public CutoffStatus status;
}
