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
 * The distributor's remuneration choice on a coverage (section 5.2).
 *
 * <p>Persistent entity backed by table {@code remuneration_choice}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "remuneration_choice")
public class RemunerationChoice extends BaseEntity {

    /** The coverage (garantie), the grain of calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    /** The chosen remuneration mode (linear or upfront). */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 30)
    public RemunerationMode mode;

    /** The linear rate, as a fraction. */
    @Column(name = "linear_rate", precision = 9, scale = 6)
    public BigDecimal linearRate;

    /** The acquisition rate for an upfront coverage, as a fraction. */
    @Column(name = "acquisition_rate", precision = 9, scale = 6)
    public BigDecimal acquisitionRate;

    /** The servicing rate for an upfront coverage, as a fraction. */
    @Column(name = "servicing_rate", precision = 9, scale = 6)
    public BigDecimal servicingRate;

    /** Whether the coverage is presented for the discount (escompte). */
    @Column(name = "advance_requested")
    public Boolean advanceRequested;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
