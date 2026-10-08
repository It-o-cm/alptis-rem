package fr.alptis.remuneration.domain;

/**
 * Registration status of a distributor in the French ORIAS register (section 5.1); a struck-off status blocks payment.
 */
public enum OriasStatus {
    /** Currently registered with ORIAS. */
    REGISTERED,
    /** Struck off the ORIAS register; payments are blocked until re-registration (CR-62). */
    STRUCK_OFF
}
