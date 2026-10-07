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

/** An insurer or delegated manager (section 5.1). */
@Entity
@Table(name = "insurer", uniqueConstraints = {
        @UniqueConstraint(name = "uq_insurer_code", columnNames = {"code"})
})
public class Insurer extends BaseEntity {

    @Column(name = "code")
    public String code;

    @Column(name = "name")
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    public InsurerRole role;
}
