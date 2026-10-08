package fr.alptis.remuneration.domain;

/**
 * Billing periodicity of a contract (section 5.2), used to spread an annualised premium over its cotisation period.
 */
public enum Periodicity {
    /** Billed monthly. */
    MONTHLY,
    /** Billed quarterly. */
    QUARTERLY,
    /** Billed every six months. */
    HALF_YEARLY,
    /** Billed yearly. */
    YEARLY,
    /** A single premium paid once. */
    SINGLE
}
