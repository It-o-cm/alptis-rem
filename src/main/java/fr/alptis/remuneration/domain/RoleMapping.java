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
 * A mapping of a directory group to a role and entity (section 5.7).
 *
 * <p>Persistent entity backed by table {@code role_mapping}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "role_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uq_role_mapping", columnNames = {"directory_group", "role", "entity_id"})
})
public class RoleMapping extends BaseEntity {

    /** The directory group mapped to a role. */
    @Column(name = "directory_group")
    public String directoryGroup;

    /** The role. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    public Role role;

    /** The entity the role is scoped to; null for every entity. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;
}
