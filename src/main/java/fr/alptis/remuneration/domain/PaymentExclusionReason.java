package fr.alptis.remuneration.domain;

/**
 * Why a payee is excluded from a payment batch (section 5.7), shown with its amount kept on the account.
 */
public enum PaymentExclusionReason {
    /** The account or a line is manually blocked. */
    ACCOUNT_BLOCKED,
    /** The payee is not ACTIVE (suspended, terminated, ORIAS struck off). */
    PAYEE_INACTIVE,
    /** The account balance is not positive. */
    NON_POSITIVE_BALANCE,
    /** The positive balance is below the entity's minimum payment amount. */
    BELOW_THRESHOLD,
    /** No bank details; paid by cheque instead. */
    NO_BANK_DETAILS,
    /** Not due this month given the payee's payment frequency. */
    FREQUENCY
}
