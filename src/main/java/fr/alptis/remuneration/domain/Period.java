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

/** A monthly processing period of an entity (section 5.5). */
@Entity
@Table(name = "period", uniqueConstraints = {
        @UniqueConstraint(name = "uq_period", columnNames = {"entity_id", "period_month"})
})
public class Period extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "period_month")
    public LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 30)
    public PeriodState state;

    @Column(name = "data_cutoff_date")
    public LocalDate dataCutoffDate;
}
