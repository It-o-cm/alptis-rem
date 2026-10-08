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
 * An execution of a calculation (section 5.5).
 *
 * <p>Persistent entity backed by table {@code calculation_run}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "calculation_run")
public class CalculationRun extends BaseEntity {

    /** The monthly processing period. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The type of run (forecast, actual, simulation). */
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", length = 30)
    public RunType runType;

    /** When the run started. */
    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    /** When the run finished. */
    @Column(name = "finished_at")
    public OffsetDateTime finishedAt;

    /** Who launched the run. */
    @Column(name = "launched_by")
    public String launchedBy;

    /** The software version that ran it. */
    @Column(name = "app_version")
    public String appVersion;

    /** Fingerprint of the rule configuration, so a rerun gives the same result (D11). */
    @Column(name = "config_hash", length = 64)
    public String configHash;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public RunStatus status;

    /** Run parameters (simulation substitution table, partial-recalc scope) (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    /** The run counters (events read, lines created, held, anomalies) (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters")
    public String counters;
}
