package fr.alptis.remuneration.domain;

/**
 * Role of a production code in the assignment of a coverage (section 5.2).
 */
public enum AssignmentRole {
    /** Main broker holding the coverage. */
    MAIN,
    /** Co-broker sharing the coverage's remuneration (co-courtage). */
    CO_BROKER
}
