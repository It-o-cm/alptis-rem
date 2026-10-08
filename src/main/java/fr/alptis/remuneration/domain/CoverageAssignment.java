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
 * The historised assignment of a coverage to a production code (section 5.2).
 *
 * <p>Persistent entity backed by table {@code coverage_assignment}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "coverage_assignment")
public class CoverageAssignment extends BaseEntity {

    /** The coverage (garantie), the grain of calculation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    /** The production code the business is attached to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_code_id")
    public ProductionCode productionCode;

    /** Whether the production code is the main broker or a co-broker. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    public AssignmentRole role;

    /** The share, as a decimal fraction (the shares of a set sum to 1). */
    @Column(name = "share", precision = 7, scale = 6)
    public BigDecimal share;

    /** Why the assignment exists (subscription, transfer, replacement order). */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 30)
    public AssignmentOrigin origin;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
