package fr.alptis.remuneration.domain;

/** Reason a payee is excluded from a payment batch (section 5.7). */
public enum PaymentExclusionReason {
    ACCOUNT_BLOCKED,
    PAYEE_INACTIVE,
    NON_POSITIVE_BALANCE,
    BELOW_THRESHOLD,
    NO_BANK_DETAILS,
    FREQUENCY
}
