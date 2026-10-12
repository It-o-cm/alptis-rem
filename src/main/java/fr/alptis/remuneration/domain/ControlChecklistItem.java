package fr.alptis.remuneration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * One item of a period's manual control checklist (CTL-03, section 7.2 step 6): a control the
 * operator ticks once reviewed for the period.
 *
 * <p>Persistent entity backed by table {@code control_checklist_item}; it inherits the common
 * bookkeeping columns and the optimistic-lock version from {@link BaseEntity}. Fields are public
 * (Panache active record); foreign keys are {@code @ManyToOne} associations.</p>
 */
@Entity
@Table(name = "control_checklist_item", uniqueConstraints = {
        @UniqueConstraint(name = "uq_control_checklist_item", columnNames = {"period_id", "control_code"})
})
public class ControlChecklistItem extends BaseEntity {

    /** The entity this row belongs to, for data partitioning (section 12.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    public LegalEntity entity;

    /** The period the checklist belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    public Period period;

    /** The control code reviewed. */
    @Column(name = "control_code")
    public String controlCode;

    /** Whether the operator has ticked this control as reviewed. */
    @Column(name = "checked")
    public Boolean checked;

    /** Who ticked it, null while unchecked. */
    @Column(name = "checked_by")
    public String checkedBy;

    /** When it was ticked, null while unchecked. */
    @Column(name = "checked_at")
    public OffsetDateTime checkedAt;
}
