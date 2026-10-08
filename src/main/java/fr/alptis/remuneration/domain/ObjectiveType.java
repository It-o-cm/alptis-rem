package fr.alptis.remuneration.domain;

/**
 * Type of a sales objective (section 8.14).
 */
public enum ObjectiveType {
    /** An individual quantitative objective. */
    INDIVIDUAL_QUANTITATIVE,
    /** A qualitative objective (criteria met). */
    QUALITATIVE,
    /** A collective quantitative objective. */
    COLLECTIVE_QUANTITATIVE
}
