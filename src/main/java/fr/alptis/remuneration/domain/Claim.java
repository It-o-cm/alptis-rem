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

/** A distributor claim (section 5.5). */
@Entity
@Table(name = "claim")
public class Claim extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    public Statement statement;

    @Column(name = "received_at")
    public OffsetDateTime receivedAt;

    @Column(name = "subject")
    public String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ClaimStatus status;

    @Column(name = "resolution")
    public String resolution;

    @Column(name = "closed_at")
    public OffsetDateTime closedAt;
}
