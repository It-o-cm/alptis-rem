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
 * A share of a payee rule (section 5.1).
 *
 * <p>Persistent entity backed by table {@code payee_rule_share}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "payee_rule_share")
public class PayeeRuleShare extends BaseEntity {

    /** The payee rule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_rule_id")
    public PayeeRule payeeRule;

    /** The kind of payee receiving this share. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", length = 30)
    public PayeeType payeeType;

    /** The technical id of the payee receiving this share. */
    @Column(name = "payee_id")
    public Long payeeId;

    /** The share, as a decimal fraction (the shares of a set sum to 1). */
    @Column(name = "share", precision = 7, scale = 6)
    public BigDecimal share;
}
