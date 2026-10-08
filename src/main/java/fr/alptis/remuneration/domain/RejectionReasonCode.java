package fr.alptis.remuneration.domain;

/**
 * Reason an import line was rejected (section 8.2); a rejected line is wrong in itself and must be corrected.
 */
public enum RejectionReasonCode {
    /** A structural error in the line. */
    STRUCTURE,
    /** A type error (e.g. an unparseable value or an unknown code value). */
    TYPE,
    /** A mandatory field is missing. */
    MISSING_FIELD,
    /** An amount is absent, non-numeric or zero. */
    AMOUNT,
    /** A date is in the future. */
    FUTURE_DATE,
    /** A duplicate of an already-integrated source key. */
    DUPLICATE
}
