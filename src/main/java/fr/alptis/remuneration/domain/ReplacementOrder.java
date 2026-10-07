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

/** A client's request to change broker on a contract (section 5.2). */
@Entity
@Table(name = "replacement_order")
public class ReplacementOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    public Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_production_code_id")
    public ProductionCode fromProductionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_production_code_id")
    public ProductionCode toProductionCode;

    @Column(name = "received_date")
    public LocalDate receivedDate;

    @Column(name = "effect_date")
    public LocalDate effectDate;
}
