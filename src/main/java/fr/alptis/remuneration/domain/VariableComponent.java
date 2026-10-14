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
    /** The annual variable of animators and managers: production-growth tiers and qualitative criteria tiers (FDV-09). */
    ANNUAL_MANAGER,
    /** The annual direct-sales variable: a share of new-business margin with overshoot and a portfolio-value scale (FDV-10). */
    ANNUAL_DIRECT_SALES,
    /** The annual regularisation of the individual variable. */
    ANNUAL_ADJUSTMENT,
    /** A manual adjustment decided by the manager, carrying its reason (FDV-13). */
    MANUAL_ADJUSTMENT
}
