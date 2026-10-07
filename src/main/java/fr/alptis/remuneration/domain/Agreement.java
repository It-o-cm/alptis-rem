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

/** A contractual agreement backing a rule (section 5.4). */
@Entity
@Table(name = "agreement")
public class Agreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", length = 30)
    public AgreementType agreementType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public DistributorGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurer_id")
    public Insurer insurer;

    @Column(name = "reference")
    public String reference;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", length = 30)
    public SignatureStatus signatureStatus;

    @Column(name = "signed_at")
    public OffsetDateTime signedAt;

    @Column(name = "document_ref")
    public String documentRef;
}
