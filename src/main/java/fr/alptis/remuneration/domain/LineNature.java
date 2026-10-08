package fr.alptis.remuneration.domain;

/**
 * Nature of a commission line (section 5.5); it says what the line is and never changes. The offset of a
 *  discount is not a nature but the {@link LineStatus#OFFSET} status of an earned line.
 */
public enum LineNature {
    /** A linear commission. */
    LINEAR,
    /** The first-year acquisition part of a précompté commission. */
    ACQUISITION,
    /** The lifelong servicing part of a précompté commission. */
    SERVICING,
    /** An advance (escompte) paid up front, or a retirement supplement. */
    ADVANCE,
    /** A positive or negative adjustment when a discount is settled or broken. */
    ADVANCE_ADJUSTMENT,
    /** A clawback of an already-earned, paid or offset commission. */
    CLAWBACK,
    /** A development surcommission (RCD). */
    RCD,
    /** A portfolio surcommission (RCP). */
    RCP,
    /** A retirement complementary remuneration. */
    COMPLEMENT,
    /** A commission on the outstanding balance. */
    OUTSTANDING,
    /** A commission on a savings arbitrage. */
    ARBITRAGE,
    /** A retrocession computed on a received commission or fund fees. */
    RETROCESSION,
    /** A file fee or honorarium, with no rate or clawback policy (CR-77). */
    FEE,
    /** A commission already computed by a third party. */
    EXTERNAL,
    /** A manual adjustment line. */
    MANUAL,
    /** An offset line between two accounts of the same distributor (compensation). */
    TRANSFER,
    /** A commission received from an insurer (INBOUND). */
    INSURER
}
