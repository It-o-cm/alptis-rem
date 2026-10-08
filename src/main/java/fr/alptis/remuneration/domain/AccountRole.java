package fr.alptis.remuneration.domain;

/**
 * Functional role of an accounting account (section 10.2); the mapping to each entity's chart of accounts
 *  is a parameter table, so the scheme carries roles rather than numbers.
 */
public enum AccountRole {
    /** Forecast commission expense. */
    FORECAST_EXPENSE,
    /** Provision for commissions to pay. */
    PROVISION,
    /** Earned commissions. */
    EARNED_COMMISSIONS,
    /** Distributor, commissions to pay. */
    DISTRIBUTOR_PAYABLE,
    /** Bank. */
    BANK,
    /** Advances to distributors (discount receivable). */
    DISTRIBUTOR_ADVANCES,
    /** Commission expense. */
    COMMISSION_EXPENSE,
    /** Clawed-back commissions (income). */
    CLAWBACK_INCOME,
    /** Insurer, commissions to receive. */
    INSURER_RECEIVABLE,
    /** Commission income. */
    COMMISSION_INCOME
}
