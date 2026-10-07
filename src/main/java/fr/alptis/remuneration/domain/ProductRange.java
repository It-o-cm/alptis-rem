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

/** A product range or market (section 5.1). */
@Entity
@Table(name = "product_range", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_range_code", columnNames = {"code"})
})
public class ProductRange extends BaseEntity {

    @Column(name = "code")
    public String code;

    @Column(name = "label")
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", length = 30)
    public ProductTarget target;
}
