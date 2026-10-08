package fr.alptis.remuneration.domain;

/**
 * Type of a payment batch within a period (section 5.7).
 */
public enum BatchType {
    /** The main cycle batch carrying carried balances, compensation and the minimum threshold. */
    CYCLE,
    /** The separate retirement-savings batch, dated the 20th-25th, with its own statement (H-57). */
    SAVINGS_CYCLE,
    /** An off-cycle batch (e.g. releasing a payment after re-registration). */
    OFF_CYCLE,
    /** The quarterly cheque batch for payees without bank details. */
    CHEQUE
}
