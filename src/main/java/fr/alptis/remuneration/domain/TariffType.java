package fr.alptis.remuneration.domain;

/**
 * Tariff band of a coverage (section 5.2), a possible rule-scope criterion.
 */
public enum TariffType {
    /** Reduced tariff. */
    REDUCED,
    /** Standard tariff. */
    STANDARD,
    /** Increased (loaded) tariff. */
    INCREASED
}
