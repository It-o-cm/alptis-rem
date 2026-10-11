package fr.alptis.remuneration.engine.families;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleFamily;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Upfront family (section 6.2): an acquisition rate and a servicing rate, chosen from a grid of
 * allowed couples, with the length in days of the first commission year. The acquisition line is
 * produced only for the part of the premium's covered period that falls within the first 365 days
 * from the coverage effect date, prorated by day; the servicing line is always produced. Clawback on
 * early termination is carried by the rule version's clawback policy, not these parameters.
 */
@ApplicationScoped
public class UpfrontFamily implements ComputingFamily {

    /** Default first-year length in days when the parameters omit it. */
    private static final int DEFAULT_FIRST_YEAR_DAYS = 365;

    /** Jackson mapper used to read the first-year length. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** JSON Schema of the upfront parameters: the grid of allowed rate couples and the first-year length. */
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
                "firstYearDays": {"type": "integer", "minimum": 1},
                "earlyRepaymentMonths": {"type": "integer", "minimum": 1},
                "recalculationRate": {"type": "number", "minimum": 0, "maximum": 1}
              },
              "required": ["allowedRates", "firstYearDays"]
            }
            """;

    /**
     * {@inheritDoc}
     *
     * @return {@link RuleFamily#UPFRONT}
     */
    @Override
    public RuleFamily code() {
        return RuleFamily.UPFRONT;
    }

    /**
     * {@inheritDoc}
     *
     * @return the upfront parameters schema
     */
    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces the servicing line (always) and, prorated over the first-year days, the
     * acquisition line (section 6.2). Each amount is rounded to the cent (P13).</p>
     *
     * @param input the calculation input
     * @return the proposed lines
     */
    @Override
    public List<ProposedLine> propose(CalculationInput input) {
        BigDecimal basis = input.basis() == null ? BigDecimal.ZERO : input.basis();
        BigDecimal ta = input.acquisitionRate() == null ? BigDecimal.ZERO : input.acquisitionRate();
        BigDecimal ts = input.servicingRate() == null ? BigDecimal.ZERO : input.servicingRate();
        BigDecimal firstYearFraction = firstYearFraction(input);

        List<ProposedLine> lines = new ArrayList<>();
        BigDecimal acquisition = basis.multiply(ta).multiply(firstYearFraction).setScale(2, RoundingMode.HALF_UP);
        if (firstYearFraction.signum() > 0) {
            String explanation = "{\"family\":\"UPFRONT\",\"part\":\"ACQUISITION\",\"basis\":" + basis.toPlainString()
                    + ",\"rate\":" + ta.toPlainString() + ",\"firstYearFraction\":" + firstYearFraction.toPlainString()
                    + ",\"amount\":" + acquisition.toPlainString() + "}";
            lines.add(new ProposedLine(LineNature.ACQUISITION, basis, ta, acquisition, explanation));
        }
        BigDecimal servicing = basis.multiply(ts).setScale(2, RoundingMode.HALF_UP);
        String explanation = "{\"family\":\"UPFRONT\",\"part\":\"SERVICING\",\"basis\":" + basis.toPlainString()
                + ",\"rate\":" + ts.toPlainString() + ",\"amount\":" + servicing.toPlainString() + "}";
        lines.add(new ProposedLine(LineNature.SERVICING, basis, ts, servicing, explanation));
        return lines;
    }

    /**
     * Computes the fraction of the premium's covered period that falls within the first year: the
     * whole premium (1) or none (0) when it carries no covered period (H-45, by event date), or the
     * prorata of the overlapping days otherwise.
     *
     * @param input the calculation input
     * @return the first-year fraction, between 0 and 1
     */
    private static BigDecimal firstYearFraction(CalculationInput input) {
        LocalDate effect = input.coverageEffectDate();
        if (effect == null) {
            return BigDecimal.ONE;
        }
        LocalDate firstYearEnd = effect.plusDays(firstYearDays(input.parameters()));
        if (input.periodFrom() == null || input.periodTo() == null) {
            return input.eventDate() != null && input.eventDate().isBefore(firstYearEnd)
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        long totalDays = ChronoUnit.DAYS.between(input.periodFrom(), input.periodTo()) + 1;
        LocalDate overlapStart = max(input.periodFrom(), effect);
        LocalDate overlapEnd = min(input.periodTo(), firstYearEnd.minusDays(1));
        long firstYearInDays = overlapEnd.isBefore(overlapStart) ? 0
                : ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
        if (firstYearInDays == 0) {
            return BigDecimal.ZERO;
        }
        if (firstYearInDays >= totalDays) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(firstYearInDays).divide(BigDecimal.valueOf(totalDays), 10, RoundingMode.HALF_UP);
    }

    /**
     * Reads the first-year length from the parameters, defaulting when absent or malformed.
     *
     * @param parameters the parameters JSON
     * @return the first-year length in days
     */
    private static int firstYearDays(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return DEFAULT_FIRST_YEAR_DAYS;
        }
        try {
            JsonNode node = MAPPER.readTree(parameters).get("firstYearDays");
            return node == null ? DEFAULT_FIRST_YEAR_DAYS : node.asInt(DEFAULT_FIRST_YEAR_DAYS);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return DEFAULT_FIRST_YEAR_DAYS;
        }
    }

    /**
     * Reads the borrower early-repayment window in months (section 6.5): the number of months during
     * which an early repayment triggers the linear recalculation; null when the rule is not a borrower
     * rule.
     *
     * @param parameters the parameters JSON
     * @return the window in months, or null
     */
    public static Integer earlyRepaymentMonths(String parameters) {
        JsonNode node = node(parameters, "earlyRepaymentMonths");
        return node == null ? null : node.asInt();
    }

    /**
     * Reads the borrower recalculation rate (section 6.5): the standard linear rate applied to the
     * collected premiums when an early repayment falls within the window; null when absent.
     *
     * @param parameters the parameters JSON
     * @return the recalculation rate, or null
     */
    public static BigDecimal recalculationRate(String parameters) {
        JsonNode node = node(parameters, "recalculationRate");
        return node == null ? null : node.decimalValue();
    }

    /**
     * Reads a named node from the parameters JSON, returning null when absent or malformed.
     *
     * @param parameters the parameters JSON
     * @param field      the field name
     * @return the node, or null
     */
    private static JsonNode node(String parameters, String field) {
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(parameters).get(field);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Returns the later of two dates.
     *
     * @param a the first date
     * @param b the second date
     * @return the later date
     */
    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /**
     * Returns the earlier of two dates.
     *
     * @param a the first date
     * @param b the second date
     * @return the earlier date
     */
    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
