package fr.alptis.remuneration.domain;

/** Reason code of an import rejection (section 8.2). */
public enum RejectionReasonCode {
    STRUCTURE,
    TYPE,
    MISSING_FIELD,
    AMOUNT,
    FUTURE_DATE,
    DUPLICATE
}
