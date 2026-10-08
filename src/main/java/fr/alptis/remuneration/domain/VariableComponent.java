package fr.alptis.remuneration.domain;

/**
 * Component of a variable-pay result (section 8.14).
 */
public enum VariableComponent {
    /** The monthly individual quantitative instalment. */
    MONTHLY_INDIVIDUAL,
    /** The quarterly qualitative pay. */
    QUARTERLY_QUALITATIVE,
    /** The annual collective pay. */
    ANNUAL_COLLECTIVE,
    /** The annual regularisation of the individual variable. */
    ANNUAL_ADJUSTMENT,
    /** A manual adjustment decided by the manager, carrying its reason (FDV-13). */
    MANUAL_ADJUSTMENT
}
