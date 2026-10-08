package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Advance (discount) family (section 6.4): the upfront remuneration paid in advance under a
 * discount convention. Its grid of allowed rate couples and first-year length mirror the upfront
 * family; the anticipation, cap and unpaid-rank trigger are carried by the discount convention
 * (section 5.4, {@code AdvanceAgreement}).
 */
@ApplicationScoped
public class AdvanceFamily implements CalculationFamily {

    /** JSON Schema of the advance parameters: the allowed rate couples and the first-year length. */
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
     * @return {@link RuleFamily#ADVANCE}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.ADVANCE;
    }

    /**
     * {@inheritDoc}
     *
     * @return the advance parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
