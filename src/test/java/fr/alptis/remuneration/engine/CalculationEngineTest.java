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
     * A non-matching perimeter and a non-computing family are both excluded; only the matching
     * computing rule produces a line.
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
        version("PLAN", "R_UPFRONT", RuleFamily.UPFRONT, LineNature.ACQUISITION,
                "{\"product\":[\"SANTE_SELECT\"]}", "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],"
                        + "\"firstYearDays\":365}", "OTHER", 10, MONTH, null);
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
}
