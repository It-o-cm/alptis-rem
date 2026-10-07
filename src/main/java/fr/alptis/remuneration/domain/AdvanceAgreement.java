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

/** A discount (advance) agreement (section 5.4). */
@Entity
@Table(name = "advance_agreement")
public class AdvanceAgreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    public Agreement agreement;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;

    @Column(name = "anticipation_days")
    public Integer anticipationDays;

    @Column(name = "max_contracts")
    public Integer maxContracts;

    @Column(name = "unpaid_rank_threshold")
    public Short unpaidRankThreshold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "split")
    public String split;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public AdvanceAgreementStatus status;
}
