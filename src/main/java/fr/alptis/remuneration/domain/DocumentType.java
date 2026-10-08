package fr.alptis.remuneration.domain;

/**
 * Type of a generated document, carried in its GED metadata (section 9.11).
 */
public enum DocumentType {
    /** A commission statement (bordereau). */
    STATEMENT,
    /** A corrective statement. */
    CORRECTED_STATEMENT,
    /** An informative statement for a member paid by its group. */
    MEMBER_STATEMENT,
    /** An annual tax certificate. */
    TAX_CERTIFICATE,
    /** A discount summary for an agreement. */
    ADVANCE_SUMMARY
}
