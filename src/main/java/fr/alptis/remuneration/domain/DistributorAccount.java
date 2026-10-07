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

/** A distributor account, per entity and payee; balance is computed (section 5.5). */
@Entity
@Table(name = "distributor_account", uniqueConstraints = {
        @UniqueConstraint(name = "uq_distributor_account", columnNames = {"entity_id", "payee_type", "payee_id"})
})
public class DistributorAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", length = 30)
    public PayeeType payeeType;

    @Column(name = "payee_id")
    public Long payeeId;

    @Column(name = "blocked")
    public Boolean blocked;

    @Column(name = "block_reason")
    public String blockReason;

    @Column(name = "blocked_until")
    public LocalDate blockedUntil;
}
