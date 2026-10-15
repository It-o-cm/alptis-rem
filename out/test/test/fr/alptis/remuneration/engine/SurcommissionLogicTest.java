package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.ConditionOperator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests of the surcommission logic (sections 6.7, 6.8): the condition comparison for every
 * operator and both outcomes, and the marginal development entitlement with its from-first-euro
 * override, open tiers and malformed input. No database, no container.
 */
class SurcommissionLogicTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal FIVE = new BigDecimal("5");

    /**
     * Each operator compares the observed value to the threshold, true and false.
     */
    @Test
    void compare_operators() {
        assertTrue(CalculationEngine.compare(FIVE, ConditionOperator.LT, TEN));
        assertFalse(CalculationEngine.compare(TEN, ConditionOperator.LT, FIVE));
        assertTrue(CalculationEngine.compare(TEN, ConditionOperator.LE, TEN));
        assertFalse(CalculationEngine.compare(TEN, ConditionOperator.LE, FIVE));
        assertTrue(CalculationEngine.compare(TEN, ConditionOperator.GT, FIVE));
        assertFalse(CalculationEngine.compare(FIVE, ConditionOperator.GT, TEN));
        assertTrue(CalculationEngine.compare(TEN, ConditionOperator.GE, TEN));
        assertFalse(CalculationEngine.compare(FIVE, ConditionOperator.GE, TEN));
        assertTrue(CalculationEngine.compare(TEN, ConditionOperator.EQ, TEN));
        assertFalse(CalculationEngine.compare(TEN, ConditionOperator.EQ, FIVE));
    }

    /** The santé development barème, as serialised for the engine. */
    private static final String SANTE =
            "[{\"lowerBound\":20000,\"rate\":0.03,\"upperBound\":45000},"
                    + "{\"lowerBound\":45000,\"rate\":0.04,\"upperBound\":75000},"
                    + "{\"lowerBound\":75000,\"rate\":0.05,\"upperBound\":100000},"
                    + "{\"lowerBound\":100001,\"rate\":0.05,\"fromFirstUnit\":true}]";

    /**
     * The marginal entitlement sums each tier's slice, reaching 1 350 € at 60 000 € and 3 200 € at
     * 100 000 €, and switches to the whole measure once the from-first-euro tier is reached.
     */
    @Test
    void marginal_entitlement() {
        assertEquals(0, entitlement(SANTE, "60000").compareTo(new BigDecimal("1350.00")));
        assertEquals(0, entitlement(SANTE, "100000").compareTo(new BigDecimal("3200.00")));
        assertEquals(0, entitlement(SANTE, "120000").compareTo(new BigDecimal("6000.00")));
        assertEquals(0, entitlement(SANTE, "15000").compareTo(new BigDecimal("0.00")));
    }

    /**
     * A null, blank or non-array scale yields nothing; an open marginal tier runs to the measure; the
     * highest reached from-first-euro tier wins, and an unreached one is ignored.
     */
    @Test
    void marginal_edges() {
        assertEquals(0, entitlement(null, "100").compareTo(BigDecimal.ZERO));
        assertEquals(0, entitlement("   ", "100").compareTo(BigDecimal.ZERO));
        assertEquals(0, entitlement("{}", "100").compareTo(BigDecimal.ZERO));
        assertEquals(0, entitlement("[{\"lowerBound\":0,\"rate\":0.1}]", "100").compareTo(new BigDecimal("10.00")));
        String twoWhole = "[{\"lowerBound\":100000,\"rate\":0.05,\"fromFirstUnit\":true},"
                + "{\"lowerBound\":200000,\"rate\":0.06,\"fromFirstUnit\":true}]";
        assertEquals(0, entitlement(twoWhole, "250000").compareTo(new BigDecimal("15000.00")));
        assertEquals(0, entitlement(twoWhole, "50000").compareTo(BigDecimal.ZERO));
        // The from-first-euro tiers in descending order keep the first (highest) reached.
        String descWhole = "[{\"lowerBound\":200000,\"rate\":0.06,\"fromFirstUnit\":true},"
                + "{\"lowerBound\":100000,\"rate\":0.05,\"fromFirstUnit\":true}]";
        assertEquals(0, entitlement(descWhole, "250000").compareTo(new BigDecimal("15000.00")));
    }

    /** Computes the marginal entitlement of a scale at a measure. */
    private static BigDecimal entitlement(String tiers, String measure) {
        return CalculationEngine.marginalEntitlement(MAPPER, tiers, new BigDecimal(measure));
    }
}
