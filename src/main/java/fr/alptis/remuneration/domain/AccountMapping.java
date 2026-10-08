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
 * The mapping of an account role to a chart-of-accounts number per entity (section 5.7).
 *
 * <p>Persistent entity backed by table {@code account_mapping}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "account_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uq_account_mapping", columnNames = {"entity_id", "account_role"})
})
public class AccountMapping extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The functional account role. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", length = 30)
    public AccountRole accountRole;

    /** The chart-of-accounts number mapped to the role. */
    @Column(name = "account_number")
    public String accountNumber;

    /** The accounting journal. */
    @Column(name = "journal")
    public String journal;

    /** The analytic axes (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analytic_axes")
    public String analyticAxes;
}
