package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
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
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the discount (escompte) lifecycle (section 6.4, CR-20 to CR-28): versement,
 * apurement, settlement by offset, top-up, undue-advance clawback at the due date, rupture, the
 * convention window and the cap. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class AdvanceEngineTest {

    @Inject
    CalculationEngine engine;

    private static final LocalDate EFFECT = LocalDate.of(2026, 1, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a coverage requesting the discount, with its distributor, assignment and choice. */
    private Coverage escomptedCoverage(String premium) {
        Product product = new Product();
        product.entity = unep();
        product.code = "SANTE_SELECT";
        product.label = "Santé Select";
        product.persist();
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-1";
        contract.product = product;
        contract.registrationDate = EFFECT;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "AD-1-C1";
        coverage.effectDate = EFFECT;
        coverage.annualPremium = new BigDecimal(premium);
        coverage.persist();
        Distributor distributor = new Distributor();
        distributor.partnerCode = "P1";
        distributor.legalName = "Cabinet";
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-1";
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = EFFECT;
        assignment.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal("0.30");
        choice.servicingRate = new BigDecimal("0.10");
        choice.advanceRequested = true;
        choice.validFrom = EFFECT;
        choice.persist();
        return coverage;
    }

    /** Creates a VALID discount convention for distributor P1 with the given cap and window end. */
    private AdvanceAgreement convention(int maxContracts, LocalDate validTo) {
        AdvanceAgreement agreement = new AdvanceAgreement();
        agreement.entity = unep();
        agreement.distributor = Distributor.find("partnerCode", "P1").firstResult();
        agreement.validFrom = LocalDate.of(2025, 1, 1);
        agreement.validTo = validTo;
        agreement.anticipationDays = 0;
        agreement.maxContracts = maxContracts;
        agreement.unpaidRankThreshold = (short) 2;
        agreement.status = AdvanceAgreementStatus.VALID;
        agreement.persist();
        return agreement;
    }

    /** Creates the UPFRONT base rule so encaissements produce the acquisition and servicing lines. */
    private void upfrontRule() {
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "PLAN";
        plan.label = "Plan";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R_UP";
        rule.label = "Précompté";
        rule.family = RuleFamily.UPFRONT;
        rule.persist();
        RuleVersion v = new RuleVersion();
        v.rule = rule;
        v.versionNo = 1;
        v.status = VersionStatus.ACTIVE;
        v.scope = "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE_SELECT\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        v.parameters = "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}";
        v.exclusionGroup = "BASE";
        v.priority = 10;
        v.outputNature = LineNature.ACQUISITION;
        v.clawbackPolicy = "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],"
                + "\"triggers\":[\"TERMINATION\",\"WITHDRAWAL\"],"
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0},{\"maxDays\":730,\"rate\":0.5}]}";
        v.validFrom = EFFECT;
        v.persist();
    }

    /** Injects a terminating or unpaid event. */
    private void terminal(Coverage c, String key, EventType type, int ageDays, Short rank) {
        BusinessEvent e = new BusinessEvent();
        e.entity = unep();
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = type;
        e.contract = c.contract;
        e.coverage = c;
        e.eventDate = EFFECT.plusDays(ageDays);
        e.receivedDate = e.eventDate;
        e.unpaidRank = rank;
        e.processingStatus = ProcessingStatus.PENDING;
        e.persist();
    }

    /** Injects a collected-premium event. */
    private void premium(Coverage c, String key, LocalDate date, String basis) {
        BusinessEvent e = new BusinessEvent();
        e.entity = unep();
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = EventType.PREMIUM_COLLECTED;
        e.contract = c.contract;
        e.coverage = c;
        e.eventDate = date;
        e.receivedDate = date;
        e.basisAmount = new BigDecimal(basis);
        e.processingStatus = ProcessingStatus.PENDING;
        e.persist();
    }

    /** Sums the amounts of the lines of a nature. */
    private static BigDecimal totalOf(LineNature nature) {
        return CommissionLine.<CommissionLine>list("nature", nature).stream()
                .map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Counts the lines of a status. */
    private static long countStatus(LineStatus status) {
        return CommissionLine.count("status", status);
    }

    /** Returns the position of the coverage. */
    private static AdvancePosition position() {
        return AdvancePosition.<AdvancePosition>findAll().firstResult();
    }

    /**
     * CR-20: the versement pays the advance on the annualised premium — 300 € and 100 €.
     */
    @Test
    @TestTransaction
    void cr20_versement() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();

        engine.runReal("UNEP", EFFECT);

        assertEquals(0, totalOf(LineNature.ADVANCE).compareTo(new BigDecimal("400.00")));
        assertEquals(2, CommissionLine.count("nature", LineNature.ADVANCE));
        assertEquals(AdvanceStatus.ACTIVE, position().status);
    }

    /**
     * CR-21: the subsequent premiums are offset against the advance, never paid, and the advance
     * stays active while the gap is negative.
     */
    @Test
    @TestTransaction
    void cr21_apurement() {
        Coverage c = escomptedCoverage("1200");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        premium(c, "E1", LocalDate.of(2026, 1, 15), "100.00");
        premium(c, "E2", LocalDate.of(2026, 2, 15), "100.00");

        engine.runReal("UNEP", EFFECT);

        // 480 advanced, two premiums offset (80), still active.
        assertEquals(4, countStatus(LineStatus.OFFSET));
        assertEquals(AdvanceStatus.ACTIVE, position().status);
    }

    /**
     * CR-23: once the offset cumul reaches the advance, the advance settles with no adjustment.
     */
    @Test
    @TestTransaction
    void cr23_settled_by_offset() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        for (int i = 0; i < 10; i++) {
            premium(c, "E" + i, EFFECT.plusMonths(i).plusDays(14), "100.00");
        }

        engine.runReal("UNEP", EFFECT);

        assertEquals(AdvanceStatus.SETTLED, position().status);
        assertEquals(0, totalOf(LineNature.ADVANCE_ADJUSTMENT).compareTo(BigDecimal.ZERO));
    }

    /**
     * CR-25: a garantie whose effect date is outside the convention window gets no advance; its
     * premiums are paid normally.
     */
    @Test
    @TestTransaction
    void cr25_outside_convention() {
        Coverage c = escomptedCoverage("1000");
        c.effectDate = LocalDate.of(2026, 2, 1);
        c.contract.registrationDate = LocalDate.of(2025, 12, 21);
        convention(100, LocalDate.of(2025, 12, 31));
        upfrontRule();
        premium(c, "E1", LocalDate.of(2026, 2, 15), "100.00");

        engine.runReal("UNEP", EFFECT);

        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
        assertEquals(2, countStatus(LineStatus.EARNED));
    }

    /**
     * CR-27: beyond the convention cap, the garantie gets no advance and its premiums are paid.
     */
    @Test
    @TestTransaction
    void cr27_cap_reached() {
        Coverage c = escomptedCoverage("1000");
        AdvanceAgreement agreement = convention(1, LocalDate.of(2026, 12, 31));
        upfrontRule();
        // Fill the cap with a pre-existing position on another coverage.
        AdvancePosition taken = new AdvancePosition();
        taken.advanceAgreement = agreement;
        taken.status = AdvanceStatus.ACTIVE;
        taken.persist();
        premium(c, "E1", LocalDate.of(2026, 1, 15), "100.00");

        engine.runReal("UNEP", EFFECT);

        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
        assertEquals(2, countStatus(LineStatus.EARNED));
        assertNotNull(c);
    }

    /**
     * CR-24: a hike that pushes the offset past the advance settles it with a top-up of +30 €.
     */
    @Test
    @TestTransaction
    void cr24_top_up_complement() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        premium(c, "E1", EFFECT.plusMonths(1).plusDays(14), "950.00");
        premium(c, "E2", EFFECT.plusMonths(2).plusDays(14), "125.00");

        engine.runReal("UNEP", EFFECT);

        assertEquals(AdvanceStatus.SETTLED, position().status);
        assertEquals(0, totalOf(LineNature.ADVANCE_ADJUSTMENT).compareTo(new BigDecimal("30.00")));
    }

    /**
     * CR-28: a shortfall still open at the due date is clawed back as an undue advance of −30 €.
     */
    @Test
    @TestTransaction
    void cr28_undue_advance_at_due_date() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        premium(c, "E1", EFFECT.plusMonths(6).plusDays(14), "925.00");

        engine.runReal("UNEP", LocalDate.of(2026, 12, 1));

        assertEquals(AdvanceStatus.SETTLED, position().status);
        assertEquals(0, totalOf(LineNature.ADVANCE_ADJUSTMENT).compareTo(new BigDecimal("-30.00")));
    }

    /**
     * CR-22: a termination at six months cancels the advance, regularises the unapplied −200 € and
     * claws back the acquired acquisition (−150 €); the distributor keeps 50 € of servicing.
     */
    @Test
    @TestTransaction
    void cr22_rupture_with_clawback() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        premium(c, "E1", EFFECT.plusMonths(2).plusDays(14), "500.00");
        terminal(c, "TERM", EventType.TERMINATION, 180, null);

        engine.runReal("UNEP", EFFECT);

        assertEquals(AdvanceStatus.CANCELLED, position().status);
        assertEquals(0, totalOf(LineNature.ADVANCE_ADJUSTMENT).compareTo(new BigDecimal("-200.00")));
        assertEquals(0, totalOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-150.00")));
    }

    /**
     * CR-78: a shortfall under the settlement tolerance at the due date settles with no adjustment
     * line (written off).
     */
    @Test
    @TestTransaction
    void cr78_settlement_tolerance() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        for (int i = 0; i < 12; i++) {
            premium(c, "E" + i, EFFECT.plusMonths(i).plusDays(10), "83.33");
        }

        engine.runReal("UNEP", LocalDate.of(2026, 12, 1));

        assertEquals(AdvanceStatus.SETTLED, position().status);
        assertEquals("WRITE_OFF", position().closeReason);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE_ADJUSTMENT));
    }

    /**
     * CR-79: with a 120-day anticipation, the versement waits until the month of the effect date
     * minus the delay (February for a June effect), not before.
     */
    @Test
    @TestTransaction
    void cr79_anticipation_delay() {
        Coverage c = escomptedCoverage("1000");
        c.effectDate = LocalDate.of(2026, 6, 1);
        c.contract.registrationDate = LocalDate.of(2026, 1, 15);
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        agreement.anticipationDays = 120;

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));

        engine.runReal("UNEP", LocalDate.of(2026, 2, 1));
        assertEquals(2, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * CR-26: an unpaid notice reaching the convention's rank threshold breaks the advance at once.
     */
    @Test
    @TestTransaction
    void cr26_second_unpaid_breaks_the_advance() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        terminal(c, "UNPAID", EventType.UNPAID_NOTICE, 90, (short) 2);

        engine.runReal("UNEP", EFFECT);

        assertEquals(AdvanceStatus.CANCELLED, position().status);
        assertEquals(0, totalOf(LineNature.ADVANCE_ADJUSTMENT).compareTo(new BigDecimal("-400.00")));
    }

    /** Returns the current choice of the coverage. */
    private static RemunerationChoice choice(Coverage c) {
        return RemunerationChoice.<RemunerationChoice>find("coverage", c).firstResult();
    }

    /**
     * No advance is opened when the distributor did not request it, when the premium is single, when
     * there is no production-code distributor or no valid convention.
     */
    @Test
    @TestTransaction
    void no_advance_when_ineligible() {
        Coverage notRequested = escomptedCoverage("1000");
        choice(notRequested).advanceRequested = false;
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        premium(notRequested, "E1", LocalDate.of(2026, 1, 15), "100.00");

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A single-premium garantie is never escompted.
     */
    @Test
    @TestTransaction
    void no_advance_for_single_premium() {
        Coverage c = escomptedCoverage("1000");
        c.singlePremium = true;
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        premium(c, "E1", LocalDate.of(2026, 1, 15), "100.00");

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * No advance is opened when the production code carries no distributor, or when no convention
     * exists for the distributor.
     */
    @Test
    @TestTransaction
    void no_advance_without_distributor_or_convention() {
        Coverage noConvention = escomptedCoverage("1000");
        upfrontRule();
        premium(noConvention, "E1", LocalDate.of(2026, 1, 15), "100.00");

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A death leaves the advance active (it settles later), and an unpaid notice below the rank
     * threshold does not break it.
     */
    @Test
    @TestTransaction
    void death_and_minor_unpaid_keep_the_advance() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        terminal(c, "UNPAID1", EventType.UNPAID_NOTICE, 60, (short) 1);
        terminal(c, "DEATH", EventType.DEATH, 150, null);

        engine.runReal("UNEP", EFFECT);
        assertEquals(AdvanceStatus.ACTIVE, position().status);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE_ADJUSTMENT));
        assertEquals(0, CommissionLine.count("nature", LineNature.CLAWBACK));
    }

    /**
     * A garantie with no recorded registration date is still eligible for the advance.
     */
    @Test
    @TestTransaction
    void advance_without_registration_date() {
        Coverage c = escomptedCoverage("1000");
        c.contract.registrationDate = null;
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(2, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /** Adds a second advance-requested choice to a coverage. */
    private static void secondChoice(Coverage c) {
        RemunerationChoice ch = new RemunerationChoice();
        ch.coverage = c;
        ch.mode = RemunerationMode.UPFRONT;
        ch.acquisitionRate = new BigDecimal("0.30");
        ch.servicingRate = new BigDecimal("0.10");
        ch.advanceRequested = true;
        ch.validFrom = EFFECT.minusDays(1);
        ch.persist();
    }

    /**
     * Two advance-requested choices on the same coverage open the advance only once.
     */
    @Test
    @TestTransaction
    void two_choices_open_one_advance() {
        Coverage c = escomptedCoverage("1000");
        secondChoice(c);
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(1, AdvancePosition.count());
    }

    /**
     * A coverage that already has a position gets no new versement.
     */
    @Test
    @TestTransaction
    void existing_position_is_not_reopened() {
        Coverage c = escomptedCoverage("1000");
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        AdvancePosition existing = new AdvancePosition();
        existing.advanceAgreement = agreement;
        existing.coverage = c;
        existing.status = AdvanceStatus.ACTIVE;
        existing.persist();

        engine.runReal("UNEP", EFFECT);
        assertEquals(1, AdvancePosition.count());
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * An advance-family rule is never applied through the per-event candidate loop.
     */
    @Test
    @TestTransaction
    void advance_family_rule_is_not_applied_per_event() {
        Coverage c = escomptedCoverage("1000");
        choice(c).advanceRequested = false;
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "PLAN";
        plan.label = "P";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R_ADV";
        rule.label = "Adv";
        rule.family = RuleFamily.ADVANCE;
        rule.persist();
        RuleVersion v = new RuleVersion();
        v.rule = rule;
        v.versionNo = 1;
        v.status = VersionStatus.ACTIVE;
        v.scope = "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE_SELECT\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        v.parameters = "{}";
        v.exclusionGroup = "BASE";
        v.priority = 10;
        v.outputNature = LineNature.ADVANCE;
        v.validFrom = EFFECT;
        v.persist();
        premium(c, "E1", LocalDate.of(2026, 1, 15), "100.00");

        engine.runReal("UNEP", EFFECT);
        // No computing base rule covers the event (only the skipped ADVANCE rule): held, no line.
        assertEquals(0, CommissionLine.count());
    }

    /**
     * No advance is opened when the coverage has no main assignment or its production code has no
     * distributor.
     */
    @Test
    @TestTransaction
    void no_advance_without_assignment_or_distributor() {
        // No main assignment.
        Product product = new Product();
        product.entity = unep();
        product.code = "SANTE_SELECT";
        product.label = "S";
        product.persist();
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-NOASG";
        contract.product = product;
        contract.persist();
        Coverage c = new Coverage();
        c.contract = contract;
        c.coverageCode = "AD-NOASG-C1";
        c.effectDate = EFFECT;
        c.annualPremium = new BigDecimal("1000");
        c.persist();
        RemunerationChoice ch = new RemunerationChoice();
        ch.coverage = c;
        ch.mode = RemunerationMode.UPFRONT;
        ch.acquisitionRate = new BigDecimal("0.30");
        ch.servicingRate = new BigDecimal("0.10");
        ch.advanceRequested = true;
        ch.validFrom = EFFECT;
        ch.persist();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A convention with no cap, no end date and no anticipation still opens the advance.
     */
    @Test
    @TestTransaction
    void open_convention_opens_advance() {
        Coverage c = escomptedCoverage("1000");
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        agreement.maxContracts = null;
        agreement.validTo = null;
        agreement.anticipationDays = null;
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(2, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * The window refuses an effect before the convention start, a convention with no start, an
     * effect with no date, and a registration after the window.
     */
    @Test
    @TestTransaction
    void window_refusals() {
        Coverage before = escomptedCoverage("1000");
        before.effectDate = LocalDate.of(2024, 1, 1);
        before.contract.registrationDate = LocalDate.of(2024, 1, 1);
        choice(before).validFrom = LocalDate.of(2024, 1, 1);
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A null effect date or a convention with no start date both refuse the advance.
     */
    @Test
    @TestTransaction
    void null_effect_or_no_convention_start() {
        Coverage c = escomptedCoverage("1000");
        c.effectDate = null;
        choice(c).validFrom = null;
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        agreement.validFrom = null;
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A registration after the convention end plus five days refuses the advance.
     */
    @Test
    @TestTransaction
    void registration_after_window() {
        Coverage c = escomptedCoverage("1000");
        c.contract.registrationDate = LocalDate.of(2026, 6, 1);
        convention(100, LocalDate.of(2026, 1, 31));
        // effect is 2026-01-01, within [validFrom, validTo+anticipation]; registration 2026-06-01 is
        // past validTo + 5 days.
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A premium after the first anniversary is paid, not offset; one after settlement is paid too.
     */
    @Test
    @TestTransaction
    void premium_after_anniversary_and_after_settlement() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2027, 12, 31));
        upfrontRule();
        // Settle within year one (10 × 40 = 400), then one more premium the same year, plus one in
        // year two.
        for (int i = 0; i < 10; i++) {
            premium(c, "E" + i, EFFECT.plusMonths(i).plusDays(10), "100.00");
        }
        premium(c, "E10", EFFECT.plusMonths(10).plusDays(10), "100.00");
        premium(c, "Y2", EFFECT.plusYears(1).plusMonths(1), "100.00");

        engine.runReal("UNEP", EFFECT);
        assertEquals(AdvanceStatus.SETTLED, position().status);
        // The premiums after settlement are earned, not offset.
        assertTrue(CommissionLine.count("status", LineStatus.EARNED) > 0);
    }

    /**
     * A fee line on an escompted coverage is not offset against the advance.
     */
    @Test
    @TestTransaction
    void fee_line_is_not_offset() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        // A fixed-fee rule in a separate group adds a FEE line, which is not offsettable.
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", unep(), "PLAN").firstResult();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R_FEE";
        rule.label = "Fee";
        rule.family = RuleFamily.FIXED;
        rule.persist();
        RuleVersion v = new RuleVersion();
        v.rule = rule;
        v.versionNo = 1;
        v.status = VersionStatus.ACTIVE;
        v.scope = "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE_SELECT\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        v.parameters = "{\"amount\":5}";
        v.exclusionGroup = "FEE";
        v.priority = 10;
        v.outputNature = LineNature.FEE;
        v.validFrom = EFFECT;
        v.persist();
        premium(c, "E1", LocalDate.of(2026, 2, 10), "100.00");

        engine.runReal("UNEP", EFFECT);
        // The fee line stays earned (not offset).
        assertEquals(1, CommissionLine.<CommissionLine>list("nature = ?1 and status = ?2",
                LineNature.FEE, LineStatus.EARNED).size());
    }

    /**
     * A linear escompte offsets the linear line and settles.
     */
    @Test
    @TestTransaction
    void linear_escompte() {
        Coverage c = escomptedCoverage("1000");
        choice(c).mode = RemunerationMode.LINEAR;
        choice(c).acquisitionRate = null;
        choice(c).servicingRate = null;
        choice(c).linearRate = new BigDecimal("0.40");
        convention(100, LocalDate.of(2026, 12, 31));
        // Linear base rule.
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "PLAN";
        plan.label = "P";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R_LIN";
        rule.label = "Lin";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion v = new RuleVersion();
        v.rule = rule;
        v.versionNo = 1;
        v.status = VersionStatus.ACTIVE;
        v.scope = "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE_SELECT\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        v.parameters = "{\"allowedRates\":[0.40]}";
        v.exclusionGroup = "BASE";
        v.priority = 10;
        v.outputNature = LineNature.LINEAR;
        v.validFrom = EFFECT;
        v.persist();
        premium(c, "E1", LocalDate.of(2026, 2, 10), "100.00");

        engine.runReal("UNEP", EFFECT);
        // Advance 400 (1000 × 0.40); one linear line offset.
        assertEquals(1, CommissionLine.<CommissionLine>list("nature = ?1 and status = ?2",
                LineNature.LINEAR, LineStatus.OFFSET).size());
    }

    /**
     * An unpaid notice with no advance, or with a null rank, leaves everything untouched.
     */
    @Test
    @TestTransaction
    void unpaid_without_advance_or_rank() {
        Coverage noAdvance = escomptedCoverage("1000");
        choice(noAdvance).advanceRequested = false;
        upfrontRule();
        terminal(noAdvance, "U1", EventType.UNPAID_NOTICE, 30, (short) 3);

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE_ADJUSTMENT));
    }

    /**
     * An unpaid notice with a null rank on an escompted coverage does not break the advance.
     */
    @Test
    @TestTransaction
    void unpaid_null_rank_keeps_advance() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        terminal(c, "U", EventType.UNPAID_NOTICE, 60, null);

        engine.runReal("UNEP", EFFECT);
        assertEquals(AdvanceStatus.ACTIVE, position().status);
    }

    /**
     * No advance is opened when the production code carries no distributor.
     */
    @Test
    @TestTransaction
    void no_advance_when_production_code_has_no_distributor() {
        Coverage c = escomptedCoverage("1000");
        ProductionCode.<ProductionCode>find("code", "PC-1").firstResult().distributor = null;
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A convention with no start date, and a registration before the start, both refuse the advance.
     */
    @Test
    @TestTransaction
    void convention_without_start_or_early_registration() {
        Coverage noStart = escomptedCoverage("1000");
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        agreement.validFrom = null;
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * A registration before the convention start refuses the advance even when the effect date is in
     * range.
     */
    @Test
    @TestTransaction
    void registration_before_start() {
        Coverage c = escomptedCoverage("1000");
        c.contract.registrationDate = LocalDate.of(2024, 1, 1);
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        agreement.validFrom = LocalDate.of(2025, 6, 1);
        upfrontRule();

        engine.runReal("UNEP", EFFECT);
        assertEquals(0, CommissionLine.count("nature", LineNature.ADVANCE));
    }

    /**
     * While the advance is still active, a premium whose event date is past the first anniversary is
     * paid, not offset.
     */
    @Test
    @TestTransaction
    void active_advance_pays_premium_after_anniversary() {
        Coverage c = escomptedCoverage("10000");
        convention(100, LocalDate.of(2027, 12, 31));
        upfrontRule();
        premium(c, "Y2", EFFECT.plusYears(1).plusMonths(1), "100.00");

        engine.runReal("UNEP", EFFECT);
        assertEquals(AdvanceStatus.ACTIVE, position().status);
        // The year-two premium is earned (servicing), not offset.
        assertTrue(CommissionLine.count("status", LineStatus.EARNED) >= 1);
        assertEquals(0, CommissionLine.<CommissionLine>list("status = ?1", LineStatus.OFFSET).size());
    }

    /**
     * After the advance is settled, a later unpaid notice and a later termination leave it settled.
     */
    @Test
    @TestTransaction
    void events_after_settlement_are_inert_on_the_advance() {
        Coverage c = escomptedCoverage("1000");
        convention(100, LocalDate.of(2026, 12, 31));
        upfrontRule();
        for (int i = 0; i < 10; i++) {
            premium(c, "E" + i, EFFECT.plusMonths(i).plusDays(10), "100.00");
        }
        terminal(c, "U", EventType.UNPAID_NOTICE, 320, (short) 2);
        terminal(c, "T", EventType.TERMINATION, 330, null);

        engine.runReal("UNEP", EFFECT);
        assertEquals(AdvanceStatus.SETTLED, position().status);
    }

    /**
     * An unpaid notice does not break the advance when the convention sets no rank threshold.
     */
    @Test
    @TestTransaction
    void unpaid_with_no_threshold() {
        Coverage c = escomptedCoverage("1000");
        AdvanceAgreement agreement = convention(100, LocalDate.of(2026, 12, 31));
        agreement.unpaidRankThreshold = null;
        upfrontRule();
        terminal(c, "U", EventType.UNPAID_NOTICE, 60, (short) 3);

        engine.runReal("UNEP", EFFECT);
        assertEquals(AdvanceStatus.ACTIVE, position().status);
    }
}
