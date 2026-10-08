package fr.alptis.remuneration.domain;

/**
 * Status of a payment batch (section 5.6).
 */
public enum BatchStatus {
    /** Being built. */
    DRAFT,
    /** Validated by Finance. */
    VALIDATED,
    /** Transfer file issued. */
    ISSUED,
    /** All its orders settled or rejected. */
    SETTLED
}
