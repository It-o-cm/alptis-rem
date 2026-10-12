package fr.alptis.remuneration.salesforce;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import fr.alptis.remuneration.domain.VariableResult;
import fr.alptis.remuneration.domain.VariableResultStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sales-force variable calculation (section 8.14, FDV). It computes the monthly individual variable
 * (FDV-05): the production of the month, weighted by market (FDV-11), is compared to the individual
 * objective; when the objective is met and the absence stays under the monthly threshold, the post's
 * instalment is paid, otherwise nothing. A manual adjustment (FDV-13) and the pay export (FDV-14) are
 * also here. The under-specified components — quarterly qualitative, annual collective, the animator,
 * manager and direct-sales scales, the annual regularisation and the résiliation reduction — await
 * the commercial-department workshop the specification calls for (section 8.14 "Limites"); this
 * delivers the CR-87/CR-88 core.
 */
@ApplicationScoped
public class SalesForceService {

    /** The 2026 market weights (FDV-11); an unknown market counts for one. */
    private static final Map<String, BigDecimal> MARKET_WEIGHTS = Map.of(
            "particulier", new BigDecimal("0.7"),
            "emprunteur", BigDecimal.ONE,
            "professionnel", new BigDecimal("1.3"),
            "collectif", new BigDecimal("0.65"));

    /** The monthly individual variable is not paid from this many absence days in the month (FDV-05). */
    private static final BigDecimal MONTHLY_ABSENCE_DAYS = BigDecimal.TEN;

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /** The JSON mapper, to validate a plan's parameters payload. */
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    /**
     * Creates the service.
     *
     * @param clock  the application date/time provider
     * @param mapper the JSON mapper
     */
    @Inject
    public SalesForceService(DateTimeProvider clock, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.clock = clock;
        this.mapper = mapper;
    }

    /**
     * The production of an employee over a window, weighted by market (FDV-11, CR-87).
     *
     * @param employee the employee
     * @param from     the window start
     * @param to       the window end
     * @return the weighted production
     */
    public BigDecimal weightedProduction(Employee employee, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (EmployeeMeasure measure : EmployeeMeasure.<EmployeeMeasure>list(
                "employee = ?1 and measureType = ?2 and periodFrom >= ?3 and periodFrom <= ?4",
                employee, MeasureType.PRODUCTION, from, to)) {
            BigDecimal weight = MARKET_WEIGHTS.getOrDefault(
                    measure.dimension == null ? "" : measure.dimension.toLowerCase(), BigDecimal.ONE);
            total = total.add(measure.value.multiply(weight));
        }
        return total;
    }

    /**
     * Computes the monthly individual variable of every active employee of an entity for a month
     * (FDV-05). Idempotent: the period's non-validated monthly-individual results are dropped first.
     *
     * @param entityCode  the entity code
     * @param periodMonth the processing month
     * @return the number of results written
     */
    @Transactional
    public int runMonth(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, periodMonth);
        LocalDate from = periodMonth.withDayOfMonth(1);
        LocalDate to = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());
        VariableResult.delete("period = ?1 and component = ?2 and status = ?3", period,
                VariableComponent.MONTHLY_INDIVIDUAL, VariableResultStatus.CALCULATED);
        int count = 0;
        for (Employee employee : activeEmployees(entity, periodMonth)) {
            VariablePlan plan = VariablePlan.<VariablePlan>find(
                    "entity = ?1 and jobCode = ?2 and component = ?3 and validFrom <= ?4"
                            + " and (validTo is null or validTo >= ?4)",
                    entity, employee.jobCode, VariableComponent.MONTHLY_INDIVIDUAL, periodMonth).firstResult();
            if (plan == null) {
                continue;
            }
            computeMonthly(period, employee, plan, from, to);
            count++;
        }
        return count;
    }

    /**
     * Computes and persists one employee's monthly individual result.
     *
     * @param period   the period
     * @param employee the employee
     * @param plan     the monthly individual plan of the post
     * @param from     the month start
     * @param to       the month end
     */
    private void computeMonthly(Period period, Employee employee, VariablePlan plan, LocalDate from,
                                LocalDate to) {
        BigDecimal weighted = weightedProduction(employee, from, to);
        BigDecimal absence = absenceDays(employee, from, to);
        SalesObjective objective = SalesObjective.<SalesObjective>find(
                "employee = ?1 and objectiveType = ?2 and periodFrom <= ?3 and periodTo >= ?3 order by versionNo desc",
                employee, ObjectiveType.INDIVIDUAL_QUANTITATIVE, from).firstResult();
        BigDecimal target = objective == null ? null : objective.targetValue;
        boolean achieved = target != null && target.signum() > 0 && weighted.compareTo(target) >= 0;
        boolean absent = absence.compareTo(MONTHLY_ABSENCE_DAYS) >= 0;
        BigDecimal rate = target == null || target.signum() == 0 ? BigDecimal.ZERO
                : weighted.divide(target, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal amount = achieved && !absent ? plan.instalmentAmount : BigDecimal.ZERO;

        VariableResult result = new VariableResult();
        result.period = period;
        result.employee = employee;
        result.plan = plan;
        result.component = VariableComponent.MONTHLY_INDIVIDUAL;
        result.achievementRate = rate;
        result.amount = amount;
        result.status = VariableResultStatus.CALCULATED;
        result.explanation = "{\"weighted\":" + weighted.toPlainString() + ",\"absenceDays\":"
                + absence.toPlainString() + ",\"target\":" + (target == null ? "null" : target.toPlainString())
                + ",\"paid\":" + (achieved && !absent) + "}";
        result.persist();
    }

    /**
     * Validates a calculated result (FDV-13).
     *
     * @param resultId the result id
     * @param userId   the validating user
     * @return the validated result
     */
    @Transactional
    public VariableResult validate(Long resultId, String userId) {
        VariableResult result = VariableResult.findById(resultId);
        if (result == null) {
            throw new ResourceNotFoundException("Résultat inconnu : " + resultId);
        }
        if (result.status != VariableResultStatus.CALCULATED) {
            throw new ValidationException("Seul un résultat calculé peut être validé");
        }
        result.status = VariableResultStatus.VALIDATED;
        result.validatedBy = userId;
        result.validatedAt = clock.now();
        return result;
    }

    /**
     * Records a manager adjustment: a bonus granted or a correction, validated at once with its reason
     * (FDV-13, hypothesis H-53).
     *
     * @param entityCode        the entity code
     * @param periodMonth       the period month
     * @param employeeExternalId the employee's external id
     * @param amount            the adjustment amount
     * @param reason            the adjustment reason
     * @param userId            the validating manager
     * @return the adjustment result
     */
    @Transactional
    public VariableResult adjust(String entityCode, LocalDate periodMonth, String employeeExternalId,
                                 BigDecimal amount, String reason, String userId) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, periodMonth);
        Employee employee = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity,
                employeeExternalId).firstResult();
        if (employee == null) {
            throw new ResourceNotFoundException("Collaborateur inconnu : " + employeeExternalId);
        }
        VariableResult result = new VariableResult();
        result.period = period;
        result.employee = employee;
        result.component = VariableComponent.MANUAL_ADJUSTMENT;
        result.amount = amount;
        result.achievementRate = BigDecimal.ZERO;
        result.adjustmentReason = reason;
        result.status = VariableResultStatus.VALIDATED;
        result.validatedBy = userId;
        result.validatedAt = clock.now();
        result.persist();
        return result;
    }

    /**
     * Creates or updates a variable plan of a post (FDV-04): its annual envelope, its monthly
     * instalment, its parameters (criteria, scales and absence rules as JSON) and its validity. A plan
     * is versioned by its validity dates, without status (section 8.14); the natural key is the entity,
     * the post, the component and the start of validity, so editing a dated plan replaces it in place
     * while a new start of validity opens a new version.
     *
     * @param entityCode the entity code
     * @param input      the plan fields
     * @return the saved plan
     */
    @Transactional
    public VariablePlan savePlan(String entityCode, PlanInput input) {
        LegalEntity entity = entity(entityCode);
        if (input.jobCode() == null || input.component() == null) {
            throw new ValidationException("Poste et composante obligatoires");
        }
        if (input.validFrom() == null) {
            throw new ValidationException("Date de début obligatoire");
        }
        if (input.envelopeAmount() == null) {
            throw new ValidationException("Enveloppe obligatoire");
        }
        validateJson(input.parameters());
        VariablePlan plan = VariablePlan.<VariablePlan>find(
                "entity = ?1 and jobCode = ?2 and component = ?3 and validFrom = ?4",
                entity, input.jobCode(), input.component(), input.validFrom()).firstResult();
        if (plan == null) {
            plan = new VariablePlan();
            plan.entity = entity;
            plan.jobCode = input.jobCode();
            plan.component = input.component();
            plan.validFrom = input.validFrom();
            plan.persist();
        }
        plan.envelopeAmount = input.envelopeAmount();
        plan.instalmentAmount = input.instalmentAmount();
        // The JSON column holds valid JSON or null — a blank payload is stored as none.
        plan.parameters = input.parameters() == null || input.parameters().isBlank() ? null : input.parameters();
        plan.validTo = input.validTo();
        return plan;
    }

    /**
     * Validates that a parameters payload, when present, is well-formed JSON.
     *
     * @param parameters the parameters payload, possibly null or blank
     */
    private void validateJson(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return;
        }
        try {
            mapper.readTree(parameters);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Paramètres JSON invalides : " + e.getOriginalMessage());
        }
    }

    /**
     * The editable fields of a variable plan (FDV-04).
     *
     * @param jobCode          the post
     * @param component        the variable component
     * @param envelopeAmount   the annual envelope
     * @param instalmentAmount the monthly instalment, or null
     * @param parameters       the criteria/scales/absence rules (JSON), or null
     * @param validFrom        the validity start (part of the natural key)
     * @param validTo          the validity end, or null
     */
    public record PlanInput(JobCode jobCode, VariableComponent component, BigDecimal envelopeAmount,
                            BigDecimal instalmentAmount, String parameters, LocalDate validFrom, LocalDate validTo) {
    }

    /**
     * Creates a collaborator managed at the screen (FDV-01), keyed by entity and external id, in
     * addition to the bulk SI-RH import. Refuses a duplicate external id.
     *
     * @param entityCode the entity code
     * @param input      the collaborator fields
     * @return the created employee
     */
    @Transactional
    public Employee createCollaborator(String entityCode, CollaboratorInput input) {
        LegalEntity entity = entity(entityCode);
        if (input.externalId() == null || input.externalId().isBlank()) {
            throw new ValidationException("Matricule obligatoire");
        }
        if (Employee.find("entity = ?1 and externalId = ?2", entity, input.externalId()).firstResult() != null) {
            throw new ValidationException("Collaborateur déjà présent : " + input.externalId());
        }
        Employee employee = new Employee();
        employee.entity = entity;
        employee.externalId = input.externalId();
        apply(employee, entity, input);
        employee.persist();
        return employee;
    }

    /**
     * Updates a collaborator's post, team, region, manager and dates (FDV-01).
     *
     * @param entityCode the entity code
     * @param externalId the collaborator's external id
     * @param input      the new collaborator fields
     * @return the updated employee
     */
    @Transactional
    public Employee updateCollaborator(String entityCode, String externalId, CollaboratorInput input) {
        LegalEntity entity = entity(entityCode);
        Employee employee = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity, externalId)
                .firstResult();
        if (employee == null) {
            throw new ResourceNotFoundException("Collaborateur inconnu : " + externalId);
        }
        apply(employee, entity, input);
        return employee;
    }

    /**
     * Applies the editable collaborator fields to an employee, resolving the optional manager.
     *
     * @param employee the employee to fill
     * @param entity   the entity, used to resolve the manager
     * @param input    the collaborator fields
     */
    private void apply(Employee employee, LegalEntity entity, CollaboratorInput input) {
        if (input.jobCode() == null) {
            throw new ValidationException("Poste obligatoire");
        }
        if (input.validFrom() == null) {
            throw new ValidationException("Date de début obligatoire");
        }
        employee.lastName = input.lastName();
        employee.firstName = input.firstName();
        employee.jobCode = input.jobCode();
        employee.team = input.team();
        employee.region = input.region();
        employee.manager = resolveManager(entity, input.managerExternalId());
        employee.validFrom = input.validFrom();
        employee.validTo = input.validTo();
    }

    /**
     * Resolves a manager by external id within the entity; an absent or blank reference means none.
     *
     * @param entity            the entity
     * @param managerExternalId the manager's external id, possibly null or blank
     * @return the manager employee, or null when none is referenced
     */
    private Employee resolveManager(LegalEntity entity, String managerExternalId) {
        if (managerExternalId == null || managerExternalId.isBlank()) {
            return null;
        }
        Employee manager = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity, managerExternalId)
                .firstResult();
        if (manager == null) {
            throw new ResourceNotFoundException("Responsable inconnu : " + managerExternalId);
        }
        return manager;
    }

    /**
     * Creates an objective (FDV-03), individual (an employee is given) or collective (none), by post
     * and period, versioned append-only: a new objective for the same key takes the next version, the
     * calculation reading the latest. Qualitative and quantitative types are both accepted.
     *
     * @param entityCode the entity code
     * @param input      the objective fields
     * @return the created objective
     */
    @Transactional
    public SalesObjective createObjective(String entityCode, ObjectiveInput input) {
        LegalEntity entity = entity(entityCode);
        if (input.objectiveType() == null) {
            throw new ValidationException("Type d'objectif obligatoire");
        }
        if (input.periodFrom() == null || input.periodTo() == null) {
            throw new ValidationException("Période obligatoire");
        }
        if (input.periodFrom().isAfter(input.periodTo())) {
            throw new ValidationException("La fin de période précède le début");
        }
        if (input.targetValue() == null) {
            throw new ValidationException("Valeur cible obligatoire");
        }
        Employee employee = resolveEmployee(entity, input.employeeExternalId());
        SalesObjective objective = new SalesObjective();
        objective.entity = entity;
        objective.employee = employee;
        objective.jobCode = input.jobCode();
        objective.objectiveType = input.objectiveType();
        objective.periodFrom = input.periodFrom();
        objective.periodTo = input.periodTo();
        objective.targetValue = input.targetValue();
        objective.versionNo = nextObjectiveVersion(entity, input, employee);
        objective.persist();
        return objective;
    }

    /**
     * Resolves an employee by external id within an entity; a null or blank id means a collective
     * objective (no employee).
     *
     * @param entity     the entity
     * @param externalId the employee's external id, possibly null or blank
     * @return the employee, or null for a collective objective
     */
    private Employee resolveEmployee(LegalEntity entity, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        Employee employee = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity, externalId)
                .firstResult();
        if (employee == null) {
            throw new ResourceNotFoundException("Collaborateur inconnu : " + externalId);
        }
        return employee;
    }

    /**
     * Computes the next version number for an objective's natural key (entity, type, post, period and
     * target employee), so versions accumulate without overwriting.
     *
     * @param entity   the entity
     * @param input    the objective fields
     * @param employee the resolved target employee, or null for a collective objective
     * @return the next version number (1 for the first)
     */
    private int nextObjectiveVersion(LegalEntity entity, ObjectiveInput input, Employee employee) {
        int max = 0;
        for (SalesObjective existing : SalesObjective.<SalesObjective>list(
                "entity = ?1 and objectiveType = ?2 and jobCode = ?3 and periodFrom = ?4 and periodTo = ?5",
                entity, input.objectiveType(), input.jobCode(), input.periodFrom(), input.periodTo())) {
            String existingTarget = existing.employee == null ? null : existing.employee.externalId;
            String target = employee == null ? null : employee.externalId;
            if (java.util.Objects.equals(existingTarget, target)) {
                max = Math.max(max, existing.versionNo == null ? 0 : existing.versionNo);
            }
        }
        return max + 1;
    }

    /**
     * The editable fields of an objective (FDV-03).
     *
     * @param employeeExternalId the target employee, or null for a collective objective
     * @param jobCode            the post the objective applies to
     * @param objectiveType      the objective type (individual/collective quantitative, qualitative)
     * @param periodFrom         the period start
     * @param periodTo           the period end
     * @param targetValue        the target value
     */
    public record ObjectiveInput(String employeeExternalId, JobCode jobCode, ObjectiveType objectiveType,
                                 LocalDate periodFrom, LocalDate periodTo, BigDecimal targetValue) {
    }

    /**
     * The editable fields of a managed collaborator (FDV-01).
     *
     * @param externalId        the external id (natural key)
     * @param lastName          the last name
     * @param firstName         the first name
     * @param jobCode           the post
     * @param team              the team
     * @param region            the region
     * @param managerExternalId the manager's external id, or null
     * @param validFrom         the validity start
     * @param validTo           the validity end, or null
     */
    public record CollaboratorInput(String externalId, String lastName, String firstName, JobCode jobCode,
                                    String team, String region, String managerExternalId, LocalDate validFrom,
                                    LocalDate validTo) {
    }

    /**
     * Produces the monthly pay export of the validated variables (FDV-14, OUT-06), marks them exported
     * and journals the file. The file-name month is the computed period; {@code pay_period} is the
     * following month.
     *
     * @param entityCode  the entity code
     * @param periodMonth the computed period month
     * @param userId      the acting user (Finance controller)
     * @return the pay export (file name and CSV content)
     */
    @Transactional
    public PayExport payExport(String entityCode, LocalDate periodMonth, String userId) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, periodMonth);
        List<VariableResult> results = VariableResult.list("period = ?1 and status = ?2", period,
                VariableResultStatus.VALIDATED);
        String payPeriod = periodMonth.plusMonths(1).getYear() + "-"
                + String.format("%02d", periodMonth.plusMonths(1).getMonthValue());
        StringBuilder csv = new StringBuilder(
                "external_id;job_code;pay_period;component;amount;adjustment_reason;validated_by;validated_at\n");
        BigDecimal total = BigDecimal.ZERO;
        for (VariableResult result : results) {
            csv.append(result.employee.externalId).append(';')
                    .append(result.employee.jobCode).append(';')
                    .append(payPeriod).append(';')
                    .append(componentLabel(result.component)).append(';')
                    .append(result.amount.toPlainString()).append(';')
                    .append(result.adjustmentReason == null ? "" : result.adjustmentReason).append(';')
                    .append(result.validatedBy).append(';')
                    .append(result.validatedAt).append('\n');
            total = total.add(result.amount);
            result.status = VariableResultStatus.EXPORTED;
        }
        String fileName = entity.code + "_VAR_" + periodMonth.getYear()
                + String.format("%02d", periodMonth.getMonthValue()) + ".csv";
        fr.alptis.remuneration.domain.ExportBatch batch = new fr.alptis.remuneration.domain.ExportBatch();
        batch.entity = entity;
        batch.flowCode = "OUT-06";
        batch.fileName = fileName;
        batch.fileHash = io.quarkus.runtime.util.HashUtil.sha256(csv.toString());
        batch.generatedAt = clock.now();
        batch.generatedBy = userId;
        batch.linesWritten = results.size();
        batch.totalAmount = total;
        batch.status = fr.alptis.remuneration.domain.ExportBatchStatus.GENERATED;
        batch.persist();
        return new PayExport(fileName, csv.toString(), results.size());
    }

    /** The French pay labels of the variable components (OUT-06). */
    private static final Map<VariableComponent, String> COMPONENT_LABELS = Map.of(
            VariableComponent.MONTHLY_INDIVIDUAL, "Mensuel individuel",
            VariableComponent.QUARTERLY_QUALITATIVE, "Qualitatif",
            VariableComponent.ANNUAL_COLLECTIVE, "Collectif",
            VariableComponent.ANNUAL_ADJUSTMENT, "Régularisation",
            VariableComponent.MANUAL_ADJUSTMENT, "Ajustement");

    /**
     * The French pay label of a variable component (OUT-06).
     *
     * @param component the component
     * @return the label
     */
    private String componentLabel(VariableComponent component) {
        return COMPONENT_LABELS.get(component);
    }

    /**
     * The result of a pay export.
     *
     * @param fileName the file name
     * @param content  the CSV content
     * @param lines    the number of lines
     */
    public record PayExport(String fileName, String content, int lines) {
    }

    /**
     * The active employees of an entity for a month.
     *
     * @param entity      the entity
     * @param periodMonth the month
     * @return the active employees
     */
    private List<Employee> activeEmployees(LegalEntity entity, LocalDate periodMonth) {
        return Employee.list("entity = ?1 and validFrom <= ?2 and (validTo is null or validTo >= ?2)",
                entity, periodMonth);
    }

    /**
     * The absence days of an employee over a window.
     *
     * @param employee the employee
     * @param from     the window start
     * @param to       the window end
     * @return the total absence days
     */
    private BigDecimal absenceDays(Employee employee, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (EmployeeMeasure measure : EmployeeMeasure.<EmployeeMeasure>list(
                "employee = ?1 and measureType = ?2 and periodFrom >= ?3 and periodFrom <= ?4",
                employee, MeasureType.ABSENCE, from, to)) {
            total = total.add(measure.value);
        }
        return total;
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
     * Looks up a period by entity and month.
     *
     * @param entity      the entity
     * @param periodMonth the month
     * @return the period
     */
    private Period period(LegalEntity entity, LocalDate periodMonth) {
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue");
        }
        return period;
    }
}
