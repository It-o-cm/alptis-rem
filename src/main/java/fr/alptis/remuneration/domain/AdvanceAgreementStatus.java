package fr.alptis.remuneration.domain;

/**
 * Status of a discount (advance) agreement (section 5.4).
 */
public enum AdvanceAgreementStatus {
    /** In force; opens the escompte for the covered products. */
    VALID,
    /** Suspended; new discounts are no longer granted. */
    SUSPENDED,
    /** Closed; see section 6.4 for the effect on running discounts. */
    CLOSED
}
