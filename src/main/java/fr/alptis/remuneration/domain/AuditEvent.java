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

/** An append-only, hash-chained audit trace (section 5.5). */
@Entity
@Table(name = "audit_event")
public class AuditEvent extends BaseEntity {

    @Column(name = "occurred_at")
    public OffsetDateTime occurredAt;

    @Column(name = "user_id")
    public String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Column(name = "action")
    public String action;

    @Column(name = "object_type")
    public String objectType;

    @Column(name = "object_id")
    public Long objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    public String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    public String afterValue;

    @Column(name = "previous_hash", length = 64)
    public String previousHash;

    @Column(name = "hash", length = 64)
    public String hash;
}
