package fr.alptis.remuneration.domain;

/**
 * Status of a statement (section 5.6); a published statement is an archived file, never regenerated.
 */
public enum StatementStatus {
    /** Provisional, before publication. */
    DRAFT,
    /** Published and archived with its fingerprint. */
    PUBLISHED,
    /** Superseded by a corrective version. */
    CORRECTED
}
