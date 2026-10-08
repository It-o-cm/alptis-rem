package fr.alptis.remuneration.domain;

/**
 * Type of a distributor-account movement other than commission lines (section 5.7).
 */
public enum AccountEntryType {
    /** A payment issued (negative on the account). */
    PAYMENT,
    /** A bank rejection crediting the account back. */
    PAYMENT_REJECTION,
    /** An opening balance carried over at a perimeter switch. */
    OPENING_BALANCE,
    /** A repayment received from a distributor. */
    REPAYMENT_RECEIVED,
    /** A write-off of an irrecoverable debit balance. */
    WRITE_OFF
}
