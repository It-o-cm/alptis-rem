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
 * A distributor's status within one entity, historised (section 5.7).
 *
 * <p>Persistent entity backed by table {@code distributor_entity}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "distributor_entity", uniqueConstraints = {
        @UniqueConstraint(name = "uq_distributor_entity", columnNames = {"distributor_id", "entity_id", "valid_from"})
})
public class DistributorEntity extends BaseEntity {

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The legal entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The distributor's status within this entity, over a validity period. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public PartnerStatus status;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
