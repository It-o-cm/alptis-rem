package fr.alptis.remuneration.domain;

/**
 * Processing status of a portfolio transfer (section 5.2).
 */
public enum TransferStatus {
    /** Received, not yet applied. */
    PENDING,
    /** Applied: the coverages have been reassigned at the effect date. */
    PROCESSED
}
