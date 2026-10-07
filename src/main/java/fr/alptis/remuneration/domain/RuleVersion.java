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

/** A dated version of a rule carrying all that is computed (section 5.4). */
@Entity
@Table(name = "rule_version", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rule_version", columnNames = {"rule_id", "version_no"})
})
public class RuleVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    public Rule rule;

    @Column(name = "version_no")
    public Integer versionNo;

    @Column(name = "valid_from")
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    public VersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_anchor", length = 30)
    public VersionAnchor versionAnchor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope")
    public String scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_event_types")
    public java.util.List<String> triggerEventTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 30)
    public RuleBasis basis;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_nature", length = 30)
    public LineNature outputNature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    public String parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clawback_policy")
    public String clawbackPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_schedule")
    public String paymentSchedule;

    @Column(name = "priority")
    public Integer priority;

    @Column(name = "exclusion_group")
    public String exclusionGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    public Agreement agreement;

    @Column(name = "approved_by")
    public String approvedBy;

    @Column(name = "approved_at")
    public OffsetDateTime approvedAt;
}
