package fr.alptis.remuneration.domain;

/**
 * Role of an insurer or delegated manager towards the group (section 5.1).
 */
public enum InsurerRole {
    /** Bears the insurance risk of the product. */
    RISK_CARRIER,
    /** Manages the contracts and may produce already-computed commission files (e.g. UMR). */
    DELEGATED_MANAGER
}
