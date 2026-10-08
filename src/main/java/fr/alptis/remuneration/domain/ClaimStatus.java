package fr.alptis.remuneration.domain;

/**
 * Status of a distributor claim (réclamation) (section 5.6).
 */
public enum ClaimStatus {
    /** Opened. */
    OPEN,
    /** Being handled, with contested lines linked. */
    IN_PROGRESS,
    /** Closed with its resolution. */
    CLOSED
}
