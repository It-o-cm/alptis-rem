package fr.alptis.remuneration.salesforce;

import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import fr.alptis.remuneration.domain.VariableResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read model for the sales-force screens (section 8.14): the variable plans by post, the
 * collaborators and their objectives, the monthly results to validate (FDV-13) and one commercial's
 * own variable and history (FDV-15).
 */
@ApplicationScoped
public class SalesForceQueryService {

    /**
     * Returns the variable plans of an entity.
     *
     * @param entityCode the entity code
     * @return the plan views
     */
    public List<PlanView> plans(String entityCode) {
        LegalEntity entity = entity(entityCode);
        return VariablePlan.<VariablePlan>list("entity = ?1 order by jobCode, component", entity).stream()
                .map(p -> new PlanView(p.jobCode, p.component, p.envelopeAmount, p.instalmentAmount, p.parameters,
                        p.validFrom, p.validTo))
                .toList();
    }

    /**
     * Returns the collaborators of an entity.
     *
     * @param entityCode the entity code
     * @return the collaborator views
     */
    public List<CollaboratorView> collaborators(String entityCode) {
        LegalEntity entity = entity(entityCode);
        return Employee.<Employee>list("entity = ?1 order by externalId", entity).stream()
                .map(e -> new CollaboratorView(e.externalId, e.lastName, e.firstName, e.jobCode, e.team, e.region,
                        e.manager == null ? null : e.manager.externalId, e.validFrom, e.validTo))
                .toList();
    }

    /**
     * Returns the objectives of an entity.
     *
     * @param entityCode the entity code
     * @return the objective views
     */
    public List<ObjectiveView> objectives(String entityCode) {
        LegalEntity entity = entity(entityCode);
        return SalesObjective.<SalesObjective>list("entity = ?1 order by id", entity)
                .stream()
                .map(o -> new ObjectiveView(o.employee == null ? null : o.employee.externalId,
                        o.objectiveType.name(), o.periodFrom, o.periodTo, o.targetValue, o.versionNo))
                .toList();
    }

    /**
     * Returns the measures received for an entity's collaborators (FDV-02): absences, production,
     * commercial activity, margin and received specifications, as fed by the SI-RH/CRM import.
     *
     * @param entityCode the entity code
     * @return the measure views
     */
    public List<MeasureView> measures(String entityCode) {
        LegalEntity entity = entity(entityCode);
        return EmployeeMeasure.<EmployeeMeasure>list(
                        "employee.entity = ?1 order by employee.externalId, periodFrom, measureType", entity)
                .stream()
                .map(m -> new MeasureView(m.employee.externalId,
                        (m.employee.lastName == null ? "" : m.employee.lastName)
                                + (m.employee.firstName == null ? "" : " " + m.employee.firstName),
                        m.measureType, m.dimension, m.periodFrom, m.periodTo, m.value))
                .toList();
    }

    /**
     * Returns the variable results of an entity's period (validation screen, FDV-13).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the result views
     */
    public List<ResultView> results(String entityCode, LocalDate month) {
        LegalEntity entity = entity(entityCode);
        return VariableResult.<VariableResult>list(
                "period.entity = ?1 and period.periodMonth = ?2 order by employee.externalId, component", entity,
                month).stream().map(SalesForceQueryService::resultView).toList();
    }

    /**
     * Returns a commercial's own variable results across every period (FDV-15).
     *
     * @param entityCode the entity code
     * @param externalId the employee's external id
     * @return the result views, newest period first
     */
    public List<ResultView> myVariable(String entityCode, String externalId) {
        LegalEntity entity = entity(entityCode);
        Employee employee = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity, externalId)
                .firstResult();
        if (employee == null) {
            throw new ResourceNotFoundException("Collaborateur inconnu : " + externalId);
        }
        return VariableResult.<VariableResult>list(
                "employee = ?1 order by period.periodMonth desc, component", employee).stream()
                .map(SalesForceQueryService::resultView).toList();
    }

    /**
     * Maps a result to its view.
     *
     * @param result the result
     * @return the view
     */
    private static ResultView resultView(VariableResult result) {
        return new ResultView(result.getId(), result.employee.externalId,
                result.employee.lastName + " " + result.employee.firstName, result.employee.jobCode,
                result.component, result.achievementRate, result.amount, result.status.name(),
                result.adjustmentReason);
    }

    /**
     * Looks up an entity by code.
     *
     * @param code the entity code
     * @return the entity
     */
    private LegalEntity entity(String code) {
        LegalEntity entity = LegalEntity.find("code", code).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + code);
        }
        return entity;
    }

    /**
     * A variable plan of a post.
     *
     * @param jobCode    the post
     * @param component  the component
     * @param envelope   the annual envelope
     * @param instalment the monthly instalment
     * @param parameters the plan parameters, scales and absence rules (JSON)
     * @param validFrom  the start of validity
     * @param validTo    the end of validity
     */
    public record PlanView(JobCode jobCode, VariableComponent component, BigDecimal envelope,
                           BigDecimal instalment, String parameters, LocalDate validFrom, LocalDate validTo) {
    }

    /**
     * A collaborator.
     *
     * @param externalId the HR matricule
     * @param lastName   the surname
     * @param firstName  the first name
     * @param jobCode    the post
     * @param team       the team
     * @param region     the region
     * @param manager    the manager's external id, or null
     */
    public record CollaboratorView(String externalId, String lastName, String firstName, JobCode jobCode,
                                   String team, String region, String manager, java.time.LocalDate validFrom,
                                   java.time.LocalDate validTo) {
    }

    /**
     * A measure received for a collaborator (FDV-02).
     *
     * @param externalId   the collaborator's external id
     * @param collaborator the collaborator's display name
     * @param measureType  the measure type
     * @param dimension    the dimension (market or activity detail)
     * @param periodFrom   the window start
     * @param periodTo     the window end
     * @param value        the measured value
     */
    public record MeasureView(String externalId, String collaborator, MeasureType measureType, String dimension,
                              java.time.LocalDate periodFrom, java.time.LocalDate periodTo, BigDecimal value) {
    }

    /**
     * An objective.
     *
     * @param employee  the employee external id, or null for a collective objective
     * @param type      the objective type
     * @param periodFrom the start
     * @param periodTo  the end
     * @param target    the target value
     * @param versionNo the version number
     */
    public record ObjectiveView(String employee, String type, LocalDate periodFrom, LocalDate periodTo,
                                BigDecimal target, Integer versionNo) {
    }

    /**
     * A variable result.
     *
     * @param id              the result id
     * @param employee        the employee external id
     * @param employeeName    the employee name
     * @param jobCode         the post
     * @param component       the component
     * @param achievementRate the achievement rate
     * @param amount          the amount
     * @param status          the status
     * @param adjustmentReason the adjustment reason, or null
     */
    public record ResultView(Long id, String employee, String employeeName, JobCode jobCode,
                             VariableComponent component, BigDecimal achievementRate, BigDecimal amount,
                             String status, String adjustmentReason) {
    }
}
