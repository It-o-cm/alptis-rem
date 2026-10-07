package fr.alptis.remuneration.domain;

/** Status of a commission line; progresses over time (section 5.5). */
public enum LineStatus {
    FORECAST,
    EARNED,
    PAYABLE,
    PAID,
    OFFSET,
    CANCELLED
}
