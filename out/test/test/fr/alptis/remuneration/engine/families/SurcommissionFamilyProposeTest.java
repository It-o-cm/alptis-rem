package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LineNature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for the surcommission families (sections 6.8, 6.9): the portfolio commission at a
 * resolved rate and the share retrocession of a received commission, with their null and malformed
 * branches. No database, no container.
 */
class SurcommissionFamilyProposeTest {

    private final PortfolioFamily portfolio = new PortfolioFamily();
    private final ShareFamily share = new ShareFamily();

    /** Builds an input with a basis, a resolved rate and a nature. */
    private static CalculationInput input(BigDecimal basis, BigDecimal rate, LineNature nature, String parameters) {
        return new CalculationInput(EventType.PREMIUM_COLLECTED, basis, rate, null, null, null, null, null, null,
                null, null, null, null, nature, parameters, null);
    }

    /**
     * CR-46: the portfolio family applies the resolved rate (4%) to the year's premiums (200 000 €),
     * giving 8 000,00 € as an RCP line.
     */
    @Test
    void cr46_portfolio() {
        List<ProposedLine> lines = portfolio.propose(input(new BigDecimal("200000.00"), new BigDecimal("0.04"),
                null, "{\"rate\":0.04}"));
        assertEquals(LineNature.RCP, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("8000.00")));
    }

    /**
     * The portfolio family honours the declared nature and tolerates a null basis and rate (a nil
     * line, as when a condition fails).
     */
    @Test
    void portfolio_edges() {
        assertEquals(LineNature.RCP, portfolio.propose(input(new BigDecimal("100"), BigDecimal.ZERO,
                LineNature.RCP, "{\"rate\":0}")).get(0).nature());
        assertEquals(0, portfolio.propose(input(null, null, null, "{}")).get(0).amount().compareTo(BigDecimal.ZERO));
    }

    /**
     * CR-50: the share family retrocedes 60% of a 100 € received commission, giving 60,00 € as a
     * RETROCESSION line.
     */
    @Test
    void cr50_share() {
        List<ProposedLine> lines = share.propose(input(new BigDecimal("100.00"), null, null, "{\"rate\":0.60}"));
        assertEquals(LineNature.RETROCESSION, lines.get(0).nature());
        assertEquals(0, lines.get(0).amount().compareTo(new BigDecimal("60.00")));
    }

    /**
     * The share family honours the declared nature and defaults the rate to zero when the parameters
     * are null, blank, malformed or without a rate.
     */
    @Test
    void share_edges() {
        assertEquals(LineNature.RCP, share.propose(input(new BigDecimal("100"), null, LineNature.RCP,
                "{\"rate\":0.1}")).get(0).nature());
        assertEquals(0, share.propose(input(new BigDecimal("100"), null, null, null)).get(0).amount()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, share.propose(input(new BigDecimal("100"), null, null, "   ")).get(0).amount()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, share.propose(input(new BigDecimal("100"), null, null, "{bad")).get(0).amount()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, share.propose(input(new BigDecimal("100"), null, null, "{}")).get(0).amount()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, share.propose(input(null, null, null, "{\"rate\":0.6}")).get(0).amount()
                .compareTo(BigDecimal.ZERO));
    }
}
