package fr.alptis.remuneration.domain;

/** Processing status of a business event (section 5.3). */
public enum ProcessingStatus {
    PENDING,
    ON_HOLD,
    PROCESSED,
    NOT_COMMISSIONABLE
}
