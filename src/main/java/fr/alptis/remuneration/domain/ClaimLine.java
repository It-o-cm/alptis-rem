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

/** A commission line contested by a claim (section 5.7). */
@Entity
@Table(name = "claim_line", uniqueConstraints = {
        @UniqueConstraint(name = "uq_claim_line", columnNames = {"claim_id", "commission_line_id"})
})
public class ClaimLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    public Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_line_id")
    public CommissionLine commissionLine;
}
