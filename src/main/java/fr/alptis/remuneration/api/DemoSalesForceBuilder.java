package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import fr.alptis.remuneration.domain.VariableResult;
import fr.alptis.remuneration.domain.VariableResultStatus;
import fr.alptis.remuneration.salesforce.SalesForceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the sales-force (forces de vente) scene of the demonstration dataset for both entities: a
 * monthly-individual variable-pay plan per post, six collaborators over two supervision levels,
 * individual objectives and three months of production and absence measures. It then computes the
 * monthly variable through the real service for September (validated and exported) and October (left
 * pending), so the plan, collaborators, objectives and results screens all show data, and the Alptis
 * sales manager and sales rep see their own perimeter.
 */
@ApplicationScoped
public class DemoSalesForceBuilder {

    /** The author stamped on the sales-force referential. */
    private static final String AUTHOR = "demo-fdv";

    /** The validated-and-exported month. */
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    /** The pending month. */
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);

    /** The three months of measures. */
    private static final List<LocalDate> MONTHS = List.of(LocalDate.of(2026, 8, 1), SEPTEMBER, OCTOBER);

    private final SalesForceService salesForce;
    private final DateTimeProvider clock;

    /**
     * Creates the builder.
     *
     * @param salesForce the sales-force service
     * @param clock      the application date/time provider
     */
    @Inject
    public DemoSalesForceBuilder(SalesForceService salesForce, DateTimeProvider clock) {
        this.salesForce = salesForce;
        this.clock = clock;
    }

    /**
     * Builds and runs the sales-force scene for both entities.
     *
     * @param world the shared demo world
     */
    void build(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            plans(entity);
            List<Employee> team = team(entity);
            objectivesAndMeasures(team);
            compute(entity);
        }
    }

    /**
     * Creates the monthly-individual plans of the two posts (sales rep and manager).
     *
     * @param entity the entity
     */
    private void plans(LegalEntity entity) {
        plan(entity, JobCode.CAC, new BigDecimal("2450.00"), new BigDecimal("146.00"));
        plan(entity, JobCode.RR, new BigDecimal("4200.00"), new BigDecimal("300.00"));
        collectivePlan(entity, JobCode.CAC, new BigDecimal("550.00"));
        collectivePlan(entity, JobCode.RR, new BigDecimal("4800.00"));
        managerPlan(entity, JobCode.RR, new BigDecimal("12000.00"),
                "{\"growthBaseline\":3000,\"growthTiers\":{\"15\":2000,\"25\":4000,\"35\":6000},"
                        + "\"criteriaTiers\":{\"3\":1500,\"5\":3000}}");
        managerPlan(entity, JobCode.DNV, new BigDecimal("16000.00"),
                "{\"marginRate\":0.10,\"overshootThreshold\":40000,\"overshootBonus\":2000,"
                        + "\"portfolioTiers\":{\"100000\":1000,\"250000\":2500}}",
                VariableComponent.ANNUAL_DIRECT_SALES);
    }

    /**
     * Creates one annual-manager variable-pay plan for a post (FDV-09), with its growth and criteria
     * scales.
     *
     * @param entity     the entity
     * @param jobCode    the post
     * @param envelope   the indicative annual envelope
     * @param parameters the growth and criteria scales (JSON)
     */
    private void managerPlan(LegalEntity entity, JobCode jobCode, BigDecimal envelope, String parameters) {
        managerPlan(entity, jobCode, envelope, parameters, VariableComponent.ANNUAL_MANAGER);
    }

    /**
     * Creates one parameterised annual variable-pay plan for a post (FDV-09/FDV-10), with its JSON scale
     * parameters and component.
     *
     * @param entity     the entity
     * @param jobCode    the post
     * @param envelope   the indicative annual envelope
     * @param parameters the scale parameters (JSON)
     * @param component  the variable component
     */
    private void managerPlan(LegalEntity entity, JobCode jobCode, BigDecimal envelope, String parameters,
                             VariableComponent component) {
        VariablePlan plan = new VariablePlan();
        plan.entity = entity;
        plan.jobCode = jobCode;
        plan.component = component;
        plan.envelopeAmount = envelope;
        plan.parameters = parameters;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        stamp(plan);
        plan.persist();
    }

    /**
     * Creates one annual-collective variable-pay plan for a post (FDV-08), with its collective envelope.
     *
     * @param entity   the entity
     * @param jobCode  the post
     * @param envelope the collective annual envelope
     */
    private void collectivePlan(LegalEntity entity, JobCode jobCode, BigDecimal envelope) {
        VariablePlan plan = new VariablePlan();
        plan.entity = entity;
        plan.jobCode = jobCode;
        plan.component = VariableComponent.ANNUAL_COLLECTIVE;
        plan.envelopeAmount = envelope;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        stamp(plan);
        plan.persist();
    }

    /**
     * Creates one monthly-individual variable-pay plan.
     *
     * @param entity     the entity
     * @param jobCode    the post
     * @param envelope   the annual envelope
     * @param instalment the monthly instalment paid when the objective is met
     */
    private void plan(LegalEntity entity, JobCode jobCode, BigDecimal envelope, BigDecimal instalment) {
        VariablePlan plan = new VariablePlan();
        plan.entity = entity;
        plan.jobCode = jobCode;
        plan.component = VariableComponent.MONTHLY_INDIVIDUAL;
        plan.envelopeAmount = envelope;
        plan.instalmentAmount = instalment;
        plan.validFrom = LocalDate.of(2026, 1, 1);
        stamp(plan);
        plan.persist();
    }

    /**
     * Creates an entity's team: two managers and four sales reps, the reps reporting to the first
     * manager. On Alptis the first manager and the first rep carry the dev usernames so those users see
     * their own perimeter.
     *
     * @param entity the entity
     * @return the created team
     */
    private List<Employee> team(LegalEntity entity) {
        boolean alptis = entity.code.equals(DemoCodes.ALPTIS);
        Map<Integer, String> names = alptis ? Map.of(0, "manager-alptis", 2, "rep-alptis") : Map.of();
        String[] last = {"Dupont", "Lambert", "Noël", "Caron", "Perrot", "Lemaire"};
        Employee firstManager = null;
        List<Employee> team = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            boolean manager = i < 2;
            Employee employee = new Employee();
            employee.entity = entity;
            employee.externalId = names.getOrDefault(i, "EMP-" + entity.code + "-" + i);
            employee.lastName = last[i];
            employee.firstName = "Agent";
            employee.jobCode = manager ? JobCode.RR : JobCode.CAC;
            employee.team = "Courtage";
            employee.region = manager ? "Direction" : "Sud";
            employee.manager = manager ? null : firstManager;
            employee.validFrom = LocalDate.of(2026, 1, 1);
            stamp(employee);
            employee.persist();
            if (i == 0) {
                firstManager = employee;
            }
            team.add(employee);
        }
        return team;
    }

    /**
     * Creates each collaborator's yearly objective and three months of production and absence measures,
     * sized so the objective is met and the instalment paid.
     *
     * @param team the team
     */
    private void objectivesAndMeasures(List<Employee> team) {
        for (Employee employee : team) {
            SalesObjective objective = new SalesObjective();
            objective.entity = employee.entity;
            objective.employee = employee;
            objective.jobCode = employee.jobCode;
            objective.objectiveType = ObjectiveType.INDIVIDUAL_QUANTITATIVE;
            objective.periodFrom = LocalDate.of(2026, 1, 1);
            objective.periodTo = LocalDate.of(2026, 12, 31);
            objective.targetValue = new BigDecimal("1000.000000");
            objective.versionNo = 1;
            stamp(objective);
            objective.persist();
            for (LocalDate month : MONTHS) {
                measure(employee, MeasureType.PRODUCTION, "particulier", month, new BigDecimal("2000.000000"));
                measure(employee, MeasureType.ABSENCE, "absence", month, new BigDecimal("2.000000"));
            }
        }
    }

    /**
     * Creates one employee measure over a month.
     *
     * @param employee  the employee
     * @param type      the measure type
     * @param dimension the measure dimension
     * @param month     the month
     * @param value     the measured value
     */
    private void measure(Employee employee, MeasureType type, String dimension, LocalDate month, BigDecimal value) {
        EmployeeMeasure measure = new EmployeeMeasure();
        measure.employee = employee;
        measure.measureType = type;
        measure.dimension = dimension;
        measure.periodFrom = month;
        measure.periodTo = month.withDayOfMonth(month.lengthOfMonth());
        measure.value = value;
        stamp(measure);
        measure.persist();
    }

    /**
     * Computes the monthly variable through the real service: September validated and exported, October
     * left calculated (pending validation).
     *
     * @param entity the entity
     */
    private void compute(LegalEntity entity) {
        salesForce.runMonth(entity.code, SEPTEMBER);
        salesForce.runMonth(entity.code, OCTOBER);
        Period september = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, SEPTEMBER).firstResult();
        for (VariableResult result : VariableResult.<VariableResult>list("period = ?1 and status = ?2", september,
                VariableResultStatus.CALCULATED)) {
            salesForce.validate(result.getId(), "manager-demo");
        }
        salesForce.payExport(entity.code, SEPTEMBER, "finance-demo");
    }

    /**
     * Stamps the creation audit columns.
     *
     * @param entity the entity to stamp
     */
    private void stamp(fr.alptis.remuneration.domain.BaseEntity entity) {
        entity.setCreatedAt(clock.now());
        entity.setCreatedBy(AUTHOR);
    }
}
