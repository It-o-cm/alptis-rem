package fr.alptis.remuneration.domain;

/**
 * Staff college of a collective coverage (section 5.2); null when the coverage has no college. Collective
 *  health distinguishes colleges so that each may carry its own rate (CR-12).
 */
public enum StaffCategory {
    /** Executive staff (cadres). */
    EXECUTIVE,
    /** Non-executive staff (non-cadres). */
    NON_EXECUTIVE
}
