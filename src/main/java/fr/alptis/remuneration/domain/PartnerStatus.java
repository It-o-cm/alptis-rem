package fr.alptis.remuneration.domain;

/**
 * Lifecycle status of a distributor (within an entity) or of a production code (section 5.1).
 *  Only {@link #ACTIVE} allows payment; the other three block payment without stopping calculation.
 */
public enum PartnerStatus {
    /** Being codified in the CRM, not yet confirmed. */
    CANDIDATE,
    /** Confirmed partnership; the only status that allows a payment to be issued. */
    ACTIVE,
    /** Temporarily suspended (e.g. ORIAS struck off); calculation continues but payment is blocked. */
    SUSPENDED,
    /** Partnership closed; calculation continues for trailing events but payment is blocked. */
    TERMINATED
}
