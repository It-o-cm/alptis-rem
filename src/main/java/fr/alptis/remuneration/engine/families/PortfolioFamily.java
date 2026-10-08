package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Portfolio family (section 6.7): a rate paid on a portfolio measure when quality and volume
 * conditions hold. The conditions are carried by the rule version's {@code Condition} rows; the
 * parameters declare the rate.
 */
@ApplicationScoped
public class PortfolioFamily implements CalculationFamily {

    /** JSON Schema of the portfolio parameters: the applied rate. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "rate": {"type": "number", "minimum": 0, "maximum": 1}
              },
              "required": ["rate"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#PORTFOLIO}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.PORTFOLIO;
    }

    /**
     * {@inheritDoc}
     *
     * @return the portfolio parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
