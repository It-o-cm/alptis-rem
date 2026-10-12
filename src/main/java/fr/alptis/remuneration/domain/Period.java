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
 * A monthly processing period of an entity (section 5.5).
 *
 * <p>Persistent entity backed by table {@code period}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "period", uniqueConstraints = {
        @UniqueConstraint(name = "uq_period", columnNames = {"entity_id", "period_month"})
})
public class Period extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The processed month. */
    @Column(name = "period_month")
    public LocalDate periodMonth;

    /** The state of the period. */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 30)
    public PeriodState state;

    /** The date at which the period's data is frozen. */
    @Column(name = "data_cutoff_date")
    public LocalDate dataCutoffDate;

    /** The first-level business validator, when a second level is required (WKF-02); null otherwise. */
    @Column(name = "first_level_validated_by")
    public String firstLevelValidatedBy;

    /** When the first-level business validation was given (WKF-02); null otherwise. */
    @Column(name = "first_level_validated_at")
    public java.time.OffsetDateTime firstLevelValidatedAt;
}
