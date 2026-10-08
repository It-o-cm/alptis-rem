package fr.alptis.remuneration.domain;

/**
 * How often a payee is paid, carried by its payment rule (section 5.1); a batch pays only the payees due that month.
 */
public enum PaymentFrequency {
    /** Paid every month. */
    MONTHLY,
    /** Paid every quarter. */
    QUARTERLY,
    /** Paid twice a year. */
    HALF_YEARLY,
    /** Paid once a year. */
    YEARLY
}
