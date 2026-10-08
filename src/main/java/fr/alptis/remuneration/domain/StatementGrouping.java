package fr.alptis.remuneration.domain;

/**
 * How a distributor prefers its statement to be broken down (section 5.1).
 */
public enum StatementGrouping {
    /** A single statement, no breakdown. */
    NONE,
    /** One sub-total per insurer. */
    BY_INSURER,
    /** One sub-total per product. */
    BY_PRODUCT
}
