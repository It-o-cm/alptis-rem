package fr.alptis.remuneration.engine.families;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Outstanding family (section 6.6, 6.9): an annual rate applied to the average outstanding balance of
 * a savings fund, prorated over the number of months of the period on twelve (CR-82). The balance is
 * the event basis and the covered period its {@code periodFrom}/{@code periodTo}; the annual rate is a
 * rule parameter. The produced nature is the one the rule declares ({@code OUTSTANDING} or
 * {@code RETROCESSION}).
 */
@ApplicationScoped
public class OutstandingFamily implements ComputingFamily {

    /** Jackson mapper used to read the annual rate. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * {@inheritDoc}
     *
     * <p>Produces one line: the balance times the annual rate times the number of months of the
     * period on twelve, rounded to the cent (P13). A period without bounds counts as a full year.</p>
     *
     * @param input the calculation input
     * @return a one-line proposal
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal balance = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal annualRate = annualRate(input.parameters());
        long months = months(input);
        BigDecimal amount = balance.multiply(annualRate).multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        LineNature nature = input.outputNature() == null ? LineNature.OUTSTANDING : input.outputNature();
        String explanation = "{\"family\":\"OUTSTANDING\",\"balance\":" + balance.toPlainString()
                + ",\"annualRate\":" + annualRate.toPlainString() + ",\"months\":" + months
                + ",\"amount\":" + amount.toPlainString() + "}";
        return List.of(new ProposedLine(nature, balance, annualRate, amount, explanation));
    }

    /**
     * Returns the number of months of the covered period, defaulting to a full year when a bound is
     * absent.
     *
     * @param input the calculation input
     * @return the number of months
     */
    private static long months(CalculationInput input) {
        if (input.periodFrom() == null || input.periodTo() == null) {
            return 12;
        }
        return ChronoUnit.MONTHS.between(input.periodFrom(), input.periodTo().plusDays(1));
    }

    /**
     * Reads the annual rate from the parameters, defaulting to zero when absent, null, blank or
     * malformed.
     *
     * @param parameters the parameters JSON
     * @return the annual rate
     */
    private static BigDecimal annualRate(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            JsonNode node = MAPPER.readTree(parameters).get("annualRate");
            return node == null ? BigDecimal.ZERO : node.decimalValue();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return BigDecimal.ZERO;
        }
    }
}
