package fr.alptis.remuneration.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * Common base class for every persistent business entity.
 *
 * <p>It carries the columns shared by all tables of specification section 5.7 &mdash;
 * a generated technical primary key, the {@code created_at} / {@code created_by} /
 * {@code updated_at} bookkeeping columns &mdash; plus a {@link Version} column used for
 * optimistic locking. Timestamps are never populated from the system clock directly:
 * persistence services set them from the injected {@link DateTimeProvider}.</p>
 *
 * <p>The natural (business) keys live on the concrete subclasses; APIs look entities up by
 * those keys, never by this technical identifier.</p>
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    /** Generated technical primary key ({@code id bigint}). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Optimistic-lock version, incremented by the persistence provider on each update. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Timestamp at which the row was first created. */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /** Identifier of the user or process that created the row. */
    @Column(name = "created_by")
    private String createdBy;

    /** Timestamp of the last modification of the row. */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Returns the generated technical primary key.
     *
     * @return the identifier, or {@code null} before the row is persisted
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the technical primary key.
     *
     * @param id the identifier to assign
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the optimistic-lock version of the row.
     *
     * @return the current version number
     */
    public long getVersion() {
        return version;
    }

    /**
     * Sets the optimistic-lock version of the row.
     *
     * @param version the version number to assign
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return the creation timestamp, or {@code null} if not yet set
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the creation timestamp to assign
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the identifier of the creator.
     *
     * @return the creator identifier, or {@code null} if not yet set
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the identifier of the creator.
     *
     * @param createdBy the creator identifier to assign
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns the last-modification timestamp.
     *
     * @return the last-modification timestamp, or {@code null} if never modified
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last-modification timestamp.
     *
     * @param updatedAt the last-modification timestamp to assign
     */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
