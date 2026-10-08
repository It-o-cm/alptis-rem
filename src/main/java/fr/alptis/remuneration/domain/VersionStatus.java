package fr.alptis.remuneration.domain;

/**
 * Lifecycle status of a rule version (section 5.4); an approved version is never overwritten and becomes
 *  active and retired on its own at its dates.
 */
public enum VersionStatus {
    /** In preparation; may be simulated but not used by a real calculation. */
    DRAFT,
    /** Approved by a second administrator; awaiting its start date. */
    APPROVED,
    /** In force at the current date. */
    ACTIVE,
    /** Past its end date; kept for replaying closed periods. */
    RETIRED
}
