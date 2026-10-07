package fr.alptis.remuneration.domain;

/** Status of a contract or coverage (section 5.2). */
public enum ContractStatus {
    PENDING,
    IN_FORCE,
    SUSPENDED,
    TERMINATED,
    WITHDRAWN,
    NOT_PURSUED,
    LITIGATION
}
