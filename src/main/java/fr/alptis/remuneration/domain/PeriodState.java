package fr.alptis.remuneration.domain;

/**
 * State of a monthly processing period (section 7.1), advanced by the role shown in that section.
 */
public enum PeriodState {
    /** Imports feed the period. */
    OPEN,
    /** Data frozen at the cut-off date. */
    LOADED,
    /** The calculation has run; lines are consultable. */
    CALCULATED,
    /** Controls have run and blocking anomalies have been handled. */
    CONTROLLED,
    /** Amounts validated by the business; lines frozen unless Finance refuses. */
    BUSINESS_VALIDATED,
    /** Payment batches and provisional statements built. */
    PAYMENT_PREPARED,
    /** Reconciliation validated and the transfer file issued. */
    FINANCE_VALIDATED,
    /** Payments executed; statements published the next day. */
    PAID,
    /** Accounting extraction produced; nothing changes any more. */
    CLOSED
}
