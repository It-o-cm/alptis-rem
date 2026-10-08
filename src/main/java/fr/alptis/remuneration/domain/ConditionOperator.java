package fr.alptis.remuneration.domain;

/**
 * Comparison operator of a rule condition (section 5.4).
 */
public enum ConditionOperator {
    /** Strictly less than the threshold. */
    LT,
    /** Less than or equal to the threshold. */
    LE,
    /** Strictly greater than the threshold. */
    GT,
    /** Greater than or equal to the threshold. */
    GE,
    /** Equal to the threshold. */
    EQ
}
