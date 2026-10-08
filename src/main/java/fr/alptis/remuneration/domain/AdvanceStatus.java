package fr.alptis.remuneration.domain;

/**
 * Status of a coverage's advance (discount) position (section 5.6).
 */
public enum AdvanceStatus {
    /** Eligible, not yet paid. */
    PENDING,
    /** Paid, being offset, or (retirement) awaiting the fifth anniversary. */
    ACTIVE,
    /** Fully offset or settled at term. */
    SETTLED,
    /** Broken by a termination or unpaid rank; a settlement line is produced. */
    CANCELLED
}
