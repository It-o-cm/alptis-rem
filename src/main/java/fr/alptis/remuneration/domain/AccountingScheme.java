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

/** A row of the accounting scheme per entity (section 5.7). */
@Entity
@Table(name = "accounting_scheme", uniqueConstraints = {
        @UniqueConstraint(name = "uq_accounting_scheme", columnNames = {"entity_id", "accounting_event", "nature", "variant"})
})
public class AccountingScheme extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_event", length = 30)
    public AccountingEvent accountingEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", length = 30)
    public LineNature nature;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant", length = 30)
    public AccountingVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "debit_role", length = 30)
    public AccountRole debitRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_role", length = 30)
    public AccountRole creditRole;
}
