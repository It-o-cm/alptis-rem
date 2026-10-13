package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dev-only seeding of a deterministic sales-force scene for the end-to-end suite (outside production):
 * a period, a collaborator with a monthly-individual plan, an objective and a production measure, so
 * the plan, validation and commercial screens have real data to exercise.
 */
@Path("/api/test/fdv-demo")
@UnlessBuildProfile("prod")
public class FdvDemoResource {

    /** The demo collaborator's matricule. */
    public static final String MATRICULE = "C-DEMO";

    /**
     * Plants the sales-force scene of an entity's month.
     *
     * @param entityCode the entity code
     * @param month      the period month (ISO date)
     * @return 204 once seeded
     */
    @POST
    @Transactional
    public Response seed(@QueryParam("entity") String entityCode, @QueryParam("month") String month) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        LocalDate periodMonth = LocalDate.parse(month);
        Period period = new Period();
        period.entity = entity;
        period.periodMonth = periodMonth;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        Period january = new Period();
        january.entity = entity;
        january.periodMonth = LocalDate.of(periodMonth.getYear() + 1, 1, 1);
        january.state = PeriodState.CONTROLLED;
        january.persist();
        Employee employee = new Employee();
        employee.entity = entity;
        employee.externalId = MATRICULE;
        employee.lastName = "Durand";
        employee.firstName = "Claire";
        employee.jobCode = JobCode.CAC;
        employee.team = "Courtage";
        employee.region = "Sud";
        employee.validFrom = LocalDate.of(2026, 1, 1);
        employee.persist();
        VariablePlan plan = new VariablePlan();
        plan.entity = entity;
        plan.jobCode = JobCode.CAC;
        plan.component = VariableComponent.MONTHLY_INDIVIDUAL;
        plan.envelopeAmount = new BigDecimal("2450.00");
        plan.instalmentAmount = new BigDecimal("146.00");
        plan.validFrom = LocalDate.of(2026, 1, 1);
        plan.validTo = LocalDate.of(2026, 12, 31);
        plan.persist();
        SalesObjective objective = new SalesObjective();
        objective.entity = entity;
        objective.employee = employee;
        objective.jobCode = JobCode.CAC;
        objective.objectiveType = ObjectiveType.INDIVIDUAL_QUANTITATIVE;
        objective.periodFrom = periodMonth.withDayOfMonth(1);
        objective.periodTo = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());
        objective.targetValue = new BigDecimal("1000.00");
        objective.versionNo = 1;
        objective.persist();
        EmployeeMeasure measure = new EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = MeasureType.PRODUCTION;
        measure.dimension = "particulier";
        measure.periodFrom = periodMonth.withDayOfMonth(1);
        measure.periodTo = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());
        measure.value = new BigDecimal("2000.00");
        measure.persist();
        return Response.noContent().build();
    }
}
