package fr.alptis.remuneration.domain;

/**
 * Direction of a commission plan or line (section 5.4): received from an insurer, or owed to a distributor.
 */
public enum Direction {
    /** A commission received by the entity from an insurer. */
    INBOUND,
    /** A commission owed by the entity to a distributor. */
    OUTBOUND
}
