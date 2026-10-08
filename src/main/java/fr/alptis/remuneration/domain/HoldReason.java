package fr.alptis.remuneration.domain;

/**
 * Why an event or line was put on hold (section 5.7), shown as an anomaly; held events do not block the period.
 */
public enum HoldReason {
    /** A referenced code (contract, coverage, production code) is unknown to the referentials. */
    UNKNOWN_REFERENCE,
    /** No rule matches the event. */
    NO_RULE,
    /** The chosen rate is not in the rule's allowed grid (CR-11). */
    RATE_OUT_OF_GRID,
    /** Two rules of the same group have the same priority on the event (H-41). */
    CONCURRENT_RULES,
    /** An external commission disagrees with its basis times rate (H-49). */
    EXTERNAL_MISMATCH,
    /** A value is outside its plausibility bounds. */
    OUT_OF_BOUNDS,
    /** A negative line with no cancellation key and no event to reconcile against. */
    UNMATCHED_NEGATIVE
}
