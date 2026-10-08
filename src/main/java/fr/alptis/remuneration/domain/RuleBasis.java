package fr.alptis.remuneration.domain;

/**
 * Amount a rule version applies its rate to (section 5.6).
 */
public enum RuleBasis {
    /** The collected premium, hors taxes. */
    COLLECTED_PREMIUM,
    /** The issued premium (forecast). */
    ISSUED_PREMIUM,
    /** The annualised premium (for the advance). */
    ANNUALISED_PREMIUM,
    /** The average outstanding balance. */
    OUTSTANDING_BALANCE,
    /** A commission received, for a share or retrocession. */
    SOURCE_COMMISSION,
    /** A fixed amount carried by the parameters. */
    FIXED_AMOUNT,
    /** The annualised scheduled payments, net of fees (retirement). */
    NET_SCHEDULED_PAYMENTS
}
