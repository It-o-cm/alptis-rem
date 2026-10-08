package fr.alptis.remuneration.domain;

/**
 * Type of a calculation run (section 5.5); all three share the same engine, only the write destination differs.
 */
public enum RunType {
    /** Provisional calculation on issued premiums. */
    FORECAST,
    /** Real calculation on collected premiums. */
    ACTUAL,
    /** Simulation written to a separate space, touching neither accounts nor entries. */
    SIMULATION
}
