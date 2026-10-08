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
 * A production code of a distributor within an entity (section 5.1).
 *
 * <p>Persistent entity backed by table {@code production_code}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "production_code", uniqueConstraints = {
        @UniqueConstraint(name = "uq_production_code", columnNames = {"entity_id", "code"})
})
public class ProductionCode extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The production code's status (same values as a distributor). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public PartnerStatus status;
}
