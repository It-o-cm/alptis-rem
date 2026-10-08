package fr.alptis.remuneration.domain;

/**
 * Which date a rule version is anchored on when selecting the applicable version (section 5.7).
 */
public enum VersionAnchor {
    /** Anchored on the event's effect date. */
    EVENT_DATE,
    /** Anchored on the coverage's effect date. */
    COVERAGE_EFFECT_DATE
}
