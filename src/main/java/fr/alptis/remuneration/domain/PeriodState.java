package fr.alptis.remuneration.domain;

/** State of a processing period (section 7.1). */
public enum PeriodState {
    OPEN,
    LOADED,
    CALCULATED,
    CONTROLLED,
    BUSINESS_VALIDATED,
    PAYMENT_PREPARED,
    FINANCE_VALIDATED,
    PAID,
    CLOSED
}
