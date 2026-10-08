package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                pBasis, pRate, pAmount, nature, parameters);
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
