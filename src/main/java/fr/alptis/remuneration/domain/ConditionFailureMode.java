package fr.alptis.remuneration.domain;

/**
 * What happens to the month when a rule condition is not met (section 5.7, H-10).
 */
public enum ConditionFailureMode {
    /** The month is lost. */
    FORFEIT,
    /** The month is deferred and may be caught up later. */
    DEFER
}
