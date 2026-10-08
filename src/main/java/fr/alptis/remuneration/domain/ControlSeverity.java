package fr.alptis.remuneration.domain;

/**
 * Severity of a control result (section 5.6); a blocking open anomaly prevents period validation.
 */
public enum ControlSeverity {
    /** Informational. */
    INFO,
    /** A warning that should be reviewed. */
    WARNING,
    /** Blocks validation of the period until decided. */
    BLOCKING
}
