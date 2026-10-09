package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The read-only view a family receives to propose its lines (D2): the event's primitive data, the
 * rates the garantie carries (P9), the dates needed for first-year and prorata, the externally
 * computed amounts and the rule's parameters. A family never sees a persistent entity, so it is
 * tested without a database (CLAUDE tests rule).
 *
 * @param eventType          the event type
 * @param basis              the basis amount (P1), or null
 * @param linearRate         the chosen linear rate carried by the garantie, or null
 * @param acquisitionRate    the chosen acquisition rate, or null
 * @param servicingRate      the chosen servicing rate, or null
 * @param coverageEffectDate the coverage effect date (first-year anchor), or null
 * @param eventDate          the event date
 * @param periodFrom         the start of the premium's covered period, or null
 * @param periodTo           the end of the premium's covered period, or null
 * @param precomputedBasis   the externally computed basis, or null
 * @param precomputedRate    the externally computed rate, or null
 * @param precomputedAmount  the externally computed amount, or null
 * @param annualisedPremium  the annualised premium (section 6.4), or null
 * @param outputNature       the line nature the rule version declares it produces
 * @param parameters         the rule-version parameters, as a JSON object string
 * @param tiers              the scale tiers of a tiered rule, as a JSON array string, or null
 */
public record CalculationInput(EventType eventType, BigDecimal basis, BigDecimal linearRate, BigDecimal acquisitionRate,
                               BigDecimal servicingRate, LocalDate coverageEffectDate, LocalDate eventDate,
                               LocalDate periodFrom, LocalDate periodTo, BigDecimal precomputedBasis,
                               BigDecimal precomputedRate, BigDecimal precomputedAmount, BigDecimal annualisedPremium,
                               LineNature outputNature, String parameters, String tiers) {
}
