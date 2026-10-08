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
 * A payment rule deciding who is paid and how often (section 5.1).
 *
 * <p>Persistent entity backed by table {@code payee_rule}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "payee_rule")
public class PayeeRule extends BaseEntity {

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The production code the business is attached to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_code_id")
    public ProductionCode productionCode;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The range. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    public ProductRange range;

    /** How often the designated payees are paid. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", length = 30)
    public PaymentFrequency paymentFrequency;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
