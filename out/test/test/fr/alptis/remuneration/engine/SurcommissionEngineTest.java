package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.ConditionFailureMode;
import fr.alptis.remuneration.domain.ConditionOperator;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.EnrollmentStatus;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleCondition;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Scale;
import fr.alptis.remuneration.domain.ScaleMeasure;
import fr.alptis.remuneration.domain.ScaleMode;
import fr.alptis.remuneration.domain.ScaleOutput;
import fr.alptis.remuneration.domain.ScaleTier;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.SurcommissionAllocation;
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
 * Acceptance tests of the surcommissions and the sharing (sections 6.7 to 6.9, step 8): the monthly
 * development remuneration and its reprise, the annual portfolio remuneration with its conditions,
 * the co-broking and group sharing, and the mandatary retrocession (CR-40 to CR-50, CR-76). Runs
 * against UNEP so the Alptis initial barème does not interfere, inside rolled-back transactions.
 */
@QuarkusTest
class SurcommissionEngineTest {

    @Inject
    CalculationEngine engine;

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

    /** Creates a distributor. */
    private static Distributor distributor(String partnerCode) {
        Distributor d = new Distributor();
        d.partnerCode = partnerCode;
        d.legalName = partnerCode;
        d.persist();
        return d;
    }

    /** Creates a distributor group. */
    private static DistributorGroup group(String code) {
        DistributorGroup g = new DistributorGroup();
        g.code = code;
        g.label = code;
        g.persist();
        return g;
    }

    /** Creates a coverage with a main assignment to a distributor and an annualised premium. */
    private Coverage coverage(String contractNumber, Product product, Distributor distributor, LocalDate effect,
                              String annualPremium) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contractNumber + "-C1";
        coverage.effectDate = effect;
        coverage.annualPremium = annualPremium == null ? null : new BigDecimal(annualPremium);
        coverage.persist();
        assign(coverage, distributor, AssignmentRole.MAIN, "1", effect);
        return coverage;
    }

    /** Adds an assignment of a role and share to a coverage, with its own production code. */
    private ProductionCode assign(Coverage coverage, Distributor distributor, AssignmentRole role, String share,
                                  LocalDate from) {
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-" + coverage.coverageCode + "-" + distributor.partnerCode;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = role;
        assignment.share = new BigDecimal(share);
        assignment.validFrom = from;
        assignment.persist();
        return pc;
    }

    /** Creates a payment rule on a production code with two shares (a distributor part and a group part). */
    private static void payeeRule(ProductionCode pc, Distributor distributor, String distributorShare,
                                  DistributorGroup group, String groupShare) {
        fr.alptis.remuneration.domain.PayeeRule rule = new fr.alptis.remuneration.domain.PayeeRule();
        rule.entity = unep();
        rule.productionCode = pc;
        rule.persist();
        share(rule, fr.alptis.remuneration.domain.PayeeType.DISTRIBUTOR, distributor.getId(), distributorShare);
        share(rule, fr.alptis.remuneration.domain.PayeeType.GROUP, group.getId(), groupShare);
    }

    /** Adds one share to a payment rule. */
    private static void share(fr.alptis.remuneration.domain.PayeeRule rule,
                              fr.alptis.remuneration.domain.PayeeType type, Long payeeId, String value) {
        fr.alptis.remuneration.domain.PayeeRuleShare s = new fr.alptis.remuneration.domain.PayeeRuleShare();
        s.payeeRule = rule;
        s.payeeType = type;
        s.payeeId = payeeId;
        s.share = new BigDecimal(value);
        s.persist();
    }

    /** Creates a linear remuneration choice for a coverage. */
    private static void linearChoice(Coverage coverage, String rate) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = new BigDecimal(rate);
        choice.validFrom = coverage.effectDate;
        choice.persist();
    }

    /** Creates an active rule version in a plan of the given direction and returns it. */
    private RuleVersion version(String planCode, Direction direction, String ruleCode, RuleFamily family,
                                LineNature nature, String scope, String parameters, String group) {
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", unep(), planCode).firstResult();
        if (plan == null) {
            plan = new CommissionPlan();
            plan.entity = unep();
            plan.code = planCode;
            plan.label = planCode;
            plan.direction = direction;
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
        version.priority = 10;
        version.outputNature = nature;
        version.validFrom = LocalDate.of(2020, 1, 1);
        version.persist();
        return version;
    }

    /** Enrols a distributor on a rule, with a signed or unsigned amendment. */
    private void enrol(Rule rule, Distributor distributor, AgreementType type, boolean signed) {
        enrol(rule, distributor, null, type, signed);
    }

    /** Enrols a distributor (optionally for a group payee) on a rule, with a signed or unsigned amendment. */
    private void enrol(Rule rule, Distributor distributor, DistributorGroup group, AgreementType type,
                       boolean signed) {
        Agreement agreement = new Agreement();
        agreement.entity = unep();
        agreement.agreementType = type;
        agreement.distributor = distributor;
        agreement.signatureStatus = signed ? SignatureStatus.SIGNED : SignatureStatus.NOT_SIGNED;
        agreement.validFrom = LocalDate.of(2020, 1, 1);
        agreement.persist();
        Enrollment enrollment = new Enrollment();
        enrollment.rule = rule;
        enrollment.distributor = distributor;
        enrollment.group = group;
        enrollment.agreement = agreement;
        enrollment.status = EnrollmentStatus.ACTIVE;
        enrollment.validFrom = LocalDate.of(2020, 1, 1);
        enrollment.persist();
    }

    /** Enrols a distributor on a rule with no amendment at all. */
    private void enrolNoAgreement(Rule rule, Distributor distributor) {
        Enrollment enrollment = new Enrollment();
        enrollment.rule = rule;
        enrollment.distributor = distributor;
        enrollment.status = EnrollmentStatus.ACTIVE;
        enrollment.validFrom = LocalDate.of(2020, 1, 1);
        enrollment.persist();
    }

    /** Records a quality indicator for a distributor over a year. */
    private static void indicator(Distributor distributor, QualityIndicatorType type, String value, int year) {
        QualityIndicator indicator = new QualityIndicator();
        indicator.entity = unep();
        indicator.distributor = distributor;
        indicator.indicatorType = type;
        indicator.periodFrom = LocalDate.of(year, 1, 1);
        indicator.periodTo = LocalDate.of(year, 12, 31);
        indicator.value = new BigDecimal(value);
        indicator.persist();
    }

    /** Adds a condition to a rule version. */
    private static void condition(RuleVersion version, QualityIndicatorType type, ConditionOperator operator,
                                  String threshold) {
        RuleCondition condition = new RuleCondition();
        condition.ruleVersion = version;
        condition.indicatorType = type;
        condition.operator = operator;
        condition.threshold = new BigDecimal(threshold);
        condition.persist();
    }

    /** Adds a payment condition whose failure defers the month's entitlement rather than forfeiting it. */
    private static void deferredCondition(RuleVersion version, QualityIndicatorType type,
                                          ConditionOperator operator, String threshold) {
        RuleCondition condition = new RuleCondition();
        condition.ruleVersion = version;
        condition.indicatorType = type;
        condition.operator = operator;
        condition.threshold = new BigDecimal(threshold);
        condition.onFailure = ConditionFailureMode.DEFER;
        condition.persist();
    }

    /** Records a quality indicator valid for a single month. */
    private static void monthlyIndicator(Distributor distributor, QualityIndicatorType type, String value,
                                         int year, int month) {
        QualityIndicator indicator = new QualityIndicator();
        indicator.entity = unep();
        indicator.distributor = distributor;
        indicator.indicatorType = type;
        indicator.periodFrom = LocalDate.of(year, month, 1);
        indicator.periodTo = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        indicator.value = new BigDecimal(value);
        indicator.persist();
    }

    /** Attaches the santé development marginal scale to a rule version. */
    private static void santeDevelopmentScale(RuleVersion version) {
        Scale scale = scale(version, ScaleMode.MARGINAL);
        tier(scale, "20000", "45000", "0.03", false);
        tier(scale, "45000", "75000", "0.04", false);
        tier(scale, "75000", "100000", "0.05", false);
        tier(scale, "100001", null, "0.05", true);
    }

    /** Attaches a count scale (prévoyance RCP) to a rule version. */
    private static void countScale(RuleVersion version) {
        Scale scale = scale(version, ScaleMode.WHOLE);
        tier(scale, "5", null, "0.01", false);
        tier(scale, "10", null, "0.02", false);
        tier(scale, "20", null, "0.03", false);
        tier(scale, "40", null, "0.04", false);
    }

    /** Creates a scale on a rule version. */
    private static Scale scale(RuleVersion version, ScaleMode mode) {
        Scale scale = new Scale();
        scale.ruleVersion = version;
        scale.mode = mode;
        scale.measure = ScaleMeasure.AMOUNT;
        scale.output = ScaleOutput.RATE;
        scale.persist();
        return scale;
    }

    /** Adds one tier to a scale. */
    private static void tier(Scale scale, String lowerBound, String upperBound, String rate, boolean fromFirstUnit) {
        ScaleTier tier = new ScaleTier();
        tier.scale = scale;
        tier.lowerBound = new BigDecimal(lowerBound);
        tier.upperBound = upperBound == null ? null : new BigDecimal(upperBound);
        tier.rate = new BigDecimal(rate);
        tier.fromFirstUnit = fromFirstUnit;
        tier.persist();
    }

    /** Injects a pending event on a coverage. */
    private BusinessEvent event(Coverage coverage, String key, EventType type, LocalDate date, String basis) {
        BusinessEvent e = new BusinessEvent();
        e.entity = unep();
        e.sourceSystem = "SYS";
        e.sourceKey = key;
        e.eventType = type;
        e.contract = coverage.contract;
        e.coverage = coverage;
        e.eventDate = date;
        e.receivedDate = date;
        e.basisAmount = basis == null ? null : new BigDecimal(basis);
        e.processingStatus = ProcessingStatus.PENDING;
        e.persist();
        return e;
    }

    /** All commission lines, in order. */
    private static List<CommissionLine> lines() {
        return CommissionLine.list("order by id");
    }

    /** The summed amount of the lines of a nature. */
    private static BigDecimal amountOf(LineNature nature) {
        return lines().stream().filter(l -> l.nature == nature).map(l -> l.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Tells whether a line of the given amount exists. */
    private static boolean hasAmount(String amount) {
        return lines().stream().anyMatch(l -> l.amount.compareTo(new BigDecimal(amount)) == 0);
    }

    private static final String RCD_SCOPE = "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE\"]}";
    private static final String RCD_PARAMS = "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}";

    /** Builds a signed-amendment santé development rule and enrols a distributor on it. */
    private RuleVersion developmentRule(Distributor distributor) {
        RuleVersion v = version("DEV", Direction.OUTBOUND, "R_RCD", RuleFamily.TIERED, LineNature.RCD,
                RCD_SCOPE, RCD_PARAMS, "RCD");
        santeDevelopmentScale(v);
        enrol(v.rule, distributor, AgreementType.RCD_AMENDMENT, true);
        return v;
    }

    /**
     * CR-40: a cumulative production under the first tier (15 000 €) yields no development line.
     */
    @Test
    @TestTransaction
    void cr40_development_below_first_tier() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        developmentRule(d);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "15000");
        event(c, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 10), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCD).compareTo(BigDecimal.ZERO));
        assertTrue(lines().isEmpty());
    }

    /**
     * CR-41: January at 30 000 € gives 300,00 €; February at 60 000 € gives 1 350 − 300 = 1 050,00 €.
     */
    @Test
    @TestTransaction
    void cr41_development_two_tiers() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        developmentRule(d);
        Coverage jan = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(jan, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 15), null);
        Coverage feb = coverage("S-2", p, d, LocalDate.of(2026, 2, 10), "30000");
        event(feb, "SUB2", EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 15), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);
        engine.runDevelopment("UNEP", LocalDate.of(2026, 2, 1), 2026);

        assertTrue(hasAmount("300.00"));
        assertTrue(hasAmount("1050.00"));
    }

    /**
     * CR-42: a cumulative production of 120 000 € reaches the from-first-euro tier, giving a 6 000,00 €
     * cumulative entitlement in the first month.
     */
    @Test
    @TestTransaction
    void cr42_development_whole_tier() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        developmentRule(d);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "120000");
        event(c, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 15), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCD).compareTo(new BigDecimal("6000.00")));
    }

    /**
     * CR-43: an unpaid rate of 6% fails the payment condition, forfeiting the month's line (0 €) with
     * its reason.
     */
    @Test
    @TestTransaction
    void cr43_development_unpaid_condition() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = developmentRule(d);
        condition(v, QualityIndicatorType.UNPAID_RATE, ConditionOperator.LT, "0.05");
        indicator(d, QualityIndicatorType.UNPAID_RATE, "0.06", 2026);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(c, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 15), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.RCD).compareTo(BigDecimal.ZERO));
        assertTrue(lines().get(0).explanation.contains("forfeited"));
    }

    /**
     * Section 6.7, DEFER: a month whose payment condition fails does not lose its entitlement when the
     * rule defers — January pays 300 €, February (condition failing) pays 0 € and is deferred, and
     * March (condition holding again) catches it up, paying 1 350 − 300 = 1 050 € instead of 0 €.
     */
    @Test
    @TestTransaction
    void development_defers_a_failed_month() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = developmentRule(d);
        deferredCondition(v, QualityIndicatorType.UNPAID_RATE, ConditionOperator.LT, "0.05");
        monthlyIndicator(d, QualityIndicatorType.UNPAID_RATE, "0.02", 2026, 1);
        // February has no indicator, so its condition fails and the month is deferred.
        monthlyIndicator(d, QualityIndicatorType.UNPAID_RATE, "0.02", 2026, 3);
        Coverage jan = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(jan, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 15), null);
        Coverage feb = coverage("S-2", p, d, LocalDate.of(2026, 2, 10), "30000");
        event(feb, "SUB2", EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 15), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);
        engine.runDevelopment("UNEP", LocalDate.of(2026, 2, 1), 2026);
        engine.runDevelopment("UNEP", LocalDate.of(2026, 3, 1), 2026);

        assertTrue(hasAmount("300.00"), "January pays its entitlement");
        assertTrue(lines().stream().anyMatch(l -> l.amount.signum() == 0 && l.explanation.contains("deferred")),
                "February is deferred, not forfeited");
        assertTrue(hasAmount("1050.00"), "March catches up the deferred month");
    }

    /**
     * CR-44: an enrolment without a signed amendment produces no line and records a control anomaly.
     */
    @Test
    @TestTransaction
    void cr44_development_without_signed_amendment() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = version("DEV", Direction.OUTBOUND, "R_RCD", RuleFamily.TIERED, LineNature.RCD,
                RCD_SCOPE, RCD_PARAMS, "RCD");
        santeDevelopmentScale(v);
        enrol(v.rule, d, AgreementType.RCD_AMENDMENT, false);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(c, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 15), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertTrue(lines().isEmpty());
        assertNotNull(ControlResult.find("objectType = ?1", "ENROLLMENT").firstResult());
    }

    /** Pre-creates a paid development line and its allocation on a contract, in a prior period. */
    private SurcommissionAllocation allocation(Coverage coverage, String amount) {
        Period period = Period.find("entity = ?1 and periodMonth = ?2", unep(), LocalDate.of(2026, 2, 1))
                .firstResult();
        if (period == null) {
            period = new Period();
            period.entity = unep();
            period.periodMonth = LocalDate.of(2026, 2, 1);
            period.state = PeriodState.OPEN;
            period.persist();
        }
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.PAID;
        line.nature = LineNature.RCD;
        line.amount = new BigDecimal(amount);
        line.persist();
        SurcommissionAllocation allocation = new SurcommissionAllocation();
        allocation.commissionLine = line;
        allocation.contract = coverage.contract;
        allocation.annualisedPremium = new BigDecimal("5000");
        allocation.allocatedAmount = new BigDecimal(amount);
        allocation.persist();
        return allocation;
    }

    /**
     * CR-45: an adhésion bearing 40 € of development remuneration, ended in the eighth month, is
     * clawed back in full (−40,00 €); ended in the fifteenth month, by half (−20,00 €).
     */
    @Test
    @TestTransaction
    void cr45_development_reprise() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 15), "5000");
        allocation(c, "40.00");
        event(c, "TERM", EventType.TERMINATION, LocalDate.of(2026, 9, 15), null);

        engine.runReal("UNEP", LocalDate.of(2026, 9, 1));

        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-40.00")));
    }

    /**
     * CR-45 variant: the same adhésion ended in the fifteenth month is clawed back by half (−20,00 €).
     */
    @Test
    @TestTransaction
    void cr45_development_reprise_half() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 15), "5000");
        allocation(c, "40.00");
        event(c, "TERM", EventType.TERMINATION, LocalDate.of(2027, 4, 15), null);

        engine.runReal("UNEP", LocalDate.of(2027, 4, 1));

        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-20.00")));
    }

    /**
     * A development reprise past two years, or on a death, claws nothing back.
     */
    @Test
    @TestTransaction
    void development_reprise_expired_or_death() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Coverage expired = coverage("S-1", p, d, LocalDate.of(2026, 1, 15), "5000");
        allocation(expired, "40.00");
        event(expired, "TERM", EventType.TERMINATION, LocalDate.of(2028, 6, 15), null);
        Coverage dead = coverage("S-2", p, d, LocalDate.of(2026, 1, 15), "5000");
        allocation(dead, "40.00");
        event(dead, "DEATH", EventType.DEATH, LocalDate.of(2026, 6, 15), null);

        engine.runReal("UNEP", LocalDate.of(2028, 6, 1));

        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(BigDecimal.ZERO));
    }

    private static final String RCP_SCOPE = "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE\"]}";

    /**
     * CR-46: a santé portfolio remuneration at 4% on 200 000 € of collected premiums, all conditions
     * met, gives 8 000,00 €.
     */
    @Test
    @TestTransaction
    void cr46_portfolio_health() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = version("RCP", Direction.OUTBOUND, "R_RCP", RuleFamily.PORTFOLIO, LineNature.RCP,
                RCP_SCOPE, "{\"rate\":0.04}", "RCP");
        condition(v, QualityIndicatorType.LOSS_RATIO, ConditionOperator.LE, "0.95");
        condition(v, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, ConditionOperator.LE, "0.20");
        enrol(v.rule, d, AgreementType.RCP_AMENDMENT, true);
        // A non-portfolio active rule is ignored by the portfolio close.
        version("BASE", Direction.OUTBOUND, "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, RCP_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE");
        indicator(d, QualityIndicatorType.LOSS_RATIO, "0.82", 2026);
        indicator(d, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, "0.14", 2026);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "200000");
        event(c, "PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "200000");

        engine.runPortfolio("UNEP", LocalDate.of(2027, 2, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCP).compareTo(new BigDecimal("8000.00")));
    }

    /**
     * CR-47: a P/C of 96% breaks the santé portfolio condition, so the remuneration is nil and the
     * failed condition is recorded.
     */
    @Test
    @TestTransaction
    void cr47_portfolio_condition_failed() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = version("RCP", Direction.OUTBOUND, "R_RCP", RuleFamily.PORTFOLIO, LineNature.RCP,
                RCP_SCOPE, "{\"rate\":0.04}", "RCP");
        condition(v, QualityIndicatorType.LOSS_RATIO, ConditionOperator.LE, "0.95");
        enrol(v.rule, d, AgreementType.RCP_AMENDMENT, true);
        indicator(d, QualityIndicatorType.LOSS_RATIO, "0.96", 2026);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "200000");
        event(c, "PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "200000");

        engine.runPortfolio("UNEP", LocalDate.of(2027, 2, 1), 2026);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.RCP).compareTo(BigDecimal.ZERO));
        assertTrue(lines().get(0).explanation.contains("LOSS_RATIO"));
    }

    /**
     * CR-48: a prévoyance portfolio remuneration with 12 new adhésions reaches the "10 to 19" tier
     * (2%), giving 1 000,00 € on 50 000 € of premiums.
     */
    @Test
    @TestTransaction
    void cr48_portfolio_provident_tier() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = version("RCP", Direction.OUTBOUND, "R_RCP", RuleFamily.PORTFOLIO, LineNature.RCP,
                RCP_SCOPE, "{\"rate\":0}", "RCP");
        countScale(v);
        condition(v, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, ConditionOperator.LE, "0.15");
        enrol(v.rule, d, AgreementType.RCP_AMENDMENT, true);
        indicator(d, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, "0.10", 2026);
        for (int i = 1; i <= 12; i++) {
            Coverage c = coverage("S-" + i, p, d, LocalDate.of(2026, 1, 10), "4000");
            event(c, "SUB" + i, EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 1), null);
            if (i == 1) {
                event(c, "PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "50000");
            }
        }

        engine.runPortfolio("UNEP", LocalDate.of(2027, 2, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCP).compareTo(new BigDecimal("1000.00")));
    }

    private static final String BASE_SCOPE =
            "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";

    /**
     * CR-49: a commission of 30 € shared by a payment rule gives 27,00 € to the distributor and
     * 3,00 € to its group.
     */
    @Test
    @TestTransaction
    void cr49_share_with_group() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        DistributorGroup g = group("G1");
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "200");
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", c).firstResult().productionCode;
        payeeRule(pc, d, "0.9", g, "0.1");
        linearChoice(c, "0.15");
        version("BASE", Direction.OUTBOUND, "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE");
        event(c, "E1", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 15), "200");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(2, lines().size());
        assertTrue(hasAmount("27.00"));
        assertTrue(hasAmount("3.00"));
    }

    /**
     * CR-76: a 100 € commission in co-broking (A 60%, B 40%) with A shared 90/10 with its group gives
     * 54,00 €, 6,00 € and 40,00 €; a later reversal of the 100 € splits the same way.
     */
    @Test
    @TestTransaction
    void cr76_co_broking_and_group() {
        Product p = product("SANTE");
        Distributor a = distributor("A");
        Distributor b = distributor("B");
        DistributorGroup ga = group("GA");
        Coverage c = coverage("S-1", p, a, LocalDate.of(2026, 1, 10), "100");
        CoverageAssignment mainA = CoverageAssignment.<CoverageAssignment>find("coverage = ?1 and role = ?2",
                c, AssignmentRole.MAIN).firstResult();
        mainA.share = new BigDecimal("0.6");
        assign(c, b, AssignmentRole.CO_BROKER, "0.4", LocalDate.of(2026, 1, 10));
        payeeRule(mainA.productionCode, a, "0.9", ga, "0.1");
        linearChoice(c, "1");
        version("BASE", Direction.OUTBOUND, "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_SCOPE,
                "{\"allowedRates\":[1]}", "BASE");
        BusinessEvent premium = event(c, "E1", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 15), "100");
        BusinessEvent reversal = event(c, "E2", EventType.PREMIUM_CANCELLED, LocalDate.of(2026, 1, 20), null);
        reversal.reversesEvent = premium;

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertTrue(hasAmount("54.00"));
        assertTrue(hasAmount("6.00"));
        assertTrue(hasAmount("40.00"));
        assertTrue(hasAmount("-54.00"));
        assertTrue(hasAmount("-6.00"));
        assertTrue(hasAmount("-40.00"));
    }

    /**
     * CR-50: a mandatary's retrocession of 60% on a 100 € received commission gives 60,00 €.
     */
    @Test
    @TestTransaction
    void cr50_mandatary_retrocession() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "100");
        version("RETRO", Direction.OUTBOUND, "R_SHARE", RuleFamily.SHARE, LineNature.RETROCESSION,
                "{\"entity\":[\"UNEP\"],\"product\":[\"SANTE\"],\"eventType\":[\"INSURER_COMMISSION\"]}",
                "{\"sourceNature\":\"INSURER\",\"rate\":0.60}", "BASE");
        event(c, "INS", EventType.INSURER_COMMISSION, LocalDate.of(2026, 1, 15), "100");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, amountOf(LineNature.RETROCESSION).compareTo(new BigDecimal("60.00")));
    }

    /**
     * A development enrolment for a group payee shares the month's remuneration among its adhésions
     * pro rata; a rerun of the same month overwrites its counter; a complement rule and a non-monthly
     * development rule are ignored by the monthly close.
     */
    @Test
    @TestTransaction
    void development_group_rerun_and_other_rules() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        DistributorGroup g = group("G1");
        RuleVersion v = version("DEV", Direction.OUTBOUND, "R_RCD", RuleFamily.TIERED, LineNature.RCD,
                RCD_SCOPE, RCD_PARAMS, "RCD");
        santeDevelopmentScale(v);
        enrol(v.rule, d, g, AgreementType.RCD_AMENDMENT, true);
        version("DEV", Direction.OUTBOUND, "R_COMP", RuleFamily.TIERED, LineNature.COMPLEMENT, RCD_SCOPE,
                "{\"counterCode\":\"C\",\"calculationFrequency\":\"ANNUAL\"}", "COMP");
        version("DEV", Direction.OUTBOUND, "R_ANNUAL", RuleFamily.TIERED, LineNature.RCD, RCD_SCOPE,
                "{\"counterCode\":\"C\",\"calculationFrequency\":\"ANNUAL\"}", "RCD2");
        Coverage c1 = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "15000");
        event(c1, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 12), null);
        Coverage c2 = coverage("S-2", p, d, LocalDate.of(2026, 1, 10), "15000");
        event(c2, "SUB2", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 20), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);
        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCD).compareTo(new BigDecimal("300.00")));
        assertEquals(2, SurcommissionAllocation.count());
        assertEquals(fr.alptis.remuneration.domain.PayeeType.GROUP,
                lines().get(0).account.payeeType);
    }

    /**
     * A non-unpaid development condition is not a payment condition; a missing unpaid indicator
     * forfeits the month; an enrolment with no amendment at all produces no line.
     */
    @Test
    @TestTransaction
    void development_forfeit_and_no_agreement() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Distributor d2 = distributor("D2");
        RuleVersion v = version("DEV", Direction.OUTBOUND, "R_RCD", RuleFamily.TIERED, LineNature.RCD,
                RCD_SCOPE, RCD_PARAMS, "RCD");
        santeDevelopmentScale(v);
        condition(v, QualityIndicatorType.LOSS_RATIO, ConditionOperator.LE, "0.95");
        condition(v, QualityIndicatorType.UNPAID_RATE, ConditionOperator.LT, "0.05");
        enrol(v.rule, d, AgreementType.RCD_AMENDMENT, true);
        enrolNoAgreement(v.rule, d2);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(c, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 12), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.RCD).compareTo(BigDecimal.ZERO));
        assertTrue(lines().get(0).explanation.contains("forfeited"));
    }

    /**
     * When the entitlement rises on a month that saw no new adhésion (the production came earlier and
     * no prior counter exists), the line is written but nothing is allocated.
     */
    @Test
    @TestTransaction
    void development_line_without_month_adhesions() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        developmentRule(d);
        Coverage jan = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(jan, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 15), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 2, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCD).compareTo(new BigDecimal("300.00")));
        assertEquals(0, SurcommissionAllocation.count());
    }

    /** Creates a bare coverage with no assignment (no production code). */
    private Coverage bareCoverage(String contractNumber, Product product, String annualPremium) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contractNumber + "-C1";
        coverage.effectDate = LocalDate.of(2026, 1, 10);
        coverage.annualPremium = annualPremium == null ? null : new BigDecimal(annualPremium);
        coverage.persist();
        return coverage;
    }

    /**
     * A satisfied unpaid condition lets the month pay; a non-tiered active rule is ignored; a
     * new-business coverage with no production code is left out and one with no annualised premium
     * contributes nothing.
     */
    @Test
    @TestTransaction
    void development_passing_condition_and_coverage_edges() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        RuleVersion v = version("DEV", Direction.OUTBOUND, "R_RCD", RuleFamily.TIERED, LineNature.RCD,
                RCD_SCOPE, RCD_PARAMS, "RCD");
        santeDevelopmentScale(v);
        condition(v, QualityIndicatorType.UNPAID_RATE, ConditionOperator.LT, "0.05");
        enrol(v.rule, d, AgreementType.RCD_AMENDMENT, true);
        indicator(d, QualityIndicatorType.UNPAID_RATE, "0.02", 2026);
        version("DEV", Direction.OUTBOUND, "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, RCD_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE");
        Coverage c1 = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "30000");
        event(c1, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 12), null);
        Coverage noPremium = coverage("S-2", p, d, LocalDate.of(2026, 1, 10), null);
        event(noPremium, "SUB2", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 13), null);
        Coverage noCode = bareCoverage("S-3", p, "9999");
        event(noCode, "SUB3", EventType.SUBSCRIPTION, LocalDate.of(2026, 1, 14), null);

        engine.runDevelopment("UNEP", LocalDate.of(2026, 1, 1), 2026);

        assertEquals(0, amountOf(LineNature.RCD).compareTo(new BigDecimal("300.00")));
    }

    /**
     * The portfolio base and new-business count ignore a payment with no coverage, a coverage of
     * another distributor, a coverage of another product, and count each new-business coverage once.
     */
    @Test
    @TestTransaction
    void portfolio_counts_and_premiums_filter() {
        Product p = product("SANTE");
        Product other = product("OTHER");
        Distributor d = distributor("D1");
        Distributor d2 = distributor("D2");
        RuleVersion v = version("RCP", Direction.OUTBOUND, "R_RCP", RuleFamily.PORTFOLIO, LineNature.RCP,
                RCP_SCOPE, "{\"rate\":0}", "RCP");
        countScale(v);
        enrol(v.rule, d, AgreementType.RCP_AMENDMENT, true);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "4000");
        event(c, "SUB1", EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 1), null);
        event(c, "SUB1B", EventType.SUBSCRIPTION, LocalDate.of(2026, 3, 1), null);
        event(c, "PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "60000");
        Coverage otherDist = coverage("S-2", p, d2, LocalDate.of(2026, 1, 10), "4000");
        event(otherDist, "SUB2", EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 1), null);
        event(otherDist, "PREM2", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "99999");
        Coverage otherProduct = coverage("O-1", other, d, LocalDate.of(2026, 1, 10), "4000");
        event(otherProduct, "SUB3", EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 1), null);
        event(otherProduct, "PREM3", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "88888");
        Coverage noCode = bareCoverage("N-1", p, "4000");
        event(noCode, "SUBN", EventType.SUBSCRIPTION, LocalDate.of(2026, 2, 1), null);
        event(noCode, "PREMN", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "55555");
        BusinessEvent orphanSub = new BusinessEvent();
        orphanSub.entity = unep();
        orphanSub.sourceSystem = "SYS";
        orphanSub.sourceKey = "ORPHANSUB";
        orphanSub.eventType = EventType.SUBSCRIPTION;
        orphanSub.eventDate = LocalDate.of(2026, 2, 1);
        orphanSub.processingStatus = ProcessingStatus.PENDING;
        orphanSub.persist();
        BusinessEvent orphanPrem = new BusinessEvent();
        orphanPrem.entity = unep();
        orphanPrem.sourceSystem = "SYS";
        orphanPrem.sourceKey = "ORPHANPREM";
        orphanPrem.eventType = EventType.PREMIUM_COLLECTED;
        orphanPrem.eventDate = LocalDate.of(2026, 6, 1);
        orphanPrem.basisAmount = new BigDecimal("77777");
        orphanPrem.processingStatus = ProcessingStatus.PENDING;
        orphanPrem.persist();

        engine.runPortfolio("UNEP", LocalDate.of(2027, 2, 1), 2026);

        // One new-business coverage (count 1 < first tier 5 → rate 0) on 60 000 € → 0; the other
        // distributor's and other product's figures are excluded.
        assertEquals(0, amountOf(LineNature.RCP).compareTo(BigDecimal.ZERO));
    }

    /**
     * A portfolio enrolment without a signed amendment, and a condition whose indicator is missing,
     * both leave the remuneration nil.
     */
    @Test
    @TestTransaction
    void portfolio_unsigned_and_missing_indicator() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Distributor d2 = distributor("D2");
        RuleVersion v = version("RCP", Direction.OUTBOUND, "R_RCP", RuleFamily.PORTFOLIO, LineNature.RCP,
                RCP_SCOPE, "{\"rate\":0.04}", "RCP");
        condition(v, QualityIndicatorType.LOSS_RATIO, ConditionOperator.LE, "0.95");
        enrol(v.rule, d, AgreementType.RCP_AMENDMENT, true);
        enrol(v.rule, d2, AgreementType.RCP_AMENDMENT, false);
        Coverage c = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "200000");
        event(c, "PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 6, 1), "200000");

        engine.runPortfolio("UNEP", LocalDate.of(2027, 2, 1), 2026);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.RCP).compareTo(BigDecimal.ZERO));
        assertNotNull(ControlResult.find("objectType = ?1", "ENROLLMENT").firstResult());
    }

    /**
     * Sharing tolerates an assignment with no share (counted as the whole) and one with no production
     * code (a line with no origin).
     */
    @Test
    @TestTransaction
    void sharing_tolerates_missing_share_and_code() {
        Product p = product("SANTE");
        Distributor d = distributor("D1");
        Coverage a = coverage("S-1", p, d, LocalDate.of(2026, 1, 10), "100");
        CoverageAssignment.<CoverageAssignment>find("coverage", a).firstResult().share = null;
        linearChoice(a, "0.15");
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "S-2";
        contract.product = p;
        contract.persist();
        Coverage b = new Coverage();
        b.contract = contract;
        b.coverageCode = "S-2-C1";
        b.effectDate = LocalDate.of(2026, 1, 10);
        b.persist();
        CoverageAssignment noCode = new CoverageAssignment();
        noCode.coverage = b;
        noCode.role = AssignmentRole.MAIN;
        noCode.share = new BigDecimal("1");
        noCode.validFrom = LocalDate.of(2026, 1, 10);
        noCode.persist();
        linearChoice(b, "0.15");
        version("BASE", Direction.OUTBOUND, "R_LIN", RuleFamily.LINEAR, LineNature.LINEAR, BASE_SCOPE,
                "{\"allowedRates\":[0.15]}", "BASE");
        event(a, "E1", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 15), "100");
        event(b, "E2", EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 1, 15), "100");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(2, lines().size());
        assertEquals(0, amountOf(LineNature.LINEAR).compareTo(new BigDecimal("30.00")));
        assertTrue(lines().stream().anyMatch(l -> l.originProductionCode == null));
    }
}
