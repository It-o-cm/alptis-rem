package fr.alptis.remuneration.domain;

/**
 * Accounting event that drives a debit/credit pair (section 10.2). Entries are generated at business
 *  validation (payment and rejection later), so a recalculation has nothing to reverse.
 */
public enum AccountingEvent {
    /** Recognition of a forecast line. */
    FORECAST_RECOGNITION,
    /** Adjustment of the forecast on termination, unpaid or endorsement. */
    FORECAST_ADJUSTMENT,
    /** Earning of a commission on a collected premium. */
    EARNING,
    /** Recognition of the amount payable at business validation. */
    PAYABLE_RECOGNITION,
    /** Payment executed. */
    PAYMENT,
    /** Bank rejection of a payment. */
    PAYMENT_REJECTION,
    /** A discount granted. */
    ADVANCE_GRANTED,
    /** Offsetting of a discount by an earned commission. */
    ADVANCE_OFFSETTING,
    /** Settlement of a discount (positive or negative). */
    ADVANCE_SETTLEMENT,
    /** Write-off of a discount remainder under the settlement tolerance (H-40). */
    ADVANCE_WRITE_OFF,
    /** A clawback. */
    CLAWBACK,
    /** A surcommission, complement, fee or adjustment; reversed if negative. */
    SUPPLEMENT,
    /** A compensation between two accounts. */
    ACCOUNT_TRANSFER,
    /** A commission received from an insurer. */
    INSURER_RECEIVABLE
}
