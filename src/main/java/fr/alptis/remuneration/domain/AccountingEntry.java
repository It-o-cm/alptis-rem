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
 * An auxiliary accounting entry (section 5.5).
 *
 * <p>Persistent entity backed by table {@code accounting_entry}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "accounting_entry")
public class AccountingEntry extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The accounting event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_event", length = 30)
    public AccountingEvent accountingEvent;

    /** The accounting journal. */
    @Column(name = "journal")
    public String journal;

    /** The entry date. */
    @Column(name = "entry_date")
    public LocalDate entryDate;

    /** The debited account number. */
    @Column(name = "debit_account")
    public String debitAccount;

    /** The credited account number. */
    @Column(name = "credit_account")
    public String creditAccount;

    /** The amount, in euros. */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** The fiscal year. */
    @Column(name = "fiscal_year")
    public Short fiscalYear;

    /** The accounting month. */
    @Column(name = "accounting_month")
    public LocalDate accountingMonth;

    /** The fiscal year the premium is attached to. */
    @Column(name = "premium_fiscal_year")
    public Short premiumFiscalYear;

    /** The commission line that explains the entry, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_line_id")
    public CommissionLine commissionLine;

    /** The payment order, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    public PaymentOrder paymentOrder;

    /** The discount position the entry relates to, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_position_id")
    public AdvancePosition advancePosition;

    /** The cut-off extract that carried the entry (set once). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cutoff_extract_id")
    public CutoffExtract cutoffExtract;
}
