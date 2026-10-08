package fr.alptis.remuneration.domain;

/**
 * Variant of an accounting-scheme row (section 10.2), selecting the direction for the general cases.
 */
public enum AccountingVariant {
    /** The general case. */
    STANDARD,
    /** When the line amount is negative (forecast decrease, undue discount, negative supplement). */
    NEGATIVE,
    /** The earning of a product without a forecast (debits the expense directly). */
    NO_FORECAST,
    /** The granting of a retirement discount, booked as an expense, without offsetting. */
    SAVINGS
}
