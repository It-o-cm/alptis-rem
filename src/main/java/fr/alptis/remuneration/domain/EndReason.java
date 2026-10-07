package fr.alptis.remuneration.domain;

/** Reason a contract or coverage ended (section 5.6). */
public enum EndReason {
    TERMINATION,
    WITHDRAWAL,
    NOT_PURSUED,
    DEATH,
    EARLY_REPAYMENT,
    SURRENDER,
    OUTBOUND_TRANSFER
}
