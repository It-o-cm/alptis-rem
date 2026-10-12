package fr.alptis.remuneration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One past run of a rule-version simulation (PAR-13): an append-only record of what was simulated and
 * the outcome, kept so the simulation screen can show a history even though each run replaces the
 * previous simulation space of the period.
 *
 * <p>Persistent entity backed by table {@code simulation_history}; it inherits the common bookkeeping
 * columns and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active
 * record); foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "simulation_history")
public class SimulationHistory extends BaseEntity {

    /** The entity this simulation belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The period that was simulated. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The simulated rule code. */
    @Column(name = "rule_code")
    public String ruleCode;

    /** The simulated version number. */
    @Column(name = "version_no")
    public Integer versionNo;

    /** The distributor the simulation was scoped to, or null for all. */
    @Column(name = "scope_distributor")
    public String scopeDistributor;

    /** The real total of the period. */
    @Column(name = "real_total", precision = 14, scale = 2)
    public BigDecimal realTotal;

    /** The simulated total of the period. */
    @Column(name = "simulated_total", precision = 14, scale = 2)
    public BigDecimal simulatedTotal;

    /** The gap (simulated − real). */
    @Column(name = "ecart", precision = 14, scale = 2)
    public BigDecimal ecart;

    /** The number of replayed lines. */
    @Column(name = "lines_replayed")
    public Integer linesReplayed;

    /** The number of modified lines. */
    @Column(name = "lines_modified")
    public Integer linesModified;

    /** The number of held events (dropped rate). */
    @Column(name = "held")
    public Integer held;

    /** Who launched the simulation. */
    @Column(name = "launched_by")
    public String launchedBy;

    /** When the simulation ran. */
    @Column(name = "run_at")
    public OffsetDateTime runAt;
}
