package fr.alptis.remuneration.domain;

/**
 * Type of a distributor quality indicator (section 5.1), used as a condition of the portfolio and development surcommissions.
 */
public enum QualityIndicatorType {
    /** Share of new business that led to a complaint about the distributor. */
    COMPLAINT_RATE,
    /** Share of new business classed not-pursued or refused. */
    NOT_PURSUED_RATE,
    /** Share of the year's new business that lapsed. */
    NEW_BUSINESS_LAPSE_RATE,
    /** Share of the opening portfolio that lapsed during the year. */
    PORTFOLIO_LAPSE_RATE,
    /** Share of members in unpaid status at the measurement date. */
    UNPAID_RATE,
    /** Claims paid over premiums collected (P/C). */
    LOSS_RATIO,
    /** A solvency score of the distributor. */
    SOLVENCY_SCORE
}
