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
 * A contractual agreement backing a rule (section 5.4).
 *
 * <p>Persistent entity backed by table {@code agreement}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "agreement")
public class Agreement extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The type of agreement. */
    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", length = 30)
    public AgreementType agreementType;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The distributor group, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public DistributorGroup group;

    /** The insurer or delegated manager, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurer_id")
    public Insurer insurer;

    /** The reference of the backing document. */
    @Column(name = "reference")
    public String reference;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;

    /** Whether the agreement is signed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", length = 30)
    public SignatureStatus signatureStatus;

    /** When the agreement was signed. */
    @Column(name = "signed_at")
    public OffsetDateTime signedAt;

    /** Reference of the document in the GED. */
    @Column(name = "document_ref")
    public String documentRef;
}
