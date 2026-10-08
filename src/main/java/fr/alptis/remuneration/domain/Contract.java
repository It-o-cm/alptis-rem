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
 * A membership contract (section 5.2).
 *
 * <p>Persistent entity backed by table {@code contract}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "contract", uniqueConstraints = {
        @UniqueConstraint(name = "uq_contract", columnNames = {"entity_id", "source_system", "contract_number"})
})
public class Contract extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The source system (CRM or management tool) this record comes from. */
    @Column(name = "source_system")
    public String sourceSystem;

    /** The contract number in the source system. */
    @Column(name = "contract_number")
    public String contractNumber;

    /** The product concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    public Product product;

    /** The policyholder. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policyholder_id")
    public Policyholder policyholder;

    /** The parent collective contract, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_contract_id")
    public Contract parentContract;

    /** The signature date. */
    @Column(name = "signature_date")
    public LocalDate signatureDate;

    /** The registration date. */
    @Column(name = "registration_date")
    public LocalDate registrationDate;

    /** The effect date. */
    @Column(name = "effect_date")
    public LocalDate effectDate;

    /** The end date; null while in force. */
    @Column(name = "end_date")
    public LocalDate endDate;

    /** The reason the object ended. */
    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    public EndReason endReason;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ContractStatus status;

    /** The billing periodicity. */
    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", length = 30)
    public Periodicity periodicity;

    /** The sales channel. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", length = 30)
    public SalesChannel salesChannel;

    /** Whether this is new business within the meaning of the glossary. */
    @Column(name = "new_business")
    public Boolean newBusiness;
}
