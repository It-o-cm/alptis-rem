package fr.alptis.remuneration.domain;

/** Functional role of an accounting account (section 10.2). */
public enum AccountRole {
    FORECAST_EXPENSE,
    PROVISION,
    EARNED_COMMISSIONS,
    DISTRIBUTOR_PAYABLE,
    BANK,
    DISTRIBUTOR_ADVANCES,
    COMMISSION_EXPENSE,
    CLAWBACK_INCOME,
    INSURER_RECEIVABLE,
    COMMISSION_INCOME
}
