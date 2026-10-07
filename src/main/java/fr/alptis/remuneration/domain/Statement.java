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

/** A statement detailing a payee's commissions over a period (section 5.5). */
@Entity
@Table(name = "statement", uniqueConstraints = {
        @UniqueConstraint(name = "uq_statement", columnNames = {"statement_number", "version_no"})
})
public class Statement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", length = 30)
    public BatchType batchType;

    @Column(name = "statement_number")
    public String statementNumber;

    @Column(name = "version_no")
    public Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 30)
    public StatementLevel level;

    @Column(name = "carried_balance", precision = 14, scale = 2)
    public BigDecimal carriedBalance;

    @Column(name = "period_total", precision = 14, scale = 2)
    public BigDecimal periodTotal;

    @Column(name = "net_amount", precision = 14, scale = 2)
    public BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public StatementStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_statement_id")
    public Statement replacesStatement;

    @Column(name = "pdf_ref")
    public String pdfRef;

    @Column(name = "pdf_hash", length = 64)
    public String pdfHash;

    @Column(name = "sheet_ref")
    public String sheetRef;

    @Column(name = "published_at")
    public OffsetDateTime publishedAt;
}
