package fr.alptis.remuneration.domain;

/** Reason a business event is put on hold (section 5.7). */
public enum HoldReason {
    UNKNOWN_REFERENCE,
    NO_RULE,
    RATE_OUT_OF_GRID,
    CONCURRENT_RULES,
    EXTERNAL_MISMATCH,
    OUT_OF_BOUNDS,
    UNMATCHED_NEGATIVE
}
