package fr.alptis.remuneration.domain;

/**
 * Status of a distributor's enrollment to a reserved rule (section 5.4).
 */
public enum EnrollmentStatus {
    /** Recorded but inactive until its agreement is signed. */
    PENDING_SIGNATURE,
    /** Active; the reserved rule applies to the distributor. */
    ACTIVE,
    /** Ended. */
    ENDED
}
