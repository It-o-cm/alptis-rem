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

/** A document template (section 5.7). */
@Entity
@Table(name = "document_template")
public class DocumentTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 30)
    public DocumentType documentType;

    @Column(name = "version_no")
    public Integer versionNo;

    @Column(name = "content", length = 10000)
    public String content;

    @Column(name = "valid_from")
    public LocalDate validFrom;
}
