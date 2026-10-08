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
 * A grouping of distributors (section 5.1).
 *
 * <p>Persistent entity backed by table {@code distributor_group}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "distributor_group", uniqueConstraints = {
        @UniqueConstraint(name = "uq_distributor_group_code", columnNames = {"code"})
})
public class DistributorGroup extends BaseEntity {

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The distributor heading the group, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_distributor_id")
    public Distributor headDistributor;
}
