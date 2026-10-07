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

/** A legal entity of the group that pays distributors (section 5.1). */
@Entity
@Table(name = "legal_entity", uniqueConstraints = {
        @UniqueConstraint(name = "uq_legal_entity_code", columnNames = {"code"})
})
public class LegalEntity extends BaseEntity {

    @Column(name = "code")
    public String code;

    @Column(name = "name")
    public String name;

    @Column(name = "debtor_iban")
    public String debtorIban;

    @Column(name = "debtor_bic")
    public String debtorBic;

    @Column(name = "min_payment_amount", precision = 14, scale = 2)
    public BigDecimal minPaymentAmount;

    @Column(name = "advance_settlement_days")
    public Integer advanceSettlementDays;

    @Column(name = "advance_settlement_tolerance", precision = 14, scale = 2)
    public BigDecimal advanceSettlementTolerance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_calendar")
    public String paymentCalendar;
}
