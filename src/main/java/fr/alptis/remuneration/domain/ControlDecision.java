package fr.alptis.remuneration.domain;

/**
 * Decision taken on a control result (section 5.6).
 */
public enum ControlDecision {
    /** Not yet decided. */
    OPEN,
    /** The underlying cause was corrected. */
    FIXED,
    /** Accepted with a justification. */
    ACCEPTED,
    /** Deferred to a later period. */
    DEFERRED
}
