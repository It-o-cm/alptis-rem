package fr.alptis.remuneration.api;

/**
 * Shared business codes of the demonstration dataset, referenced across the demo builders so the
 * referential, contracts, cycle and sales-force blocks stay coherent (same product, plan and
 * distributor codes everywhere). French labels live with the objects they name; only codes live here.
 */
interface DemoCodes {

    /** The historic Alptis entity carrying the mock-up scene. */
    static final String ALPTIS = "ALPTIS_ASSURANCES";

    /** The UNEP entity, showcasing all nine families end to end. */
    static final String UNEP = "UNEP";

    /** The mock-up cabinet partner code; also the idempotency anchor of the whole dataset. */
    static final String CABINET = "P13897";

    /** The demonstration plan code (per entity) holding the nine-family rules. */
    static final String DEMO_PLAN = "DEMO_2026";

    // --- Products (created for both entities) ---------------------------------

    /** Demo health product carrying the linear rule and the mock-up 110 € scene (off the Guide barème). */
    static final String P_LINEAR = "DEMO_SANTE";

    /** Demo health product carrying the upfront rule and the discount advance (off the Guide barème). */
    static final String P_UPFRONT = "DEMO_PREC";

    /** Provident product carrying the fixed-fee rule. */
    static final String P_PREVOYANCE = "PREVOYANCE_PRO_PLUS";

    /** Provident product carrying the external-commission rule. */
    static final String P_DECES = "DECES_PLUS";

    /** Collective product carrying the share (retrocession), RCD and portfolio rules. */
    static final String P_COLLECTIVE = "SANTE_COLLECTIVE";

    /** Retirement-savings product carrying the savings advance, outstanding and complement rules. */
    static final String P_PER = "PER";

    /** Borrower product. */
    static final String P_EMPRUNTEUR = "EMPRUNTEUR";

    /** Demo-only product dedicated to the fixed-fee family (kept off the Guide barème). */
    static final String P_FORFAIT = "FORFAIT_SERVICE";

    /** Demo-only product dedicated to the external family (kept off the Guide barème). */
    static final String P_EXTERNE = "COURTAGE_EXTERNE";

    // --- Family scene tags (also the demo rule-code suffixes) ----------------

    /** Linear scene tag. */
    static final String F_LINEAR = "LINEAR";

    /** Upfront (and discount-advance) scene tag. */
    static final String F_UPFRONT = "UPFRONT";

    /** Discount-advance (escompte) scene tag: an upfront coverage followed over several months. */
    static final String F_ESCOMPTE = "ESCOMPTE";

    /** Fixed-fee scene tag. */
    static final String F_FIXED = "FIXED";

    /** External-commission scene tag. */
    static final String F_EXTERNAL = "EXTERNAL";

    /** Share (retrocession) scene tag. */
    static final String F_SHARE = "SHARE";

    /** Outstanding-balance scene tag. */
    static final String F_OUTSTANDING = "OUTSTANDING";

    /** Retirement-savings advance scene tag. */
    static final String F_SAVINGS = "SAVINGS";

    /** Development remuneration (RCD) scene tag. */
    static final String F_RCD = "RCD";

    /** Portfolio remuneration (RCP) scene tag. */
    static final String F_PORTFOLIO = "PORTFOLIO";

    /** Retirement complement scene tag. */
    static final String F_COMPLEMENT = "COMPLEMENT";
}
