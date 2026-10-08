package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * External family (section 6.10): the commission is computed outside the system and carried by the
 * event (precomputed basis, rate and amount); the rule only attributes and attaches it, without
 * recalculation. It takes no parameters of its own, so its schema accepts an empty object.
 */
@ApplicationScoped
public class ExternalFamily implements ComputingFamily {

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

    /**
     * {@inheritDoc}
     *
     * <p>Produces a single {@code EXTERNAL} line carrying the amount computed outside the system,
     * without recalculation (CR-51); a missing amount yields zero.</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal amount = (input.precomputedAmount() == null ? BigDecimal.ZERO : input.precomputedAmount())
                .setScale(2, RoundingMode.HALF_UP);
        String explanation = "{\"family\":\"EXTERNAL\",\"basis\":"
                + (input.precomputedBasis() == null ? "null" : input.precomputedBasis().toPlainString())
                + ",\"rate\":" + (input.precomputedRate() == null ? "null" : input.precomputedRate().toPlainString())
                + ",\"amount\":" + amount.toPlainString() + "}";
        return List.of(new ProposedLine(LineNature.EXTERNAL, input.precomputedBasis(), input.precomputedRate(),
                amount, explanation));
    }
}
