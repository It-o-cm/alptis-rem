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
 * A distributor account, per entity and payee; balance is computed (section 5.5).
 *
 * <p>Persistent entity backed by table {@code distributor_account}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "distributor_account", uniqueConstraints = {
        @UniqueConstraint(name = "uq_distributor_account", columnNames = {"entity_id", "payee_type", "payee_id"})
})
public class DistributorAccount extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The kind of payee the account belongs to. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", length = 30)
    public PayeeType payeeType;

    /** The technical id of the payee. */
    @Column(name = "payee_id")
    public Long payeeId;

    /** Whether the account is manually blocked. */
    @Column(name = "blocked")
    public Boolean blocked;

    /** Why the account is blocked. */
    @Column(name = "block_reason")
    public String blockReason;

    /** Date the block is lifted. */
    @Column(name = "blocked_until")
    public LocalDate blockedUntil;
}
