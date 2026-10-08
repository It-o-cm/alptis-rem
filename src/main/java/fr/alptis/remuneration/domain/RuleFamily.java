package fr.alptis.remuneration.domain;

/**
 * Calculation family of a rule (section 5.6). A family is a class declaring the JSON schema of its
 *  parameters; adding one touches neither the engine nor the registry.
 */
public enum RuleFamily {
    /** A constant rate applied to the basis every year. */
    LINEAR,
    /** Acquisition the first year plus servicing for life (précompté). */
    UPFRONT,
    /** An advance (escompte) of the first-year commissions, offset by later commissions. */
    ADVANCE,
    /** A scale over a running cumulative counter (RCD). */
    TIERED,
    /** An annual portfolio commission under quality conditions (RCP). */
    PORTFOLIO,
    /** A share of another commission (retrocession, group share). */
    SHARE,
    /** A commission on the average outstanding balance of a savings fund. */
    OUTSTANDING,
    /** A fixed amount (file fees, honoraria). */
    FIXED,
    /** A commission already computed by a third party, integrated as is. */
    EXTERNAL
}
