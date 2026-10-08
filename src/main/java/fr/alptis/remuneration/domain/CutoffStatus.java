package fr.alptis.remuneration.domain;

/**
 * Status of a cut-off accounting extraction (section 5.6).
 */
public enum CutoffStatus {
    /** Generated at period closing. */
    GENERATED,
    /** Delivered to the SI Finance. */
    DELIVERED
}
