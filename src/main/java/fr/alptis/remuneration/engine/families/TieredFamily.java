package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Tiered family (section 6.6): a scale on a cumulative counter (amount or count), marginal or whole.
 * The tiers themselves are carried by the rule version's {@code Scale}; the parameters name the
 * counter the cumulative measure feeds.
 */
@ApplicationScoped
public class TieredFamily implements CalculationFamily {

    /** JSON Schema of the tiered parameters: the counter code the scale reads. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "counterCode": {"type": "string", "minLength": 1}
              },
              "required": ["counterCode"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#TIERED}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.TIERED;
    }

    /**
     * {@inheritDoc}
     *
     * @return the tiered parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
