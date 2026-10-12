package fr.alptis.remuneration.salesforce;

import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import fr.alptis.remuneration.domain.VariableResult;
import fr.alptis.remuneration.domain.VariableResultStatus;
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
 * Tests of the sales-force read model: plans, collaborators (with and without a manager), objectives
 * (individual and collective), period results and a commercial's own variable, plus the unknown
 * paths. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class SalesForceQueryServiceTest {

    @Inject
    SalesForceQueryService query;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Builds a manager, a managed collaborator, a plan, two objectives and a result. */
    private Employee scene() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        Employee manager = employee("M0", null);
        Employee employee = employee("E1", manager);
        VariablePlan plan = new VariablePlan();
        plan.entity = unep();
        plan.jobCode = JobCode.CAC;
        plan.component = VariableComponent.MONTHLY_INDIVIDUAL;
        plan.envelopeAmount = new BigDecimal("2450.00");
        plan.instalmentAmount = new BigDecimal("146.00");
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.persist();
        objective(employee, ObjectiveType.INDIVIDUAL_QUANTITATIVE);
        objective(null, ObjectiveType.COLLECTIVE_QUANTITATIVE);
        VariableResult result = new VariableResult();
        result.period = period;
        result.employee = employee;
        result.component = VariableComponent.MONTHLY_INDIVIDUAL;
        result.amount = new BigDecimal("146.00");
        result.achievementRate = BigDecimal.ONE;
        result.status = VariableResultStatus.CALCULATED;
        result.persist();
        return employee;
    }

    /** Creates an employee with an optional manager. */
    private static Employee employee(String externalId, Employee manager) {
        Employee employee = new Employee();
        employee.entity = unep();
        employee.externalId = externalId;
        employee.lastName = externalId;
        employee.firstName = "Jean";
        employee.jobCode = JobCode.CAC;
        employee.manager = manager;
        employee.validFrom = LocalDate.of(2026, 1, 1);
        employee.persist();
        return employee;
    }

    /** Creates an objective for an employee (null = collective). */
    private static void objective(Employee employee, ObjectiveType type) {
        SalesObjective objective = new SalesObjective();
        objective.entity = unep();
        objective.employee = employee;
        objective.jobCode = JobCode.CAC;
        objective.objectiveType = type;
        objective.periodFrom = LocalDate.of(2026, 9, 1);
        objective.periodTo = LocalDate.of(2026, 9, 30);
        objective.targetValue = new BigDecimal("1000.00");
        objective.versionNo = 1;
        objective.persist();
    }

    /**
     * The read model returns the plans, the collaborators with and without a manager, the individual
     * and collective objectives, the period results and the employee's own variable.
     */
    @Test
    @TestTransaction
    void reads_the_scene() {
        Employee e = scene();
        assertTrue(query.plans("UNEP").stream().anyMatch(p -> p.jobCode() == JobCode.CAC));
        assertTrue(query.collaborators("UNEP").stream().anyMatch(c -> "E1".equals(c.externalId()) && "M0".equals(c.manager())));
        assertTrue(query.collaborators("UNEP").stream().anyMatch(c -> "M0".equals(c.externalId()) && c.manager() == null));
        assertTrue(query.objectives("UNEP").stream().anyMatch(o -> o.employee() == null));
        assertTrue(query.objectives("UNEP").stream().anyMatch(o -> "E1".equals(o.employee())));
        assertEquals(1, query.results("UNEP", M).size());
        assertEquals(1, query.myVariable("UNEP", e.externalId).size());
    }

    /**
     * The measures received for an entity are read (FDV-02), the collaborator name assembled from the
     * present name parts (both set, and both absent).
     */
    @Test
    @TestTransaction
    void reads_received_measures() {
        Employee named = employee("E1", null);
        measure(named, fr.alptis.remuneration.domain.MeasureType.PRODUCTION, "particulier", "2000");
        Employee anonymous = employee("E2", null);
        anonymous.lastName = null;
        anonymous.firstName = null;
        measure(anonymous, fr.alptis.remuneration.domain.MeasureType.ABSENCE, "absence", "3");

        var measures = query.measures("UNEP");
        assertTrue(measures.stream().anyMatch(m -> "E1".equals(m.externalId())
                && m.collaborator().equals("E1 Jean")
                && m.measureType() == fr.alptis.remuneration.domain.MeasureType.PRODUCTION));
        assertTrue(measures.stream().anyMatch(m -> "E2".equals(m.externalId()) && m.collaborator().isEmpty()));
        assertThrows(ResourceNotFoundException.class, () -> query.measures("GHOST"));
    }

    /** Creates one measure over September for an employee. */
    private static void measure(Employee employee, fr.alptis.remuneration.domain.MeasureType type, String dimension,
                                String value) {
        fr.alptis.remuneration.domain.EmployeeMeasure measure = new fr.alptis.remuneration.domain.EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = type;
        measure.dimension = dimension;
        measure.periodFrom = LocalDate.of(2026, 9, 1);
        measure.periodTo = LocalDate.of(2026, 9, 30);
        measure.value = new BigDecimal(value);
        measure.persist();
    }

    /**
     * An unknown entity and an unknown employee are rejected.
     */
    @Test
    @TestTransaction
    void unknown_paths_are_rejected() {
        scene();
        assertThrows(ResourceNotFoundException.class, () -> query.plans("GHOST"));
        assertThrows(ResourceNotFoundException.class, () -> query.myVariable("UNEP", "GHOST"));
    }
}
