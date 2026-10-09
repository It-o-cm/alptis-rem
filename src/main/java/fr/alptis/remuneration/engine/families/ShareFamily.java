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
 * Share family (section 6.9): a quota of a commission already received, for a mandatary's
 * retrocession (a share of the insurer commission, CR-50) or a fund retrocession. The basis is the
 * received commission carried by the event; the rate is a rule parameter. Without a received
 * commission there is no retrocession.
 */
@ApplicationScoped
public class ShareFamily implements ComputingFamily {

    /** Jackson mapper used to read the share rate. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** JSON Schema of the share parameters: the source line nature and the applied rate. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "sourceNature": {"type": "string", "minLength": 1},
                "rate": {"type": "number", "minimum": 0, "maximum": 1}
              },
              "required": ["sourceNature", "rate"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#SHARE}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.SHARE;
    }

    /**
     * {@inheritDoc}
     *
     * @return the share parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces one line: the received commission (the basis) times the share rate, rounded to the
     * cent (P13), of the declared nature (defaulting to {@code RETROCESSION}).</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal basis = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal rate = rate(input.parameters());
        BigDecimal amount = basis.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        LineNature nature = input.outputNature() == null ? LineNature.RETROCESSION : input.outputNature();
        String explanation = "{\"family\":\"SHARE\",\"nature\":\"" + nature.name() + "\",\"basis\":"
                + basis.toPlainString() + ",\"rate\":" + rate.toPlainString() + ",\"amount\":"
                + amount.toPlainString() + "}";
        return List.of(new ProposedLine(nature, basis, rate, amount, explanation));
    }

    /**
     * Reads the share rate from the parameters, defaulting to zero when absent, null, blank or
     * malformed.
     *
     * @param parameters the parameters JSON
     * @return the share rate
     */
    private static BigDecimal rate(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            JsonNode node = MAPPER.readTree(parameters).get("rate");
            return node == null ? BigDecimal.ZERO : node.decimalValue();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return BigDecimal.ZERO;
        }
    }
}
