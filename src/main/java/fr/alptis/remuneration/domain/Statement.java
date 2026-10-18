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
 * A statement detailing a payee's commissions over a period (section 5.5).
 *
 * <p>Persistent entity backed by table {@code statement}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "statement", uniqueConstraints = {
        @UniqueConstraint(name = "uq_statement", columnNames = {"statement_number", "version_no"})
})
public class Statement extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The distributor account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    /** The kind of batch the statement belongs to. */
    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", length = 30)
    public BatchType batchType;

    /** The statement number (AAAA-MM + 6-digit order number). */
    @Column(name = "statement_number")
    public String statementNumber;

    /** The version number; a version is never overwritten. */
    @Column(name = "version_no")
    public Integer versionNo;

    /** The beneficiary level of the statement. */
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 30)
    public StatementLevel level;

    /** The balance carried over from previous periods. */
    @Column(name = "carried_balance", precision = 14, scale = 2)
    public BigDecimal carriedBalance;

    /** The period total. */
    @Column(name = "period_total", precision = 14, scale = 2)
    public BigDecimal periodTotal;

    /** The net amount to pay. */
    @Column(name = "net_amount", precision = 14, scale = 2)
    public BigDecimal netAmount;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public StatementStatus status;

    /** The statement this one corrects, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_statement_id")
    public Statement replacesStatement;

    /** Reference of the archived PDF. */
    @Column(name = "pdf_ref")
    public String pdfRef;

    /** Fingerprint of the archived PDF. */
    @Column(name = "pdf_hash", length = 64)
    public String pdfHash;

    /** Reference of the spreadsheet rendering. */
    @Column(name = "sheet_ref")
    public String sheetRef;

    /** When the statement was published. */
    @Column(name = "published_at")
    public OffsetDateTime publishedAt;

    /** The publication date: the day after the payment execution (section 7.1, BRD-04). */
    @Column(name = "publication_date")
    public java.time.LocalDate publicationDate;

    /** The reference of the metadata deposited for the GED at publication (BRD-04, BRD-10). */
    @Column(name = "ged_reference")
    public String gedReference;
}
