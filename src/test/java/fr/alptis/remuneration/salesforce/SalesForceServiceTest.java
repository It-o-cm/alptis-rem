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
}
