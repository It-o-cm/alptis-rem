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
 * A distributor claim (section 5.5).
 *
 * <p>Persistent entity backed by table {@code claim}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "claim")
public class Claim extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The statement carrying this, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    public Statement statement;

    /** When the file or object was received. */
    @Column(name = "received_at")
    public OffsetDateTime receivedAt;

    /** The subject of the claim. */
    @Column(name = "subject")
    public String subject;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public ClaimStatus status;

    /** The resolution of the claim. */
    @Column(name = "resolution")
    public String resolution;

    /** When the claim was closed. */
    @Column(name = "closed_at")
    public OffsetDateTime closedAt;
}
