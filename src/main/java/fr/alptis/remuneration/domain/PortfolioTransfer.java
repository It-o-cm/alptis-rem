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
 * A transfer of coverages from one distributor to another (section 5.2).
 *
 * <p>Persistent entity backed by table {@code portfolio_transfer}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "portfolio_transfer")
public class PortfolioTransfer extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The transferring distributor (cédant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_distributor_id")
    public Distributor fromDistributor;

    /** The receiving distributor (repreneur). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_distributor_id")
    public Distributor toDistributor;

    /** The effect date. */
    @Column(name = "effect_date")
    public LocalDate effectDate;

    /** The transferred scope: a list of production codes, empty for the whole portfolio (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope")
    public String scope;

    /** Whether the transferor repays the discount receivable or the transferee takes it over. */
    @Enumerated(EnumType.STRING)
    @Column(name = "advance_debt_handling", length = 30)
    public AdvanceDebtHandling advanceDebtHandling;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public TransferStatus status;
}
