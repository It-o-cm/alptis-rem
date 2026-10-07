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

/** Bank details of a payee, historised (section 5.1). */
@Entity
@Table(name = "bank_account")
public class BankAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", length = 30)
    public PayeeType holderType;

    @Column(name = "holder_id")
    public Long holderId;

    @Column(name = "iban")
    public String iban;

    @Column(name = "bic")
    public String bic;

    @Column(name = "holder_name")
    public String holderName;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;
}
