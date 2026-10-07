package fr.alptis.remuneration.domain;

/** Kind of payment or cumulative-counter holder: production code, distributor or group (section 5.6). Also used as the bank-account holder type. */
public enum PayeeType {
    PRODUCTION_CODE,
    DISTRIBUTOR,
    GROUP
}
