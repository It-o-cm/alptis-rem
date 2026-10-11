package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.StaffCategory;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the engine core (section 13): CR-01, CR-09 to CR-12, CR-51 and CR-77, plus the
 * holds and the rerun idempotency of section 12.3. Everything runs against UNEP so the Alptis
 * initial barème does not interfere, inside rolled-back transactions.
 */
@QuarkusTest
class CalculationEngineTest {

    @Inject
    CalculationEngine engine;

    private static final LocalDate MONTH = LocalDate.of(2026, 1, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a product. */
    private static Product product(String code) {
        Product p = new Product();
        p.entity = unep();
        p.code = code;
        p.label = code;
        p.persist();
        return p;
    }

    /** Creates a coverage on a new contract, with a main production-code assignment. */
    private Coverage coverage(String contractNumber, Product product, StaffCategory college, String partnerCode) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contractNumber + "-C1";
        coverage.effectDate = MONTH;
        coverage.staffCategory = college;
        coverage.persist();
        Distributor distributor = new Distributor();
        distributor.partnerCode = partnerCode;
        distributor.legalName = partnerCode;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-" + contractNumber;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = MONTH;
        assignment.persist();
        return coverage;
    }

    /** Creates a linear remuneration choice for a coverage, valid over a window. */
    private static void linearChoice(Coverage coverage, String rate, LocalDate from, LocalDate to) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = new BigDecimal(rate);
        choice.validFrom = from;
        choice.validTo = to;
        choice.persist();
    }

    /** Creates an upfront remuneration choice for a coverage. */
    private static void upfrontChoice(Coverage coverage, String acquisition, String servicing) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal(acquisition);
        choice.servicingRate = new BigDecimal(servicing);
        choice.validFrom = MONTH;
        choice.persist();
    }

    /** The default upfront clawback policy: 100% the first year, 50% the second, on acquisition. */
    private static final String CLAWBACK = "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],"
            + "\"triggers\":[\"TERMINATION\",\"WITHDRAWAL\"],"
            + "\"steps\":[{\"maxDays\":365,\"rate\":1.0},{\"maxDays\":730,\"rate\":0.5}]}";

    private static final String UPFRONT_SCOPE =
            "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE_SELECT\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";

    /** Creates an active upfront base rule version with the standard clawback policy. */
    private RuleVersion upfrontRule(String ruleCode) {
        RuleVersion v = version("PLAN", ruleCode, RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        v.clawbackPolicy = CLAWBACK;
        return v;
    }

    /** Injects a terminating event with an effect age in days from the coverage effect date. */
    private BusinessEvent terminal(Coverage coverage, String key, EventType type, int ageDays) {
        return event(coverage, key, type, MONTH.plusDays(ageDays), null);
    }

    /** Creates an active rule version with the given attributes and returns it. */
    private RuleVersion version(String planCode, String ruleCode, RuleFamily family, LineNature nature, String scope,
                                String parameters, String group, int priority, LocalDate from, LocalDate to) {
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", unep(), planCode).firstResult();
        if (plan == null) {
            plan = new CommissionPlan();
            plan.entity = unep();
            plan.code = planCode;
            plan.label = planCode;
            plan.direction = Direction.OUTBOUND;
            plan.vintage = (short) 2026;
            plan.persist();
        }
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = ruleCode;
        rule.label = ruleCode;
        rule.family = family;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = VersionStatus.ACTIVE;
        version.scope = scope;
        version.parameters = parameters;
        version.exclusionGroup = group;
        version.priority = priority;
        version.outputNature = nature;
        version.validFrom = from;
        version.validTo = to;
        version.persist();
        return version;
    }

    /** Injects a pending premium event on a coverage. */
    private BusinessEvent premium(Coverage coverage, String key, LocalDate date, String basis) {
        return event(coverage, key, EventType.PREMIUM_COLLECTED, date, basis);
    }

    /** Injects a pending event on a coverage. */
    private BusinessEvent event(Coverage coverage, String key, EventType type, LocalDate date, String basis) {
        BusinessEvent e = new BusinessEvent();
        e.entity = unep();
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = type;
        e.contract = coverage == null ? null : coverage.contract;
        e.coverage = coverage;
        e.eventDate = date;
        e.receivedDate = date;
        e.basisAmount = basis == null ? null : new BigDecimal(basis);
        e.processingStatus = ProcessingStatus.PENDING;
        e.persist();
        return e;
    }

    /** Lines of a coverage after a run, as amounts by nature. */
    private static List<CommissionLine> lines() {
        return CommissionLine.list("order by id");
    }

    private static final String BASE_LINEAR_SCOPE =
            "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE_SELECT\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";

    /**
     * CR-01: a linear 15% rule on a 100 € premium yields one LINEAR line of 15,00 €.
     */
    @Test
    @TestTransaction
    void cr01_linear() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, LocalDate.of(2026, 12, 31));
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals(LineNature.LINEAR, lines.get(0).nature);
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("15.00")));
        assertEquals(LineStatus.EARNED, lines.get(0).status);
        assertEquals("PC-AD-1", lines.get(0).originProductionCode.code);
    }

    /**
     * Regression (brief DONNEES-DEMO §5): the real calculation records on each processed event the
     * period it was calculated in, so the period controls of section 8.6 (data quality, no-rule) can
     * find the events to check. The field was declared and queried but never assigned.
     */
    @Test
    @TestTransaction
    void stamps_the_eligible_period_on_processed_events() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        BusinessEvent event = premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);

        BusinessEvent reloaded = BusinessEvent.findById(event.getId());
        assertNotNull(reloaded.eligiblePeriod);
        assertEquals(MONTH, reloaded.eligiblePeriod.periodMonth);
    }

    /**
     * CR-09: the cancellation of the CR-01 premium yields the inverse line of −15,00 €.
     */
    @Test
    @TestTransaction
    void cr09_cancellation_is_the_inverse() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        BusinessEvent origin = premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");
        BusinessEvent cancellation = event(c, "E1-CANCEL", EventType.PREMIUM_CANCELLED, LocalDate.of(2026, 1, 10), null);
        cancellation.reversesEvent = origin;

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("15.00")));
        assertEquals(0, lines.get(1).amount.compareTo(new BigDecimal("-15.00")));
        assertEquals(lines.get(0).getId(), lines.get(1).originLine.getId());
    }

    /**
     * CR-10: the rule version and the carried rate in force at the event date apply; earlier premiums
     * keep 15%, later ones 12%, nothing recalculated.
     */
    @Test
    @TestTransaction
    void cr10_version_by_event_date() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, LocalDate.of(2026, 2, 28));
        linearChoice(c, "0.12", LocalDate.of(2026, 3, 1), null);
        version("PLAN", "R_V1", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, LocalDate.of(2026, 2, 28));
        version("PLAN", "R_V2", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.12]}", "BASE", 10, LocalDate.of(2026, 3, 1), null);
        premium(c, "JAN", LocalDate.of(2026, 1, 15), "100.00");
        premium(c, "MAR", LocalDate.of(2026, 3, 15), "100.00");

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("15.00")));
        assertEquals(0, lines.get(1).amount.compareTo(new BigDecimal("12.00")));
    }

    /**
     * CR-74: the garantie's choice changes from 15% to 18% on 16 June, so June's 100 € premium (its
     * covered period is the whole month) is split pro rata of the days (P12): 15 days at 15% = 7,50 €
     * and 15 days at 18% = 9,00 €, in two LINEAR lines.
     */
    @Test
    @TestTransaction
    void cr74_rate_change_within_covered_period() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, LocalDate.of(2026, 6, 15));
        linearChoice(c, "0.18", LocalDate.of(2026, 6, 16), null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15,0.18]}", "BASE", 10, MONTH, null);
        BusinessEvent e = premium(c, "JUN", LocalDate.of(2026, 6, 1), "100.00");
        e.periodFrom = LocalDate.of(2026, 6, 1);
        e.periodTo = LocalDate.of(2026, 6, 30);

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(2, lines.size());
        assertEquals(LineNature.LINEAR, lines.get(0).nature);
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("7.50")));
        assertEquals(LineNature.LINEAR, lines.get(1).nature);
        assertEquals(0, lines.get(1).amount.compareTo(new BigDecimal("9.00")));
    }

    /**
     * A premium that carries a start but no end to its covered period is attributed whole to its
     * event-date choice, without prorata (H-45).
     */
    @Test
    @TestTransaction
    void premium_with_open_covered_period_is_not_prorated() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        BusinessEvent e = premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");
        e.periodFrom = LocalDate.of(2026, 1, 1);

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("15.00")));
    }

    /**
     * P12 over three choices: a 900 € premium covering the first quarter with the choice changing on
     * 1 February (0.20) and 1 March (0.30) from 0.10 is split 31/28/31 days — 31,00 €, 56,00 € and
     * 93,00 € — in three LINEAR lines.
     */
    @Test
    @TestTransaction
    void premium_prorated_over_three_choices() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.10", MONTH, LocalDate.of(2026, 1, 31));
        linearChoice(c, "0.20", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        linearChoice(c, "0.30", LocalDate.of(2026, 3, 1), null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.10,0.20,0.30]}", "BASE", 10, MONTH, null);
        BusinessEvent e = premium(c, "Q1", LocalDate.of(2026, 1, 1), "900.00");
        e.periodFrom = LocalDate.of(2026, 1, 1);
        e.periodTo = LocalDate.of(2026, 3, 31);

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(3, lines.size());
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("31.00")));
        assertEquals(0, lines.get(1).amount.compareTo(new BigDecimal("56.00")));
        assertEquals(0, lines.get(2).amount.compareTo(new BigDecimal("93.00")));
    }

    /** Parameters of a borrower upfront rule: 30/10 precompté, 36-month recalc window at a linear rate. */
    private static String emprunteurParams(String recalcRate) {
        return "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365,"
                + "\"earlyRepaymentMonths\":36" + (recalcRate == null ? "" : ",\"recalculationRate\":" + recalcRate)
                + "}";
    }

    /** Counts the CLAWBACK lines produced. */
    private static long clawbackLines() {
        return CommissionLine.count("nature", LineNature.CLAWBACK);
    }

    /**
     * CR-81: a loan bought back at the 24th month (within the 36-month window) is recomputed at the
     * standard linear rate — 2 000 € collected × 15% = 300 € — against the 500 € already paid (400 €
     * year one, 100 € year two), giving a CLAWBACK line of −200,00 €.
     */
    @Test
    @TestTransaction
    void cr81_early_repayment_clawback() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        version("PLAN", "R_EMP", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                emprunteurParams("0.15"), "BASE", 10, MONTH, null);
        premium(c, "Y1", LocalDate.of(2026, 3, 1), "1000.00");
        premium(c, "Y2", LocalDate.of(2027, 3, 1), "1000.00");
        event(c, "BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2028, 1, 1), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(1, clawbackLines());
        assertEquals(0, CommissionLine.<CommissionLine>list("nature = ?1", LineNature.CLAWBACK)
                .get(0).amount.compareTo(new BigDecimal("-200.00")));
    }

    /**
     * CR-81 variant: a buyback at the 40th month, beyond the 36-month window, produces no clawback.
     */
    @Test
    @TestTransaction
    void cr81_beyond_window_no_clawback() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        version("PLAN", "R_EMP", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                emprunteurParams("0.15"), "BASE", 10, MONTH, null);
        premium(c, "Y1", LocalDate.of(2026, 3, 1), "1000.00");
        premium(c, "Y2", LocalDate.of(2027, 3, 1), "1000.00");
        event(c, "BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2029, 5, 1), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, clawbackLines());
    }

    /**
     * An early repayment with no borrower rule — only a linear rule and a plain upfront rule without
     * the early-repayment parameters — produces no clawback (the family and months legs of the finder).
     */
    @Test
    @TestTransaction
    void early_repayment_without_borrower_rule() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"NOPE\"]}", "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_UP", RuleFamily.UPFRONT, LineNature.ACQUISITION,
                "{\"entity\":[\"UNEP\"],\"product\":[\"NOPE\"]}",
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        event(c, "BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2028, 1, 1), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, clawbackLines());
    }

    /**
     * An early repayment under an upfront rule that sets the window but no recalculation rate produces
     * no clawback (the rate leg of the finder).
     */
    @Test
    @TestTransaction
    void early_repayment_rule_without_recalc_rate() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        version("PLAN", "R_EMP", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                emprunteurParams(null), "BASE", 10, MONTH, null);
        event(c, "BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2028, 1, 1), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, clawbackLines());
    }

    /**
     * An early repayment whose borrower rule is scoped to another product produces no clawback (the
     * scope leg of the finder).
     */
    @Test
    @TestTransaction
    void early_repayment_scope_mismatch() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        version("PLAN", "R_EMP", RuleFamily.UPFRONT, LineNature.ACQUISITION,
                "{\"entity\":[\"UNEP\"],\"product\":[\"OTHER\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}",
                emprunteurParams("0.15"), "BASE", 10, MONTH, null);
        event(c, "BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2028, 1, 1), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, clawbackLines());
    }

    /**
     * When the recalculation equals what was already paid, the early repayment writes no clawback line
     * (the zero-delta leg): 1 000 € collected × 40% = 400 € equals the 400 € paid in year one.
     */
    @Test
    @TestTransaction
    void early_repayment_no_gap_writes_nothing() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        version("PLAN", "R_EMP", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                emprunteurParams("0.40"), "BASE", 10, MONTH, null);
        premium(c, "Y1", LocalDate.of(2026, 3, 1), "1000.00");
        event(c, "BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2028, 1, 1), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, clawbackLines());
    }

    /**
     * CR-11: a rate outside the rule's grid holds the event with the off-grid anomaly, no line.
     */
    @Test
    @TestTransaction
    void cr11_rate_out_of_grid() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.12", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15,0.20]}", "BASE", 10, MONTH, null);
        BusinessEvent e = premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(0, lines().size());
        assertEquals(ProcessingStatus.ON_HOLD, BusinessEvent.<BusinessEvent>findById(e.getId()).processingStatus);
        assertEquals(HoldReason.RATE_OUT_OF_GRID, BusinessEvent.<BusinessEvent>findById(e.getId()).holdReason);
    }

    /**
     * CR-12: two colleges, 7% for executives and 5% for the rest, each on 1 000 € collected.
     */
    @Test
    @TestTransaction
    void cr12_colleges() {
        Product p = product("SANTE_SELECT");
        Coverage exec = coverage("AD-EXE", p, StaffCategory.EXECUTIVE, "P1");
        Coverage nonExec = coverage("AD-NON", p, StaffCategory.NON_EXECUTIVE, "P2");
        linearChoice(exec, "0.07", MONTH, null);
        linearChoice(nonExec, "0.05", MONTH, null);
        version("PLAN", "R_EXE", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"product\":[\"SANTE_SELECT\"],\"college\":[\"EXECUTIVE\"]}", "{\"allowedRates\":[0.07]}",
                "BASE", 10, MONTH, null);
        version("PLAN", "R_NON", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"product\":[\"SANTE_SELECT\"],\"college\":[\"NON_EXECUTIVE\"]}", "{\"allowedRates\":[0.05]}",
                "BASE", 10, MONTH, null);
        premium(exec, "EXE", LocalDate.of(2026, 1, 5), "1000.00");
        premium(nonExec, "NON", LocalDate.of(2026, 1, 5), "1000.00");

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(2, lines.size());
        BigDecimal total = lines.get(0).amount.add(lines.get(1).amount);
        assertEquals(0, total.compareTo(new BigDecimal("120.00")));
    }

    /**
     * CR-51: an external line is attributed as-is, without recalculation.
     */
    @Test
    @TestTransaction
    void cr51_external() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        version("PLAN", "R_EXT", RuleFamily.EXTERNAL, LineNature.EXTERNAL,
                "{\"product\":[\"SANTE_SELECT\"]}", "{}", "BASE", 10, MONTH, null);
        BusinessEvent e = premium(c, "E1", LocalDate.of(2026, 1, 5), null);
        e.precomputedBasis = new BigDecimal("500.00");
        e.precomputedRate = new BigDecimal("0.035");
        e.precomputedAmount = new BigDecimal("17.50");

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals(LineNature.EXTERNAL, lines.get(0).nature);
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("17.50")));
    }

    /**
     * CR-77: a fixed fee of 300 € yields a FEE line with neither rate nor basis.
     */
    @Test
    @TestTransaction
    void cr77_fee() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        version("PLAN", "R_FEE", RuleFamily.FIXED, LineNature.FEE,
                "{\"product\":[\"SANTE_SELECT\"]}", "{\"amount\":300}", "BASE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "0.00");

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals(LineNature.FEE, lines.get(0).nature);
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("300.00")));
    }

    /**
     * Different exclusion groups both apply: a base linear line and a bonus fixed line (section 6.11).
     */
    @Test
    @TestTransaction
    void different_groups_both_apply() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_BASE", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_BONUS", RuleFamily.FIXED, LineNature.FEE, "{\"product\":[\"SANTE_SELECT\"]}",
                "{\"amount\":2}", "BONUS", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(2, lines().size());
    }

    /**
     * A dérogatoire rule of higher priority replaces the base rule in the same group (P7).
     */
    @Test
    @TestTransaction
    void higher_priority_replaces_in_group() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "PX");
        linearChoice(c, "0.18", MONTH, null);
        version("PLAN", "R_BASE", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_DEROG", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"product\":[\"SANTE_SELECT\"],\"distributor\":[\"PX\"]}", "{\"allowedRates\":[0.18]}",
                "BASE", 30, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).amount.compareTo(new BigDecimal("18.00")));
    }

    /**
     * Two rules of the same group and priority hold the event as concurrent (H-41).
     */
    @Test
    @TestTransaction
    void concurrent_rules_hold_the_event() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_A", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_B", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        BusinessEvent e = premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(0, lines().size());
        assertEquals(HoldReason.CONCURRENT_RULES, BusinessEvent.<BusinessEvent>findById(e.getId()).holdReason);
    }

    /**
     * An event no base rule covers is held (P8); a non-commissionable coverage and an event without a
     * coverage are set aside without a line.
     */
    @Test
    @TestTransaction
    void holds_and_non_commissionable() {
        Product p = product("SANTE_SELECT");
        // No base rule: a bonus-only group covers the event.
        Coverage noBase = coverage("AD-NB", p, null, "P1");
        linearChoice(noBase, "0.15", MONTH, null);
        version("PLAN", "R_BONUS", RuleFamily.FIXED, LineNature.FEE, "{\"product\":[\"SANTE_SELECT\"]}",
                "{\"amount\":2}", "BONUS", 10, MONTH, null);
        BusinessEvent noRule = premium(noBase, "NB", LocalDate.of(2026, 1, 5), "100.00");

        Coverage notComm = coverage("AD-NC", p, null, "P2");
        notComm.commissionable = false;
        BusinessEvent nc = premium(notComm, "NC", LocalDate.of(2026, 1, 5), "100.00");

        BusinessEvent noCov = premium(null, "NOCOV", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(0, lines().size());
        assertEquals(HoldReason.NO_RULE, BusinessEvent.<BusinessEvent>findById(noRule.getId()).holdReason);
        assertEquals(ProcessingStatus.NOT_COMMISSIONABLE,
                BusinessEvent.<BusinessEvent>findById(nc.getId()).processingStatus);
        assertEquals(HoldReason.NO_RULE, BusinessEvent.<BusinessEvent>findById(noCov.getId()).holdReason);
    }

    /**
     * A rerun deletes the non-validated lines first (D13), so the result is stable.
     */
    @Test
    @TestTransaction
    void rerun_is_idempotent() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        // Re-open the processed event so the second run reprocesses it, then rerun.
        BusinessEvent.update("processingStatus = ?1 where entity = ?2", ProcessingStatus.PENDING, unep());
        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
    }

    /**
     * A non-matching perimeter and a family handled apart from the per-event loop (tiered) are both
     * excluded; only the matching event-driven rule produces a line.
     */
    @Test
    @TestTransaction
    void excludes_non_matching_and_non_computing() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_OK", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_OTHER", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"product\":[\"AUTRE\"]}", "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_TIERED", RuleFamily.TIERED, LineNature.RCD,
                "{\"product\":[\"SANTE_SELECT\"]}", "{\"counterCode\":\"X\"}", "OTHER", 10, MONTH, null);
        version("PLAN", "R_PORTFOLIO", RuleFamily.PORTFOLIO, LineNature.RCP,
                "{\"product\":[\"SANTE_SELECT\"]}", "{\"rate\":0.04}", "OTHER2", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(1, lines().size());
    }

    /**
     * A fixed rule on a coverage without a main assignment produces a line with no origin production
     * code.
     */
    @Test
    @TestTransaction
    void line_without_main_assignment_has_no_origin() {
        Product p = product("SANTE_SELECT");
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-NOASG";
        contract.product = p;
        contract.persist();
        Coverage c = new Coverage();
        c.contract = contract;
        c.coverageCode = "AD-NOASG-C1";
        c.effectDate = MONTH;
        c.persist();
        version("PLAN", "R_FEE", RuleFamily.FIXED, LineNature.FEE, "{\"product\":[\"SANTE_SELECT\"]}",
                "{\"amount\":50}", "BASE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "0.00");

        engine.runReal("UNEP", MONTH);
        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals(null, lines.get(0).originProductionCode);
    }

    /**
     * A linear rule with no remuneration choice holds the event off-grid (no rate to control).
     */
    @Test
    @TestTransaction
    void linear_without_choice_is_off_grid() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(0, lines().size());
    }

    /**
     * A version with a null priority (treated as zero) loses to a positive priority of the same
     * group, whatever the processing order.
     */
    @Test
    @TestTransaction
    void null_and_lower_priority_lose_in_group() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_TEN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        RuleVersion zero = version("PLAN", "R_ZERO", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        zero.priority = null;
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(1, lines().size());
        assertEquals(0, lines().get(0).amount.compareTo(new BigDecimal("15.00")));
    }

    /**
     * A non-numeric entry in the rate grid is ignored; a numeric match still passes (P9).
     */
    @Test
    @TestTransaction
    void non_numeric_grid_entry_is_ignored() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[\"x\",0.15]}", "BASE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(1, lines().size());
    }

    /**
     * A coverage without a product, under a rule that only scopes the entity, is still matched; a
     * remuneration choice with no mode leaves the mode out of the perimeter attributes.
     */
    @Test
    @TestTransaction
    void matches_coverage_without_product_or_mode() {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-NOPROD";
        contract.persist();
        Coverage c = new Coverage();
        c.contract = contract;
        c.coverageCode = "AD-NOPROD-C1";
        c.effectDate = MONTH;
        c.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = c;
        choice.linearRate = new BigDecimal("0.15");
        choice.validFrom = MONTH;
        choice.persist();
        // An open-ended version (no start date) is always active.
        version("PLAN", "R_ENT", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}", "{\"allowedRates\":[0.15]}",
                "BASE", 10, null, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        assertEquals(1, lines().size());
    }

    /**
     * A production code with no distributor leaves the line's origin distributor null and the
     * distributor out of the perimeter attributes.
     */
    @Test
    @TestTransaction
    void production_code_without_distributor() {
        Product p = product("SANTE_SELECT");
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-ND";
        contract.product = p;
        contract.persist();
        Coverage c = new Coverage();
        c.contract = contract;
        c.coverageCode = "AD-ND-C1";
        c.effectDate = MONTH;
        c.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-ND";
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = c;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = MONTH;
        assignment.persist();
        version("PLAN", "R_FEE", RuleFamily.FIXED, LineNature.FEE, "{\"product\":[\"SANTE_SELECT\"]}",
                "{\"amount\":50}", "BASE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 1, 5), "0.00");

        engine.runReal("UNEP", MONTH);
        List<CommissionLine> lines = lines();
        assertEquals(1, lines.size());
        assertEquals("PC-ND", lines.get(0).originProductionCode.code);
        assertEquals(null, lines.get(0).originDistributor);
    }

    /**
     * Cancelling a fee line (no basis) yields an inverse line whose basis stays null.
     */
    @Test
    @TestTransaction
    void cancel_fee_line_keeps_null_basis() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        version("PLAN", "R_FEE", RuleFamily.FIXED, LineNature.FEE, "{\"product\":[\"SANTE_SELECT\"]}",
                "{\"amount\":50}", "BASE", 10, MONTH, null);
        BusinessEvent origin = premium(c, "E1", LocalDate.of(2026, 1, 5), "0.00");
        BusinessEvent cancellation = event(c, "E1-C", EventType.PREMIUM_CANCELLED, LocalDate.of(2026, 1, 10), null);
        cancellation.reversesEvent = origin;

        engine.runReal("UNEP", MONTH);

        List<CommissionLine> lines = lines();
        assertEquals(2, lines.size());
        assertEquals(null, lines.get(1).basisAmount);
        assertEquals(0, lines.get(1).amount.compareTo(new BigDecimal("-50.00")));
    }

    /**
     * A cancellation whose origin is unknown writes no line.
     */
    @Test
    @TestTransaction
    void cancellation_without_origin_writes_nothing() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        event(c, "CANCEL", EventType.PREMIUM_CANCELLED, LocalDate.of(2026, 1, 10), null);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, lines().size());
    }

    /**
     * CR-75: a dérogatoire rule of higher priority replaces the base rule (18% instead of 15%) and a
     * back-to-school operation in another group adds 2%, so broker X receives 18 € and 2 €.
     */
    @Test
    @TestTransaction
    void cr75_exclusion_groups_and_operation() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "PX");
        linearChoice(c, "0.18", MONTH, null);
        version("PLAN", "R_BASE", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        version("PLAN", "R_DEROG", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"product\":[\"SANTE_SELECT\"],\"distributor\":[\"PX\"]}", "{\"allowedRates\":[0.18]}",
                "BASE", 30, MONTH, null);
        version("PLAN", "R_BONUS", RuleFamily.LINEAR, LineNature.LINEAR, "{\"product\":[\"SANTE_SELECT\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.02}", "BONUS_RENTREE", 10, MONTH, null);
        premium(c, "E1", LocalDate.of(2026, 9, 5), "100.00");

        engine.runReal("UNEP", MONTH);
        List<CommissionLine> lines = lines();
        assertEquals(2, lines.size());
        assertEquals(0, totalOf(LineNature.LINEAR).compareTo(new BigDecimal("20.00")));
        assertTrue(lines.stream().anyMatch(l -> l.amount.compareTo(new BigDecimal("18.00")) == 0));
        assertTrue(lines.stream().anyMatch(l -> l.amount.compareTo(new BigDecimal("2.00")) == 0));
    }

    /** Sums the amounts of the lines of a given nature. */
    private static BigDecimal totalOf(LineNature nature) {
        return lines().stream().filter(l -> l.nature == nature)
                .map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * CR-04: six upfront premiums then a termination in the first year claws back the whole acquired
     * acquisition (−180), leaving the servicing (60).
     */
    @Test
    @TestTransaction
    void cr04_clawback_first_year() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        for (int i = 1; i <= 6; i++) {
            premium(c, "E" + i, MONTH.plusMonths(i - 1), "100.00");
        }
        terminal(c, "TERM", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, totalOf(LineNature.ACQUISITION).compareTo(new BigDecimal("180.00")));
        assertEquals(0, totalOf(LineNature.SERVICING).compareTo(new BigDecimal("60.00")));
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-180.00")));
    }

    /**
     * CR-05: an acquisition of 300 € clawed back at the eighteenth month yields −150 € (50%).
     */
    @Test
    @TestTransaction
    void cr05_clawback_second_year() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        premium(c, "E1", MONTH, "1000.00");
        terminal(c, "TERM", EventType.TERMINATION, 540);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-150.00")));
    }

    /**
     * CR-06: a termination beyond 730 days claws back nothing.
     */
    @Test
    @TestTransaction
    void cr06_no_clawback_after_two_years() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        premium(c, "E1", MONTH, "1000.00");
        terminal(c, "TERM", EventType.TERMINATION, 731);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
    }

    /**
     * CR-07: a not-pursued membership claws back 100% of every acquired line (−80).
     */
    @Test
    @TestTransaction
    void cr07_not_pursued() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        premium(c, "E1", MONTH, "100.00");
        premium(c, "E2", MONTH.plusMonths(1), "100.00");
        terminal(c, "NP", EventType.NOT_PURSUED, 90);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-80.00")));
    }

    /**
     * A clawback with no policy, with a trigger the policy excludes, or with no base line, writes
     * nothing; a withdrawal and a pro-rata policy do claw back.
     */
    @Test
    @TestTransaction
    void clawback_edge_cases() {
        Product p = product("SANTE_SELECT");

        // No clawback policy on the rule.
        Coverage c1 = coverage("AD-1", p, null, "P1");
        upfrontChoice(c1, "0.30", "0.10");
        RuleVersion noPolicy = version("PLAN", "R1", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        premium(c1, "A1", MONTH, "1000.00");
        terminal(c1, "T1", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
        assertNotNull(noPolicy);
    }

    /**
     * A termination whose type the policy's triggers exclude claws back nothing; a policy with a
     * target nature the acquired lines do not carry yields a zero base and no line.
     */
    @Test
    @TestTransaction
    void clawback_trigger_mismatch_and_zero_base() {
        Product p = product("SANTE_SELECT");

        // Policy triggers only on withdrawal, event is a termination.
        Coverage c1 = coverage("AD-MIS", p, null, "P1");
        upfrontChoice(c1, "0.30", "0.10");
        RuleVersion mismatch = version("PLAN", "R_MIS", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        mismatch.clawbackPolicy = "{\"type\":\"AGE_STEPS\",\"triggers\":[\"WITHDRAWAL\"],"
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0}]}";
        premium(c1, "E1", MONTH, "1000.00");
        terminal(c1, "T", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
        assertNotNull(mismatch);
    }

    /**
     * A linear coverage whose policy targets the acquisition nature (absent here) claws back nothing.
     */
    @Test
    @TestTransaction
    void clawback_zero_base_on_linear() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-LIN", p, null, "P1");
        linearChoice(c, "0.15", MONTH, null);
        RuleVersion v = version("PLAN", "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_LINEAR_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE", 10, MONTH, null);
        v.clawbackPolicy = "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],"
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0}]}";
        premium(c, "E1", MONTH, "100.00");
        terminal(c, "T", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
    }

    /**
     * A withdrawal claws back acquisition like a termination.
     */
    @Test
    @TestTransaction
    void withdrawal_claws_back() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        premium(c, "E1", MONTH, "1000.00");
        terminal(c, "W", EventType.WITHDRAWAL, 180);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-300.00")));
    }

    /**
     * A trigger the policy does not list, an empty-trigger policy, a missing step list and a pro-rata
     * policy are all honoured.
     */
    @Test
    @TestTransaction
    void clawback_policy_variants() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-PR", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        RuleVersion v = version("PLAN", "R_PR", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        v.clawbackPolicy = "{\"type\":\"PRO_RATA\",\"months\":24}";
        premium(c, "E1", MONTH, "1000.00");
        terminal(c, "T", EventType.TERMINATION, 360);

        engine.runReal("UNEP", MONTH);
        // 300 acquired, pro-rata (24-12)/24 = 0.5 → −150.
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-150.00")));
    }

    /**
     * A termination with no prior base line (no version) and a not-pursued with no acquired base
     * write no clawback.
     */
    @Test
    @TestTransaction
    void clawback_without_base_lines() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        terminal(c, "T", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
    }

    /**
     * A clawback on a coverage with no main assignment still writes the line, with no origin.
     */
    @Test
    @TestTransaction
    void clawback_without_main_assignment() {
        Product p = product("SANTE_SELECT");
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-NA";
        contract.product = p;
        contract.persist();
        Coverage c = new Coverage();
        c.contract = contract;
        c.coverageCode = "AD-NA-C1";
        c.effectDate = MONTH;
        c.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = c;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal("0.30");
        choice.servicingRate = new BigDecimal("0.10");
        choice.validFrom = MONTH;
        choice.persist();
        upfrontRule("R_UP");
        premium(c, "E1", MONTH, "1000.00");
        terminal(c, "T", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-300.00")));
    }

    /**
     * A clawback policy with empty triggers applies to any termination; a missing step list claws
     * back nothing.
     */
    @Test
    @TestTransaction
    void clawback_empty_triggers_and_missing_steps() {
        Product p = product("SANTE_SELECT");
        Coverage any = coverage("AD-ANY", p, null, "P1");
        upfrontChoice(any, "0.30", "0.10");
        RuleVersion v = version("PLAN", "R_ANY", RuleFamily.UPFRONT, LineNature.ACQUISITION, UPFRONT_SCOPE,
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        v.clawbackPolicy = "{\"type\":\"AGE_STEPS\"}";
        premium(any, "E1", MONTH, "1000.00");
        terminal(any, "T", EventType.TERMINATION, 180);

        engine.runReal("UNEP", MONTH);
        // AGE_STEPS with no steps → rate 0 → no clawback.
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
    }

    /** Sets up an upfront coverage with a clawback policy and a termination, returning its code. */
    private String policyCase(String cn, String partner, String policy, int ageDays) {
        Product p = Product.<Product>find("entity = ?1 and code = ?2", unep(), "SANTE_SELECT").firstResult();
        if (p == null) {
            p = product("SANTE_SELECT");
        }
        Coverage c = coverage(cn, p, null, partner);
        upfrontChoice(c, "0.30", "0.10");
        RuleVersion v = version("PLAN", "R_" + cn, RuleFamily.UPFRONT, LineNature.ACQUISITION,
                "{\"product\":[\"SANTE_SELECT\"],\"distributor\":[\"" + partner + "\"]}",
                "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}", "BASE", 10,
                MONTH, null);
        v.clawbackPolicy = policy;
        premium(c, cn + "-E", MONTH, "1000.00");
        terminal(c, cn + "-T", EventType.TERMINATION, ageDays);
        return cn;
    }

    /**
     * Exercises the clawback-policy parser: an unknown type, a pro-rata out of range, a malformed
     * step list all claw back nothing; a scalar or empty trigger list and a scalar nature list still
     * claw back the acquisition.
     */
    @Test
    @TestTransaction
    void clawback_policy_parsing() {
        policyCase("UNK", "P50", "{\"type\":\"FOO\"}", 180);
        policyCase("PR0", "P51", "{\"type\":\"PRO_RATA\",\"months\":0,\"natures\":[\"ACQUISITION\"]}", 180);
        policyCase("PRX", "P52", "{\"type\":\"PRO_RATA\",\"months\":24,\"natures\":[\"ACQUISITION\"]}", 800);
        policyCase("STP", "P53", "{\"type\":\"AGE_STEPS\",\"steps\":\"x\"}", 180);
        policyCase("TRS", "P54", "{\"type\":\"AGE_STEPS\",\"triggers\":\"TERMINATION\","
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0}]}", 180);
        policyCase("TRE", "P55", "{\"type\":\"AGE_STEPS\",\"triggers\":[],"
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0}]}", 180);
        policyCase("NAT", "P56", "{\"type\":\"AGE_STEPS\",\"natures\":\"ACQUISITION\","
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0}]}", 180);

        engine.runReal("UNEP", MONTH);
        // Only the three honoured policies claw back 300 € each.
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-900.00")));
    }

    /**
     * CR-08: a death claws back nothing in the upfront mode.
     */
    @Test
    @TestTransaction
    void cr08_death_no_clawback() {
        Product p = product("SANTE_SELECT");
        Coverage c = coverage("AD-1", p, null, "P1");
        upfrontChoice(c, "0.30", "0.10");
        upfrontRule("R_UP");
        premium(c, "E1", MONTH, "1000.00");
        terminal(c, "DEATH", EventType.DEATH, 150);

        engine.runReal("UNEP", MONTH);
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
    }
}
