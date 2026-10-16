package fr.alptis.remuneration.salesforce;

import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import fr.alptis.remuneration.domain.VariableResult;
import fr.alptis.remuneration.domain.VariableResultStatus;
import fr.alptis.remuneration.salesforce.SalesForceService.PayExport;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the sales-force variable (section 8.14): the market weighting (CR-87), the
 * absence that blocks the monthly instalment (CR-88), the payment when present, the edges (no plan,
 * no objective), the manager validation and adjustment, and the pay export. Runs against UNEP in
 * rolled-back transactions.
 */
@QuarkusTest
class SalesForceServiceTest {

    @Inject
    SalesForceService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates the September period. */
    private static Period period() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        return period;
    }

    /** Creates an employee. */
    private static Employee employee(String externalId, JobCode jobCode) {
        Employee employee = new Employee();
        employee.entity = unep();
        employee.externalId = externalId;
        employee.lastName = externalId;
        employee.firstName = "Jean";
        employee.jobCode = jobCode;
        employee.validFrom = LocalDate.of(2026, 1, 1);
        employee.persist();
        return employee;
    }

    /** Creates a monthly-individual plan for a post. */
    private static void plan(JobCode jobCode, String envelope, String instalment) {
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = jobCode;
        plan.component = VariableComponent.MONTHLY_INDIVIDUAL;
        plan.envelopeAmount = new BigDecimal(envelope);
        plan.instalmentAmount = new BigDecimal(instalment);
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
    }

    /** Creates an individual quantitative objective. */
    private static void objective(Employee employee, String target) {
        SalesObjective objective = new SalesObjective();
        objective.entity = unep();
        objective.employee = employee;
        objective.jobCode = employee.jobCode;
        objective.objectiveType = ObjectiveType.INDIVIDUAL_QUANTITATIVE;
        objective.periodFrom = FROM;
        objective.periodTo = TO;
        objective.targetValue = new BigDecimal(target);
        objective.versionNo = 1;
        objective.persist();
    }

    /** Creates a measure. */
    private static void measure(Employee employee, MeasureType type, String dimension, String value) {
        EmployeeMeasure measure = new EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = type;
        measure.dimension = dimension;
        measure.periodFrom = FROM;
        measure.periodTo = TO;
        measure.value = new BigDecimal(value);
        measure.persist();
    }

    /** Returns the monthly-individual result of an employee. */
    private static VariableResult monthly(Employee employee) {
        return VariableResult.<VariableResult>find("employee = ?1 and component = ?2", employee,
                VariableComponent.MONTHLY_INDIVIDUAL).firstResult();
    }

    /**
     * CR-87: production of 10 000 € on the particulier market and 5 000 € on the professionnel market
     * counts for 13 500 € against the objective.
     */
    @Test
    @TestTransaction
    void cr87_market_weighting() {
        Employee e = employee("E1", JobCode.CAC);
        measure(e, MeasureType.PRODUCTION, "particulier", "10000.00");
        measure(e, MeasureType.PRODUCTION, "professionnel", "5000.00");

        assertEquals(0, new BigDecimal("13500.00").compareTo(service.weightedProduction(e, FROM, TO)));

        // An unknown market and a market-less measure both count for one (default weight).
        measure(e, MeasureType.PRODUCTION, "autre", "1.00");
        measure(e, MeasureType.PRODUCTION, null, "2.00");
        assertEquals(0, new BigDecimal("13503.00").compareTo(service.weightedProduction(e, FROM, TO)));
    }

    /**
     * The instalment is not paid when the objective is not met or when the target is zero; the
     * achievement rate is the weighted production over the target, capped, or zero without a target.
     */
    @Test
    @TestTransaction
    void not_achieved_and_zero_target_pay_nothing() {
        Period p = period();
        plan(JobCode.CAC, "2450.00", "146.00");
        Employee below = employee("BELOW", JobCode.CAC);
        objective(below, "1000.00");
        measure(below, MeasureType.PRODUCTION, "emprunteur", "500.00");
        Employee zero = employee("ZERO", JobCode.CAC);
        objective(zero, "0.00");
        measure(zero, MeasureType.PRODUCTION, "emprunteur", "500.00");

        service.runMonth("UNEP", M);

        VariableResult belowResult = monthly(below);
        assertEquals(0, BigDecimal.ZERO.compareTo(belowResult.amount));
        assertEquals(0, new BigDecimal("0.500000").compareTo(belowResult.achievementRate));
        assertEquals(0, BigDecimal.ZERO.compareTo(monthly(zero).amount));
        assertEquals(0, BigDecimal.ZERO.compareTo(monthly(zero).achievementRate));
    }

    /**
     * CR-88: the monthly objective is met but ten working days of absence in the month block the
     * instalment; with no absence it is paid.
     */
    @Test
    @TestTransaction
    void cr88_absence_blocks_the_instalment() {
        Period p = period();
        Employee e = employee("E1", JobCode.CAC);
        plan(JobCode.CAC, "2450.00", "146.00");
        objective(e, "1000.00");
        measure(e, MeasureType.PRODUCTION, "particulier", "2000.00");
        measure(e, MeasureType.ABSENCE, "conges", "10");

        service.runMonth("UNEP", M);
        assertEquals(0, BigDecimal.ZERO.compareTo(monthly(e).amount));

        // Without absence, the instalment is paid and the achievement capped at 100 %.
        EmployeeMeasure.delete("employee = ?1 and measureType = ?2", e, MeasureType.ABSENCE);
        service.runMonth("UNEP", M);
        VariableResult paid = monthly(e);
        assertEquals(0, new BigDecimal("146.00").compareTo(paid.amount));
        assertEquals(0, BigDecimal.ONE.compareTo(paid.achievementRate));
    }

    /**
     * An employee without a plan is skipped; one without an objective is not achieved and paid
     * nothing.
     */
    @Test
    @TestTransaction
    void edges_without_plan_or_objective() {
        period();
        Employee noPlan = employee("NOPLAN", JobCode.RB);
        Employee noObjective = employee("NOOBJ", JobCode.CAC);
        plan(JobCode.CAC, "2450.00", "146.00");
        measure(noObjective, MeasureType.PRODUCTION, "particulier", "2000.00");

        int written = service.runMonth("UNEP", M);
        assertEquals(1, written);
        assertEquals(null, monthly(noPlan));
        assertEquals(0, BigDecimal.ZERO.compareTo(monthly(noObjective).amount));
        assertEquals(0, BigDecimal.ZERO.compareTo(monthly(noObjective).achievementRate));
    }

    /**
     * A result is validated, a manager adjustment is recorded, and the pay export produces the OUT-06
     * file and marks the results exported.
     */
    @Test
    @TestTransaction
    void validates_adjusts_and_exports() {
        Period p = period();
        Employee e = employee("E1", JobCode.CAC);
        plan(JobCode.CAC, "2450.00", "146.00");
        objective(e, "1000.00");
        measure(e, MeasureType.PRODUCTION, "particulier", "2000.00");
        service.runMonth("UNEP", M);

        VariableResult result = monthly(e);
        service.validate(result.getId(), "manager");
        assertEquals(VariableResultStatus.VALIDATED, monthly(e).status);
        assertThrows(ValidationException.class, () -> service.validate(result.getId(), "manager"));
        assertThrows(ResourceNotFoundException.class, () -> service.validate(999999L, "manager"));

        service.adjust("UNEP", M, "E1", new BigDecimal("50.00"), "Prime exceptionnelle", "manager");
        assertThrows(ResourceNotFoundException.class,
                () -> service.adjust("UNEP", M, "GHOST", BigDecimal.TEN, "x", "manager"));
        // A manual adjustment without a justification is refused (FDV-08): blank and missing reason.
        assertThrows(ValidationException.class,
                () -> service.adjust("UNEP", M, "E1", BigDecimal.TEN, "  ", "manager"));
        assertThrows(ValidationException.class,
                () -> service.adjust("UNEP", M, "E1", BigDecimal.TEN, null, "manager"));

        PayExport export = service.payExport("UNEP", M, "finance");
        assertEquals("UNEP_VAR_202609.csv", export.fileName());
        assertTrue(export.content().contains("external_id;job_code;pay_period"));
        assertTrue(export.content().contains("Mensuel individuel"));
        assertTrue(export.content().contains("Ajustement"));
        assertEquals(2, export.lines());
        assertEquals(VariableResultStatus.EXPORTED, monthly(e).status);
    }

    /**
     * Unknown entity and period are rejected.
     */
    @Test
    @TestTransaction
    void unknown_inputs_are_rejected() {
        period();
        assertThrows(ResourceNotFoundException.class, () -> service.runMonth("GHOST", M));
        assertThrows(ResourceNotFoundException.class,
                () -> service.runMonth("UNEP", LocalDate.of(2030, 1, 1)));
    }

    /**
     * FDV-01: a collaborator is created at the screen with a manager, then updated — post, team and
     * manager change, the blank manager reference clearing it.
     */
    @Test
    @TestTransaction
    void creates_and_updates_a_collaborator() {
        employee("MGR", JobCode.RR);
        Employee created = service.createCollaborator("UNEP", new SalesForceService.CollaboratorInput(
                "C-1", "Martin", "Alice", JobCode.CAC, "Équipe A", "Sud", "MGR",
                LocalDate.of(2026, 1, 1), null));
        assertEquals("MGR", created.manager.externalId);
        assertEquals(JobCode.CAC, created.jobCode);

        // Update: new post, new team, manager cleared by a blank reference.
        Employee updated = service.updateCollaborator("UNEP", "C-1", new SalesForceService.CollaboratorInput(
                "C-1", "Martin", "Alice", JobCode.RB, "Équipe B", "Nord", "  ",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 12, 31)));
        assertEquals(JobCode.RB, updated.jobCode);
        assertEquals("Équipe B", updated.team);
        assertEquals(null, updated.manager);
        assertEquals(LocalDate.of(2026, 12, 31), updated.validTo);

        // A null manager reference also leaves no manager.
        Employee noManager = service.createCollaborator("UNEP", new SalesForceService.CollaboratorInput(
                "C-2", "Durand", "Bob", JobCode.CAC, null, null, null, LocalDate.of(2026, 1, 1), null));
        assertEquals(null, noManager.manager);
    }

    /**
     * FDV-16: the simulator shares the real rule without persisting — a hypothetical reaching the
     * objective pays the instalment, a low one raises a shortfall alert, the measured production is used
     * when none is supplied, and missing plan, missing objective and zero target are handled.
     */
    @Test
    @TestTransaction
    void simulates_the_monthly_variable() {
        Employee e1 = employee("E1", JobCode.CAC);
        plan(JobCode.CAC, "2450", "146");
        objective(e1, "1000");

        // A hypothetical above the objective pays the instalment.
        var high = service.simulateMonthly("UNEP", "E1", M, new BigDecimal("2000"));
        assertTrue(high.achieved());
        assertEquals(0, new BigDecimal("146").compareTo(high.amount()));
        assertTrue(high.alert().contains("Objectif atteint"));

        // A low hypothetical raises a shortfall alert and pays nothing.
        var low = service.simulateMonthly("UNEP", "E1", M, new BigDecimal("500"));
        assertTrue(!low.achieved());
        assertEquals(0, BigDecimal.ZERO.compareTo(low.amount()));
        assertTrue(low.alert().contains("Il manque 500"));

        // With no hypothetical, the measured production is used (2000 particulier → 1400 pondéré ≥ 1000).
        measure(e1, MeasureType.PRODUCTION, "particulier", "2000");
        var measured = service.simulateMonthly("UNEP", "E1", M, null);
        assertTrue(measured.achieved());

        // An employee with a plan but no objective: no target, rate zero.
        Employee e2 = employee("E2", JobCode.CAC);
        var noObjective = service.simulateMonthly("UNEP", "E2", M, new BigDecimal("3000"));
        assertEquals(null, noObjective.target());
        assertEquals(0, BigDecimal.ZERO.compareTo(noObjective.achievementRate()));
        assertTrue(noObjective.alert().contains("Aucun objectif"));

        // A zero-target objective: never achieved, rate zero.
        Employee e3 = employee("E3", JobCode.RR);
        plan(JobCode.RR, "4200", "300");
        objective(e3, "0");
        var zeroTarget = service.simulateMonthly("UNEP", "E3", M, new BigDecimal("100"));
        assertTrue(!zeroTarget.achieved());
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroTarget.achievementRate()));

        // An employee with no plan at all.
        Employee e4 = employee("E4", JobCode.DR);
        var noPlan = service.simulateMonthly("UNEP", "E4", M, new BigDecimal("100"));
        assertTrue(noPlan.alert().contains("Aucun plan"));

        assertThrows(ResourceNotFoundException.class, () -> service.simulateMonthly("UNEP", "GHOST", M, null));
    }

    /**
     * FDV-04: a plan is created with its envelope, instalment and parameters, then updated in place for
     * the same key (post, component, start of validity); a blank parameters payload is accepted.
     */
    @Test
    @TestTransaction
    void creates_and_updates_a_plan() {
        VariablePlan created = service.savePlan("UNEP", new SalesForceService.PlanInput(
                JobCode.RR, VariableComponent.MONTHLY_INDIVIDUAL, new BigDecimal("12000"), new BigDecimal("700"),
                "{\"absenceDays\":10}", LocalDate.of(2026, 1, 1), null));
        assertEquals(0, new BigDecimal("12000").compareTo(created.envelopeAmount));

        VariablePlan updated = service.savePlan("UNEP", new SalesForceService.PlanInput(
                JobCode.RR, VariableComponent.MONTHLY_INDIVIDUAL, new BigDecimal("12500"), new BigDecimal("720"),
                "", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertEquals(created.getId(), updated.getId());
        assertEquals(0, new BigDecimal("12500").compareTo(updated.envelopeAmount));
        assertEquals(LocalDate.of(2026, 12, 31), updated.validTo);
        assertEquals(1, VariablePlan.count("entity = ?1 and jobCode = ?2 and component = ?3 and validFrom = ?4",
                unep(), JobCode.RR, VariableComponent.MONTHLY_INDIVIDUAL, LocalDate.of(2026, 1, 1)));
    }

    /**
     * FDV-04: the plan write refuses a missing post, component, start date or envelope, and malformed
     * parameters JSON.
     */
    @Test
    @TestTransaction
    void rejects_invalid_plans() {
        assertThrows(ValidationException.class, () -> service.savePlan("UNEP", new SalesForceService.PlanInput(
                null, VariableComponent.MONTHLY_INDIVIDUAL, BigDecimal.TEN, null, null, FROM, null)));
        assertThrows(ValidationException.class, () -> service.savePlan("UNEP", new SalesForceService.PlanInput(
                JobCode.CAC, null, BigDecimal.TEN, null, null, FROM, null)));
        assertThrows(ValidationException.class, () -> service.savePlan("UNEP", new SalesForceService.PlanInput(
                JobCode.CAC, VariableComponent.MONTHLY_INDIVIDUAL, BigDecimal.TEN, null, null, null, null)));
        assertThrows(ValidationException.class, () -> service.savePlan("UNEP", new SalesForceService.PlanInput(
                JobCode.CAC, VariableComponent.MONTHLY_INDIVIDUAL, null, null, null, FROM, null)));
        assertThrows(ValidationException.class, () -> service.savePlan("UNEP", new SalesForceService.PlanInput(
                JobCode.CAC, VariableComponent.MONTHLY_INDIVIDUAL, BigDecimal.TEN, null, "{bad", FROM, null)));
    }

    /** Creates the January N+1 period the regularization is booked in. */
    private static Period january(int nextYear) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = LocalDate.of(nextYear, 1, 1);
        period.state = PeriodState.CONTROLLED;
        period.persist();
        return period;
    }

    /** Creates an annual individual objective covering the whole year. */
    private static void yearObjective(Employee employee, int year, String target) {
        SalesObjective objective = new SalesObjective();
        objective.entity = unep();
        objective.employee = employee;
        objective.jobCode = employee.jobCode;
        objective.objectiveType = ObjectiveType.INDIVIDUAL_QUANTITATIVE;
        objective.periodFrom = LocalDate.of(year, 1, 1);
        objective.periodTo = LocalDate.of(year, 12, 31);
        objective.targetValue = new BigDecimal(target);
        objective.versionNo = 1;
        objective.persist();
    }

    /** Persists a monthly individual result of an amount in a period. */
    private static void monthlyResult(Period period, Employee employee, String amount) {
        VariableResult result = new VariableResult();
        result.period = period;
        result.employee = employee;
        result.component = VariableComponent.MONTHLY_INDIVIDUAL;
        result.amount = new BigDecimal(amount);
        result.achievementRate = BigDecimal.ONE;
        result.status = VariableResultStatus.VALIDATED;
        result.persist();
    }

    /** The annual-adjustment regularization result of an employee. */
    private static VariableResult regul(Employee employee) {
        return VariableResult.<VariableResult>find("employee = ?1 and component = ?2", employee,
                VariableComponent.ANNUAL_ADJUSTMENT).firstResult();
    }

    /**
     * FDV-06: the January N+1 regularization — envelope × annual achievement rate (capped at 100 %)
     * minus the monthly instalments paid. The CAC reference example gives 2 450 × 95 % − 1 314 = 1 013,50 €.
     * A rate above 100 % is capped, a month of absence reduces the envelope, and no objective pays zero.
     */
    @Test
    @TestTransaction
    void fdv06_regularizes_the_annual_individual() {
        Period sep = period();
        january(2027);
        // CAC reference example: 95 % achievement, nine instalments paid.
        Employee cac = employee("CAC1", JobCode.CAC);
        plan(JobCode.CAC, "2450", "146");
        yearObjective(cac, 2026, "1000");
        measure(cac, MeasureType.PRODUCTION, "emprunteur", "950");
        for (int i = 0; i < 9; i++) {
            monthlyResult(sep, cac, "146");
        }
        // A rate above 100 % is capped: 4 200 × 100 % − 0.
        Employee rr = employee("RR1", JobCode.RR);
        plan(JobCode.RR, "4200", "300");
        yearObjective(rr, 2026, "1000");
        measure(rr, MeasureType.PRODUCTION, "emprunteur", "2000");
        // A month of absence reduces the envelope by one instalment: (7 700 − 458) × 100 %.
        Employee dr = employee("DR1", JobCode.DR);
        plan(JobCode.DR, "7700", "458");
        yearObjective(dr, 2026, "1000");
        measure(dr, MeasureType.PRODUCTION, "emprunteur", "1000");
        measure(dr, MeasureType.ABSENCE, "absence", "10");
        // No objective pays nothing.
        Employee dnv = employee("DNV1", JobCode.DNV);
        plan(JobCode.DNV, "16000", "933");

        assertEquals(4, service.runAnnualRegularization("UNEP", 2026));

        assertEquals(0, regul(cac).amount.compareTo(new BigDecimal("1013.50")));
        assertEquals(0, regul(cac).achievementRate.compareTo(new BigDecimal("0.95")));
        assertEquals(0, regul(rr).amount.compareTo(new BigDecimal("4200.00")));
        assertEquals(0, regul(dr).amount.compareTo(new BigDecimal("7242.00")));
        assertEquals(0, regul(dnv).amount.compareTo(BigDecimal.ZERO));

        // A rerun replaces the calculated results rather than doubling them.
        assertEquals(4, service.runAnnualRegularization("UNEP", 2026));
        assertEquals(4, VariableResult.count("component = ?1", VariableComponent.ANNUAL_ADJUSTMENT));
    }

    /**
     * FDV-06 edges: the envelope reduced below zero by absence is floored at zero; a plan without a
     * monthly instalment reduces nothing; an employee without a plan is skipped; an unknown period is
     * rejected.
     */
    @Test
    @TestTransaction
    void fdv06_regularization_edges() {
        january(2027);
        // A tiny envelope reduced below zero by a month of absence is floored at zero.
        Employee rb = employee("RB1", JobCode.RB);
        plan(JobCode.RB, "100", "146");
        yearObjective(rb, 2026, "1000");
        measure(rb, MeasureType.PRODUCTION, "emprunteur", "1000");
        measure(rb, MeasureType.ABSENCE, "absence", "10");
        // A plan with no monthly instalment reduces nothing even with an absent month.
        Employee cac = employee("CAC1", JobCode.CAC);
        VariablePlan noInstalment = new VariablePlan();
        noInstalment.entity = unep();
        noInstalment.jobCode = JobCode.CAC;
        noInstalment.component = VariableComponent.MONTHLY_INDIVIDUAL;
        noInstalment.envelopeAmount = new BigDecimal("2450");
        noInstalment.instalmentAmount = null;
        noInstalment.validFrom = LocalDate.of(2026, 1, 1);
        noInstalment.persist();
        yearObjective(cac, 2026, "1000");
        measure(cac, MeasureType.PRODUCTION, "emprunteur", "1000");
        measure(cac, MeasureType.ABSENCE, "absence", "10");
        // An employee without a plan is skipped.
        employee("DR1", JobCode.DR);

        assertEquals(2, service.runAnnualRegularization("UNEP", 2026));
        assertEquals(0, regul(rb).amount.compareTo(BigDecimal.ZERO));
        assertEquals(0, regul(cac).amount.compareTo(new BigDecimal("2450.00")));

        assertThrows(ResourceNotFoundException.class, () -> service.runAnnualRegularization("UNEP", 2099));
    }

    /** Creates a quarterly-qualitative plan for a post, with its parameters payload. */
    private static void quarterlyPlan(JobCode jobCode, String parameters) {
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = jobCode;
        plan.component = VariableComponent.QUARTERLY_QUALITATIVE;
        plan.envelopeAmount = new BigDecimal("2000");
        plan.parameters = parameters;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
    }

    /** Creates a qualitative objective covering the whole year for an employee. */
    private static void qualitativeObjective(Employee employee, String target) {
        SalesObjective objective = new SalesObjective();
        objective.entity = unep();
        objective.employee = employee;
        objective.jobCode = employee.jobCode;
        objective.objectiveType = ObjectiveType.QUALITATIVE;
        objective.periodFrom = LocalDate.of(2026, 1, 1);
        objective.periodTo = LocalDate.of(2026, 12, 31);
        objective.targetValue = new BigDecimal(target);
        objective.versionNo = 1;
        objective.persist();
    }

    /** The quarterly-qualitative result of an employee. */
    private static VariableResult qualitative(Employee employee) {
        return VariableResult.<VariableResult>find("employee = ?1 and component = ?2", employee,
                VariableComponent.QUARTERLY_QUALITATIVE).firstResult();
    }

    /** The full parameters of the reference quarterly plan (criterion 666,67 €, complement 500 €). */
    private static final String QUALI_PARAMS =
            "{\"criterionAmount\":666.67,\"developmentComplement\":500,\"developmentOvershoot\":2,"
                    + "\"absenceDaysBlocking\":30}";

    /**
     * FDV-07: the quarterly qualitative variable pays the per-criterion amount for each criterion met
     * over the quarter, plus the new-producer complement when the development criterion clears its
     * objective by the overshoot. With a 10 threshold, productivity 12 and development 13 are met
     * (efficiency 8 is not) and development clears 10 + 2, so 2 × 666,67 + 500 = 1 833,34 €; the rate
     * is 2 / 3.
     */
    @Test
    @TestTransaction
    void fdv07_computes_the_quarterly_qualitative() {
        period();
        Employee cac = employee("CAC1", JobCode.CAC);
        quarterlyPlan(JobCode.CAC, QUALI_PARAMS);
        qualitativeObjective(cac, "10");
        measure(cac, MeasureType.ACTIVITY, "productivite", "12");
        measure(cac, MeasureType.ACTIVITY, "efficacite", "8");
        measure(cac, MeasureType.ACTIVITY, "developpement", "13");

        assertEquals(1, service.runQuarterlyQualitative("UNEP", M));
        assertEquals(0, qualitative(cac).amount.compareTo(new BigDecimal("1833.34")));
        assertEquals(0, qualitative(cac).achievementRate.compareTo(new BigDecimal("0.666667")));

        // A rerun replaces the calculated result rather than doubling it.
        assertEquals(1, service.runQuarterlyQualitative("UNEP", M));
        assertEquals(1, VariableResult.count("component = ?1", VariableComponent.QUARTERLY_QUALITATIVE));
    }

    /**
     * FDV-07 edges: six weeks of absence block the pay; no qualitative objective meets no criterion; a
     * development criterion met but short of the overshoot earns no complement, and a plan without an
     * absence threshold never blocks; an employee without a quarterly plan is skipped; an unknown period
     * is rejected.
     */
    @Test
    @TestTransaction
    void fdv07_qualitative_edges() {
        period();
        // All three criteria met but the quarterly absence threshold is reached: nothing is paid.
        Employee blocked = employee("BLOCK", JobCode.CAC);
        quarterlyPlan(JobCode.CAC, QUALI_PARAMS);
        qualitativeObjective(blocked, "10");
        measure(blocked, MeasureType.ACTIVITY, "productivite", "12");
        measure(blocked, MeasureType.ACTIVITY, "efficacite", "12");
        measure(blocked, MeasureType.ACTIVITY, "developpement", "13");
        measure(blocked, MeasureType.ABSENCE, "absence", "30");
        // No qualitative objective: no criterion is met and no complement is due.
        Employee noObjective = employee("NOOBJ", JobCode.RB);
        quarterlyPlan(JobCode.RB, QUALI_PARAMS);
        measure(noObjective, MeasureType.ACTIVITY, "productivite", "99");
        measure(noObjective, MeasureType.ACTIVITY, "developpement", "99");
        // Development met (11 ≥ 10) but short of the +2 overshoot, and no absence threshold configured.
        Employee noComplement = employee("NOCOMP", JobCode.DR);
        quarterlyPlan(JobCode.DR, "{\"criterionAmount\":666.67,\"developmentComplement\":500,\"developmentOvershoot\":2}");
        qualitativeObjective(noComplement, "10");
        measure(noComplement, MeasureType.ACTIVITY, "productivite", "10");
        measure(noComplement, MeasureType.ACTIVITY, "developpement", "11");
        measure(noComplement, MeasureType.ABSENCE, "absence", "50");
        // An employee without a quarterly plan is skipped.
        employee("NOPLAN", JobCode.RR);

        assertEquals(3, service.runQuarterlyQualitative("UNEP", M));
        assertEquals(0, qualitative(blocked).amount.compareTo(BigDecimal.ZERO));
        assertEquals(0, qualitative(noObjective).amount.compareTo(BigDecimal.ZERO));
        assertEquals(0, qualitative(noComplement).amount.compareTo(new BigDecimal("1333.34")));

        assertThrows(ResourceNotFoundException.class, () -> service.runQuarterlyQualitative("UNEP", LocalDate.of(2099, 9, 1)));
    }

    /**
     * FDV-03: objectives are versioned append-only per key; an individual, a second version of it, a
     * different employee (own version 1) and a collective objective are all created; an existing
     * version-less objective still advances the next version.
     */
    @Test
    @TestTransaction
    void manages_versioned_objectives() {
        employee("E1", JobCode.CAC);
        employee("E2", JobCode.CAC);
        SalesObjective v1 = service.createObjective("UNEP", new SalesForceService.ObjectiveInput(
                "E1", JobCode.CAC, ObjectiveType.INDIVIDUAL_QUANTITATIVE, FROM, TO, new BigDecimal("1000")));
        assertEquals(1, v1.versionNo);
        SalesObjective v2 = service.createObjective("UNEP", new SalesForceService.ObjectiveInput(
                "E1", JobCode.CAC, ObjectiveType.INDIVIDUAL_QUANTITATIVE, FROM, TO, new BigDecimal("1200")));
        assertEquals(2, v2.versionNo);
        // A different employee for the same key keeps its own version 1 (target mismatch skipped).
        SalesObjective other = service.createObjective("UNEP", new SalesForceService.ObjectiveInput(
                "E2", JobCode.CAC, ObjectiveType.INDIVIDUAL_QUANTITATIVE, FROM, TO, new BigDecimal("900")));
        assertEquals(1, other.versionNo);
        // A collective objective (blank employee → none).
        SalesObjective collective = service.createObjective("UNEP", new SalesForceService.ObjectiveInput(
                "  ", JobCode.CAC, ObjectiveType.COLLECTIVE_QUANTITATIVE, FROM, TO, new BigDecimal("50000")));
        assertEquals(null, collective.employee);
        assertEquals(1, collective.versionNo);

        // An existing version-less objective of the same key still lets the next version advance to 1.
        SalesObjective legacy = new SalesObjective();
        legacy.entity = unep();
        legacy.jobCode = JobCode.RR;
        legacy.objectiveType = ObjectiveType.QUALITATIVE;
        legacy.periodFrom = FROM;
        legacy.periodTo = TO;
        legacy.targetValue = new BigDecimal("1");
        legacy.versionNo = null;
        legacy.persist();
        SalesObjective qualitative = service.createObjective("UNEP", new SalesForceService.ObjectiveInput(
                null, JobCode.RR, ObjectiveType.QUALITATIVE, FROM, TO, new BigDecimal("3")));
        assertEquals(1, qualitative.versionNo);
    }

    /**
     * FDV-03: the objective write refuses a missing type, period bound, inverted period, missing target
     * and an unknown target employee.
     */
    @Test
    @TestTransaction
    void rejects_invalid_objectives() {
        assertThrows(ValidationException.class, () -> service.createObjective("UNEP",
                new SalesForceService.ObjectiveInput(null, JobCode.CAC, null, FROM, TO, BigDecimal.ONE)));
        assertThrows(ValidationException.class, () -> service.createObjective("UNEP",
                new SalesForceService.ObjectiveInput(null, JobCode.CAC, ObjectiveType.QUALITATIVE, null, TO,
                        BigDecimal.ONE)));
        assertThrows(ValidationException.class, () -> service.createObjective("UNEP",
                new SalesForceService.ObjectiveInput(null, JobCode.CAC, ObjectiveType.QUALITATIVE, FROM, null,
                        BigDecimal.ONE)));
        assertThrows(ValidationException.class, () -> service.createObjective("UNEP",
                new SalesForceService.ObjectiveInput(null, JobCode.CAC, ObjectiveType.QUALITATIVE, TO, FROM,
                        BigDecimal.ONE)));
        assertThrows(ValidationException.class, () -> service.createObjective("UNEP",
                new SalesForceService.ObjectiveInput(null, JobCode.CAC, ObjectiveType.QUALITATIVE, FROM, TO, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.createObjective("UNEP",
                new SalesForceService.ObjectiveInput("GHOST", JobCode.CAC, ObjectiveType.INDIVIDUAL_QUANTITATIVE,
                        FROM, TO, BigDecimal.ONE)));
    }

    /**
     * FDV-01: the writes refuse a blank matricule, a duplicate, a missing post or start date, an
     * unknown manager and an unknown collaborator on update.
     */
    @Test
    @TestTransaction
    void rejects_invalid_collaborators() {
        service.createCollaborator("UNEP", new SalesForceService.CollaboratorInput(
                "C-9", "X", "Y", JobCode.CAC, null, null, null, LocalDate.of(2026, 1, 1), null));

        assertThrows(ValidationException.class, () -> service.createCollaborator("UNEP",
                new SalesForceService.CollaboratorInput(null, "X", "Y", JobCode.CAC, null, null, null,
                        LocalDate.of(2026, 1, 1), null)));
        assertThrows(ValidationException.class, () -> service.createCollaborator("UNEP",
                new SalesForceService.CollaboratorInput(" ", "X", "Y", JobCode.CAC, null, null, null,
                        LocalDate.of(2026, 1, 1), null)));
        assertThrows(ValidationException.class, () -> service.createCollaborator("UNEP",
                new SalesForceService.CollaboratorInput("C-9", "X", "Y", JobCode.CAC, null, null, null,
                        LocalDate.of(2026, 1, 1), null)));
        assertThrows(ValidationException.class, () -> service.createCollaborator("UNEP",
                new SalesForceService.CollaboratorInput("C-10", "X", "Y", null, null, null, null,
                        LocalDate.of(2026, 1, 1), null)));
        assertThrows(ValidationException.class, () -> service.createCollaborator("UNEP",
                new SalesForceService.CollaboratorInput("C-11", "X", "Y", JobCode.CAC, null, null, null, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.createCollaborator("UNEP",
                new SalesForceService.CollaboratorInput("C-12", "X", "Y", JobCode.CAC, null, null, "GHOST",
                        LocalDate.of(2026, 1, 1), null)));
        assertThrows(ResourceNotFoundException.class, () -> service.updateCollaborator("UNEP", "GHOST",
                new SalesForceService.CollaboratorInput("GHOST", "X", "Y", JobCode.CAC, null, null, null,
                        LocalDate.of(2026, 1, 1), null)));
    }

    /** Creates a June N+1 period the collective regularization is booked in. */
    private static void june(int nextYear) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = LocalDate.of(nextYear, 6, 1);
        period.state = PeriodState.CONTROLLED;
        period.persist();
    }

    /** Creates an annual-collective plan for a post, with its envelope and no instalment. */
    private static void collectivePlan(JobCode jobCode, String envelope) {
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = jobCode;
        plan.component = VariableComponent.ANNUAL_COLLECTIVE;
        plan.envelopeAmount = new BigDecimal(envelope);
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
    }

    /** Creates the entity-wide collective objective (no employee) for 2026. */
    private static void collectiveObjective(String target) {
        SalesObjective objective = new SalesObjective();
        objective.entity = unep();
        objective.objectiveType = ObjectiveType.COLLECTIVE_QUANTITATIVE;
        objective.periodFrom = LocalDate.of(2026, 1, 1);
        objective.periodTo = LocalDate.of(2026, 12, 31);
        objective.targetValue = new BigDecimal(target);
        objective.versionNo = 1;
        objective.persist();
    }

    /** Creates an absence measure of a given number of days in a specific month. */
    private static void absenceInMonth(Employee employee, LocalDate month, String days) {
        EmployeeMeasure measure = new EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = MeasureType.ABSENCE;
        measure.dimension = "absence";
        measure.periodFrom = month;
        measure.periodTo = month.withDayOfMonth(month.lengthOfMonth());
        measure.value = new BigDecimal(days);
        measure.persist();
    }

    /** The collective result of an employee in a settlement month. */
    private static VariableResult collectiveIn(Employee employee, LocalDate month) {
        return VariableResult.<VariableResult>find(
                "employee = ?1 and component = ?2 and period.periodMonth = ?3",
                employee, VariableComponent.ANNUAL_COLLECTIVE, month).firstResult();
    }

    /**
     * FDV-08: the annual collective variable is settled in January N+1 — each post's envelope times the
     * collective achievement rate (collective weighted production over the collective objective, capped),
     * prorated by worked months. Collective production is 1 400 (E1) + 500 (E2) = 1 900 against a 2 800
     * objective, so the rate is 1 900 / 2 800 = 0,678571 and each CAC's 550 € envelope settles at
     * 550 × 0,678571 = 373,21 €. Re-settling in June with unchanged figures writes only the delta: zero.
     */
    @Test
    @TestTransaction
    void fdv08_collective_settles_in_january_then_regularizes_in_june() {
        Employee e1 = employee("E1", JobCode.CAC);
        Employee e2 = employee("E2", JobCode.CAC);
        collectivePlan(JobCode.CAC, "550");
        collectiveObjective("2800");
        measure(e1, MeasureType.PRODUCTION, "particulier", "2000");
        measure(e2, MeasureType.PRODUCTION, "emprunteur", "500");
        january(2027);

        assertEquals(2, service.runAnnualCollective("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, collectiveIn(e1, LocalDate.of(2027, 1, 1)).amount.compareTo(new BigDecimal("373.21")));
        assertEquals(0, collectiveIn(e1, LocalDate.of(2027, 1, 1)).achievementRate.compareTo(new BigDecimal("0.678571")));
        assertEquals(0, collectiveIn(e2, LocalDate.of(2027, 1, 1)).amount.compareTo(new BigDecimal("373.21")));

        // June regularization with unchanged figures pays the delta over January: zero.
        june(2027);
        assertEquals(2, service.runAnnualCollective("UNEP", LocalDate.of(2027, 6, 1)));
        assertEquals(0, collectiveIn(e1, LocalDate.of(2027, 6, 1)).amount.compareTo(BigDecimal.ZERO));

        // A January rerun replaces the calculated results rather than doubling them.
        assertEquals(2, service.runAnnualCollective("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(2, VariableResult.count("component = ?1 and period.periodMonth = ?2",
                VariableComponent.ANNUAL_COLLECTIVE, LocalDate.of(2027, 1, 1)));
    }

    /**
     * FDV-08 edges: one absent month prorates the envelope by eleven twelfths (550 × 11/12 = 504,17 €);
     * an employee without a collective plan is skipped; and with no collective objective the rate is zero
     * and nothing is paid.
     */
    @Test
    @TestTransaction
    void fdv08_collective_absence_prorates_and_no_objective_pays_zero() {
        Employee worker = employee("WORK", JobCode.CAC);
        employee("NOPLAN", JobCode.RB);
        collectivePlan(JobCode.CAC, "550");
        collectiveObjective("1000");
        measure(worker, MeasureType.PRODUCTION, "particulier", "2000");
        absenceInMonth(worker, LocalDate.of(2026, 1, 1), "10");
        january(2027);

        assertEquals(1, service.runAnnualCollective("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, collectiveIn(worker, LocalDate.of(2027, 1, 1)).amount.compareTo(new BigDecimal("504.17")));

        // With no collective objective the rate is zero and nothing is paid.
        SalesObjective.delete("objectiveType = ?1", ObjectiveType.COLLECTIVE_QUANTITATIVE);
        service.runAnnualCollective("UNEP", LocalDate.of(2027, 1, 1));
        assertEquals(0, collectiveIn(worker, LocalDate.of(2027, 1, 1)).amount.compareTo(BigDecimal.ZERO));
    }

    /** Creates an annual-manager plan for a post, with its growth and criteria scales. */
    private static void managerPlan(JobCode jobCode, String parameters) {
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = jobCode;
        plan.component = VariableComponent.ANNUAL_MANAGER;
        plan.envelopeAmount = new BigDecimal("0");
        plan.parameters = parameters;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
    }

    /** The annual-manager result of an employee. */
    private static VariableResult manager(Employee employee) {
        return VariableResult.<VariableResult>find("employee = ?1 and component = ?2", employee,
                VariableComponent.ANNUAL_MANAGER).firstResult();
    }

    /**
     * FDV-09: the animators/managers variable sums a production-growth tier and a met-criteria tier.
     * Weighted production 1 400 over a 1 000 baseline is a 40 % growth, reaching the 35 % tier (3 000 €);
     * three qualitative criteria are met, reaching the 3-criteria tier (500 €): 3 500 € in all.
     */
    @Test
    @TestTransaction
    void fdv09_manager_variable_sums_growth_and_criteria_tiers() {
        Employee e = employee("E1", JobCode.CAC);
        managerPlan(JobCode.CAC, "{\"growthBaseline\":1000,\"growthTiers\":{\"15\":1000,\"25\":2000,\"35\":3000},"
                + "\"criteriaTiers\":{\"3\":500,\"5\":1500}}");
        measure(e, MeasureType.PRODUCTION, "particulier", "2000");
        qualitativeObjective(e, "10");
        measure(e, MeasureType.ACTIVITY, "productivite", "12");
        measure(e, MeasureType.ACTIVITY, "efficacite", "11");
        measure(e, MeasureType.ACTIVITY, "developpement", "13");
        january(2027);

        assertEquals(1, service.runManagerVariable("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, new BigDecimal("3500.00").compareTo(manager(e).amount));
        assertEquals(0, new BigDecimal("0.400000").compareTo(manager(e).achievementRate));

        // A rerun replaces the calculated result rather than doubling it.
        assertEquals(1, service.runManagerVariable("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(1, VariableResult.count("component = ?1", VariableComponent.ANNUAL_MANAGER));
    }

    /**
     * FDV-09 edges: a plan without a growth baseline earns no growth tier and, with no qualitative
     * objective, no criteria tier (custom criteria list); a plan without a growth-scale map and a custom
     * criterion met earns only its criteria tier; a non-object growth scale earns nothing; and an
     * employee without a manager plan is skipped.
     */
    @Test
    @TestTransaction
    void fdv09_manager_edges() {
        // No growth baseline and no qualitative objective: growth 0, no criterion met (custom list).
        Employee noBase = employee("NOBASE", JobCode.CAC);
        managerPlan(JobCode.CAC, "{\"growthTiers\":{\"15\":1000},\"criteriaTiers\":{\"2\":300},"
                + "\"criteria\":[\"productivite\",\"efficacite\"]}");
        measure(noBase, MeasureType.PRODUCTION, "particulier", "5000");
        measure(noBase, MeasureType.ACTIVITY, "productivite", "99");
        // No growth scale at all, but one default criterion met against a threshold pays its criteria tier.
        Employee onlyCriteria = employee("ONLYCRIT", JobCode.DR);
        managerPlan(JobCode.DR, "{\"growthBaseline\":1000,\"criteriaTiers\":{\"1\":200}}");
        measure(onlyCriteria, MeasureType.PRODUCTION, "particulier", "2000");
        qualitativeObjective(onlyCriteria, "10");
        measure(onlyCriteria, MeasureType.ACTIVITY, "productivite", "12");
        // A non-object growth scale yields no growth tier (and no criteria scale: nothing).
        Employee badTiers = employee("BADT", JobCode.RR);
        managerPlan(JobCode.RR, "{\"growthBaseline\":1000,\"growthTiers\":5}");
        measure(badTiers, MeasureType.PRODUCTION, "particulier", "2000");
        // Descending growth tiers (a lower key after a higher one is skipped) and a non-array criteria
        // node (fallen back to the default criteria): growth 40 % reaches 3000 and one default criterion
        // met pays 100, so 3100.
        Employee combo = employee("COMBO", JobCode.DNV);
        managerPlan(JobCode.DNV, "{\"growthBaseline\":1000,\"growthTiers\":{\"35\":3000,\"15\":1000},"
                + "\"criteria\":\"productivite\",\"criteriaTiers\":{\"1\":100}}");
        measure(combo, MeasureType.PRODUCTION, "particulier", "2000");
        qualitativeObjective(combo, "10");
        measure(combo, MeasureType.ACTIVITY, "productivite", "12");
        // An employee without a manager plan is skipped.
        employee("NOPLAN", JobCode.RB);
        january(2027);

        assertEquals(4, service.runManagerVariable("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, BigDecimal.ZERO.compareTo(manager(noBase).amount));
        assertEquals(0, new BigDecimal("200.00").compareTo(manager(onlyCriteria).amount));
        assertEquals(0, BigDecimal.ZERO.compareTo(manager(badTiers).amount));
        assertEquals(0, new BigDecimal("3100.00").compareTo(manager(combo).amount));
    }

    /** Creates a direct-sales plan for a post, with its scale parameters. */
    private static void directPlan(JobCode jobCode, String parameters) {
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = jobCode;
        plan.component = VariableComponent.ANNUAL_DIRECT_SALES;
        plan.envelopeAmount = new BigDecimal("0");
        plan.parameters = parameters;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
    }

    /** Creates a yearly margin measure on a dimension for an employee. */
    private static void margin(Employee employee, String dimension, String value) {
        EmployeeMeasure measure = new EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = MeasureType.MARGIN;
        measure.dimension = dimension;
        measure.periodFrom = LocalDate.of(2026, 1, 1);
        measure.periodTo = LocalDate.of(2026, 12, 31);
        measure.value = new BigDecimal(value);
        measure.persist();
    }

    /** The direct-sales result of an employee in a settlement month. */
    private static VariableResult directIn(Employee employee, LocalDate month) {
        return VariableResult.<VariableResult>find(
                "employee = ?1 and component = ?2 and period.periodMonth = ?3",
                employee, VariableComponent.ANNUAL_DIRECT_SALES, month).firstResult();
    }

    /**
     * FDV-10: the direct-sales variable is 10 % of a 50 000 € new-business margin (5000 €) plus a 1000 €
     * overshoot bonus (margin over 40 000 €) plus a 1500 € portfolio tier (300 000 € reaches the
     * 250 000 € tier), a 7500 € right. January pays 70 % (5250 €); June settles in full, writing the
     * 2250 € balance.
     */
    @Test
    @TestTransaction
    void fdv10_direct_sales_pays_70_percent_then_settles_in_june() {
        Employee e = employee("E1", JobCode.CAC);
        directPlan(JobCode.CAC, "{\"marginRate\":0.10,\"overshootThreshold\":40000,\"overshootBonus\":1000,"
                + "\"portfolioTiers\":{\"100000\":500,\"250000\":1500}}");
        margin(e, "nouvelles", "50000");
        margin(e, "portefeuille", "300000");
        january(2027);

        assertEquals(1, service.runDirectSalesVariable("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, new BigDecimal("5250.00").compareTo(directIn(e, LocalDate.of(2027, 1, 1)).amount));

        june(2027);
        assertEquals(1, service.runDirectSalesVariable("UNEP", LocalDate.of(2027, 6, 1)));
        assertEquals(0, new BigDecimal("2250.00").compareTo(directIn(e, LocalDate.of(2027, 6, 1)).amount));

        // A January rerun replaces the calculated result rather than doubling it.
        assertEquals(1, service.runDirectSalesVariable("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(1, VariableResult.count("component = ?1 and period.periodMonth = ?2",
                VariableComponent.ANNUAL_DIRECT_SALES, LocalDate.of(2027, 1, 1)));
    }

    /**
     * FDV-10 edges: custom margin and portfolio dimensions and an explicit January fraction, with no
     * overshoot threshold (A); the default dimensions and January fraction with an overshoot threshold
     * not reached (B); and an employee without a direct-sales plan is skipped. A pays 2000 × 0,5 = 1000 €,
     * B pays 1000 × 0,70 = 700 €.
     */
    @Test
    @TestTransaction
    void fdv10_direct_sales_edges() {
        Employee a = employee("A", JobCode.CAC);
        directPlan(JobCode.CAC, "{\"marginRate\":0.20,\"marginDimension\":\"md\",\"portfolioDimension\":\"pd\","
                + "\"januaryFraction\":0.5,\"portfolioTiers\":{\"500000\":9000}}");
        margin(a, "md", "10000");
        margin(a, "pd", "100000");
        Employee b = employee("B", JobCode.DR);
        directPlan(JobCode.DR, "{\"marginRate\":0.10,\"overshootThreshold\":100000,\"overshootBonus\":5000}");
        margin(b, "nouvelles", "10000");
        employee("NOPLAN", JobCode.RB);
        january(2027);

        assertEquals(2, service.runDirectSalesVariable("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, new BigDecimal("1000.00").compareTo(directIn(a, LocalDate.of(2027, 1, 1)).amount));
        assertEquals(0, new BigDecimal("700.00").compareTo(directIn(b, LocalDate.of(2027, 1, 1)).amount));
    }

    /** Creates a termination-reduction plan for a post, with its parameters. */
    private static void terminationPlan(JobCode jobCode, String parameters) {
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = jobCode;
        plan.component = VariableComponent.TERMINATION_REDUCTION;
        plan.envelopeAmount = new BigDecimal("0");
        plan.parameters = parameters;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
    }

    /** Creates a yearly early-termination measure on a dimension for an employee. */
    private static void earlyTermination(Employee employee, String dimension, String value) {
        EmployeeMeasure measure = new EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = MeasureType.EARLY_TERMINATION;
        measure.dimension = dimension;
        measure.periodFrom = LocalDate.of(2026, 1, 1);
        measure.periodTo = LocalDate.of(2026, 12, 31);
        measure.value = new BigDecimal(value);
        measure.persist();
    }

    /** The termination-reduction result of an employee. */
    private static VariableResult reduction(Employee employee) {
        return VariableResult.<VariableResult>find("employee = ?1 and component = ?2", employee,
                VariableComponent.TERMINATION_REDUCTION).firstResult();
    }

    /**
     * FDV-12: the termination reduction is a negative amount equal to the reduction rate times the
     * early-terminated production. A has 15 % of 10 000 € (−1500 €) on the default dimension; B has 20 %
     * of 5 000 € (−1000 €) on a custom dimension; C has a plan but no early terminations (0 €); and an
     * employee without a reduction plan is skipped.
     */
    @Test
    @TestTransaction
    void fdv12_reduces_the_variable_by_early_terminations() {
        Employee a = employee("A", JobCode.CAC);
        terminationPlan(JobCode.CAC, "{\"reductionRate\":0.15}");
        earlyTermination(a, "resiliations", "10000");
        Employee b = employee("B", JobCode.DR);
        terminationPlan(JobCode.DR, "{\"reductionRate\":0.20,\"terminationDimension\":\"annul\"}");
        earlyTermination(b, "annul", "5000");
        Employee c = employee("C", JobCode.RR);
        terminationPlan(JobCode.RR, "{\"reductionRate\":0.10}");
        employee("NOPLAN", JobCode.RB);
        january(2027);

        assertEquals(3, service.runTerminationReduction("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(0, new BigDecimal("-1500.00").compareTo(reduction(a).amount));
        assertEquals(0, new BigDecimal("-1000.00").compareTo(reduction(b).amount));
        assertEquals(0, BigDecimal.ZERO.compareTo(reduction(c).amount));

        // A rerun replaces the calculated result rather than doubling it.
        assertEquals(3, service.runTerminationReduction("UNEP", LocalDate.of(2027, 1, 1)));
        assertEquals(3, VariableResult.count("component = ?1", VariableComponent.TERMINATION_REDUCTION));
    }
}
