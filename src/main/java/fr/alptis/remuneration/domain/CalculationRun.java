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

/** An execution of a calculation (section 5.5). */
@Entity
@Table(name = "calculation_run")
public class CalculationRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", length = 30)
    public RunType runType;

    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    @Column(name = "finished_at")
    public OffsetDateTime finishedAt;

    @Column(name = "launched_by")
    public String launchedBy;

    @Column(name = "app_version")
    public String appVersion;

    @Column(name = "config_hash", length = 64)
    public String configHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public RunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters")
    public String counters;
}
