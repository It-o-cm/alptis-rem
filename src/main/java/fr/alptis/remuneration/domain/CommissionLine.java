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

/** An immutable commission line, centre of the model (section 5.5). */
@Entity
@Table(name = "commission_line")
public class CommissionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    public CalculationRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", length = 30)
    public LineNature nature;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 30)
    public Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public LineStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public BusinessEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    public RuleVersion ruleVersion;

    @Column(name = "basis_amount", precision = 14, scale = 2)
    public BigDecimal basisAmount;

    @Column(name = "rate", precision = 9, scale = 6)
    public BigDecimal rate;

    @Column(name = "amount", precision = 14, scale = 2)
    public BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    public DistributorAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_production_code_id")
    public ProductionCode originProductionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_distributor_id")
    public Distributor originDistributor;

    @Column(name = "attachment_month")
    public LocalDate attachmentMonth;

    @Column(name = "premium_fiscal_year")
    public Short premiumFiscalYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_line_id")
    public CommissionLine originLine;

    @Column(name = "reason")
    public String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation")
    public String explanation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_position_id")
    public AdvancePosition advancePosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    public Statement statement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    public PaymentOrder paymentOrder;

    @Column(name = "blocked")
    public Boolean blocked;

    @Column(name = "block_reason")
    public String blockReason;

    @Column(name = "blocked_until")
    public LocalDate blockedUntil;

    @Column(name = "earned_at")
    public OffsetDateTime earnedAt;

    @Column(name = "payable_at")
    public OffsetDateTime payableAt;

    @Column(name = "paid_at")
    public OffsetDateTime paidAt;
}
