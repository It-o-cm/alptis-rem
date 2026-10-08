package fr.alptis.remuneration.domain;

/**
 * Status of an export batch (section 5.6).
 */
public enum ExportBatchStatus {
    /** Generated and journalled. */
    GENERATED,
    /** Delivered to the neighbouring system. */
    DELIVERED,
    /** Generation failed. */
    FAILED
}
