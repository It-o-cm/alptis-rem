package fr.alptis.remuneration.domain;

/** Status of an import batch (section 5.6). */
public enum ImportBatchStatus {
    RECEIVED,
    LOADED,
    PARTIALLY_LOADED,
    REFUSED
}
