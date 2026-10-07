package fr.alptis.remuneration.domain;

/** Accounting event that drives a pair of entries (section 10.2). */
public enum AccountingEvent {
    FORECAST_RECOGNITION,
    FORECAST_ADJUSTMENT,
    EARNING,
    PAYABLE_RECOGNITION,
    PAYMENT,
    PAYMENT_REJECTION,
    ADVANCE_GRANTED,
    ADVANCE_OFFSETTING,
    ADVANCE_SETTLEMENT,
    ADVANCE_WRITE_OFF,
    CLAWBACK,
    SUPPLEMENT,
    ACCOUNT_TRANSFER,
    INSURER_RECEIVABLE
}
