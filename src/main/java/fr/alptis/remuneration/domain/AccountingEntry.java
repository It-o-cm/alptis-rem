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

/** An auxiliary accounting entry (section 5.5). */
@Entity
@Table(name = "accounting_entry")
public class AccountingEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_event", length = 30)
    public AccountingEvent accountingEvent;

    @Column(name = "journal")
    public String journal;

    @Column(name = "entry_date")
    public LocalDate entryDate;

    @Column(name = "debit_account")
    public String debitAccount;

    @Column(name = "credit_account")
    public String creditAccount;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(name = "fiscal_year")
    public Short fiscalYear;

    @Column(name = "accounting_month")
    public LocalDate accountingMonth;

    @Column(name = "premium_fiscal_year")
    public Short premiumFiscalYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_line_id")
    public CommissionLine commissionLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    public PaymentOrder paymentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_position_id")
    public AdvancePosition advancePosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cutoff_extract_id")
    public CutoffExtract cutoffExtract;
}
