package fr.alptis.remuneration.domain;

/**
 * Status of a manual adjustment (section 5.6); adjustments carry a two-signature workflow.
 */
public enum AdjustmentStatus {
    /** Being entered. */
    DRAFT,
    /** Submitted for approval. */
    SUBMITTED,
    /** Approved by a second user. */
    APPROVED,
    /** Rejected. */
    REJECTED,
    /** Applied as a line on the account. */
    APPLIED
}
