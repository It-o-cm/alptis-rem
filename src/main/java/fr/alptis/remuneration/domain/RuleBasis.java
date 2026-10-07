package fr.alptis.remuneration.domain;

/** Basis a rule version applies its rate to (section 5.6). */
public enum RuleBasis {
    COLLECTED_PREMIUM,
    ISSUED_PREMIUM,
    ANNUALISED_PREMIUM,
    OUTSTANDING_BALANCE,
    SOURCE_COMMISSION,
    FIXED_AMOUNT,
    NET_SCHEDULED_PAYMENTS
}
