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
 * An append-only, hash-chained audit trace (section 5.5).
 *
 * <p>Persistent entity backed by table {@code audit_event}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent extends BaseEntity {

    /** When the action occurred. */
    @Column(name = "occurred_at")
    public OffsetDateTime occurredAt;

    /** Who performed the action. */
    @Column(name = "user_id")
    public String userId;

    /** The entity the action is scoped to; null for a global action. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The action code. */
    @Column(name = "action")
    public String action;

    /** The affected object type. */
    @Column(name = "object_type")
    public String objectType;

    /** The affected object id. */
    @Column(name = "object_id")
    public Long objectId;

    /** The before value (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    public String beforeValue;

    /** The after value (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    public String afterValue;

    /** Fingerprint of the previous trace (chaining). */
    @Column(name = "previous_hash", length = 64)
    public String previousHash;

    /** Fingerprint of this trace. */
    @Column(name = "hash", length = 64)
    public String hash;
}
