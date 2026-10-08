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
 * A dated version of a rule carrying all that is computed (section 5.4).
 *
 * <p>Persistent entity backed by table {@code rule_version}; it inherits the common bookkeeping columns
 * and the optimistic-lock version from {@link BaseEntity}. Fields are public (Panache active record);
 * foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "rule_version", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rule_version", columnNames = {"rule_id", "version_no"})
})
public class RuleVersion extends BaseEntity {

    /** The rule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    public Rule rule;

    /** The version number; a version is never overwritten. */
    @Column(name = "version_no")
    public Integer versionNo;

    /** Start of the validity period. */
    @Column(name = "valid_from")
    public LocalDate validFrom;

    /** End of the validity period; null while in force. */
    @Column(name = "valid_to")
    public LocalDate validTo;

    /** The current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public VersionStatus status;

    /** Which date the version is anchored on when selected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "version_anchor", length = 30)
    public VersionAnchor versionAnchor;

    /** The scope: the conjunction of filters selecting the events the version applies to (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope")
    public String scope;

    /** The triggering event types, copied from the scope for indexing. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_event_types")
    public java.util.List<String> triggerEventTypes;

    /** The amount the version applies its rate to. */
    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 30)
    public RuleBasis basis;

    /** The nature of the produced line; null for UPFRONT, whose two natures are fixed by the family. */
    @Enumerated(EnumType.STRING)
    @Column(name = "output_nature", length = 30)
    public LineNature outputNature;

    /** The typed formula parameters, validated against the family's schema (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    /** The clawback policy of this version (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clawback_policy")
    public String clawbackPolicy;

    /** The payment schedule of this version (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_schedule")
    public String paymentSchedule;

    /** The priority used to arbitrate within an exclusion group. */
    @Column(name = "priority")
    public Integer priority;

    /** The exclusion group; competing versions of the same group are arbitrated by priority. */
    @Column(name = "exclusion_group")
    public String exclusionGroup;

    /** The agreement that founds this, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    public Agreement agreement;

    /** Who approved the version (must differ from its creator). */
    @Column(name = "approved_by")
    public String approvedBy;

    /** When the version was approved. */
    @Column(name = "approved_at")
    public OffsetDateTime approvedAt;
}
