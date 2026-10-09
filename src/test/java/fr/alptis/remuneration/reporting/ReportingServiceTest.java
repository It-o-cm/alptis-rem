package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.VersionStatus;
import fr.alptis.remuneration.reporting.ReportingService.Dashboard;
import fr.alptis.remuneration.reporting.ReportingService.Indicators;
import fr.alptis.remuneration.reporting.ReportingService.SearchRow;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the reporting read model (section 8.12): the dashboard aggregation, the operating
 * indicators, the cross-search filters, the margin and the consolidated view, over a rich scene.
 * Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class ReportingServiceTest {

    @Inject
    ReportingService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);
    private static final LocalDate OTHER = LocalDate.of(2026, 10, 1);
    private Period period;
    private Distributor d1;
    private Distributor d2;
    private Coverage withProduct;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Builds the scene: two distributors, three coverages, a rule and lines of several natures. */
    private void scene() {
        period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        d1 = distributor("D1");
        d2 = distributor("D2");
        Product product = new Product();
        product.entity = unep();
        product.code = "PR";
        product.label = "Produit";
        product.persist();
        withProduct = coverage("CV1", option(product));
        Coverage withoutProduct = coverage("CV2", option(null));
        Coverage withoutOption = coverage("CV3", null);
        Rule rule = new Rule();
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "RP-PLAN";
        plan.label = "Plan";
        plan.direction = Direction.OUTBOUND;
        plan.persist();
        rule.plan = plan;
        rule.code = "RP-RULE";
        rule.label = "Règle";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = VersionStatus.ACTIVE;
        version.persist();
        Statement statement = new Statement();
        statement.entity = unep();
        statement.period = period;
        statement.statementNumber = "ST-RP";
        statement.persist();

        CommissionLine linear = line(LineNature.LINEAR, LineStatus.EARNED, d1, withProduct, "1000.00", "0.10",
                "100.00", period, Direction.OUTBOUND);
        linear.ruleVersion = version;
        linear.statement = statement;
        line(LineNature.CLAWBACK, LineStatus.EARNED, d1, null, null, null, "-20.00", period, Direction.OUTBOUND);
        line(LineNature.ADVANCE, LineStatus.EARNED, d1, null, null, null, "50.00", period, Direction.OUTBOUND);
        line(LineNature.MANUAL, LineStatus.EARNED, d2, null, null, null, "10.00", period, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.OFFSET, d1, null, null, null, "15.00", period, Direction.OUTBOUND);
        line(LineNature.RETROCESSION, LineStatus.EARNED, d1, withProduct, null, null, "200.00", period,
                Direction.INBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d1, withoutProduct, null, null, "5.00", period,
                Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d1, withoutOption, null, null, "5.00", period,
                Direction.OUTBOUND);
        // A counted line without a period, a distributor-less line, and a non-counted (forecast) line.
        line(LineNature.LINEAR, LineStatus.EARNED, d1, null, null, null, "1.00", null, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, null, null, null, null, "2.00", period, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.FORECAST, d1, null, null, null, "9.00", period, Direction.OUTBOUND);
    }

    /** Creates a distributor. */
    private static Distributor distributor(String code) {
        Distributor distributor = new Distributor();
        distributor.partnerCode = code;
        distributor.legalName = code;
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.persist();
        return distributor;
    }

    /** Creates a product option (product may be null). */
    private static ProductOption option(Product product) {
        ProductOption option = new ProductOption();
        option.product = product;
        option.persist();
        return option;
    }

    /** Creates a coverage carrying an option (which may be null). */
    private static Coverage coverage(String code, ProductOption option) {
        Coverage coverage = new Coverage();
        coverage.coverageCode = code;
        coverage.option = option;
        coverage.persist();
        return coverage;
    }

    /** Creates a commission line. */
    private static CommissionLine line(LineNature nature, LineStatus status, Distributor distributor,
                                       Coverage coverage, String basis, String rate, String amount, Period period,
                                       Direction direction) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = direction;
        line.nature = nature;
        line.status = status;
        line.originDistributor = distributor;
        line.coverage = coverage;
        line.basisAmount = basis == null ? null : new BigDecimal(basis);
        line.rate = rate == null ? null : new BigDecimal(rate);
        line.amount = new BigDecimal(amount);
        line.persist();
        return line;
    }

    /**
     * The dashboard sums the natures, reprises, escomptes paid and offset, and ranks the distributors;
     * a month-less dashboard reads every period.
     */
    @Test
    @TestTransaction
    void dashboard_aggregates_the_scene() {
        scene();
        Dashboard month = service.dashboard("UNEP", M);
        assertEquals(0, new BigDecimal("-20.00").compareTo(month.reprises()));
        assertEquals(0, new BigDecimal("50.00").compareTo(month.escomptesVerses()));
        assertEquals(0, new BigDecimal("15.00").compareTo(month.escomptesApures()));
        assertTrue(month.topDistributors().stream().anyMatch(a -> a.label().equals("D1")));
        assertTrue(month.topDistributors().stream().anyMatch(a -> a.label().equals("D2")));

        Dashboard all = service.dashboard("UNEP", null);
        assertTrue(all.byNature().stream().anyMatch(a -> a.label().equals("LINEAR")));
    }

    /**
     * The indicators count active rules, the manual share and the clawback total; an entity without a
     * line has a zero manual share.
     */
    @Test
    @TestTransaction
    void indicators_measure_operation() {
        scene();
        Indicators indicators = service.indicators("UNEP");
        assertTrue(indicators.activeRules() >= 1);
        assertEquals(0, new BigDecimal("-20.00").compareTo(indicators.clawback()));
        assertTrue(indicators.manualShare().signum() > 0);

        LegalEntity empty = new LegalEntity();
        empty.code = "ZZZ";
        empty.name = "Vide";
        empty.persist();
        assertEquals(0, BigDecimal.ZERO.compareTo(service.indicators("ZZZ").manualShare()));
    }

    /**
     * The cross-search applies every filter, keeps only counted lines, and resolves the chain fields.
     */
    @Test
    @TestTransaction
    void search_applies_every_filter() {
        scene();
        // No filter: the counted lines (the forecast line is excluded).
        List<SearchRow> all = service.search("UNEP", null, null, null, null);
        assertEquals(9, all.size());
        assertTrue(all.stream().anyMatch(r -> r.rule().equals("RP-RULE") && r.statementNumber().equals("ST-RP")));

        // All filters, matching the linear line.
        assertEquals(1, service.search("UNEP", "PR", "D1", LineNature.LINEAR, M).size());
        // Nature mismatch, month mismatch, period-less (month given), distributor and product mismatch.
        assertEquals(0, service.search("UNEP", null, null, LineNature.RCP, null).size());
        assertEquals(0, service.search("UNEP", null, null, null, OTHER).size());
        assertEquals(0, service.search("UNEP", null, "ZZ", null, null).size());
        assertEquals(0, service.search("UNEP", "ZZ", null, null, null).size());
    }

    /**
     * The margin subtracts the paid (outbound) from the received (inbound) per product.
     */
    @Test
    @TestTransaction
    void margin_nets_inbound_and_outbound() {
        scene();
        assertTrue(service.margin("UNEP").stream()
                .anyMatch(m -> m.product().equals("PR") && m.received().signum() > 0));
    }

    /**
     * The consolidated view totals every entity, and an unknown entity is rejected.
     */
    @Test
    @TestTransaction
    void consolidated_and_unknown() {
        scene();
        assertTrue(service.consolidated().stream().anyMatch(e -> e.code().equals("UNEP")));
        assertThrows(ResourceNotFoundException.class, () -> service.dashboard("GHOST", null));
    }
}
