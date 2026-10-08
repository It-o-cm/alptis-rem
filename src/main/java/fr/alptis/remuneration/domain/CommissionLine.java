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
 * An immutable commission line, centre of the model (section 5.5).
 *
 * <p>Persistent entity backed by table {@code commission_line}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "commission_line")
public class CommissionLine extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The calculation run that produced this line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    public CalculationRun run;

    /** What the line is (never changes). */
    @Enumerated(EnumType.STRING)
    @Column(name = "nature", length = 30)
    public LineNature nature;

    /** Whether the line is received or owed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 30)
    public Direction direction;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public LineStatus status;

    /** The membership contract (adhésion). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    /** The coverage (garantie), the grain of calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    /** The business event that triggered the line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public BusinessEvent event;

    /** The rule version that produced this line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    public RuleVersion ruleVersion;

    /** The basis the line was computed on. */
    @Column(name = "basis_amount", precision = 14, scale = 2)
    public BigDecimal basisAmount;

    /** The rate applied, if any. */
    @Column(name = "rate", precision = 9, scale = 6)
    public BigDecimal rate;

    /** The line amount, in euros (negative for a clawback). */
    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    /** The distributor account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    /** The production code the line originates from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_production_code_id")
    public ProductionCode originProductionCode;

    /** The distributor the line originates from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_distributor_id")
    public Distributor originDistributor;

    /** The accounting attachment month. */
    @Column(name = "attachment_month")
    public LocalDate attachmentMonth;

    /** The fiscal year the premium is attached to. */
    @Column(name = "premium_fiscal_year")
    public Short premiumFiscalYear;

    /** The original line, for a correction or reverse line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_line_id")
    public CommissionLine originLine;

    /** Free-text reason or anomaly motive. */
    @Column(name = "reason")
    public String reason;

    /** The inputs of the calculation, produced by the calculation itself (JSON, D14). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation")
    public String explanation;

    /** The discount position this line offsets against, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_position_id")
    public AdvancePosition advancePosition;

    /** The statement carrying this, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    public Statement statement;

    /** The payment order, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    public PaymentOrder paymentOrder;

    /** Whether the line is blocked from payment. */
    @Column(name = "blocked")
    public Boolean blocked;

    /** Why the line is blocked. */
    @Column(name = "block_reason")
    public String blockReason;

    /** Date the block is lifted. */
    @Column(name = "blocked_until")
    public LocalDate blockedUntil;

    /** When the line was earned. */
    @Column(name = "earned_at")
    public OffsetDateTime earnedAt;

    /** When the line became payable. */
    @Column(name = "payable_at")
    public OffsetDateTime payableAt;

    /** When the line was paid. */
    @Column(name = "paid_at")
    public OffsetDateTime paidAt;
}
