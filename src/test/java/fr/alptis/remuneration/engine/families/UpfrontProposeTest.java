package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for the upfront family (section 6.2): first-year acquisition and servicing,
 * year-two servicing only, and the first-year prorata at the boundary (CR-02, CR-03, CR-73).
 */
class UpfrontProposeTest {

    private final UpfrontFamily upfront = new UpfrontFamily();

    /** Builds an upfront input. */
    private static CalculationInput input(String basis, LocalDate effect, LocalDate eventDate, LocalDate from,
                                          LocalDate to, String parameters) {
        return new CalculationInput(EventType.PREMIUM_COLLECTED, new BigDecimal(basis), null,
                new BigDecimal("0.30"), new BigDecimal("0.10"), effect, eventDate, from, to, null, null, null,
                null, LineNature.ACQUISITION, parameters);
    }

    /** Returns the amount of the first line of a nature. */
    private static BigDecimal amount(List<ProposedLine> lines, LineNature nature) {
        return lines.stream().filter(l -> l.nature() == nature).findFirst().orElseThrow().amount();
    }

    /**
     * CR-02: a 1 000 € annual premium in the first year yields acquisition 300 € and servicing 100 €.
     */
    @Test
    void cr02_first_year() {
        List<ProposedLine> lines = upfront.propose(
                input("1000", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), null, null, null));
        assertEquals(2, lines.size());
        assertEquals(0, amount(lines, LineNature.ACQUISITION).compareTo(new BigDecimal("300.00")));
        assertEquals(0, amount(lines, LineNature.SERVICING).compareTo(new BigDecimal("100.00")));
    }

    /**
     * CR-03: in the second year only the servicing line remains.
     */
    @Test
    void cr03_second_year() {
        List<ProposedLine> lines = upfront.propose(
                input("1000", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1), null, null, "{\"firstYearDays\":365}"));
        assertEquals(1, lines.size());
        assertEquals(LineNature.SERVICING, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("100.00")));
    }

    /**
     * CR-73: a quarterly premium straddling the first-year boundary prorates the acquisition by day.
     */
    @Test
    void cr73_prorata_at_the_boundary() {
        List<ProposedLine> lines = upfront.propose(input("300", LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 1),
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 4, 30), "{\"firstYearDays\":365}"));
        assertEquals(0, amount(lines, LineNature.ACQUISITION).compareTo(new BigDecimal("28.31")));
        assertEquals(0, amount(lines, LineNature.SERVICING).compareTo(new BigDecimal("30.00")));
    }

    /**
     * A premium whose covered period lies entirely in the first year gets the full acquisition; one
     * lying entirely after it gets none.
     */
    @Test
    void whole_period_inside_or_outside_first_year() {
        List<ProposedLine> inside = upfront.propose(input("100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null));
        assertEquals(0, amount(inside, LineNature.ACQUISITION).compareTo(new BigDecimal("30.00")));

        List<ProposedLine> outside = upfront.propose(input("100", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30), null));
        assertEquals(1, outside.size());
        assertEquals(LineNature.SERVICING, outside.get(0).nature());
    }

    /**
     * Covers the remaining branches: null amounts, a missing event date, a half-open covered period,
     * a default first-year length and a blank parameter string.
     */
    @Test
    void covers_edge_branches() {
        // Null basis, rates and event date, no covered period: a zero servicing line, no acquisition.
        CalculationInput allNull = new CalculationInput(EventType.PREMIUM_COLLECTED, null, null, null, null,
                LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, LineNature.ACQUISITION, null);
        List<ProposedLine> none = upfront.propose(allNull);
        assertEquals(1, none.size());
        assertEquals(0, none.get(0).amount().compareTo(new BigDecimal("0.00")));

        // A half-open covered period (no end) falls back to the event date.
        List<ProposedLine> halfOpen = upfront.propose(input("100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1), null, null));
        assertEquals(0, amount(halfOpen, LineNature.ACQUISITION).compareTo(new BigDecimal("30.00")));

        // Parameters present without firstYearDays (default) and a period whose start precedes the
        // effect date, exercising the later-date choice.
        List<ProposedLine> defaulted = upfront.propose(input("100", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "{}"));
        assertEquals(2, defaulted.size());

        // A blank parameter string also uses the default first-year length.
        List<ProposedLine> blank = upfront.propose(input("100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                null, null, "   "));
        assertEquals(0, amount(blank, LineNature.ACQUISITION).compareTo(new BigDecimal("30.00")));
    }

    /**
     * With no coverage effect date, or a malformed first-year length, the whole premium is treated as
     * first year.
     */
    @Test
    void null_effect_and_malformed_parameters_give_full_acquisition() {
        List<ProposedLine> noEffect = upfront.propose(input("100", null, LocalDate.of(2030, 1, 1), null, null, null));
        assertEquals(0, amount(noEffect, LineNature.ACQUISITION).compareTo(new BigDecimal("30.00")));

        List<ProposedLine> badParams = upfront.propose(
                input("100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), null, null, "{bad"));
        assertEquals(0, amount(badParams, LineNature.ACQUISITION).compareTo(new BigDecimal("30.00")));
    }
}
