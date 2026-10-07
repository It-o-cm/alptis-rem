package fr.alptis.remuneration.domain;

/** Status of a coverage advance position (section 5.6). */
public enum AdvanceStatus {
    PENDING,
    ACTIVE,
    SETTLED,
    CANCELLED
}
