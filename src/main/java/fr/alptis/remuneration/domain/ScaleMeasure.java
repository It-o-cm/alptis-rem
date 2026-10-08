package fr.alptis.remuneration.domain;

/**
 * What a scale's tiers are measured against (section 5.7).
 */
public enum ScaleMeasure {
    /** An amount (e.g. cumulative annualised premiums). */
    AMOUNT,
    /** A count (e.g. number of new members). */
    COUNT
}
