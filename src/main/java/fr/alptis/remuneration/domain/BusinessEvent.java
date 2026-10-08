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
 * Everything that enters the calculation; never modified (section 5.3).
 *
 * <p>Persistent entity backed by table {@code business_event}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "business_event", uniqueConstraints = {
        @UniqueConstraint(name = "uq_business_event", columnNames = {"entity_id", "source_system", "source_key"})
})
public class BusinessEvent extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The source system (CRM or management tool) this record comes from. */
    @Column(name = "source_system")
    public String sourceSystem;

    /** The source key, unique per source system, which guarantees idempotency (section 5.3). */
    @Column(name = "source_key")
    public String sourceKey;

    /** The type of business event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30)
    public EventType eventType;

    /** The membership contract (adhésion). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    /** The coverage (garantie), the grain of calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    /** The business effect date (drives rule selection and seniority). */
    @Column(name = "event_date")
    public LocalDate eventDate;

    /** The date the application became aware of the fact (drives the processing period). */
    @Column(name = "received_date")
    public LocalDate receivedDate;

    /** Start of the covered period. */
    @Column(name = "period_from")
    public LocalDate periodFrom;

    /** End of the covered period. */
    @Column(name = "period_to")
    public LocalDate periodTo;

    /** The gross amount (TTC). */
    @Column(name = "gross_amount", precision = 14, scale = 2)
    public BigDecimal grossAmount;

    /** Taxes and contributions. */
    @Column(name = "tax_amount", precision = 14, scale = 2)
    public BigDecimal taxAmount;

    /** Fees (association dues, entry fees). */
    @Column(name = "fee_amount", precision = 14, scale = 2)
    public BigDecimal feeAmount;

    /** The basis, hors taxes (gross minus tax minus fee if not given). */
    @Column(name = "basis_amount", precision = 14, scale = 2)
    public BigDecimal basisAmount;

    /** The financial fund (savings). */
    @Column(name = "fund_code")
    public String fundCode;

    /** The management profile (savings). */
    @Column(name = "management_profile")
    public String managementProfile;

    /** The rank of the unpaid, for a discount-breaking notice. */
    @Column(name = "unpaid_rank")
    public Short unpaidRank;

    /** Premium recovered after litigation, excluded from the basis. */
    @Column(name = "recovered_after_litigation")
    public Boolean recoveredAfterLitigation;

    /** The event this one cancels, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_event_id")
    public BusinessEvent reversesEvent;

    /** Basis given by the insurer when the commission arrives already computed. */
    @Column(name = "precomputed_basis", precision = 14, scale = 2)
    public BigDecimal precomputedBasis;

    /** Rate given by the insurer when the commission arrives already computed. */
    @Column(name = "precomputed_rate", precision = 9, scale = 6)
    public BigDecimal precomputedRate;

    /** Amount given by the insurer when the commission arrives already computed. */
    @Column(name = "precomputed_amount", precision = 14, scale = 2)
    public BigDecimal precomputedAmount;

    /** The beneficiary targeted by an already-computed commission. */
    @Column(name = "beneficiary_code")
    public String beneficiaryCode;

    /** Free label reproduced on the statement. */
    @Column(name = "label")
    public String label;

    /** Where the event is in its life. */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 30)
    public ProcessingStatus processingStatus;

    /** Why the event is held, if on hold. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason", length = 30)
    public HoldReason holdReason;

    /** The period in which the event is eligible for calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eligible_period_id")
    public Period eligiblePeriod;

    /** The import batch this line came from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    public ImportBatch importBatch;
}
