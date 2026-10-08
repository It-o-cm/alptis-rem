package fr.alptis.remuneration.domain;

/**
 * Family of a control definition (section 8.6).
 */
public enum ControlFamily {
    /** Expected data or flows missing. */
    COMPLETENESS,
    /** Inconsistent or implausible input data. */
    DATA_QUALITY,
    /** Calculation anomalies (e.g. rate out of grid). */
    CALCULATION,
    /** Discount anomalies. */
    ADVANCE,
    /** Clawback anomalies. */
    CLAWBACK,
    /** Surcommission anomalies. */
    SUPPLEMENT,
    /** Account anomalies (e.g. long-standing debtor). */
    ACCOUNT,
    /** Payment anomalies (e.g. bank details changed). */
    PAYMENT,
    /** Accounting anomalies. */
    ACCOUNTING,
    /** External-flow anomalies. */
    EXTERNAL_FLOW
}
