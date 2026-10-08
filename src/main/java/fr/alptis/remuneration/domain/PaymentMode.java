package fr.alptis.remuneration.domain;

/**
 * Means of a payment order (section 5.6).
 */
public enum PaymentMode {
    /** A SEPA credit transfer. */
    TRANSFER,
    /** A cheque, for a payee without bank details. */
    CHEQUE
}
