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

/** Everything that enters the calculation; never modified (section 5.3). */
@Entity
@Table(name = "business_event", uniqueConstraints = {
        @UniqueConstraint(name = "uq_business_event", columnNames = {"entity_id", "source_system", "source_key"})
})
public class BusinessEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "source_key")
    public String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30)
    public EventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    @Column(name = "event_date")
    public LocalDate eventDate;

    @Column(name = "received_date")
    public LocalDate receivedDate;

    @Column(name = "period_from")
    public LocalDate periodFrom;

    @Column(name = "period_to")
    public LocalDate periodTo;

    @Column(name = "gross_amount", precision = 14, scale = 2)
    public BigDecimal grossAmount;

    @Column(name = "tax_amount", precision = 14, scale = 2)
    public BigDecimal taxAmount;

    @Column(name = "fee_amount", precision = 14, scale = 2)
    public BigDecimal feeAmount;

    @Column(name = "basis_amount", precision = 14, scale = 2)
    public BigDecimal basisAmount;

    @Column(name = "fund_code")
    public String fundCode;

    @Column(name = "management_profile")
    public String managementProfile;

    @Column(name = "unpaid_rank")
    public Short unpaidRank;

    @Column(name = "recovered_after_litigation")
    public Boolean recoveredAfterLitigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_event_id")
    public BusinessEvent reversesEvent;

    @Column(name = "precomputed_basis", precision = 14, scale = 2)
    public BigDecimal precomputedBasis;

    @Column(name = "precomputed_rate", precision = 9, scale = 6)
    public BigDecimal precomputedRate;

    @Column(name = "precomputed_amount", precision = 14, scale = 2)
    public BigDecimal precomputedAmount;

    @Column(name = "beneficiary_code")
    public String beneficiaryCode;

    @Column(name = "label")
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 30)
    public ProcessingStatus processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason", length = 30)
    public HoldReason holdReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eligible_period_id")
    public Period eligiblePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    public ImportBatch importBatch;
}
