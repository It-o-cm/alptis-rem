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
 * A commission line contested by a claim (section 5.7).
 *
 * <p>Persistent entity backed by table {@code claim_line}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "claim_line", uniqueConstraints = {
        @UniqueConstraint(name = "uq_claim_line", columnNames = {"claim_id", "commission_line_id"})
})
public class ClaimLine extends BaseEntity {

    /** The claim. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    public Claim claim;

    /** The contested commission line. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_line_id")
    public CommissionLine commissionLine;
}
