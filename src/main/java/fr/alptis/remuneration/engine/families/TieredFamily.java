package fr.alptis.remuneration.engine.families;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tiered family (section 6.6): a scale on a cumulative measure. For the retirement complementary
 * remuneration ({@code COMPLEMENT}) the scale is read in whole mode — the rate of the reached tier
 * applies to the whole measure, the lower bound of each tier being inclusive (CR-35). The tiers are
 * passed as a JSON array; the parameters name the counter the measure feeds.
 */
@ApplicationScoped
public class TieredFamily implements ComputingFamily {

    /** Jackson mapper used to read the tiers. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** JSON Schema of the tiered parameters: the counter code the scale reads. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "counterCode": {"type": "string", "minLength": 1}
              },
              "required": ["counterCode"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#TIERED}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.TIERED;
    }

    /**
     * {@inheritDoc}
     *
     * @return the tiered parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Selects the tier whose inclusive lower bound the measure reaches and applies its rate to the
     * whole measure (CR-35), rounded to the cent (P13). An empty scale yields a zero line.</p>
     *
     * @param input the calculation input, carrying the measure as basis and the tiers
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal measure = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal rate = rateFor(measure, input.tiers());
        BigDecimal amount = measure.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        LineNature nature = input.outputNature() == null ? LineNature.COMPLEMENT : input.outputNature();
        String explanation = "{\"family\":\"TIERED\",\"nature\":\"" + nature.name() + "\",\"measure\":"
                + measure.toPlainString() + ",\"rate\":" + rate.toPlainString() + ",\"amount\":"
                + amount.toPlainString() + "}";
        return List.of(new ProposedLine(nature, measure, rate, amount, explanation));
    }

    /**
     * Returns the rate of the highest tier whose inclusive lower bound the measure reaches, or zero
     * when the scale is absent, blank, malformed or unreached.
     *
     * @param measure the measure
     * @param tiers   the tiers JSON array string, or null
     * @return the applicable whole-measure rate
     */
    public static BigDecimal rateFor(BigDecimal measure, String tiers) {
        if (tiers == null || tiers.isBlank()) {
            return BigDecimal.ZERO;
        }
        JsonNode array;
        try {
            array = MAPPER.readTree(tiers);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return BigDecimal.ZERO;
        }
        if (!array.isArray()) {
            return BigDecimal.ZERO;
        }
        BigDecimal best = BigDecimal.ZERO;
        BigDecimal bestLower = null;
        for (JsonNode tier : array) {
            BigDecimal lower = tier.path("lowerBound").decimalValue();
            if (measure.compareTo(lower) >= 0 && (bestLower == null || lower.compareTo(bestLower) > 0)) {
                bestLower = lower;
                best = tier.path("rate").decimalValue();
            }
        }
        return best;
    }
}
