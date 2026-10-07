package fr.alptis.remuneration.domain;

/** Status of a distributor or production code (section 5.1); only ACTIVE allows payment. */
public enum PartnerStatus {
    CANDIDATE,
    ACTIVE,
    SUSPENDED,
    TERMINATED
}
