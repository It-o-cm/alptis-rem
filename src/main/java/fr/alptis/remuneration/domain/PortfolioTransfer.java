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

/** A transfer of coverages from one distributor to another (section 5.2). */
@Entity
@Table(name = "portfolio_transfer")
public class PortfolioTransfer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_distributor_id")
    public Distributor fromDistributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_distributor_id")
    public Distributor toDistributor;

    @Column(name = "effect_date")
    public LocalDate effectDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope")
    public String scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "advance_debt_handling", length = 30)
    public AdvanceDebtHandling advanceDebtHandling;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public TransferStatus status;
}
