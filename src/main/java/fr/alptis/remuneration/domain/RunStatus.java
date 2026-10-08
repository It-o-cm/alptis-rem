package fr.alptis.remuneration.domain;

/**
 * Status of a calculation run (section 5.6).
 */
public enum RunStatus {
    /** In progress. */
    RUNNING,
    /** Finished with its counters. */
    COMPLETED,
    /** Failed; failing units are replayed alone. */
    FAILED
}
