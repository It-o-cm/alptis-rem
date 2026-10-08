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
 * A document template (section 5.7).
 *
 * <p>Persistent entity backed by table {@code document_template}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "document_template")
public class DocumentTemplate extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The type of document the template renders. */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 30)
    public DocumentType documentType;

    /** The version number; a version is never overwritten. */
    @Column(name = "version_no")
    public Integer versionNo;

    /** The template content. */
    @Column(name = "content", length = 10000)
    public String content;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;
}
