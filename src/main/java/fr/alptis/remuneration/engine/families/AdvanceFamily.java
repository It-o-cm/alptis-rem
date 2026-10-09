package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Advance (discount) family (section 6.4): the upfront remuneration paid in advance, computed on the
 * annualised premium — one {@code ADVANCE} line per nature (acquisition and servicing in upfront,
 * a single line in linear). The anticipation, cap and unpaid-rank trigger are carried by the
 * discount convention (section 5.4, {@code AdvanceAgreement}); the apurement, settlement and
 * clawback are driven by the engine.
 */
@ApplicationScoped
public class AdvanceFamily implements ComputingFamily {

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

    /**
     * {@inheritDoc}
     *
     * <p>Proposes the advance on the annualised premium: in upfront an acquisition and a servicing
     * {@code ADVANCE} line, in linear a single {@code ADVANCE} line. Each amount is rounded to the
     * cent (P13); a zero rate produces no line.</p>
     *
     * @param input the calculation input
     * @return the proposed advance lines
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal premium = input.annualisedPremium() == null ? BigDecimal.ZERO : input.annualisedPremium();
        List<ProposedLine> lines = new ArrayList<>();
        if (input.acquisitionRate() != null || input.servicingRate() != null) {
            addLine(lines, premium, input.acquisitionRate(), "ACQUISITION");
            addLine(lines, premium, input.servicingRate(), "SERVICING");
        } else {
            addLine(lines, premium, input.linearRate(), "LINEAR");
        }
        return lines;
    }

    /**
     * Adds an {@code ADVANCE} line for a nature when the rate is present and non-zero.
     *
     * @param lines   the accumulator
     * @param premium the annualised premium
     * @param rate    the rate of the nature, possibly null
     * @param part    the advanced nature (ACQUISITION, SERVICING or LINEAR)
     */
    private static void addLine(List<ProposedLine> lines, BigDecimal premium, BigDecimal rate, String part) {
        if (rate == null || rate.signum() == 0) {
            return;
        }
        BigDecimal amount = premium.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        String explanation = "{\"family\":\"ADVANCE\",\"part\":\"" + part + "\",\"annualisedPremium\":"
                + premium.toPlainString() + ",\"rate\":" + rate.toPlainString() + ",\"amount\":"
                + amount.toPlainString() + "}";
        lines.add(new ProposedLine(LineNature.ADVANCE, premium, rate, amount, explanation));
    }
}
