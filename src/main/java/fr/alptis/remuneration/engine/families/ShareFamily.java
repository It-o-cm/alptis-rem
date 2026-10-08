package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Share family (section 6.8): a quota of another commission already computed (for example a
 * retrocession of a share of the insurer commission). Its parameters name the source nature and the
 * share rate.
 */
@ApplicationScoped
public class ShareFamily implements CalculationFamily {

    /** JSON Schema of the share parameters: the source line nature and the applied rate. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "sourceNature": {"type": "string", "minLength": 1},
                "rate": {"type": "number", "minimum": 0, "maximum": 1}
              },
              "required": ["sourceNature", "rate"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#SHARE}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.SHARE;
    }

    /**
     * {@inheritDoc}
     *
     * @return the share parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }
}
