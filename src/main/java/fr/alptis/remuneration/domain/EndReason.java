package fr.alptis.remuneration.domain;

/**
 * Reason a contract or coverage ended (section 5.2); it drives whether a clawback or a settlement applies.
 */
public enum EndReason {
    /** Ordinary termination (résiliation). */
    TERMINATION,
    /** Withdrawal during the cooling-off period (renonciation). */
    WITHDRAWAL,
    /** Classed not-pursued or refused after registration. */
    NOT_PURSUED,
    /** Death of the member; no clawback applies (CR-08). */
    DEATH,
    /** Early full repayment of a borrower loan (CR-81). */
    EARLY_REPAYMENT,
    /** Surrender of a savings contract. */
    SURRENDER,
    /** Outbound transfer of a savings contract to another carrier. */
    OUTBOUND_TRANSFER
}
