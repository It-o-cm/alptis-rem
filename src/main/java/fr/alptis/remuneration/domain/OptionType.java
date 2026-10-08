package fr.alptis.remuneration.domain;

/**
 * Kind of a product option (section 5.1).
 */
public enum OptionType {
    /** A level or formula of the product. */
    BASE,
    /** An optional reinforcement added to the base. */
    RIDER,
    /** A bundle of guarantees sold together. */
    PACK,
    /** A financial fund of a savings product (used for outstanding-based commissions). */
    FUND
}
