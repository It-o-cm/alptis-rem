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
 * A product range or market (section 5.1).
 *
 * <p>Persistent entity backed by table {@code product_range}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "product_range", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_range_code", columnNames = {"code"})
})
public class ProductRange extends BaseEntity {

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The target clientele (individual, professional, company). */
    @Enumerated(EnumType.STRING)
    @Column(name = "target", length = 30)
    public ProductTarget target;

    /**
     * Whether this is a retirement-savings range: its commission lines are paid in a distinct
     * {@code SAVINGS_CYCLE} lot dated the 20th to the 25th, with its own statement (section 10.5/10.6).
     */
    @Column(name = "retirement_savings")
    public Boolean retirementSavings;
}
