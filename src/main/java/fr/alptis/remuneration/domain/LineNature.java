package fr.alptis.remuneration.domain;

/** Nature of a commission line; never changes (section 5.5). */
public enum LineNature {
    LINEAR,
    ACQUISITION,
    SERVICING,
    ADVANCE,
    ADVANCE_ADJUSTMENT,
    CLAWBACK,
    RCD,
    RCP,
    COMPLEMENT,
    OUTSTANDING,
    ARBITRAGE,
    RETROCESSION,
    FEE,
    EXTERNAL,
    MANUAL,
    TRANSFER,
    INSURER
}
