package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Outstanding family (section 6.9): an annual rate applied to the outstanding balance of a savings
 * contract, prorated over the period. Its parameters declare the annual rate.
 */
@ApplicationScoped
public class OutstandingFamily implements CalculationFamily {

    /** JSON Schema of the outstanding parameters: the annual rate on the balance. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "annualRate": {"type": "number", "minimum": 0, "maximum": 1}
              },
              "required": ["annualRate"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#OUTSTANDING}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.OUTSTANDING;
    }

    /**
     * {@inheritDoc}
     *
     * @return the outstanding parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
