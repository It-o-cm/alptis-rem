package fr.alptis.remuneration.domain;

/**
 * Kind of a payment beneficiary or cumulative-counter holder (section 5.1). Also used as the bank-account
 *  holder type. The beneficiary is not an object of its own: it is designated by a type and an id.
 */
public enum PayeeType {
    /** A production code of a distributor. */
    PRODUCTION_CODE,
    /** A distributor. */
    DISTRIBUTOR,
    /** A distributor group. */
    GROUP
}
