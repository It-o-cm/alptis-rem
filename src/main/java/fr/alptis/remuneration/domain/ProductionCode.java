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

/** A production code of a distributor within an entity (section 5.1). */
@Entity
@Table(name = "production_code", uniqueConstraints = {
        @UniqueConstraint(name = "uq_production_code", columnNames = {"entity_id", "code"})
})
public class ProductionCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    @Column(name = "code")
    public String code;

    @Column(name = "label")
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public PartnerStatus status;
}
