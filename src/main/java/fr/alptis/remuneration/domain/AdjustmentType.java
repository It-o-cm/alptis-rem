package fr.alptis.remuneration.domain;

/**
 * Type of a manual adjustment (section 5.6).
 */
public enum AdjustmentType {
    /** An exceptional advance paid to a distributor, on a repayment schedule. */
    EXCEPTIONAL_ADVANCE,
    /** A manual clawback. */
    MANUAL_CLAWBACK,
    /** A correction of a line. */
    CORRECTION,
    /** An exceptional commission. */
    EXCEPTIONAL_COMMISSION,
    /** A transfer between two accounts of the same distributor. */
    ACCOUNT_TRANSFER
}
