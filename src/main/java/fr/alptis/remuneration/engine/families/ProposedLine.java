package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.LineNature;

import java.math.BigDecimal;

/**
 * One commission line a family proposes for an event (D2): its nature, the basis and rate it used
 * (either may be null when the family does not use one), the rounded amount, and the explanation of
 * the calculation (D14), produced by the family itself. The engine decides the line's status,
 * beneficiary and destination; the family only proposes.
 *
 * @param nature      the line nature
 * @param basisAmount the basis used, or null
 * @param rate        the rate used, or null
 * @param amount      the rounded amount (P13)
 * @param explanation the calculation explanation, as a JSON object string
 */
public record ProposedLine(LineNature nature, BigDecimal basisAmount, BigDecimal rate, BigDecimal amount,
                           String explanation) {
}
