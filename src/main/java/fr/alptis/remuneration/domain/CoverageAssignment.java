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

/** The historised assignment of a coverage to a production code (section 5.2). */
@Entity
@Table(name = "coverage_assignment")
public class CoverageAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    public Coverage coverage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_code_id")
    public ProductionCode productionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    public AssignmentRole role;

    @Column(name = "share", precision = 7, scale = 6)
    public BigDecimal share;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 30)
    public AssignmentOrigin origin;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;
}
