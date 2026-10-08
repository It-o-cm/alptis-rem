package fr.alptis.remuneration.domain;

/**
 * Beneficiary level of a statement (section 5.6).
 */
public enum StatementLevel {
    /** A statement at the production-code level. */
    PRODUCTION_CODE,
    /** A statement at the distributor level. */
    DISTRIBUTOR,
    /** A statement at the distributor-group level. */
    GROUP
}
