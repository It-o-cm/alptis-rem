package fr.alptis.remuneration.domain;

/**
 * What happens to the outstanding discount receivable when a portfolio is transferred (section 5.2, CR-80).
 */
public enum AdvanceDebtHandling {
    /** The transferor repays the receivable; the discount is cancelled and adjusted on its account. */
    REPAID_BY_TRANSFEROR,
    /** The transferee takes over the receivable; the discount stays active and offsets against its commissions. */
    TAKEN_OVER
}
