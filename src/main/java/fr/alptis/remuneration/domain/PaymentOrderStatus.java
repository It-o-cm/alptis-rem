package fr.alptis.remuneration.domain;

/** Status of a payment order (section 5.6). */
public enum PaymentOrderStatus {
    TO_VALIDATE,
    VALIDATED,
    ISSUED,
    SETTLED,
    REJECTED,
    CANCELLED
}
