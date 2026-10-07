package fr.alptis.remuneration.domain;

/** Type of a distributor-account movement other than commission lines (section 5.7). */
public enum AccountEntryType {
    PAYMENT,
    PAYMENT_REJECTION,
    OPENING_BALANCE,
    REPAYMENT_RECEIVED,
    WRITE_OFF
}
