package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Upfront family (section 6.2): an acquisition rate and a servicing rate, chosen from a grid of
 * allowed couples, with the length in days of the first commission year. Clawback on early
 * termination is carried by the rule version's clawback policy, not these parameters.
 */
@ApplicationScoped
public class UpfrontFamily implements CalculationFamily {

    /** JSON Schema of the upfront parameters: the grid of allowed rate couples and the first-year length. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "allowedRates": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "properties": {
                      "acquisition": {"type": "number", "minimum": 0, "maximum": 1},
                      "servicing": {"type": "number", "minimum": 0, "maximum": 1}
                    },
                    "required": ["acquisition", "servicing"]
                  }
                },
                "firstYearDays": {"type": "integer", "minimum": 1}
              },
              "required": ["allowedRates", "firstYearDays"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#UPFRONT}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.UPFRONT;
    }

    /**
     * {@inheritDoc}
     *
     * @return the upfront parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
