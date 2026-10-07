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

/** The distributor's remuneration choice on a coverage (section 5.2). */
@Entity
@Table(name = "remuneration_choice")
public class RemunerationChoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 30)
    public RemunerationMode mode;

    @Column(name = "linear_rate", precision = 9, scale = 6)
    public BigDecimal linearRate;

    @Column(name = "acquisition_rate", precision = 9, scale = 6)
    public BigDecimal acquisitionRate;

    @Column(name = "servicing_rate", precision = 9, scale = 6)
    public BigDecimal servicingRate;

    @Column(name = "advance_requested")
    public Boolean advanceRequested;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;
}
