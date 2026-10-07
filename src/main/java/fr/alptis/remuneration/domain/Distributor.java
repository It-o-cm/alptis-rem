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

/** A distributor (section 5.1). */
@Entity
@Table(name = "distributor", uniqueConstraints = {
        @UniqueConstraint(name = "uq_distributor_partner_code", columnNames = {"partner_code"})
})
public class Distributor extends BaseEntity {

    @Column(name = "partner_code")
    public String partnerCode;

    @Column(name = "legal_name")
    public String legalName;

    @Column(name = "legal_form")
    public String legalForm;

    @Column(name = "siren", length = 9)
    public String siren;

    @Column(name = "address_line1")
    public String addressLine1;

    @Column(name = "address_line2")
    public String addressLine2;

    @Column(name = "postal_code")
    public String postalCode;

    @Column(name = "city")
    public String city;

    @Column(name = "orias_number")
    public String oriasNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "orias_status", length = 30)
    public OriasStatus oriasStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "distributor_type", length = 30)
    public DistributorType distributorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public DistributorGroup group;

    @Column(name = "sales_delegation")
    public String salesDelegation;

    @Column(name = "commissionable")
    public Boolean commissionable;

    @Column(name = "advance_excluded")
    public Boolean advanceExcluded;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_grouping", length = 30)
    public StatementGrouping statementGrouping;
}
