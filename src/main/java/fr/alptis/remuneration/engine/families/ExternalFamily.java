package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * External family (section 6.8): the commission is computed outside the system and carried by the
 * event (precomputed basis, rate and amount); the rule only attributes and attaches it. It takes no
 * parameters of its own, so its schema accepts an empty object.
 */
@ApplicationScoped
public class ExternalFamily implements CalculationFamily {

    /** JSON Schema of the external parameters: an object with no required property. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object"
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#EXTERNAL}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.EXTERNAL;
    }

    /**
     * {@inheritDoc}
     *
     * @return the external parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
