package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.VersionStatus;
import fr.alptis.remuneration.reporting.ReportingService.Consolidated;
import fr.alptis.remuneration.reporting.ReportingService.Dashboard;
import fr.alptis.remuneration.reporting.ReportingService.Indicators;
import fr.alptis.remuneration.reporting.ReportingService.LineFilter;
import fr.alptis.remuneration.reporting.ReportingService.Margin;
import fr.alptis.remuneration.reporting.ReportingService.SearchRow;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the reporting read model (section 8.12): the month-range dashboards with every axis and
 * rubric (REP-01), the REP-03 filters, the operating indicators month by month (REP-09), the margin
 * by product, insurer and distributor (REP-08), the cross-search (REP-02/03) and the consolidated
 * view (REP-07). Runs against UNEP in rolled-back transactions; expected amounts are posted by hand.
 */
@QuarkusTest
class ReportingServiceTest {

    @Inject
    ReportingService service;

    private static final LocalDate AUG = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEP = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    private static final LocalDate EMPTY_MONTH = LocalDate.of(2026, 7, 1);
    private static final LineFilter NONE = LineFilter.none();

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Builds a rich scene over three months with groups, delegation, products and several natures. */
    private void scene() {
        Period sep = period(SEP);
        Period oct = period(OCT);
        Period aug = period(AUG);

        DistributorGroup groupG = group("G");
        DistributorGroup groupH = group("H");
        Distributor d1 = distributor("D1", null, null);
        Distributor d3 = distributor("D3", groupG, null);
        Distributor d4 = distributor("D4", null, "DEL");
        Distributor d5 = distributor("D5", groupH, null);

        Product product = new Product();
        product.entity = unep();
        product.code = "PR";
        product.label = "Produit";
        product.range = range();
        product.insurer = insurer();
        product.persist();
        Coverage withProduct = coverage("CV1", option(product));
        Coverage withoutProduct = coverage("CV2", option(null));
        Coverage withoutOption = coverage("CV3", null);

        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "RP-PLAN";
        plan.label = "Plan";
        plan.direction = Direction.OUTBOUND;
        plan.persist();
        Rule rule = new Rule();
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
        statement.period = sep;
        statement.statementNumber = "ST-RP";
        statement.persist();

        CommissionLine linear = line(LineNature.LINEAR, LineStatus.EARNED, d1, withProduct, "1000.00", "0.10",
                "100.00", sep, Direction.OUTBOUND);
        linear.ruleVersion = version;
        linear.statement = statement;
        linear.earnedAt = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        linear.paidAt = OffsetDateTime.parse("2026-09-06T00:00:00Z");
        // Two more timestamped lines so the min/max cycle-duration comparisons take both outcomes:
        // one neither earlier earned nor later paid, one earlier earned and later paid.
        CommissionLine mid = line(LineNature.LINEAR, LineStatus.EARNED, d1, null, null, null, "3.00", sep,
                Direction.OUTBOUND);
        mid.earnedAt = OffsetDateTime.parse("2026-09-02T00:00:00Z");
        mid.paidAt = OffsetDateTime.parse("2026-09-04T00:00:00Z");
        CommissionLine wide = line(LineNature.LINEAR, LineStatus.EARNED, d1, null, null, null, "3.00", sep,
                Direction.OUTBOUND);
        wide.earnedAt = OffsetDateTime.parse("2026-08-30T00:00:00Z");
        wide.paidAt = OffsetDateTime.parse("2026-09-10T00:00:00Z");
        line(LineNature.CLAWBACK, LineStatus.EARNED, d1, null, null, null, "-20.00", sep, Direction.OUTBOUND);
        line(LineNature.ADVANCE, LineStatus.EARNED, d1, null, null, null, "50.00", sep, Direction.OUTBOUND);
        line(LineNature.MANUAL, LineStatus.EARNED, d4, null, null, null, "10.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.OFFSET, d1, null, null, null, "15.00", sep, Direction.OUTBOUND);
        line(LineNature.RETROCESSION, LineStatus.EARNED, d1, withProduct, null, null, "200.00", sep,
                Direction.INBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d3, withProduct, null, null, "30.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d5, withProduct, null, null, "25.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d1, withoutProduct, null, null, "5.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d1, withoutOption, null, null, "5.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, null, null, null, null, "2.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.FORECAST, d1, null, null, null, "9.00", sep, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d1, null, null, null, "1.00", null, Direction.OUTBOUND);

        CommissionLine octLine = line(LineNature.LINEAR, LineStatus.EARNED, d1, withProduct, null, null, "40.00",
                oct, Direction.OUTBOUND);
        octLine.earnedAt = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        line(LineNature.LINEAR, LineStatus.OFFSET, d1, null, null, null, "7.00", oct, Direction.OUTBOUND);
        line(LineNature.LINEAR, LineStatus.EARNED, d1, withProduct, null, null, "8.00", aug, Direction.OUTBOUND);
    }

    /** Creates a period in a controlled state. */
    private static Period period(LocalDate month) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        return period;
    }

    /** Creates a distributor group. */
    private static DistributorGroup group(String code) {
        DistributorGroup group = new DistributorGroup();
        group.code = code;
        group.label = code;
        group.persist();
        return group;
    }

    /** Creates a distributor with an optional group and delegation. */
    private static Distributor distributor(String code, DistributorGroup group, String delegation) {
        Distributor distributor = new Distributor();
        distributor.partnerCode = code;
        distributor.legalName = code;
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.group = group;
        distributor.salesDelegation = delegation;
        distributor.persist();
        return distributor;
    }

    /** Creates the product range. */
    private static ProductRange range() {
        ProductRange range = new ProductRange();
        range.code = "SANTE";
        range.label = "Santé";
        range.persist();
        return range;
    }

    /** Creates the insurer. */
    private static Insurer insurer() {
        Insurer insurer = new Insurer();
        insurer.code = "INS";
        insurer.name = "Assureur";
        insurer.persist();
        return insurer;
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
     * The default-range dashboard spans the months carrying data and fills every axis and rubric.
     */
    @Test
    @TestTransaction
    void dashboard_default_range_fills_every_axis() {
        scene();
        Dashboard d = service.dashboard("UNEP", null, null, NONE);
        assertEquals(List.of(AUG, SEP, OCT), d.months());
        assertTrue(d.byNature().stream().anyMatch(a -> a.label().equals("LINEAR")));
        assertTrue(d.byGamme().stream().anyMatch(a -> a.label().equals("Santé")));
        assertTrue(d.byGamme().stream().anyMatch(a -> a.label().equals("—")));
        assertTrue(d.byMonthNature().stream().anyMatch(m -> m.month().equals(OCT) && m.nature().equals("LINEAR")));
        assertTrue(d.reprisesByMonth().stream()
                .anyMatch(m -> m.month().equals(SEP) && new BigDecimal("-20.00").compareTo(m.primary()) == 0));
        assertTrue(d.escompteByMonth().stream().anyMatch(m -> m.month().equals(SEP)
                && new BigDecimal("50.00").compareTo(m.primary()) == 0
                && new BigDecimal("15.00").compareTo(m.second()) == 0
                && new BigDecimal("35.00").compareTo(m.third()) == 0));
        assertTrue(d.topDistributors().stream().anyMatch(a -> a.label().equals("D1")));
        assertEquals(0, new BigDecimal("-20.00").compareTo(d.reprises()));
        assertEquals(0, new BigDecimal("50.00").compareTo(d.escomptesVerses()));
        assertEquals(0, new BigDecimal("22.00").compareTo(d.escomptesApures()));
        assertEquals(0, new BigDecimal("28.00").compareTo(d.creance()));
        assertTrue(d.debtorAccounts() >= 0);
        assertTrue(d.anomalies() >= 0);
    }

    /**
     * An explicit range selects exactly its months; an inverted range yields an empty one; a single
     * bound anchors the other.
     */
    @Test
    @TestTransaction
    void dashboard_range_bounds() {
        scene();
        assertEquals(List.of(SEP), service.dashboard("UNEP", SEP, SEP, NONE).months());
        assertTrue(service.dashboard("UNEP", OCT, SEP, NONE).months().isEmpty());
        assertEquals(List.of(SEP, OCT), service.dashboard("UNEP", SEP, null, NONE).months());
        assertEquals(List.of(AUG, SEP), service.dashboard("UNEP", null, SEP, NONE).months());
        // An entity without any line: an empty range and empty aggregates.
        LegalEntity empty = new LegalEntity();
        empty.code = "ZZZ";
        empty.name = "Vide";
        empty.persist();
        assertTrue(service.dashboard("ZZZ", null, null, NONE).months().isEmpty());
    }

    /**
     * Each REP-03 filter — product, distributor, group/hierarchy, delegation, nature — selects the
     * right lines.
     */
    @Test
    @TestTransaction
    void dashboard_filters_select_lines() {
        scene();
        assertTrue(service.dashboard("UNEP", null, null, new LineFilter("PR", null, null, null, null))
                .byNature().stream().noneMatch(a -> a.label().equals("CLAWBACK")));
        assertTrue(service.dashboard("UNEP", null, null, new LineFilter(null, "D1", null, null, null))
                .topDistributors().stream().allMatch(a -> a.label().equals("D1")));
        // Group filter keeps only D3 (group G); D1 (no group), D5 (group H) and the null distributor drop.
        List<ReportingService.Amount> grouped =
                service.dashboard("UNEP", null, null, new LineFilter(null, null, "G", null, null))
                        .topDistributors();
        assertEquals(1, grouped.size());
        assertEquals("D3", grouped.get(0).label());
        // Delegation filter keeps only D4.
        List<ReportingService.Amount> delegated =
                service.dashboard("UNEP", null, null, new LineFilter(null, null, null, "DEL", null))
                        .topDistributors();
        assertEquals(1, delegated.size());
        assertEquals("D4", delegated.get(0).label());
        assertTrue(service.dashboard("UNEP", null, null, new LineFilter(null, null, null, null, LineNature.CLAWBACK))
                .byNature().stream().allMatch(a -> a.label().equals("CLAWBACK")));
    }

    /**
     * The indicators run month by month: active rules, cycle duration (zero when nothing is paid),
     * manual share (zero for an empty month) and clawed-back amounts.
     */
    @Test
    @TestTransaction
    void indicators_month_by_month() {
        scene();
        Indicators indicators = service.indicators("UNEP", null, null);
        assertEquals(List.of(AUG, SEP, OCT), indicators.months());
        ReportingService.IndicatorMonth sep = indicators.rows().stream()
                .filter(r -> r.month().equals(SEP)).findFirst().orElseThrow();
        assertTrue(sep.activeRules() >= 1);
        // Earliest earned 2026-08-30, latest paid 2026-09-10 → 11 days.
        assertEquals(0, new BigDecimal("11").compareTo(sep.cycleDuration()));
        assertTrue(sep.manualShare().signum() > 0);
        assertEquals(0, new BigDecimal("-20.00").compareTo(sep.clawback()));
        ReportingService.IndicatorMonth oct = indicators.rows().stream()
                .filter(r -> r.month().equals(OCT)).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(oct.cycleDuration()));
        // An explicit month without any line: a zero manual share.
        ReportingService.IndicatorMonth emptyMonth = service.indicators("UNEP", EMPTY_MONTH, EMPTY_MONTH)
                .rows().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(emptyMonth.manualShare()));
    }

    /**
     * The margin nets inbound against outbound on the product, insurer and distributor axes.
     */
    @Test
    @TestTransaction
    void margin_on_three_axes() {
        scene();
        Margin margin = service.margin("UNEP");
        assertTrue(margin.byProduct().stream()
                .anyMatch(m -> m.key().equals("PR") && m.received().signum() > 0 && m.paid().signum() > 0));
        assertTrue(margin.byInsurer().stream().anyMatch(m -> m.key().equals("INS")));
        assertTrue(margin.byDistributor().stream().anyMatch(m -> m.key().equals("D1")));
    }

    /**
     * The cross-search keeps only counted lines, applies every filter and resolves the chain fields.
     */
    @Test
    @TestTransaction
    void search_applies_every_filter() {
        scene();
        List<SearchRow> all = service.search("UNEP", NONE, null);
        assertTrue(all.stream().anyMatch(r -> r.rule().equals("RP-RULE") && r.statementNumber().equals("ST-RP")
                && r.gamme().equals("Santé")));
        assertEquals(1, service.search("UNEP", new LineFilter("PR", "D1", null, null, LineNature.LINEAR), SEP)
                .size());
        assertEquals(0, service.search("UNEP", new LineFilter(null, null, null, null, LineNature.RCP), null).size());
        assertEquals(0, service.search("UNEP", NONE, EMPTY_MONTH).size());
    }

    /**
     * The consolidated view totals every entity month by month over the range.
     */
    @Test
    @TestTransaction
    void consolidated_by_entity_and_month() {
        scene();
        Consolidated all = service.consolidated(null, null);
        assertTrue(all.summaries().stream().anyMatch(e -> e.code().equals("UNEP")));
        assertTrue(all.cells().stream().anyMatch(c -> c.entity().equals("UNEP") && c.month().equals(SEP)));
        assertTrue(all.months().contains(SEP));
        assertTrue(service.consolidated(SEP, SEP).months().contains(SEP));
    }

    /**
     * Every reporting list exports as a semicolon-separated CSV with its header and rows; an unknown
     * object is rejected (REP-04).
     */
    @Test
    @TestTransaction
    void export_csv_per_object() {
        scene();
        String search = service.exportCsv("UNEP", "SEARCH", null, null, NONE, null);
        assertTrue(search.startsWith("distributor;product;gamme;nature;basis;rate;amount;rule;statement;month\n"));
        assertTrue(search.contains(";RP-RULE;ST-RP;"));
        assertTrue(service.exportCsv("UNEP", "MARGIN", null, null, NONE, null).contains("PRODUCT;PR;"));
        assertTrue(service.exportCsv("UNEP", "INDICATORS", null, null, NONE, null)
                .startsWith("month;active_rules;cycle_duration_days;manual_share;clawback\n"));
        assertTrue(service.exportCsv("UNEP", "DASHBOARD", null, null, NONE, null)
                .contains("2026-09-01;LINEAR;"));
        assertTrue(service.exportCsv("UNEP", "CONSOLIDATED", null, null, NONE, null)
                .startsWith("entity;month;total\n"));
        assertThrows(fr.alptis.remuneration.domain.ValidationException.class,
                () -> service.exportCsv("UNEP", "NOPE", null, null, NONE, null));
    }

    /**
     * An unknown entity is rejected.
     */
    @Test
    @TestTransaction
    void unknown_entity_is_rejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.dashboard("GHOST", null, null, NONE));
    }
}
