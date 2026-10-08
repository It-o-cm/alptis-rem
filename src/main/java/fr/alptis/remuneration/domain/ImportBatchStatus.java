package fr.alptis.remuneration.domain;

/**
 * Status of an import batch (section 5.6).
 */
public enum ImportBatchStatus {
    /** Received, not yet processed. */
    RECEIVED,
    /** All lines loaded without rejection. */
    LOADED,
    /** Loaded with some lines rejected. */
    PARTIALLY_LOADED,
    /** Refused as an already-imported file. */
    REFUSED
}
