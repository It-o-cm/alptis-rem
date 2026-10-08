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
 * A distributor (section 5.1).
 *
 * <p>Persistent entity backed by table {@code distributor}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "distributor", uniqueConstraints = {
        @UniqueConstraint(name = "uq_distributor_partner_code", columnNames = {"partner_code"})
})
public class Distributor extends BaseEntity {

    /** The partner code (P + 5 digits in Sesame); shared across entities. */
    @Column(name = "partner_code")
    public String partnerCode;

    /** The registered legal name. */
    @Column(name = "legal_name")
    public String legalName;

    /** The legal form. */
    @Column(name = "legal_form")
    public String legalForm;

    /** The 9-digit SIREN number. */
    @Column(name = "siren", length = 9)
    public String siren;

    /** First address line, reproduced on statements and cheques. */
    @Column(name = "address_line1")
    public String addressLine1;

    /** Second address line. */
    @Column(name = "address_line2")
    public String addressLine2;

    /** Postal code. */
    @Column(name = "postal_code")
    public String postalCode;

    /** City. */
    @Column(name = "city")
    public String city;

    /** ORIAS registration number. */
    @Column(name = "orias_number")
    public String oriasNumber;

    /** ORIAS registration status; a struck-off status blocks payment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "orias_status", length = 30)
    public OriasStatus oriasStatus;

    /** The commercial type of distributor. */
    @Enumerated(EnumType.STRING)
    @Column(name = "distributor_type", length = 30)
    public DistributorType distributorType;

    /** The distributor group, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public DistributorGroup group;

    /** The commercial delegation, if any. */
    @Column(name = "sales_delegation")
    public String salesDelegation;

    /** Whether the distributor is paid. */
    @Column(name = "commissionable")
    public Boolean commissionable;

    /** Whether the distributor is excluded from the discount (escompte). */
    @Column(name = "advance_excluded")
    public Boolean advanceExcluded;

    /** The distributor's preferred statement breakdown. */
    @Enumerated(EnumType.STRING)
    @Column(name = "statement_grouping", length = 30)
    public StatementGrouping statementGrouping;
}
