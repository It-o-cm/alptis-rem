package fr.alptis.remuneration.domain;

/**
 * Status of a commission line (section 5.5); it says where the line is and progresses over time.
 */
public enum LineStatus {
    /** A provisional line, never paid and never turned into an earned line. */
    FORECAST,
    /** Earned on a collected premium. */
    EARNED,
    /** Validated and carried on a statement. */
    PAYABLE,
    /** Settled. */
    PAID,
    /** Offset against a discount, hence not paid. */
    OFFSET,
    /** Cancelled by a reverse line before business validation. */
    CANCELLED
}
