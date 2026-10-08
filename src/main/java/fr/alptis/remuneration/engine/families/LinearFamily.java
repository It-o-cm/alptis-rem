package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Linear family (section 6.1): a single rate applied to the basis, chosen from a grid of allowed
 * rates the garanties carry (P9). Its parameters declare the allowed linear rates.
 */
@ApplicationScoped
public class LinearFamily implements CalculationFamily {

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
}
