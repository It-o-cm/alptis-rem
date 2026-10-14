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

        // A quarterly qualitative plan, objective and criteria measures (FDV-07): productivity and
        // development are met (12 and 13 against the 10 threshold), efficiency is not (8); development
        // also clears the +2 overshoot, so the new-producer complement is due.
        VariablePlan qualitativePlan = new VariablePlan();
        qualitativePlan.entity = entity;
        qualitativePlan.jobCode = JobCode.CAC;
        qualitativePlan.component = VariableComponent.QUARTERLY_QUALITATIVE;
        qualitativePlan.envelopeAmount = new BigDecimal("2000.00");
        qualitativePlan.parameters = "{\"criterionAmount\":666.67,\"developmentComplement\":500,"
                + "\"developmentOvershoot\":2,\"absenceDaysBlocking\":30}";
        qualitativePlan.validFrom = LocalDate.of(2026, 1, 1);
        qualitativePlan.validTo = LocalDate.of(2026, 12, 31);
        qualitativePlan.persist();
        SalesObjective qualitative = new SalesObjective();
        qualitative.entity = entity;
        qualitative.employee = employee;
        qualitative.jobCode = JobCode.CAC;
        qualitative.objectiveType = ObjectiveType.QUALITATIVE;
        qualitative.periodFrom = LocalDate.of(2026, 1, 1);
        qualitative.periodTo = LocalDate.of(2026, 12, 31);
        qualitative.targetValue = new BigDecimal("10.00");
        qualitative.versionNo = 1;
        qualitative.persist();
        LocalDate quarterStart = periodMonth.minusMonths(2).withDayOfMonth(1);
        seedActivity(employee, "productivite", quarterStart, new BigDecimal("12.00"));
        seedActivity(employee, "efficacite", quarterStart, new BigDecimal("8.00"));
        seedActivity(employee, "developpement", quarterStart, new BigDecimal("13.00"));

        // An annual-collective plan for the CAC post (FDV-08). The entity-wide collective objective is
        // created by the scenario itself (through the objectives screen), so the objectives screen keeps a
        // single collective objective of its own.
        VariablePlan collectivePlan = new VariablePlan();
        collectivePlan.entity = entity;
        collectivePlan.jobCode = JobCode.CAC;
        collectivePlan.component = VariableComponent.ANNUAL_COLLECTIVE;
        collectivePlan.envelopeAmount = new BigDecimal("550.00");
        collectivePlan.validFrom = LocalDate.of(2026, 1, 1);
        collectivePlan.validTo = LocalDate.of(2026, 12, 31);
        collectivePlan.persist();

        // An annual-manager plan for the CAC post (FDV-09): the weighted production of 1400 over a 1000
        // baseline is a 40 % growth, reaching the 35 % tier (3000 €); only two qualitative criteria are
        // met (below the 3-criteria tier), so the settled amount is 3000 €.
        VariablePlan managerPlan = new VariablePlan();
        managerPlan.entity = entity;
        managerPlan.jobCode = JobCode.CAC;
        managerPlan.component = VariableComponent.ANNUAL_MANAGER;
        managerPlan.envelopeAmount = new BigDecimal("3000.00");
        managerPlan.parameters = "{\"growthBaseline\":1000,\"growthTiers\":{\"15\":1000,\"25\":2000,\"35\":3000},"
                + "\"criteriaTiers\":{\"3\":500,\"5\":1000}}";
        managerPlan.validFrom = LocalDate.of(2026, 1, 1);
        managerPlan.validTo = LocalDate.of(2026, 12, 31);
        managerPlan.persist();

        // A direct-sales plan and its margin and portfolio measures (FDV-10): 10 % of a 50 000 €
        // new-business margin (5000 €) plus a 1000 € overshoot bonus (margin over 40 000 €) plus a
        // 1500 € portfolio tier (300 000 € reaches the 250 000 € tier) make a 7500 € right; 70 % is paid
        // in January N+1, i.e. 5250 €.
        VariablePlan directPlan = new VariablePlan();
        directPlan.entity = entity;
        directPlan.jobCode = JobCode.CAC;
        directPlan.component = VariableComponent.ANNUAL_DIRECT_SALES;
        directPlan.envelopeAmount = new BigDecimal("7500.00");
        directPlan.parameters = "{\"marginRate\":0.10,\"overshootThreshold\":40000,\"overshootBonus\":1000,"
                + "\"portfolioTiers\":{\"100000\":500,\"250000\":1500}}";
        directPlan.validFrom = LocalDate.of(2026, 1, 1);
        directPlan.validTo = LocalDate.of(2026, 12, 31);
        directPlan.persist();
        seedMargin(employee, "nouvelles", new BigDecimal("50000.00"));
        seedMargin(employee, "portefeuille", new BigDecimal("300000.00"));
        return Response.noContent().build();
    }

    /**
     * Seeds one activity measure for a criterion dimension of the demo collaborator (FDV-07).
     *
     * @param employee  the collaborator
     * @param criterion the activity dimension
     * @param from      the measure start date
     * @param value     the measured value
     */
    private void seedActivity(Employee employee, String criterion, LocalDate from, BigDecimal value) {
        EmployeeMeasure activity = new EmployeeMeasure();
        activity.employee = employee;
        activity.measureType = MeasureType.ACTIVITY;
        activity.dimension = criterion;
        activity.periodFrom = from;
        activity.periodTo = from.withDayOfMonth(from.lengthOfMonth());
        activity.value = value;
        activity.persist();
    }

    /**
     * Seeds one yearly margin measure on a dimension for the demo collaborator (FDV-10).
     *
     * @param employee  the collaborator
     * @param dimension the margin dimension (new business or portfolio)
     * @param value     the measured value
     */
    private void seedMargin(Employee employee, String dimension, BigDecimal value) {
        EmployeeMeasure margin = new EmployeeMeasure();
        margin.employee = employee;
        margin.measureType = MeasureType.MARGIN;
        margin.dimension = dimension;
        margin.periodFrom = LocalDate.of(2026, 1, 1);
        margin.periodTo = LocalDate.of(2026, 12, 31);
        margin.value = value;
        margin.persist();
    }
}
