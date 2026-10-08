package fr.alptis.remuneration.domain;

/**
 * Processing status of a business event (section 5.3); an event is never modified, only superseded.
 */
public enum ProcessingStatus {
    /** Imported, awaiting the next calculation. */
    PENDING,
    /** Integrated but held, a referenced object or a rule being missing; replayed automatically later. */
    ON_HOLD,
    /** Processed by a calculation, having produced its lines. */
    PROCESSED,
    /** Processed but giving no commission. */
    NOT_COMMISSIONABLE
}
