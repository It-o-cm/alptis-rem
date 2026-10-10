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

    /** Health product carrying the linear rule and the mock-up 110 € scene. */
    static final String P_SANTE_SELECT = "SANTE_SELECT";

    /** Health product carrying the upfront rule and the discount (escompte) advance. */
    static final String P_SANTE_PRO_PLUS = "SANTE_PRO_PLUS";

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
}
