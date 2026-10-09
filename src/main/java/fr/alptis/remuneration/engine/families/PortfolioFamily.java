package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Portfolio family (section 6.8): a rate paid on a portfolio measure (the year's collected premiums)
 * when quality and volume conditions hold. The conditions and the applicable rate (flat or chosen
 * from a count scale) are resolved by the engine, which carries the rule's conditions and counters;
 * the family only applies the resolved rate to the basis.
 */
@ApplicationScoped
public class PortfolioFamily implements ComputingFamily {

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

    /**
     * {@inheritDoc}
     *
     * <p>Produces one line: the basis times the resolved rate (carried as the linear rate), rounded to
     * the cent (P13), of the declared nature (defaulting to {@code RCP}).</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal basis = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal rate = input.linearRate() == null ? BigDecimal.ZERO : input.linearRate();
        BigDecimal amount = basis.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        LineNature nature = input.outputNature() == null ? LineNature.RCP : input.outputNature();
        String explanation = "{\"family\":\"PORTFOLIO\",\"nature\":\"" + nature.name() + "\",\"basis\":"
                + basis.toPlainString() + ",\"rate\":" + rate.toPlainString() + ",\"amount\":"
                + amount.toPlainString() + "}";
        return List.of(new ProposedLine(nature, basis, rate, amount, explanation));
    }
}
