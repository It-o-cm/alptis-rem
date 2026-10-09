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
 * Linear family (section 6.2): one line per premium, the basis times a rate. The rate comes either
 * from the garantie, chosen from the grid of allowed rates and controlled against it (P9,
 * {@code rateSource = COVERAGE}), or straight from the rule when the rate is imposed and not grid
 * controlled (section 6.6: 1.20% from year six, 0.30% on an arbitrage, 2% of a back-to-school
 * operation; {@code rateSource = RULE}). The produced nature is the one the rule declares
 * ({@code LINEAR} or {@code ARBITRAGE}).
 */
@ApplicationScoped
public class LinearFamily implements ComputingFamily {

    /** Jackson mapper used to read the rule-driven rate and rank. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** JSON Schema of the linear parameters: the grid of allowed rates and the rule-driven options. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "allowedRates": {
                  "type": "array",
                  "minItems": 1,
                  "items": {"type": "number", "minimum": 0, "maximum": 1}
                },
                "rateSource": {"enum": ["COVERAGE", "RULE"]},
                "rate": {"type": "number", "minimum": 0, "maximum": 1},
                "seniorityOrigin": {"enum": ["EFFECT_DATE", "FIRST_SCHEDULED_PAYMENT"]},
                "minRankInCalendarYear": {"type": "integer", "minimum": 1}
              }
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#LINEAR}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.LINEAR;
    }

    /**
     * {@inheritDoc}
     *
     * @return the linear parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces a single line: the basis times the chosen rate, rounded to the cent (P13), of the
     * nature the rule declares (defaulting to {@code LINEAR}).</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal basis = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal ruleRate = ruleRate(input.parameters());
        BigDecimal rate = ruleRate != null ? ruleRate
                : (input.linearRate() == null ? BigDecimal.ZERO : input.linearRate());
        BigDecimal amount = basis.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        LineNature nature = input.outputNature() == null ? LineNature.LINEAR : input.outputNature();
        String explanation = "{\"family\":\"LINEAR\",\"nature\":\"" + nature.name() + "\",\"basis\":"
                + basis.toPlainString() + ",\"rate\":" + rate.toPlainString() + ",\"amount\":"
                + amount.toPlainString() + "}";
        return List.of(new ProposedLine(nature, basis, rate, amount, explanation));
    }

    /**
     * Reads the rate the rule itself imposes when {@code rateSource} is {@code RULE} (section 6.6,
     * 6.11); null when the rate comes from the garantie and is controlled against the grid (P9).
     *
     * @param parameters the parameters JSON
     * @return the rule-driven rate, or null
     */
    public static BigDecimal ruleRate(String parameters) {
        JsonNode params = read(parameters);
        if (params == null) {
            return null;
        }
        JsonNode source = params.get("rateSource");
        if (source == null || !"RULE".equals(source.asText())) {
            return null;
        }
        JsonNode rate = params.get("rate");
        return rate == null ? null : rate.decimalValue();
    }

    /**
     * Reads the minimum rank the event must reach in its calendar year for the same contract
     * (section 6.6: 2 for arbitrages); null when the rule applies to every event.
     *
     * @param parameters the parameters JSON
     * @return the minimum rank, or null
     */
    public static Integer minRankInCalendarYear(String parameters) {
        JsonNode params = read(parameters);
        if (params == null) {
            return null;
        }
        JsonNode rank = params.get("minRankInCalendarYear");
        return rank == null ? null : rank.asInt();
    }

    /**
     * Parses a parameters JSON object, returning null when it is null, blank or malformed.
     *
     * @param parameters the parameters JSON
     * @return the parsed node, or null
     */
    private static JsonNode read(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(parameters);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }
}
