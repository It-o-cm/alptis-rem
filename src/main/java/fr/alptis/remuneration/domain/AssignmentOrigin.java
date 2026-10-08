package fr.alptis.remuneration.domain;

/**
 * Why a coverage assignment came to be (section 5.2), kept for traceability of portfolio moves.
 */
public enum AssignmentOrigin {
    /** Set at subscription. */
    SUBSCRIPTION,
    /** Result of a portfolio transfer. */
    TRANSFER,
    /** Result of a client replacement order. */
    REPLACEMENT_ORDER
}
