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
 * A level, formula, rider, pack or financial fund of a product (section 5.1).
 *
 * <p>Persistent entity backed by table {@code product_option}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "product_option", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_option", columnNames = {"product_id", "code"})
})
public class ProductOption extends BaseEntity {

    /** The product concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    public Product product;

    /** The business code, unique within its scope. */
    @Column(name = "code")
    public String code;

    /** The human-readable label. */
    @Column(name = "label")
    public String label;

    /** The kind of option (level, rider, pack, financial fund). */
    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", length = 30)
    public OptionType optionType;

    /** The first day the option is valid (REF-01). */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** The last day the option is valid, or null while in force (REF-01). */
    @Column(name = "valid_to")
    public LocalDate validTo;
}
