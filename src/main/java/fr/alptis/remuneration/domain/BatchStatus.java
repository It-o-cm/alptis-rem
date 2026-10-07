package fr.alptis.remuneration.domain;

/** Status of a payment batch (section 5.6). */
public enum BatchStatus {
    DRAFT,
    VALIDATED,
    ISSUED,
    SETTLED
}
