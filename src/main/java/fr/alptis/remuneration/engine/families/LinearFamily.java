package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Linear family (section 6.2): one line per premium, the basis times the linear rate the garantie
 * carries (P9), chosen from a grid of allowed rates. Its parameters declare the allowed linear rates.
 */
@ApplicationScoped
public class LinearFamily implements ComputingFamily {

    /** JSON Schema of the linear parameters: the grid of allowed rates. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "allowedRates": {
                  "type": "array",
                  "minItems": 1,
                  "items": {"type": "number", "minimum": 0, "maximum": 1}
                }
              },
              "required": ["allowedRates"]
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
     * <p>Produces a single {@code LINEAR} line: the basis times the chosen linear rate, rounded to
     * the cent (P13).</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal basis = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal rate = input.linearRate() == null ? BigDecimal.ZERO : input.linearRate();
        BigDecimal amount = basis.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        String explanation = "{\"family\":\"LINEAR\",\"basis\":" + basis.toPlainString()
                + ",\"rate\":" + rate.toPlainString() + ",\"amount\":" + amount.toPlainString() + "}";
        return List.of(new ProposedLine(LineNature.LINEAR, basis, rate, amount, explanation));
    }
}
