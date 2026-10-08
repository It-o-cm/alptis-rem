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
 * Fixed family (section 6.5, 6.8): a fixed amount per event (handling fee, fee, flat remuneration),
 * independent of any basis, with neither rate nor clawback. Its parameters declare the amount; the
 * rule version's output nature says what kind of line it is (for example {@code FEE}).
 */
@ApplicationScoped
public class FixedFamily implements ComputingFamily {

    /** Jackson mapper used to read the fixed amount from the parameters. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** JSON Schema of the fixed parameters: the amount paid per event. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "amount": {"type": "number", "minimum": 0}
              },
              "required": ["amount"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#FIXED}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.FIXED;
    }

    /**
     * {@inheritDoc}
     *
     * @return the fixed parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces a single line of the rule's output nature, carrying the fixed amount from the
     * parameters, with neither basis nor rate.</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal amount = readAmount(input.parameters()).setScale(2, RoundingMode.HALF_UP);
        LineNature nature = input.outputNature() == null ? LineNature.FEE : input.outputNature();
        String explanation = "{\"family\":\"FIXED\",\"amount\":" + amount.toPlainString() + "}";
        return List.of(new ProposedLine(nature, null, null, amount, explanation));
    }

    /**
     * Reads the {@code amount} from the parameters JSON, defaulting to zero when absent or malformed.
     *
     * @param parameters the parameters JSON
     * @return the amount
     */
    private static BigDecimal readAmount(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            JsonNode amount = MAPPER.readTree(parameters).get("amount");
            return amount == null ? BigDecimal.ZERO : amount.decimalValue();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return BigDecimal.ZERO;
        }
    }
}
