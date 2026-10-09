package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.RunStatus;
import fr.alptis.remuneration.domain.RunType;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VersionStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.engine.SimulationService.SimulationResult;
import fr.alptis.remuneration.engine.SimulationService.Substitution;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of the simulation (PAR-13, CR-69): the reproducibility of a period replayed with
 * its own configuration (identical lines to the cent), the substitution of a grid rate, the holding
 * of a dropped rate, the distributor scope and the separate space. Runs against UNEP in rolled-back
 * transactions.
 */
@QuarkusTest
class SimulationServiceTest {

    @Inject
    SimulationService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);
    private long seq;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period and its real calculation run, returning the run. */
    private CalculationRun scene() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = RunType.ACTUAL;
        run.status = RunStatus.COMPLETED;
        run.persist();
        return run;
    }

    /** Creates a rule with a version in a plan of UNEP. */
    private Rule rule(String code, int versionNo) {
        CommissionPlan plan = CommissionPlan.<CommissionPlan>find("entity = ?1", unep()).firstResult();
        if (plan == null) {
            plan = new CommissionPlan();
            plan.entity = unep();
            plan.code = "PLAN-UNEP";
            plan.label = "Plan UNEP";
            plan.direction = Direction.OUTBOUND;
            plan.persist();
        }
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = code;
        rule.label = code;
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = versionNo;
        version.status = VersionStatus.DRAFT;
        version.persist();
        return rule;
    }

    /** Creates a distributor with a partner code. */
    private Distributor distributor(String code) {
        Distributor distributor = new Distributor();
        distributor.partnerCode = code;
        distributor.legalName = code;
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.persist();
        return distributor;
    }

    /** Creates a real line produced by a rule's version, carrying a basis, a rate and an amount. */
    private void realLine(CalculationRun run, Rule rule, LineNature nature, String basis, String rate,
                          String amount, Distributor distributor) {
        RuleVersion version = RuleVersion.<RuleVersion>find("rule = ?1", rule).firstResult();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = run.period;
        line.run = run;
        line.direction = Direction.OUTBOUND;
        line.nature = nature;
        line.status = LineStatus.EARNED;
        line.ruleVersion = version;
        line.basisAmount = new BigDecimal(basis);
        line.rate = new BigDecimal(rate);
        line.amount = new BigDecimal(amount);
        line.originDistributor = distributor;
        line.persist();
    }

    /**
     * CR-69: replaying a period with its own configuration (no substitution) reproduces the lines to
     * the cent — no modified line, no held event, a zero gap.
     */
    @Test
    @TestTransaction
    void cr69_reproduces_identical_lines() {
        CalculationRun run = scene();
        Rule apport = rule("APPORT", 1);
        realLine(run, apport, LineNature.ACQUISITION, "281000.00", "0.30", "84300.00", distributor("D1"));

        SimulationResult result = service.simulate("UNEP", M, "APPORT", 1, List.of(), null);

        assertEquals(0, result.linesModified());
        assertEquals(0, result.held());
        assertEquals(1, result.linesReplayed());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.ecart()));
        assertEquals(0, new BigDecimal("84300.00").compareTo(result.simulatedTotal()));
        assertEquals(1, CommissionLine.count("run.id = ?1", result.runId()));
    }

    /**
     * A substitution of the apport rate from 30 % to 25 % recomputes the apport lines on their basis
     * and leaves the other rules unchanged (the mock-up figures).
     */
    @Test
    @TestTransaction
    void substitutes_a_grid_rate() {
        CalculationRun run = scene();
        Rule apport = rule("APPORT", 2);
        Rule suivi = rule("SUIVI", 1);
        Distributor d1 = distributor("D1");
        realLine(run, apport, LineNature.ACQUISITION, "281000.00", "0.30", "84300.00", d1);
        realLine(run, suivi, LineNature.SERVICING, "281000.00", "0.10", "28100.00", d1);

        SimulationResult result = service.simulate("UNEP", M, "APPORT", 2,
                List.of(new Substitution(new BigDecimal("0.30"), new BigDecimal("0.25"))), null);

        assertEquals(1, result.linesModified());
        assertEquals(0, result.held());
        assertEquals(0, new BigDecimal("-14050.00").compareTo(result.ecart()));
        SimulationService.NatureComparison acquisition = result.byNature().stream()
                .filter(n -> n.nature().equals("ACQUISITION")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("70250.00").compareTo(acquisition.simulated()));
        // Servicing is out of the simulated rule's scope and stays unchanged.
        SimulationService.NatureComparison servicing = result.byNature().stream()
                .filter(n -> n.nature().equals("SERVICING")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.00").compareTo(servicing.ecart()));
    }

    /**
     * When the simulated version drops the rate without a substitution, the carrying event is held and
     * counted apart (no simulated line).
     */
    @Test
    @TestTransaction
    void drops_a_rate_and_holds_the_event() {
        CalculationRun run = scene();
        Rule apport = rule("APPORT", 2);
        realLine(run, apport, LineNature.ACQUISITION, "281000.00", "0.30", "84300.00", distributor("D1"));

        SimulationResult result = service.simulate("UNEP", M, "APPORT", 2,
                List.of(new Substitution(new BigDecimal("0.30"), null)), null);

        assertEquals(1, result.held());
        assertEquals(0, result.linesReplayed());
        assertEquals(0, CommissionLine.count("run.id = ?1", result.runId()));
    }

    /**
     * A distributor scope limits the substitution to one distributor's lines; the others stay
     * unchanged. A second simulation replaces the first in the separate space.
     */
    @Test
    @TestTransaction
    void scopes_to_one_distributor_and_replaces_prior_simulation() {
        CalculationRun run = scene();
        Rule apport = rule("APPORT", 2);
        realLine(run, apport, LineNature.ACQUISITION, "1000.00", "0.30", "300.00", distributor("D1"));
        realLine(run, apport, LineNature.ACQUISITION, "1000.00", "0.30", "300.00", distributor("D2"));
        List<Substitution> table = List.of(new Substitution(new BigDecimal("0.30"), new BigDecimal("0.25")));

        SimulationResult scoped = service.simulate("UNEP", M, "APPORT", 2, table, "D1");
        assertEquals(1, scoped.linesModified());

        // Re-simulating for every distributor replaces the prior run's lines, not adding to them.
        SimulationResult all = service.simulate("UNEP", M, "APPORT", 2, table, null);
        assertEquals(2, all.linesModified());
        assertEquals(2, CommissionLine.count("run.runType = ?1", RunType.SIMULATION));
    }

    /** Creates a raw real line with nullable basis/rate, version and distributor. */
    private void rawLine(CalculationRun run, RuleVersion version, String basis, String rate, String amount,
                         Distributor distributor) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = run.period;
        line.run = run;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.ACQUISITION;
        line.status = LineStatus.EARNED;
        line.ruleVersion = version;
        line.basisAmount = basis == null ? null : new BigDecimal(basis);
        line.rate = rate == null ? null : new BigDecimal(rate);
        line.amount = new BigDecimal(amount);
        line.originDistributor = distributor;
        line.persist();
    }

    /**
     * The simulation copies unchanged every line it cannot recompute — a line with no rate, no basis,
     * no rule version — keeps a zero-basis in-scope line at its rate, handles a distributor-less line
     * and a two-entry table, and accepts a null table.
     */
    @Test
    @TestTransaction
    void covers_line_edge_cases() {
        CalculationRun run = scene();
        Rule apport = rule("APPORT", 2);
        RuleVersion version = RuleVersion.<RuleVersion>find("rule = ?1", apport).firstResult();
        Distributor d1 = distributor("D1");
        rawLine(run, version, "100.00", null, "50.00", d1);
        rawLine(run, version, null, "0.30", "50.00", d1);
        rawLine(run, version, "0.00", "0.30", "0.00", d1);
        rawLine(run, null, "100.00", "0.30", "30.00", d1);
        rawLine(run, version, "100.00", "0.30", "30.00", null);
        // A line at a rate matched only by the second substitution (exercises the non-matching entry).
        rawLine(run, version, "100.00", "0.10", "10.00", d1);

        SimulationResult result = service.simulate("UNEP", M, "APPORT", 2,
                List.of(new Substitution(new BigDecimal("0.30"), new BigDecimal("0.25")),
                        new Substitution(new BigDecimal("0.10"), new BigDecimal("0.08"))), null);
        assertEquals(0, result.held());
        assertEquals(6, result.linesReplayed());

        // A distributor scope with a distributor-less in-scope line present exercises the scope jamb.
        SimulationResult scoped = service.simulate("UNEP", M, "APPORT", 2,
                List.of(new Substitution(new BigDecimal("0.30"), new BigDecimal("0.25"))), "D1");
        assertEquals(6, scoped.linesReplayed());

        // A null substitution table is accepted (treated as empty); the lines are copied unchanged.
        SimulationResult plain = service.simulate("UNEP", M, "APPORT", 2, null, null);
        assertEquals(0, plain.linesModified());
    }

    /**
     * Unknown entity, period, rule or version are rejected.
     */
    @Test
    @TestTransaction
    void unknown_inputs_are_rejected() {
        scene();
        rule("APPORT", 1);
        assertThrows(ResourceNotFoundException.class,
                () -> service.simulate("GHOST", M, "APPORT", 1, List.of(), null));
        assertThrows(ResourceNotFoundException.class,
                () -> service.simulate("UNEP", LocalDate.of(2030, 1, 1), "APPORT", 1, List.of(), null));
        assertThrows(ResourceNotFoundException.class,
                () -> service.simulate("UNEP", M, "NOPE", 1, List.of(), null));
        assertThrows(ValidationException.class,
                () -> service.simulate("UNEP", M, "APPORT", 99, List.of(), null));
    }
}
