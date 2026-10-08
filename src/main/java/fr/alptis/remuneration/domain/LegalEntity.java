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
 * A legal entity of the group that pays distributors (section 5.1).
 *
 * <p>Persistent entity backed by table {@code legal_entity}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "legal_entity", uniqueConstraints = {
        @UniqueConstraint(name = "uq_legal_entity_code", columnNames = {"code"})
})
public class LegalEntity extends BaseEntity {

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The name. */
    @Column(name = "name")
    public String name;

    /** IBAN the entity pays from (SEPA debtor). */
    @Column(name = "debtor_iban")
    public String debtorIban;

    /** BIC the entity pays from. */
    @Column(name = "debtor_bic")
    public String debtorBic;

    /** Minimum balance below which no payment is issued (H-26). */
    @Column(name = "min_payment_amount", precision = 14, scale = 2)
    public BigDecimal minPaymentAmount;

    /** Days after which an unsettled discount is settled at term (default 360, H-06). */
    @Column(name = "advance_settlement_days")
    public Integer advanceSettlementDays;

    /** Tolerance under which a discount remainder is written off rather than regularised (H-40). */
    @Column(name = "advance_settlement_tolerance", precision = 14, scale = 2)
    public BigDecimal advanceSettlementTolerance;

    /** The entity's payment calendar milestones (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_calendar")
    public String paymentCalendar;
}
