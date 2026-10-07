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

/** A share of a payee rule (section 5.1). */
@Entity
@Table(name = "payee_rule_share")
public class PayeeRuleShare extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_rule_id")
    public PayeeRule payeeRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", length = 30)
    public PayeeType payeeType;

    @Column(name = "payee_id")
    public Long payeeId;

    @Column(name = "share", precision = 7, scale = 6)
    public BigDecimal share;
}
