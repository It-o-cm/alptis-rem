package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Scale;
import fr.alptis.remuneration.domain.ScaleMeasure;
import fr.alptis.remuneration.domain.ScaleMode;
import fr.alptis.remuneration.domain.ScaleOutput;
import fr.alptis.remuneration.domain.ScaleTier;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Acceptance tests of the retirement-savings calculation (section 6.6, step 7): initial and scheduled
 * payments, outstanding and arbitrage commissions, the discount without a convention with its recall
 * and annual readjustment, the annual complement, and the expected insurer commission (CR-30 to
 * CR-36, CR-82 to CR-84). Everything runs against UNEP so the Alptis initial barème does not
 * interfere, inside rolled-back transactions.
 */
@QuarkusTest
class SavingsEngineTest {

    @Inject
    CalculationEngine engine;

    private static final LocalDate MONTH = LocalDate.of(2026, 1, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a savings product. */
    private static Product product(String code) {
        Product p = new Product();
        p.entity = unep();
        p.code = code;
        p.label = code;
        p.persist();
        return p;
    }

    /** Creates a savings coverage on a new contract, assigned to the given distributor. */
    private Coverage coverage(String contractNumber, Product product, Distributor distributor, LocalDate effect) {
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
        coverage.persist();
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
        assignment.validFrom = effect;
        assignment.persist();
        return coverage;
    }

    /** Creates a distributor. */
    private static Distributor distributor(String partnerCode) {
        Distributor distributor = new Distributor();
        distributor.partnerCode = partnerCode;
        distributor.legalName = partnerCode;
        distributor.persist();
        return distributor;
    }

    /** Creates a linear remuneration choice carrying the contract's linear rate (frais minus half a point). */
    private static void linearChoice(Coverage coverage, String rate) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = rate == null ? null : new BigDecimal(rate);
        choice.validFrom = coverage.effectDate;
        choice.persist();
    }

    /** Creates an active rule version, in the given plan direction, and returns it. */
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

    /** Attaches a whole-measure scale with the retirement complement tiers to a rule version. */
    private static void complementScale(RuleVersion version) {
        Scale scale = new Scale();
        scale.ruleVersion = version;
        scale.mode = ScaleMode.WHOLE;
        scale.measure = ScaleMeasure.AMOUNT;
        scale.output = ScaleOutput.RATE;
        scale.persist();
        tier(scale, "0", "0.10");
        tier(scale, "10000", "0.12");
        tier(scale, "14000", "0.15");
        tier(scale, "20000", "0.20");
        tier(scale, "50000", "0.25");
    }

    /** Adds one tier to a scale. */
    private static void tier(Scale scale, String lowerBound, String rate) {
        ScaleTier tier = new ScaleTier();
        tier.scale = scale;
        tier.lowerBound = new BigDecimal(lowerBound);
        tier.rate = new BigDecimal(rate);
        tier.fromFirstUnit = Boolean.TRUE;
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

    /** All commission lines after a run, in order. */
    private static List<CommissionLine> lines() {
        return CommissionLine.list("order by id");
    }

    /** The amount of the single line of a nature. */
    private static BigDecimal amountOf(LineNature nature) {
        return lines().stream().filter(l -> l.nature == nature).map(l -> l.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final String SAVINGS_SCOPE = "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"]}";

    /**
     * CR-30: an initial payment of 6 000 € at 2,5% fees (rate 2,0% carried by the garantie) yields a
     * LINEAR line of 120,00 €.
     */
    @Test
    @TestTransaction
    void cr30_initial_payment() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.02");
        version("OUT", Direction.OUTBOUND, "R_INIT", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"INITIAL_PAYMENT\"]}",
                "{\"allowedRates\":[0.02]}", "BASE");
        event(c, "E1", EventType.INITIAL_PAYMENT, LocalDate.of(2026, 1, 5), "6000.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.LINEAR).compareTo(new BigDecimal("120.00")));
    }

    /**
     * CR-31: a scheduled payment of 300 € at 3,5% fees (rate 3,0%) in linear mode yields a LINEAR line
     * of 9,00 € per payment.
     */
    @Test
    @TestTransaction
    void cr31_scheduled_payment_linear() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.03");
        version("OUT", Direction.OUTBOUND, "R_SCHED", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"SCHEDULED_PAYMENT\"]}",
                "{\"allowedRates\":[0.03]}", "BASE");
        event(c, "E1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 5), "300.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.LINEAR).compareTo(new BigDecimal("9.00")));
    }

    /**
     * CR-82: a 0,30% annual rate on a 20 000 € average balance over a quarterly file yields an
     * OUTSTANDING line of 15,00 €.
     */
    @Test
    @TestTransaction
    void cr82_outstanding() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_OUT", RuleFamily.OUTSTANDING, LineNature.OUTSTANDING,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"OUTSTANDING_BALANCE\"]}",
                "{\"annualRate\":0.003}", "BASE");
        BusinessEvent e = event(c, "E1", EventType.OUTSTANDING_BALANCE, LocalDate.of(2026, 1, 31), "20000.00");
        e.periodFrom = LocalDate.of(2026, 1, 1);
        e.periodTo = LocalDate.of(2026, 3, 31);

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.OUTSTANDING).compareTo(new BigDecimal("15.00")));
    }

    /**
     * CR-83: nothing on the first arbitrage of the calendar year, an ARBITRAGE line of 30,00 € on the
     * second (0,30% of 10 000 €).
     */
    @Test
    @TestTransaction
    void cr83_arbitrage_second_of_year() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_ARB", RuleFamily.LINEAR, LineNature.ARBITRAGE,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"ARBITRAGE\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.003,\"minRankInCalendarYear\":2}", "BASE");
        event(c, "A1", EventType.ARBITRAGE, LocalDate.of(2026, 3, 1), "10000.00");
        event(c, "A2", EventType.ARBITRAGE, LocalDate.of(2026, 6, 1), "10000.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.ARBITRAGE).compareTo(new BigDecimal("30.00")));
    }

    /** Parameters of an outbound retirement discount at 8%. */
    private static final String OUT_DISCOUNT =
            "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.08,"
                    + "\"offsetting\":false,\"annualReadjustment\":false}";

    /**
     * CR-32: a discounted scheduled plan of 300 € per month yields a single ADVANCE line of 288,00 €
     * in year one (3 600 × 8%), the scheduled payments producing no line.
     */
    @Test
    @TestTransaction
    void cr32_discount_year_one() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.03");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 10), "300.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.ADVANCE).compareTo(new BigDecimal("288.00")));
        assertEquals(AdvanceStatus.ACTIVE, AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /**
     * CR-32 variant: a 100 €-per-month discounted plan suspended after 25 payments gives an escompte
     * of 96,00 € and a recall of −59,75 € (96 − 2 500 × 1,45%); the position leaves the discounted mode.
     */
    @Test
    @TestTransaction
    void cr32_discount_recall() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        for (int i = 1; i <= 25; i++) {
            event(c, "S" + i, EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 1).plusDays(i), "100.00");
        }
        event(c, "SUS", EventType.PLAN_SUSPENDED, LocalDate.of(2026, 1, 28), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, amountOf(LineNature.ADVANCE).compareTo(new BigDecimal("96.00")));
        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-59.75")));
        assertEquals(AdvanceStatus.CANCELLED,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /** Parameters of an inbound (insurer) retirement discount at 10% with a 1,95% recall rate. */
    private static final String IN_DISCOUNT =
            "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.10,"
                    + "\"recallRate\":0.0195,\"annualReadjustment\":false}";

    /** The expected amount recorded for a coverage with a given tag. */
    private static BigDecimal expected(Coverage coverage, String tag) {
        ControlResult result = ControlResult.find("objectId = ?1 and justification = ?2", coverage.getId(), tag)
                .firstResult();
        return result == null ? null : new BigDecimal(result.expectedValue);
    }

    /**
     * CR-33: the expected recall of the insurer commission — escompte of 120 €, 25 payments of 100 €
     * at 1,95% — is −71,25 €, kept as an expected amount and never a line.
     */
    @Test
    @TestTransaction
    void cr33_expected_insurer_recall() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.0145");
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, IN_DISCOUNT, "SAVINGS");
        for (int i = 1; i <= 25; i++) {
            event(c, "S" + i, EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 1).plusDays(i), "100.00");
        }
        event(c, "SUS", EventType.PLAN_SUSPENDED, LocalDate.of(2026, 1, 28), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, lines().size());
        assertEquals(0, expected(c, "SAVINGS_RECALL").compareTo(new BigDecimal("-71.25")));
    }

    /**
     * CR-34: the expected recall of the insurer commission — escompte of 240 €, 21 payments of 200 €
     * at 1,95% — is −158,10 €.
     */
    @Test
    @TestTransaction
    void cr34_expected_insurer_recall() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.0145");
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, IN_DISCOUNT, "SAVINGS");
        for (int i = 1; i <= 21; i++) {
            event(c, "S" + i, EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 1).plusDays(i), "200.00");
        }
        event(c, "SUS", EventType.PLAN_SUSPENDED, LocalDate.of(2026, 1, 25), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, expected(c, "SAVINGS_RECALL").compareTo(new BigDecimal("-158.10")));
    }

    /**
     * CR-35: the retirement complement on a distributor's production under 10 000 € (two contracts of
     * 3 510 € and 3 529,80 € net annualised) applies 10% to each, giving 351,00 € and 352,98 €,
     * 703,98 € in all.
     */
    @Test
    @TestTransaction
    void cr35_annual_complement() {
        Product p = product("PER");
        Distributor d = distributor("D1");
        Coverage c1 = coverage("PER-1", p, d, LocalDate.of(2026, 1, 1));
        Coverage c2 = coverage("PER-2", p, d, LocalDate.of(2026, 1, 1));
        RuleVersion rule = version("OUT", Direction.OUTBOUND, "R_COMP", RuleFamily.TIERED, LineNature.COMPLEMENT,
                SAVINGS_SCOPE, "{\"counterCode\":\"PER\",\"calculationFrequency\":\"ANNUAL\"}", "COMPLEMENT");
        complementScale(rule);
        event(c1, "C1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3510.00");
        event(c2, "C2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3529.80");

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(2, lines().size());
        assertEquals(0, amountOf(LineNature.COMPLEMENT).compareTo(new BigDecimal("703.98")));
    }

    /**
     * CR-36: the expected insurer surcommission — 28% of 3 600 € of scheduled payments, capped at
     * 50 000 € — is 1 008,00 €, kept as an expected amount.
     */
    @Test
    @TestTransaction
    void cr36_expected_surcommission() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("IN", Direction.INBOUND, "R_SUR", RuleFamily.LINEAR, LineNature.LINEAR, SAVINGS_SCOPE,
                "{\"rateSource\":\"RULE\",\"rate\":0.28,\"calculationFrequency\":\"ANNUAL\",\"annualCap\":50000}",
                "SURCOMMISSION");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3600.00");

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(0, lines().size());
        assertEquals(0, expected(c, "SURCOMMISSION").compareTo(new BigDecimal("1008.00")));
    }

    /** Pre-creates an active savings discount position anchored at a first-payment date. */
    private AdvancePosition discountPosition(Coverage coverage, String advanced, LocalDate anchor) {
        AdvancePosition position = new AdvancePosition();
        position.coverage = coverage;
        position.status = AdvanceStatus.ACTIVE;
        position.settlementDueDate = anchor.plusYears(5);
        position.advancedAcquisition = BigDecimal.ZERO;
        position.advancedServicing = BigDecimal.ZERO;
        position.advancedLinear = new BigDecimal(advanced);
        position.offsetAcquisition = BigDecimal.ZERO;
        position.offsetServicing = BigDecimal.ZERO;
        position.offsetLinear = BigDecimal.ZERO;
        position.persist();
        return position;
    }

    /** Parameters of an outbound retirement discount with the annual readjustment enabled. */
    private static final String OUT_READJUST =
            "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.08,"
                    + "\"offsetting\":false,\"annualReadjustment\":true,\"readjustmentTolerance\":0}";

    /**
     * CR-84: at the first anniversary, 4 200 € collected over twelve months against a 3 600 € base
     * gives a discount supplement of 48,00 € (600 × 8%) as an ADVANCE line.
     */
    @Test
    @TestTransaction
    void cr84_annual_readjustment_supplement() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 1, 15));
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_READJUST, "SAVINGS");
        discountPosition(c, "288.00", LocalDate.of(2025, 1, 15));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 6, 1), "2100.00");
        event(c, "S2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 12, 1), "2100.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.ADVANCE).compareTo(new BigDecimal("48.00")));
    }

    /**
     * CR-84 variant: 3 000 € collected against the 3 600 € base gives a reprise of −48,00 € as a
     * CLAWBACK line.
     */
    @Test
    @TestTransaction
    void cr84_annual_readjustment_reprise() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 1, 15));
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_READJUST, "SAVINGS");
        discountPosition(c, "288.00", LocalDate.of(2025, 1, 15));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 6, 1), "1500.00");
        event(c, "S2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 12, 1), "1500.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-48.00")));
    }

    /**
     * The retirement discount is settled without regularisation at its fifth anniversary, leaving no
     * line and closing the position.
     */
    @Test
    @TestTransaction
    void discount_settled_at_fifth_anniversary() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2021, 1, 15));
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_READJUST, "SAVINGS");
        discountPosition(c, "288.00", LocalDate.of(2021, 1, 15));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 10), "300.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
        assertEquals(AdvanceStatus.SETTLED,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /**
     * The annual readjustment stays silent when the collected payments stay within the rule's
     * tolerance (a 300 € gap on a 3 600 € base, under 20%).
     */
    @Test
    @TestTransaction
    void readjustment_within_tolerance() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 1, 15));
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_TOL", RuleFamily.ADVANCE, LineNature.ADVANCE, SAVINGS_SCOPE,
                "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"rate\":0.08,\"annualReadjustment\":true,"
                        + "\"readjustmentTolerance\":0.2}", "SAVINGS");
        discountPosition(c, "288.00", LocalDate.of(2025, 1, 15));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 6, 1), "3900.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
    }

    /**
     * On the insurer side the annual readjustment is kept as an expected amount, without a line, the
     * base being the annualised first scheduled payment.
     */
    @Test
    @TestTransaction
    void readjustment_inbound_expected() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 1, 15));
        linearChoice(c, "0.0145");
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE, SAVINGS_SCOPE,
                "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"rate\":0.10,\"annualReadjustment\":true,"
                        + "\"readjustmentTolerance\":0}", "SAVINGS");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 1, 15), "300.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
        assertNotNull(expected(c, "SAVINGS_READJUST"));
    }

    /**
     * Outside an anniversary month the readjustment does nothing even while the discount is active.
     */
    @Test
    @TestTransaction
    void no_readjustment_off_anniversary() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 3, 15));
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_READJUST, "SAVINGS");
        discountPosition(c, "288.00", LocalDate.of(2025, 3, 15));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 5), "300.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
        assertEquals(AdvanceStatus.ACTIVE,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /**
     * A recall with a nil gap (payments collected at the linear rate exactly match the advance) writes
     * no clawback line and still leaves the discounted mode.
     */
    @Test
    @TestTransaction
    void nil_recall_writes_no_line() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 3, 15));
        linearChoice(c, "0.08");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_READJUST, "SAVINGS");
        discountPosition(c, "96.00", LocalDate.of(2025, 3, 15));
        // 1 200 collected at 8% exactly offsets the 96 € advanced, leaving a nil recall.
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 5), "1200.00");
        event(c, "SUS", EventType.PLAN_SUSPENDED, LocalDate.of(2026, 1, 6), null);

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
        assertEquals(AdvanceStatus.CANCELLED,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
        assertNull(expected(c, "SAVINGS_RECALL"));
    }

    /** Creates a bare savings coverage with no main assignment and no production code. */
    private Coverage bareCoverage(String contractNumber, Product product, LocalDate effect) {
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
        coverage.persist();
        return coverage;
    }

    /**
     * In a discounted coverage with no scheduled payment yet, the discount stays unopened and a
     * non-scheduled, non-recall event (an outstanding balance) is still charged through its own rule.
     */
    @Test
    @TestTransaction
    void savings_coverage_outstanding_without_scheduled() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        version("OUT", Direction.OUTBOUND, "R_OUT", RuleFamily.OUTSTANDING, LineNature.OUTSTANDING,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"OUTSTANDING_BALANCE\"]}",
                "{\"annualRate\":0.003}", "BASE");
        BusinessEvent e = event(c, "B1", EventType.OUTSTANDING_BALANCE, LocalDate.of(2026, 1, 31), "20000.00");
        e.periodFrom = LocalDate.of(2026, 1, 1);
        e.periodTo = LocalDate.of(2026, 3, 31);

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.OUTSTANDING).compareTo(new BigDecimal("15.00")));
        assertNull(AdvancePosition.find("coverage", c).firstResult());
    }

    /**
     * A recall without an open discount (neither a position on the distributor side nor a scheduled
     * payment on the insurer side) writes nothing and records nothing.
     */
    @Test
    @TestTransaction
    void recall_without_open_discount() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, IN_DISCOUNT, "SAVINGS");
        event(c, "SUS", EventType.PLAN_SUSPENDED, LocalDate.of(2026, 1, 10), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, lines().size());
        assertNull(expected(c, "SAVINGS_RECALL"));
    }

    /**
     * On the insurer side with no scheduled payment, the annual readjustment finds no anchor and does
     * nothing.
     */
    @Test
    @TestTransaction
    void inbound_readjustment_without_scheduled() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE, SAVINGS_SCOPE,
                "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"rate\":0.10,\"annualReadjustment\":true,"
                        + "\"recallRate\":0.0195}", "SAVINGS");
        event(c, "B1", EventType.OUTSTANDING_BALANCE, LocalDate.of(2026, 1, 15), "100.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(0, lines().size());
        assertNull(expected(c, "SAVINGS_READJUST"));
    }

    /**
     * When the initial annualised base is zero (a first scheduled payment of nil amount), the
     * tolerance test is skipped and the whole collected amount drives the inbound readjustment; a
     * nil-amount payment contributes zero.
     */
    @Test
    @TestTransaction
    void inbound_readjustment_zero_base() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 1, 15));
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE, SAVINGS_SCOPE,
                "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"rate\":0.10,\"annualReadjustment\":true,"
                        + "\"readjustmentTolerance\":0}", "SAVINGS");
        event(c, "S0", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 1, 15), "0.00");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 6, 1), "100.00");
        event(c, "S2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 7, 1), null);

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
        assertEquals(0, expected(c, "SAVINGS_READJUST").compareTo(new BigDecimal("10.00")));
    }

    /**
     * A discount opened on a coverage without a main production code carries no origin on its ADVANCE
     * line.
     */
    @Test
    @TestTransaction
    void discount_line_without_main_assignment() {
        Product p = product("PER");
        Coverage c = bareCoverage("PER-NOASG", p, MONTH);
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 10), "300.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.ADVANCE).compareTo(new BigDecimal("288.00")));
        assertNull(lines().get(0).originProductionCode);
    }

    /**
     * The expected amount keeps the received insurer commission as the observed value, summing the
     * {@code INSURER} lines (a nil amount counting as zero), and reuses the existing control.
     */
    @Test
    @TestTransaction
    void expected_keeps_received_and_reuses_control() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, "0.0145");
        ControlDefinition control = new ControlDefinition();
        control.entity = unep();
        control.code = "CAL-13";
        control.label = "Commission reçue différente de l'attendu";
        control.family = ControlFamily.CALCULATION;
        control.severity = ControlSeverity.WARNING;
        control.active = Boolean.TRUE;
        control.persist();
        insurerLine(c, "50.00");
        insurerLine(c, null);
        version("IN", Direction.INBOUND, "R_IN", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, IN_DISCOUNT, "SAVINGS");
        for (int i = 1; i <= 25; i++) {
            event(c, "S" + i, EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 1).plusDays(i), "100.00");
        }
        event(c, "SUS", EventType.PLAN_SUSPENDED, LocalDate.of(2026, 1, 28), null);

        engine.runReal("UNEP", MONTH);

        ControlResult result = ControlResult.<ControlResult>find("objectId = ?1 and justification = ?2",
                c.getId(), "SAVINGS_RECALL").firstResult();
        assertEquals(0, new BigDecimal(result.expectedValue).compareTo(new BigDecimal("-71.25")));
        assertEquals(0, new BigDecimal(result.observedValue).compareTo(new BigDecimal("50.00")));
        assertEquals(1, ControlDefinition.count("entity = ?1 and code = ?2", unep(), "CAL-13"));
    }

    /** Persists a settled INSURER commission line (surviving the rerun delete) on a coverage. */
    private void insurerLine(Coverage coverage, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.direction = Direction.INBOUND;
        line.status = fr.alptis.remuneration.domain.LineStatus.PAID;
        line.contract = coverage.contract;
        line.coverage = coverage;
        line.nature = LineNature.INSURER;
        line.amount = amount == null ? null : new BigDecimal(amount);
        line.persist();
    }

    /**
     * The annual close over an existing period draws the complement only from the tiered complement
     * rule, ignoring a tiered development rule, an outbound linear rule and a non-annual inbound rule;
     * it groups by distributor (a coverage with no production code forming its own group), dedups a
     * coverage's several payments and leaves out a coverage of another product.
     */
    @Test
    @TestTransaction
    void annual_close_existing_period_and_mixed_rules() {
        Product per = product("PER");
        Product other = product("OTHER");
        fr.alptis.remuneration.domain.Period period = new fr.alptis.remuneration.domain.Period();
        period.entity = unep();
        period.periodMonth = LocalDate.of(2026, 12, 1);
        period.state = fr.alptis.remuneration.domain.PeriodState.OPEN;
        period.persist();
        Distributor d = distributor("D1");
        Coverage c1 = coverage("PER-1", per, d, LocalDate.of(2026, 1, 1));
        Coverage c2 = bareCoverage("PER-2", per, LocalDate.of(2026, 1, 1));
        Coverage c3 = coverage("OTH-1", other, d, LocalDate.of(2026, 1, 1));
        RuleVersion comp = version("OUT", Direction.OUTBOUND, "R_COMP", RuleFamily.TIERED, LineNature.COMPLEMENT,
                SAVINGS_SCOPE, "{\"counterCode\":\"PER\",\"calculationFrequency\":\"ANNUAL\"}", "COMPLEMENT");
        complementScale(comp);
        version("OUT", Direction.OUTBOUND, "R_RCD", RuleFamily.TIERED, LineNature.RCD, SAVINGS_SCOPE,
                "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"ANNUAL\"}", "RCD");
        version("OUT", Direction.OUTBOUND, "R_LINOUT", RuleFamily.LINEAR, LineNature.LINEAR, SAVINGS_SCOPE,
                "{\"rateSource\":\"RULE\",\"rate\":0.1,\"calculationFrequency\":\"ANNUAL\"}", "BASE");
        version("IN", Direction.INBOUND, "R_LININ", RuleFamily.LINEAR, LineNature.LINEAR, SAVINGS_SCOPE,
                "{\"rateSource\":\"RULE\",\"rate\":0.1}", "BASE");
        event(c1, "C1A", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 3, 1), "1755.00");
        event(c1, "C1B", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 9, 1), "1755.00");
        event(c2, "C2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3529.80");
        event(c3, "C3", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "4000.00");

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(2, lines().size());
        assertEquals(0, amountOf(LineNature.COMPLEMENT).compareTo(new BigDecimal("703.98")));
    }

    /**
     * A tiered complement rule with no scale yields a nil complement.
     */
    @Test
    @TestTransaction
    void complement_without_scale() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_COMP", RuleFamily.TIERED, LineNature.COMPLEMENT, SAVINGS_SCOPE,
                "{\"counterCode\":\"PER\",\"calculationFrequency\":\"ANNUAL\"}", "COMPLEMENT");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3600.00");

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.COMPLEMENT).compareTo(new BigDecimal("0.00")));
    }

    /**
     * An inbound annual rule with no rule-driven rate expects nothing.
     */
    @Test
    @TestTransaction
    void surcommission_without_rate() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("IN", Direction.INBOUND, "R_SUR", RuleFamily.LINEAR, LineNature.LINEAR, SAVINGS_SCOPE,
                "{\"calculationFrequency\":\"ANNUAL\"}", "SURCOMMISSION");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3600.00");

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(0, expected(c, "SURCOMMISSION").compareTo(new BigDecimal("0.00")));
    }

    /**
     * The expected surcommission is capped per contract: 28% of 3 600 € (1 008 €) is clamped to a
     * 500 € cap.
     */
    @Test
    @TestTransaction
    void surcommission_capped() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("IN", Direction.INBOUND, "R_SUR", RuleFamily.LINEAR, LineNature.LINEAR, SAVINGS_SCOPE,
                "{\"rateSource\":\"RULE\",\"rate\":0.28,\"calculationFrequency\":\"ANNUAL\",\"annualCap\":500}",
                "SURCOMMISSION");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3600.00");

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(0, expected(c, "SURCOMMISSION").compareTo(new BigDecimal("500.00")));
    }

    /**
     * Among the entity's advance rules, a savings rule of another product (perimeter mismatch) and an
     * inactive one (future validity) are both left out; only the matching active rule opens the
     * discount.
     */
    @Test
    @TestTransaction
    void savings_rule_mismatched_or_inactive() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        version("OUT", Direction.OUTBOUND, "R_OTHER", RuleFamily.ADVANCE, LineNature.ADVANCE,
                "{\"entity\":[\"UNEP\"],\"product\":[\"OTHER\"]}", OUT_DISCOUNT, "SAVINGS");
        RuleVersion future = version("OUT", Direction.OUTBOUND, "R_FUT", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        future.validFrom = LocalDate.of(2099, 1, 1);
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 10), "300.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.ADVANCE).compareTo(new BigDecimal("288.00")));
    }

    /**
     * A surrender recalls the discount; without a remuneration choice the linear rate is nil, so the
     * recall equals the whole advance.
     */
    @Test
    @TestTransaction
    void recall_on_surrender_without_choice() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 10), "100.00");
        event(c, "SUR", EventType.SURRENDER, LocalDate.of(2026, 1, 20), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-96.00")));
        assertEquals(AdvanceStatus.CANCELLED,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /**
     * An outbound transfer recalls the discount; a choice carrying no linear rate also yields a nil
     * linear rate.
     */
    @Test
    @TestTransaction
    void recall_on_outbound_transfer_null_linear_rate() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        linearChoice(c, null);
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 1, 10), "100.00");
        event(c, "TRF", EventType.OUTBOUND_TRANSFER, LocalDate.of(2026, 1, 20), null);

        engine.runReal("UNEP", MONTH);

        assertEquals(0, amountOf(LineNature.CLAWBACK).compareTo(new BigDecimal("-96.00")));
        assertEquals(AdvanceStatus.CANCELLED,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /**
     * The discount versement waits: a first scheduled payment whose month the period has not reached
     * opens nothing yet.
     */
    @Test
    @TestTransaction
    void discount_versement_not_yet_due() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), MONTH);
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 3, 10), "300.00");

        engine.runReal("UNEP", MONTH);

        assertEquals(0, lines().size());
        assertNull(AdvancePosition.find("coverage", c).firstResult());
    }

    /**
     * At an anniversary where the collected payments match the base exactly, the readjustment is nil
     * and writes no line, the discount staying active.
     */
    @Test
    @TestTransaction
    void readjustment_zero_amount() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2025, 1, 15));
        linearChoice(c, "0.0145");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_READJUST, "SAVINGS");
        discountPosition(c, "288.00", LocalDate.of(2025, 1, 15));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 6, 1), "1800.00");
        event(c, "S2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 12, 1), "1800.00");

        engine.runReal("UNEP", LocalDate.of(2026, 1, 1));

        assertEquals(0, lines().size());
        assertEquals(AdvanceStatus.ACTIVE,
                AdvancePosition.<AdvancePosition>find("coverage", c).firstResult().status);
    }

    /** Creates a savings coverage assigned to a production code that carries no distributor. */
    private Coverage coverageNoDistributor(String contractNumber, Product product) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contractNumber + "-C1";
        coverage.effectDate = LocalDate.of(2026, 1, 1);
        coverage.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-" + contractNumber;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = coverage.effectDate;
        assignment.persist();
        return coverage;
    }

    /**
     * The complement groups a production code without a distributor under its own group, and skips a
     * scheduled payment carrying no coverage.
     */
    @Test
    @TestTransaction
    void complement_production_code_without_distributor() {
        Product p = product("PER");
        Coverage c = coverageNoDistributor("PER-1", p);
        RuleVersion comp = version("OUT", Direction.OUTBOUND, "R_COMP", RuleFamily.TIERED, LineNature.COMPLEMENT,
                SAVINGS_SCOPE, "{\"counterCode\":\"PER\",\"calculationFrequency\":\"ANNUAL\"}", "COMPLEMENT");
        complementScale(comp);
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 6, 1), "3510.00");
        BusinessEvent orphan = new BusinessEvent();
        orphan.entity = unep();
        orphan.sourceSystem = "SYS";
        orphan.sourceKey = "ORPHAN";
        orphan.eventType = EventType.SCHEDULED_PAYMENT;
        orphan.eventDate = LocalDate.of(2026, 6, 1);
        orphan.basisAmount = new BigDecimal("999.00");
        orphan.processingStatus = ProcessingStatus.PENDING;
        orphan.persist();

        engine.runAnnualClose("UNEP", LocalDate.of(2026, 12, 1), 2026);

        assertEquals(1, lines().size());
        assertEquals(0, amountOf(LineNature.COMPLEMENT).compareTo(new BigDecimal("351.00")));
    }

    /**
     * Section 6.6: the scheduled payments of a discounted retirement contract produce no line during the
     * discount (years one to five) but earn the product's post-discount linear rate of 1,20% from the
     * sixth year — 300 € × 1,20% = 3,60 €. The decoy rules exercise the finder's skip paths (no
     * seniority condition, inactive version, another product's scope).
     */
    @Test
    @TestTransaction
    void scheduled_payment_earns_the_post_discount_rate_from_year_six() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2021, 1, 1));
        linearChoice(c, "0.03");
        version("OUT", Direction.OUTBOUND, "R_DISC", RuleFamily.ADVANCE, LineNature.ADVANCE,
                SAVINGS_SCOPE, OUT_DISCOUNT, "SAVINGS");
        version("OUT", Direction.OUTBOUND, "R_LIN6", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"SCHEDULED_PAYMENT\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.012,\"minSeniorityYears\":5,"
                        + "\"seniorityOrigin\":\"FIRST_SCHEDULED_PAYMENT\"}", "LIN6");
        version("OUT", Direction.OUTBOUND, "R_NOMIN", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"SCHEDULED_PAYMENT\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.05}", "NOMIN");
        RuleVersion inactive = version("OUT", Direction.OUTBOUND, "R_INACT", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"PER\"],\"eventType\":[\"SCHEDULED_PAYMENT\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.05,\"minSeniorityYears\":5}", "INACT");
        inactive.validTo = LocalDate.of(2020, 12, 31);
        version("OUT", Direction.OUTBOUND, "R_OTHER", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"UNEP\"],\"product\":[\"OTHER\"],\"eventType\":[\"SCHEDULED_PAYMENT\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.05,\"minSeniorityYears\":5}", "OTHER");
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2021, 2, 10), "300.00");
        event(c, "S6", EventType.SCHEDULED_PAYMENT, LocalDate.of(2026, 3, 10), "300.00");

        engine.runReal("UNEP", LocalDate.of(2026, 3, 1));

        assertEquals(1, lines().stream().filter(l -> l.nature == LineNature.LINEAR).count());
        assertEquals(0, amountOf(LineNature.LINEAR).compareTo(new BigDecimal("3.60")));
    }

    /**
     * The seniority origin follows the rule parameter: the first scheduled payment when the rule says
     * so, otherwise the garantie's effect date (section 6.6).
     */
    @Test
    @TestTransaction
    void seniority_origin_date_follows_the_parameter() {
        Product p = product("PER");
        Coverage c = coverage("PER-1", p, distributor("D1"), LocalDate.of(2021, 1, 1));
        event(c, "S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2021, 2, 10), "300.00");
        RuleVersion fromFirst = version("OUT", Direction.OUTBOUND, "R_F", RuleFamily.LINEAR, LineNature.LINEAR,
                SAVINGS_SCOPE, "{\"seniorityOrigin\":\"FIRST_SCHEDULED_PAYMENT\"}", "F");
        RuleVersion fromEffect = version("OUT", Direction.OUTBOUND, "R_E", RuleFamily.LINEAR, LineNature.LINEAR,
                SAVINGS_SCOPE, "{\"seniorityOrigin\":\"EFFECT_DATE\"}", "E");

        assertEquals(LocalDate.of(2021, 2, 10), engine.seniorityOriginDate(c, fromFirst));
        assertEquals(LocalDate.of(2021, 1, 1), engine.seniorityOriginDate(c, fromEffect));
    }
}
