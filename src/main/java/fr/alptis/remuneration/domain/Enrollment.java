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
 * An enrollment of a distributor to a reserved rule (section 5.4).
 *
 * <p>Persistent entity backed by table {@code enrollment}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "enrollment")
public class Enrollment extends BaseEntity {

    /** The rule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    public Rule rule;

    /** The distributor concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    public Distributor distributor;

    /** The distributor group, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public DistributorGroup group;

    /** The agreement that founds this, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    public Agreement agreement;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public EnrollmentStatus status;
}
