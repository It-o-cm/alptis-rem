package fr.alptis.remuneration.domain;

/**
 * Status of a contract or coverage over its life (section 5.2).
 */
public enum ContractStatus {
    /** Registered but not yet in force. */
    PENDING,
    /** In force. */
    IN_FORCE,
    /** Payments suspended or contract reduced (mise en réduction). */
    SUSPENDED,
    /** Terminated. */
    TERMINATED,
    /** Withdrawn during the cooling-off period. */
    WITHDRAWN,
    /** Classed not-pursued or refused. */
    NOT_PURSUED,
    /** Under litigation (contentieux). */
    LITIGATION
}
