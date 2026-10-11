package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the core calculation families (section 6.2, 6.5, 6.10): the proposals of the
 * linear, fixed and external families, with their null-input branches. No database, no container.
 */
class FamilyProposeTest {

    private final LinearFamily linear = new LinearFamily();
    private final FixedFamily fixed = new FixedFamily();
    private final ExternalFamily external = new ExternalFamily();

    /** Builds an input with only the fields a core family reads. */
    private static CalculationInput input(BigDecimal basis, BigDecimal linearRate, LineNature nature,
                                          String parameters, BigDecimal pBasis, BigDecimal pRate, BigDecimal pAmount) {
        return new CalculationInput(EventType.PREMIUM_COLLECTED, basis, linearRate, null, null, null, null, null, null,
                pBasis, pRate, pAmount, null, nature, parameters, null);
    }

    /**
     * CR-01: a linear 15% rule on a 100 € basis yields one LINEAR line of 15,00 €.
     */
    @Test
    void cr01_linear() {
        List<ProposedLine> lines = linear.propose(
                input(new BigDecimal("100.00"), new BigDecimal("0.15"), null, null, null, null, null));
        assertEquals(1, lines.size());
        assertEquals(LineNature.LINEAR, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("15.00")));
    }

    /**
     * A linear rule with a null basis and rate proposes a zero line without crashing.
     */
    @Test
    void linear_tolerates_null_basis_and_rate() {
        List<ProposedLine> lines = linear.propose(input(null, null, null, null, null, null, null));
        assertEquals(0, lines.get(0).amount().compareTo(BigDecimal.ZERO));
    }

    /**
     * CR-77: a fixed rule carries its amount as a FEE line, with neither basis nor rate.
     */
    @Test
    void cr77_fixed_fee() {
        List<ProposedLine> lines = fixed.propose(
                input(null, null, LineNature.FEE, "{\"amount\":300}", null, null, null));
        assertEquals(LineNature.FEE, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("300.00")));
        assertEquals(null, lines.get(0).rate());
        assertEquals(null, lines.get(0).basisAmount());
    }

    /**
     * The fixed family defaults to a zero FEE line when the amount, parameters or nature are absent
     * or malformed.
     */
    @Test
    void fixed_defaults_when_amount_absent() {
        assertEquals(0, fixed.propose(input(null, null, null, null, null, null, null)).get(0).amount()
                .compareTo(BigDecimal.ZERO));
        assertEquals(LineNature.FEE, fixed.propose(input(null, null, null, "{}", null, null, null)).get(0).nature());
        assertEquals(0, fixed.propose(input(null, null, LineNature.FEE, "{bad", null, null, null)).get(0).amount()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, fixed.propose(input(null, null, LineNature.FEE, "   ", null, null, null)).get(0).amount()
                .compareTo(BigDecimal.ZERO));
    }

    /**
     * A linear operation applies its own rule-driven rate (section 6.6, 6.11) instead of the
     * garantie's; the reader tolerates a null, blank or malformed parameter string, an absent
     * {@code rateSource}, a non-RULE source and an absent rate.
     */
    @Test
    void linear_rule_driven_rate() {
        List<ProposedLine> lines = linear.propose(input(new BigDecimal("100.00"), new BigDecimal("0.18"), null,
                "{\"rateSource\":\"RULE\",\"rate\":0.02}", null, null, null));
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("2.00")));
        assertEquals(0, LinearFamily.ruleRate("{\"rateSource\":\"RULE\",\"rate\":0.02}").compareTo(new BigDecimal("0.02")));
        assertEquals(null, LinearFamily.ruleRate(null));
        assertEquals(null, LinearFamily.ruleRate("   "));
        assertEquals(null, LinearFamily.ruleRate("{bad"));
        assertEquals(null, LinearFamily.ruleRate("{}"));
        assertEquals(null, LinearFamily.ruleRate("{\"rateSource\":\"COVERAGE\"}"));
        assertEquals(null, LinearFamily.ruleRate("{\"rateSource\":\"RULE\"}"));
    }

    /**
     * CR-83: a linear rule declaring an {@code ARBITRAGE} output produces an {@code ARBITRAGE} line at
     * the rule-driven rate; the minimum-rank reader returns the parameter, or null when absent or the
     * parameters are missing.
     */
    @Test
    void linear_arbitrage_nature_and_rank() {
        List<ProposedLine> lines = linear.propose(input(new BigDecimal("10000.00"), null, LineNature.ARBITRAGE,
                "{\"rateSource\":\"RULE\",\"rate\":0.003,\"minRankInCalendarYear\":2}", null, null, null));
        assertEquals(LineNature.ARBITRAGE, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("30.00")));
        assertEquals(2, LinearFamily.minRankInCalendarYear(
                "{\"rateSource\":\"RULE\",\"rate\":0.003,\"minRankInCalendarYear\":2}"));
        assertEquals(null, LinearFamily.minRankInCalendarYear("{}"));
        assertEquals(null, LinearFamily.minRankInCalendarYear(null));
        // Seniority parameters (section 6.6): the minimum seniority and the origin flag.
        assertEquals(5, LinearFamily.minSeniorityYears(
                "{\"minSeniorityYears\":5,\"seniorityOrigin\":\"FIRST_SCHEDULED_PAYMENT\"}"));
        assertEquals(null, LinearFamily.minSeniorityYears("{}"));
        assertEquals(null, LinearFamily.minSeniorityYears(null));
        assertTrue(LinearFamily.seniorityFromFirstScheduledPayment(
                "{\"seniorityOrigin\":\"FIRST_SCHEDULED_PAYMENT\"}"));
        assertFalse(LinearFamily.seniorityFromFirstScheduledPayment("{\"seniorityOrigin\":\"EFFECT_DATE\"}"));
        assertFalse(LinearFamily.seniorityFromFirstScheduledPayment("{}"));
        assertFalse(LinearFamily.seniorityFromFirstScheduledPayment(null));
    }

    /**
     * CR-51: an external rule carries the precomputed amount as an EXTERNAL line, without recalculation.
     */
    @Test
    void cr51_external() {
        List<ProposedLine> lines = external.propose(input(null, null, null, null,
                new BigDecimal("500.00"), new BigDecimal("0.035"), new BigDecimal("17.50")));
        assertEquals(LineNature.EXTERNAL, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("17.50")));
        assertEquals(0, lines.get(0).basisAmount().compareTo(new BigDecimal("500.00")));
    }

    /**
     * The external family proposes a zero line when the event carries no precomputed amount.
     */
    @Test
    void external_tolerates_missing_amounts() {
        List<ProposedLine> lines = external.propose(input(null, null, null, null, null, null, null));
        assertEquals(0, lines.get(0).amount().compareTo(BigDecimal.ZERO));
        assertEquals(null, lines.get(0).basisAmount());
        assertEquals(null, lines.get(0).rate());
    }
}
