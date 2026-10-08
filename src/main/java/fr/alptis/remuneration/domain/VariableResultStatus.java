package fr.alptis.remuneration.domain;

/**
 * Status of a variable-pay result (section 8.14).
 */
public enum VariableResultStatus {
    /** Calculated. */
    CALCULATED,
    /** Validated by the manager. */
    VALIDATED,
    /** Exported to payroll. */
    EXPORTED
}
