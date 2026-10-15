package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for the savings families (section 6.6): the outstanding commission prorated over
 * the period (CR-82) and the tiered complement selected in whole mode (CR-35), with their null and
 * malformed branches. No database, no container.
 */
class SavingsFamilyProposeTest {

    private final OutstandingFamily outstanding = new OutstandingFamily();
    private final TieredFamily tiered = new TieredFamily();

    /** Builds an input for an outstanding or tiered proposal. */
    private static CalculationInput input(BigDecimal basis, LocalDate from, LocalDate to, LineNature nature,
                                          String parameters, String tiers) {
        return new CalculationInput(EventType.OUTSTANDING_BALANCE, basis, null, null, null, null, null, from, to,
                null, null, null, null, nature, parameters, tiers);
    }

    /**
     * CR-82: a 0,30% annual rate on a 20 000 € balance over a quarterly file yields 15,00 €.
     */
    @Test
    void cr82_outstanding_quarter() {
        List<ProposedLine> lines = outstanding.propose(input(new BigDecimal("20000.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null, "{\"annualRate\":0.003}", null));
        assertEquals(LineNature.OUTSTANDING, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("15.00")));
    }

    /**
     * Without a covered period the balance is charged for a full year; the declared nature and a
     * null, blank or malformed rate are honoured.
     */
    @Test
    void outstanding_full_year_and_edges() {
        List<ProposedLine> full = outstanding.propose(input(new BigDecimal("10000.00"), null, null,
                LineNature.RETROCESSION, "{\"annualRate\":0.003}", null));
        assertEquals(LineNature.RETROCESSION, full.get(0).nature());
        assertEquals(0, full.get(0).amount().compareTo(new BigDecimal("30.00")));
        assertEquals(0, outstanding.propose(input(new BigDecimal("10000.00"), null, null, null, null, null))
                .get(0).amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, outstanding.propose(input(new BigDecimal("10000.00"), null, null, null, "   ", null))
                .get(0).amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, outstanding.propose(input(new BigDecimal("10000.00"), null, null, null, "{bad", null))
                .get(0).amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, outstanding.propose(input(new BigDecimal("10000.00"), null, null, null, "{}", null))
                .get(0).amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, outstanding.propose(input(null, null, null, null, "{\"annualRate\":0.003}", null))
                .get(0).amount().compareTo(BigDecimal.ZERO));
        // A single missing bound also counts as a full year.
        assertEquals(0, outstanding.propose(input(new BigDecimal("10000.00"), LocalDate.of(2026, 1, 1), null, null,
                "{\"annualRate\":0.003}", null)).get(0).amount().compareTo(new BigDecimal("30.00")));
        assertEquals(0, outstanding.propose(input(new BigDecimal("10000.00"), null, LocalDate.of(2026, 3, 31), null,
                "{\"annualRate\":0.003}", null)).get(0).amount().compareTo(new BigDecimal("30.00")));
    }

    /** The tiers of the retirement complement scale. */
    private static final String TIERS =
            "[{\"lowerBound\":0,\"rate\":0.10},{\"lowerBound\":10000,\"rate\":0.12},"
                    + "{\"lowerBound\":14000,\"rate\":0.15}]";

    /**
     * CR-35: a whole-measure scale applies the reached tier's rate to the production; below 10 000 €
     * the first tier (10%) applies, so 7 039,80 € gives 703,98 €.
     */
    @Test
    void cr35_complement_whole() {
        List<ProposedLine> lines = tiered.propose(
                input(new BigDecimal("7039.80"), null, null, null, "{\"counterCode\":\"PER\"}", TIERS));
        assertEquals(LineNature.COMPLEMENT, lines.get(0).nature());
        assertEquals(0, lines.get(0).rate().compareTo(new BigDecimal("0.10")));
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("703.98")));
    }

    /**
     * The whole-measure selection climbs to the reached tier and honours the declared nature; an
     * absent, blank, malformed, non-array or unreached scale yields a zero rate.
     */
    @Test
    void tiered_selection_and_edges() {
        assertEquals(0, tiered.propose(input(new BigDecimal("15000"), null, null, LineNature.RCD,
                "{\"counterCode\":\"X\"}", TIERS)).get(0).rate().compareTo(new BigDecimal("0.15")));
        assertEquals(LineNature.RCD, tiered.propose(input(new BigDecimal("15000"), null, null, LineNature.RCD,
                "{\"counterCode\":\"X\"}", TIERS)).get(0).nature());
        assertEquals(0, TieredFamily.rateFor(new BigDecimal("100"), null).compareTo(BigDecimal.ZERO));
        assertEquals(0, TieredFamily.rateFor(new BigDecimal("100"), "   ").compareTo(BigDecimal.ZERO));
        assertEquals(0, TieredFamily.rateFor(new BigDecimal("100"), "{bad").compareTo(BigDecimal.ZERO));
        assertEquals(0, TieredFamily.rateFor(new BigDecimal("100"), "{}").compareTo(BigDecimal.ZERO));
        assertEquals(0, TieredFamily.rateFor(new BigDecimal("100"),
                "[{\"lowerBound\":500,\"rate\":0.2}]").compareTo(BigDecimal.ZERO));
        assertEquals(0, tiered.propose(input(null, null, null, null, "{\"counterCode\":\"X\"}", TIERS))
                .get(0).amount().compareTo(BigDecimal.ZERO));
        // An out-of-order scale keeps the highest reached lower bound, not the last read tier.
        assertEquals(0, TieredFamily.rateFor(new BigDecimal("20000"),
                "[{\"lowerBound\":0,\"rate\":0.10},{\"lowerBound\":14000,\"rate\":0.15},"
                        + "{\"lowerBound\":10000,\"rate\":0.12}]").compareTo(new BigDecimal("0.15")));
    }
}
