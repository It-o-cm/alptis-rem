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
 * Bank details of a payee, historised (section 5.1).
 *
 * <p>Persistent entity backed by table {@code bank_account}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "bank_account")
public class BankAccount extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The kind of the account holder (production code, distributor or group). */
    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", length = 30)
    public PayeeType holderType;

    /** The technical id of the account holder. */
    @Column(name = "holder_id")
    public Long holderId;

    /** The IBAN of the account. */
    @Column(name = "iban")
    public String iban;

    /** The BIC of the bank. */
    @Column(name = "bic")
    public String bic;

    /** The account holder's name, reproduced on the transfer. */
    @Column(name = "holder_name")
    public String holderName;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
