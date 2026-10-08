package fr.alptis.remuneration.domain;

/**
 * Status of a rejected import line (section 5.6).
 */
public enum ImportRejectionStatus {
    /** Awaiting correction. */
    OPEN,
    /** Corrected; it restarts on its own on re-import. */
    CORRECTED,
    /** Set aside (e.g. an accepted duplicate). */
    DISCARDED
}
