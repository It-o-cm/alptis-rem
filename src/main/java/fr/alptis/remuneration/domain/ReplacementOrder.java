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
 * A client's request to change broker on a contract (section 5.2).
 *
 * <p>Persistent entity backed by table {@code replacement_order}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "replacement_order")
public class ReplacementOrder extends BaseEntity {

    /** The membership contract (adhésion). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    /** The former production code. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_production_code_id")
    public ProductionCode fromProductionCode;

    /** The new production code. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_production_code_id")
    public ProductionCode toProductionCode;

    /** The date the application became aware of the fact (drives the processing period). */
    @Column(name = "received_date")
    public LocalDate receivedDate;

    /** The effect date. */
    @Column(name = "effect_date")
    public LocalDate effectDate;
}
