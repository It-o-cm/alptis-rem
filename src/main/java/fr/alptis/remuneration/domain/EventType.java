package fr.alptis.remuneration.domain;

/**
 * Type of a business event (section 5.3), the unit of everything that enters the calculation.
 */
public enum EventType {
    /** A premium called (émission), hors taxes; basis of the forecast. */
    PREMIUM_ISSUED,
    /** A premium actually paid (encaissement); default basis of commissions. */
    PREMIUM_COLLECTED,
    /** Cancellation of a collection (direct-debit rejection); reverses its line. */
    PREMIUM_CANCELLED,
    /** A premium exempted from payment. */
    PREMIUM_EXEMPTED,
    /** Subscription of the contract. */
    SUBSCRIPTION,
    /** Amendment changing the premium. */
    ENDORSEMENT,
    /** Termination of the contract or coverage; may trigger a clawback. */
    TERMINATION,
    /** Withdrawal during the cooling-off period. */
    WITHDRAWAL,
    /** Classed not-pursued; claws back 100% of earned lines. */
    NOT_PURSUED,
    /** Death of the member; no clawback. */
    DEATH,
    /** An unpaid-premium notice carrying its rank, which may break a discount. */
    UNPAID_NOTICE,
    /** Opening of a pre-litigation or litigation procedure. */
    LITIGATION_OPENED,
    /** Reinstatement of a suspended contract. */
    REINSTATEMENT,
    /** Initial retirement-savings payment. */
    INITIAL_PAYMENT,
    /** A free (voluntary) retirement-savings payment. */
    FREE_PAYMENT,
    /** A scheduled retirement-savings payment, basis of the retirement discount. */
    SCHEDULED_PAYMENT,
    /** An inbound transfer into a savings contract. */
    INBOUND_TRANSFER,
    /** An arbitrage between savings funds (CR-83). */
    ARBITRAGE,
    /** A fee levied on the outstanding balance. */
    OUTSTANDING_FEE,
    /** The average outstanding balance of the period, basis of outstanding commissions (CR-82). */
    OUTSTANDING_BALANCE,
    /** Suspension of a scheduled-payment plan, which triggers the retirement recall. */
    PLAN_SUSPENDED,
    /** A change of the savings plan. */
    PLAN_CHANGED,
    /** Total or partial surrender of a savings contract. */
    SURRENDER,
    /** An outbound transfer of a savings contract. */
    OUTBOUND_TRANSFER,
    /** Full early repayment of a borrower loan; triggers a linear recompute (CR-81). */
    EARLY_REPAYMENT,
    /** Collected file fees, carried by the gross amount (CR-77). */
    FILE_FEE_COLLECTED,
    /** Partial early repayment; no commission effect (H-47). */
    PARTIAL_EARLY_REPAYMENT,
    /** A commission received from an insurer by the entity (INBOUND). */
    INSURER_COMMISSION,
    /** A distributor commission already computed by a third party (EXTERNAL). */
    EXTERNAL_COMMISSION
}
