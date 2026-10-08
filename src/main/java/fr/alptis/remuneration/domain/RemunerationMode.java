package fr.alptis.remuneration.domain;

/**
 * Remuneration mode chosen for a coverage (section 5.2); the escompte (advance) is an orthogonal option.
 */
public enum RemunerationMode {
    /** The same rate every year, applied to the collected premium. */
    LINEAR,
    /** An acquisition commission the first year plus a servicing commission for life (précompté). */
    UPFRONT
}
