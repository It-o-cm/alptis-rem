package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for the advance family (section 6.4): the advance computed on the annualised
 * premium, one line per nature in upfront and a single line in linear (CR-20).
 */
class AdvanceProposeTest {

    private final AdvanceFamily advance = new AdvanceFamily();

    /** Builds an advance input with the given annualised premium and rates. */
    private static CalculationInput input(String premium, String acquisition, String servicing, String linear) {
        return new CalculationInput(EventType.PREMIUM_COLLECTED, null,
                linear == null ? null : new BigDecimal(linear),
                acquisition == null ? null : new BigDecimal(acquisition),
                servicing == null ? null : new BigDecimal(servicing),
                null, null, null, null, null, null, null,
                premium == null ? null : new BigDecimal(premium), LineNature.ADVANCE, null, null);
    }

    /**
     * CR-20: an upfront advance on a 1 000 € annualised premium at 30 % and 10 % yields two ADVANCE
     * lines of 300,00 € and 100,00 €.
     */
    @Test
    void cr20_upfront_advance() {
        List<ProposedLine> lines = advance.propose(input("1000", "0.30", "0.10", null));
        assertEquals(2, lines.size());
        assertEquals(LineNature.ADVANCE, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("300.00")));
        assertEquals(0, lines.get(1).amount().compareTo(new BigDecimal("100.00")));
    }

    /**
     * A linear advance yields a single ADVANCE line on the annualised premium.
     */
    @Test
    void linear_advance() {
        List<ProposedLine> lines = advance.propose(input("1000", null, null, "0.15"));
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("150.00")));
    }

    /**
     * A zero or missing rate, or a missing premium, produces no advance line.
     */
    @Test
    void zero_rate_or_missing_premium_produces_nothing() {
        assertEquals(1, advance.propose(input("1000", "0.30", "0", null)).size());
        assertEquals(0, advance.propose(input(null, null, null, null)).size());
    }

    /**
     * A servicing-only upfront advance (no acquisition rate) yields a single ADVANCE line.
     */
    @Test
    void servicing_only_upfront_advance() {
        List<ProposedLine> lines = advance.propose(input("1000", null, "0.10", null));
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("100.00")));
    }
}
