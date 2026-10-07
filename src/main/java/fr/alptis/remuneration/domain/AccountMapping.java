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

/** The mapping of an account role to a chart-of-accounts number per entity (section 5.7). */
@Entity
@Table(name = "account_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uq_account_mapping", columnNames = {"entity_id", "account_role"})
})
public class AccountMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", length = 30)
    public AccountRole accountRole;

    @Column(name = "account_number")
    public String accountNumber;

    @Column(name = "journal")
    public String journal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analytic_axes")
    public String analyticAxes;
}
