package fr.alptis.remuneration.domain;

/**
 * Status of a payment order (section 5.6).
 */
public enum PaymentOrderStatus {
    /** Awaiting Finance validation. */
    TO_VALIDATE,
    /** Validated. */
    VALIDATED,
    /** Issued in the transfer file. */
    ISSUED,
    /** Settled by the bank. */
    SETTLED,
    /** Rejected by the bank; the lines return payable (CR-85). */
    REJECTED,
    /** Cancelled (e.g. its batch was refused). */
    CANCELLED
}
