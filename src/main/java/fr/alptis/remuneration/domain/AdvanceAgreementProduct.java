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
 * A product covered by a discount agreement (section 5.7).
 *
 * <p>Persistent entity backed by table {@code advance_agreement_product}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "advance_agreement_product", uniqueConstraints = {
        @UniqueConstraint(name = "uq_advance_agreement_product", columnNames = {"advance_agreement_id", "product_id"})
})
public class AdvanceAgreementProduct extends BaseEntity {

    /** The advance agreement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_agreement_id")
    public AdvanceAgreement advanceAgreement;

    /** The product concerned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    public Product product;
}
