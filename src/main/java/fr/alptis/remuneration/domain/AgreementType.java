package fr.alptis.remuneration.domain;

/**
 * Nature of the contractual agreement that founds a rule or a discount (section 5.4).
 */
public enum AgreementType {
    /** A partnership agreement with a distributor. */
    PARTNERSHIP,
    /** An amendment opening the development surcommission (RCD). */
    RCD_AMENDMENT,
    /** An amendment opening the portfolio surcommission (RCP). */
    RCP_AMENDMENT,
    /** A discount (advance) agreement opening the escompte for some products. */
    ADVANCE_AGREEMENT,
    /** A protocol with an insurer governing commissions received (e.g. the UMR protocol). */
    INSURER_PROTOCOL,
    /** A mandate governing a retrocession to an agent (e.g. Promantis). */
    MANDATE
}
