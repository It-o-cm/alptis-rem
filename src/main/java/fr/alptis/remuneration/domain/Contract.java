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

/** A membership contract (section 5.2). */
@Entity
@Table(name = "contract", uniqueConstraints = {
        @UniqueConstraint(name = "uq_contract", columnNames = {"entity_id", "source_system", "contract_number"})
})
public class Contract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "contract_number")
    public String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    public Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policyholder_id")
    public Policyholder policyholder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_contract_id")
    public Contract parentContract;

    @Column(name = "signature_date")
    public LocalDate signatureDate;

    @Column(name = "registration_date")
    public LocalDate registrationDate;

    @Column(name = "effect_date")
    public LocalDate effectDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    public EndReason endReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ContractStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", length = 30)
    public Periodicity periodicity;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", length = 30)
    public SalesChannel salesChannel;

    @Column(name = "new_business")
    public Boolean newBusiness;
}
