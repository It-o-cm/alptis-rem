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
 * A distributor-account movement other than a commission line (section 5.5).
 *
 * <p>Persistent entity backed by table {@code account_entry}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "account_entry")
public class AccountEntry extends BaseEntity {

    /** The distributor account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The movement date. */
    @Column(name = "entry_date")
    public LocalDate entryDate;

    /** The kind of movement. */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 30)
    public AccountEntryType entryType;

    /** The amount, in euros. */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** The payment order, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    public PaymentOrder paymentOrder;
}
