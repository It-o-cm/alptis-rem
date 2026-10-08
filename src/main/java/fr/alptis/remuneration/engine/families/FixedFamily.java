package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Fixed family (section 6.8): a fixed amount per event (handling fee, fee, flat remuneration),
 * independent of any basis. Its parameters declare the amount.
 */
@ApplicationScoped
public class FixedFamily implements CalculationFamily {

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
}
