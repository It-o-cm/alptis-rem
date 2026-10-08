package fr.alptis.remuneration.domain;

/**
 * Type of a received sales-force measure (section 8.14).
 */
public enum MeasureType {
    /** Absence days excluding leave and RTT. */
    ABSENCE,
    /** Production, broken down by market. */
    PRODUCTION,
    /** Commercial activity from the CRM (calls, meetings, quotes, new producers). */
    ACTIVITY,
    /** Gross margin. */
    MARGIN,
    /** Specifications received. */
    SPECIFICATIONS_RECEIVED
}
