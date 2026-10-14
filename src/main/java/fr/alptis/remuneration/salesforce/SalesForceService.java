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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sales-force variable calculation (section 8.14, FDV). It computes the monthly individual variable
 * (FDV-05): the production of the month, weighted by market (FDV-11), is compared to the individual
 * objective; when the objective is met and the absence stays under the monthly threshold, the post's
 * instalment is paid, otherwise nothing. A manual adjustment (FDV-13) and the pay export (FDV-14) are
 * also here. The annual individual regularisation (FDV-06) and the quarterly qualitative variable
 * (FDV-07) are computed too; the amounts, criteria, scales and absence thresholds the specification
 * leaves to the commercial-department workshop are driven by the plan's parameters (section 8.14 —
 * "ce sont des paramètres"), the simplest choice recorded in {@code docs/DECISIONS.md}.
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
     * Runs the annual individual regularization of January N+1 (FDV-06), launched by the business
     * administrator: for each post with a monthly-individual plan, the annual right is the envelope
     * multiplied by the annual achievement rate (weighted production over the year against the
     * objective, capped at 100 %); the regularization is that right minus the monthly instalments
     * already paid. A month not paid for absence reduces the envelope by the post's instalment (section
     * 8.14 "Limites"). A negative gap is left to the manager's decision (H-53). The results are booked in
     * the January N+1 period.
     *
     * @param entityCode the entity code
     * @param year       the year being regularized
     * @return the number of regularization results written
     */
    @Transactional
    public int runAnnualRegularization(String entityCode, int year) {
        LegalEntity entity = entity(entityCode);
        Period january = period(entity, LocalDate.of(year + 1, 1, 1));
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        VariableResult.delete("period = ?1 and component = ?2 and status = ?3", january,
                VariableComponent.ANNUAL_ADJUSTMENT, VariableResultStatus.CALCULATED);
        int count = 0;
        for (Employee employee : activeEmployees(entity, yearStart)) {
            VariablePlan plan = VariablePlan.<VariablePlan>find(
                    "entity = ?1 and jobCode = ?2 and component = ?3 and validFrom <= ?4"
                            + " and (validTo is null or validTo >= ?4)",
                    entity, employee.jobCode, VariableComponent.MONTHLY_INDIVIDUAL, yearStart).firstResult();
            if (plan == null) {
                continue;
            }
            computeAnnualRegularization(january, employee, plan, year, yearStart, yearEnd);
            count++;
        }
        return count;
    }

    /**
     * Computes and persists one employee's annual regularization result (FDV-06).
     *
     * @param january   the January N+1 period the result is booked in
     * @param employee  the employee
     * @param plan      the monthly-individual plan of the post
     * @param year      the regularized year
     * @param yearStart the first day of the year
     * @param yearEnd   the last day of the year
     */
    private void computeAnnualRegularization(Period january, Employee employee, VariablePlan plan, int year,
                                             LocalDate yearStart, LocalDate yearEnd) {
        BigDecimal weighted = weightedProduction(employee, yearStart, yearEnd);
        BigDecimal objective = annualObjective(employee, yearStart);
        BigDecimal rate = objective.signum() == 0 ? BigDecimal.ZERO
                : weighted.divide(objective, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal instalment = plan.instalmentAmount == null ? BigDecimal.ZERO : plan.instalmentAmount;
        int absentMonths = absentMonths(employee, year);
        BigDecimal envelope = plan.envelopeAmount
                .subtract(instalment.multiply(BigDecimal.valueOf(absentMonths))).max(BigDecimal.ZERO);
        BigDecimal annualRight = envelope.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyPaid = monthlyPaid(employee, year);
        BigDecimal regularization = annualRight.subtract(monthlyPaid);

        VariableResult result = new VariableResult();
        result.period = january;
        result.employee = employee;
        result.plan = plan;
        result.component = VariableComponent.ANNUAL_ADJUSTMENT;
        result.achievementRate = rate;
        result.amount = regularization;
        result.status = VariableResultStatus.CALCULATED;
        result.explanation = "{\"annualRate\":" + rate.toPlainString() + ",\"envelope\":"
                + envelope.toPlainString() + ",\"annualRight\":" + annualRight.toPlainString() + ",\"monthlyPaid\":"
                + monthlyPaid.toPlainString() + ",\"absentMonths\":" + absentMonths + "}";
        result.persist();
    }

    /**
     * The employee's annual individual objective target in force at the year's start, or zero.
     *
     * @param employee  the employee
     * @param yearStart the first day of the year
     * @return the target value, or zero
     */
    private BigDecimal annualObjective(Employee employee, LocalDate yearStart) {
        SalesObjective objective = SalesObjective.<SalesObjective>find(
                "employee = ?1 and objectiveType = ?2 and periodFrom <= ?3 and periodTo >= ?3 order by versionNo desc",
                employee, ObjectiveType.INDIVIDUAL_QUANTITATIVE, yearStart).firstResult();
        return objective == null ? BigDecimal.ZERO : objective.targetValue;
    }

    /**
     * The sum of the employee's monthly individual results paid over the year.
     *
     * @param employee the employee
     * @param year     the year
     * @return the total paid
     */
    private BigDecimal monthlyPaid(Employee employee, int year) {
        BigDecimal total = BigDecimal.ZERO;
        for (VariableResult result : VariableResult.<VariableResult>list(
                "employee = ?1 and component = ?2 and period.periodMonth >= ?3 and period.periodMonth <= ?4",
                employee, VariableComponent.MONTHLY_INDIVIDUAL, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 1))) {
            total = total.add(result.amount);
        }
        return total;
    }

    /**
     * The number of months of the year in which the employee's absence reached the monthly threshold.
     *
     * @param employee the employee
     * @param year     the year
     * @return the count of absence-blocked months
     */
    private int absentMonths(Employee employee, int year) {
        int months = 0;
        for (int m = 1; m <= 12; m++) {
            LocalDate first = LocalDate.of(year, m, 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            if (absenceDays(employee, first, last).compareTo(MONTHLY_ABSENCE_DAYS) >= 0) {
                months++;
            }
        }
        return months;
    }

    /**
     * Runs the annual collective quantitative variable (FDV-08), launched by the business administrator:
     * each post's collective envelope is paid when the entity's collective annual objective is met,
     * prorated by the months the employee worked (one absent month removes one twelfth). It is settled a
     * first time in January N+1 and regularized in June N+1; a later settlement writes only the delta
     * over what the earlier settlement of the same year already paid, so re-running regularizes rather
     * than duplicates. The settled year is the settlement month's year minus one. Idempotent: the
     * settlement period's non-validated collective results are dropped first. The collective objective is
     * the entity-wide {@code COLLECTIVE_QUANTITATIVE} objective (no employee); each envelope is a plan
     * parameter (section 8.14 — "ce sont des paramètres", DEC 2026-10-14).
     *
     * @param entityCode      the entity code
     * @param settlementMonth the month the collective variable is settled in (January or June N+1)
     * @return the number of collective results written
     */
    @Transactional
    public int runAnnualCollective(String entityCode, LocalDate settlementMonth) {
        LegalEntity entity = entity(entityCode);
        Period settlement = period(entity, settlementMonth);
        int year = settlementMonth.getYear() - 1;
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        VariableResult.delete("period = ?1 and component = ?2 and status = ?3", settlement,
                VariableComponent.ANNUAL_COLLECTIVE, VariableResultStatus.CALCULATED);
        BigDecimal objective = collectiveObjective(entity, yearStart);
        BigDecimal production = collectiveProduction(entity, yearStart, yearEnd);
        BigDecimal rate = objective.signum() == 0 ? BigDecimal.ZERO
                : production.divide(objective, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        int count = 0;
        for (Employee employee : activeEmployees(entity, yearStart)) {
            VariablePlan plan = planOf(entity, employee, VariableComponent.ANNUAL_COLLECTIVE, yearStart);
            if (plan == null) {
                continue;
            }
            computeAnnualCollective(settlement, employee, plan, year, rate);
            count++;
        }
        return count;
    }

    /**
     * Computes and persists one employee's annual collective result (FDV-08): the post envelope times the
     * collective achievement rate times the worked-months proration, minus what the same year's earlier
     * settlement already paid.
     *
     * @param settlement the settlement period the result is booked in
     * @param employee   the employee
     * @param plan       the annual-collective plan of the post
     * @param year       the settled year
     * @param rate       the collective achievement rate (capped at 100 %)
     */
    private void computeAnnualCollective(Period settlement, Employee employee, VariablePlan plan, int year,
                                         BigDecimal rate) {
        int absentMonths = absentMonths(employee, year);
        BigDecimal proration = BigDecimal.valueOf(12L - absentMonths)
                .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        BigDecimal right = plan.envelopeAmount.multiply(rate).multiply(proration).setScale(2, RoundingMode.HALF_UP);
        BigDecimal alreadyPaid = collectivePaid(employee, settlement.periodMonth);
        BigDecimal amount = right.subtract(alreadyPaid);

        VariableResult result = new VariableResult();
        result.period = settlement;
        result.employee = employee;
        result.plan = plan;
        result.component = VariableComponent.ANNUAL_COLLECTIVE;
        result.achievementRate = rate;
        result.amount = amount;
        result.status = VariableResultStatus.CALCULATED;
        result.explanation = "{\"collectiveRate\":" + rate.toPlainString() + ",\"envelope\":"
                + plan.envelopeAmount.toPlainString() + ",\"proration\":" + proration.toPlainString()
                + ",\"collectiveRight\":" + right.toPlainString() + ",\"alreadyPaid\":" + alreadyPaid.toPlainString()
                + ",\"absentMonths\":" + absentMonths + "}";
        result.persist();
    }

    /**
     * The entity's collective annual objective target in force at the year's start, or zero. The
     * collective objective carries no employee (it applies to the whole entity).
     *
     * @param entity    the entity
     * @param yearStart the first day of the year
     * @return the target value, or zero
     */
    private BigDecimal collectiveObjective(LegalEntity entity, LocalDate yearStart) {
        SalesObjective objective = SalesObjective.<SalesObjective>find(
                "entity = ?1 and employee is null and objectiveType = ?2 and periodFrom <= ?3 and periodTo >= ?3"
                        + " order by versionNo desc",
                entity, ObjectiveType.COLLECTIVE_QUANTITATIVE, yearStart).firstResult();
        return objective == null ? BigDecimal.ZERO : objective.targetValue;
    }

    /**
     * The entity's collective weighted production over a window: the sum of the weighted production of
     * every employee active at the window start.
     *
     * @param entity the entity
     * @param from   the window start
     * @param to     the window end
     * @return the collective weighted production
     */
    private BigDecimal collectiveProduction(LegalEntity entity, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (Employee employee : activeEmployees(entity, from)) {
            total = total.add(weightedProduction(employee, from, to));
        }
        return total;
    }

    /**
     * The sum of the employee's collective results already settled earlier in the settlement year, so a
     * June regularization pays only the delta over the January settlement.
     *
     * @param employee        the employee
     * @param settlementMonth the current settlement month
     * @return the collective amount already paid this settlement year before this month
     */
    private BigDecimal collectivePaid(Employee employee, LocalDate settlementMonth) {
        BigDecimal total = BigDecimal.ZERO;
        LocalDate yearStart = settlementMonth.withDayOfYear(1);
        for (VariableResult result : VariableResult.<VariableResult>list(
                "employee = ?1 and component = ?2 and period.periodMonth >= ?3 and period.periodMonth < ?4",
                employee, VariableComponent.ANNUAL_COLLECTIVE, yearStart, settlementMonth)) {
            total = total.add(result.amount);
        }
        return total;
    }

    /**
     * Runs the annual variable of animators and managers (FDV-09), launched by the business administrator
     * and booked in the given settlement month (January N+1): for each post with an annual-manager plan,
     * a production-growth tier amount (the plan's {@code growthTiers} map of growth percentage to amount,
     * the production growth being the weighted production's excess over the plan's {@code growthBaseline})
     * plus a qualitative-criteria tier amount (the plan's {@code criteriaTiers} map of met-criteria count
     * to amount). Both scales and the baseline are plan parameters (section 8.14 — "ce sont des
     * paramètres", DEC 2026-10-14). Idempotent: the period's non-validated manager results are dropped.
     *
     * @param entityCode      the entity code
     * @param settlementMonth the month the manager variable is booked in (January N+1)
     * @return the number of manager results written
     */
    @Transactional
    public int runManagerVariable(String entityCode, LocalDate settlementMonth) {
        LegalEntity entity = entity(entityCode);
        Period settlement = period(entity, settlementMonth);
        int year = settlementMonth.getYear() - 1;
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        VariableResult.delete("period = ?1 and component = ?2 and status = ?3", settlement,
                VariableComponent.ANNUAL_MANAGER, VariableResultStatus.CALCULATED);
        int count = 0;
        for (Employee employee : activeEmployees(entity, yearStart)) {
            VariablePlan plan = planOf(entity, employee, VariableComponent.ANNUAL_MANAGER, yearStart);
            if (plan == null) {
                continue;
            }
            computeManagerVariable(settlement, employee, plan, yearStart, yearEnd);
            count++;
        }
        return count;
    }

    /**
     * Computes and persists one animator/manager result (FDV-09): the growth-tier amount plus the
     * criteria-tier amount.
     *
     * @param settlement the settlement period the result is booked in
     * @param employee   the employee
     * @param plan       the annual-manager plan of the post
     * @param yearStart  the first day of the measured year
     * @param yearEnd    the last day of the measured year
     */
    private void computeManagerVariable(Period settlement, Employee employee, VariablePlan plan, LocalDate yearStart,
                                        LocalDate yearEnd) {
        com.fasterxml.jackson.databind.JsonNode params = readParameters(mapper, plan.parameters);
        BigDecimal weighted = weightedProduction(employee, yearStart, yearEnd);
        BigDecimal baseline = decimalParam(params, "growthBaseline");
        BigDecimal growthPercent = baseline.signum() == 0 ? BigDecimal.ZERO
                : weighted.subtract(baseline).divide(baseline, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
        BigDecimal growthAmount = tierAmount(params.get("growthTiers"), growthPercent);
        BigDecimal threshold = qualitativeThreshold(employee, yearStart);
        int metCount = managerCriteriaMet(employee, params.get("criteria"), threshold, yearStart, yearEnd);
        BigDecimal criteriaAmount = tierAmount(params.get("criteriaTiers"), BigDecimal.valueOf(metCount));
        BigDecimal amount = growthAmount.add(criteriaAmount).setScale(2, RoundingMode.HALF_UP);

        VariableResult result = new VariableResult();
        result.period = settlement;
        result.employee = employee;
        result.plan = plan;
        result.component = VariableComponent.ANNUAL_MANAGER;
        result.achievementRate = growthPercent.movePointLeft(2);
        result.amount = amount;
        result.status = VariableResultStatus.CALCULATED;
        result.explanation = "{\"growthPercent\":" + growthPercent.toPlainString() + ",\"growthAmount\":"
                + growthAmount.toPlainString() + ",\"criteriaMet\":" + metCount + ",\"criteriaAmount\":"
                + criteriaAmount.toPlainString() + "}";
        result.persist();
    }

    /**
     * The amount of the highest tier whose numeric key does not exceed the value, or zero when no tier is
     * reached or the map is absent. A tier map pairs a numeric key (a growth percentage or a met-criteria
     * count) with the amount earned once the key is reached.
     *
     * @param tiers the tier map node, possibly null
     * @param value the value to place against the tiers
     * @return the reached tier's amount, or zero
     */
    private static BigDecimal tierAmount(com.fasterxml.jackson.databind.JsonNode tiers, BigDecimal value) {
        if (tiers == null || !tiers.isObject()) {
            return BigDecimal.ZERO;
        }
        BigDecimal bestKey = null;
        BigDecimal amount = BigDecimal.ZERO;
        for (Iterator<String> names = tiers.fieldNames(); names.hasNext();) {
            String key = names.next();
            BigDecimal threshold = new BigDecimal(key);
            if (threshold.compareTo(value) <= 0 && (bestKey == null || threshold.compareTo(bestKey) > 0)) {
                bestKey = threshold;
                amount = new BigDecimal(tiers.get(key).asText());
            }
        }
        return amount;
    }

    /**
     * The number of qualitative criteria an animator/manager met over the year: a criterion is met when
     * its summed activity reaches the manager's qualitative objective threshold. The criteria are the
     * plan's {@code criteria} list when given, otherwise the three standard quarterly criteria.
     *
     * @param employee  the employee
     * @param criteria  the plan's criteria list node, possibly null
     * @param threshold the qualitative objective threshold
     * @param from      the year start
     * @param to        the year end
     * @return the count of met criteria
     */
    private int managerCriteriaMet(Employee employee, com.fasterxml.jackson.databind.JsonNode criteria,
                                   BigDecimal threshold, LocalDate from, LocalDate to) {
        List<String> dimensions = QUALITATIVE_CRITERIA;
        if (criteria != null && criteria.isArray()) {
            dimensions = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node : criteria) {
                dimensions.add(node.asText());
            }
        }
        int met = 0;
        for (String dimension : dimensions) {
            if (threshold.signum() > 0 && activityValue(employee, dimension, from, to).compareTo(threshold) >= 0) {
                met++;
            }
        }
        return met;
    }

    /** The three qualitative criteria of the quarterly variable (FDV-07), as measure dimensions. */
    private static final List<String> QUALITATIVE_CRITERIA = List.of("productivite", "efficacite", "developpement");

    /** The development criterion, which carries the "new producers" complement (FDV-07). */
    private static final String DEVELOPMENT_CRITERION = "developpement";

    /**
     * Computes the quarterly qualitative variable of every active employee of an entity for the quarter
     * ending at a month (FDV-07), launched by the business administrator. For each post with a
     * quarterly-qualitative plan, a configured amount is paid per criterion met over the quarter
     * (productivity, efficiency, new-producer development), plus a complement when the development
     * criterion exceeds its objective by the configured overshoot; nothing is paid from the configured
     * quarterly absence threshold. A criterion is met when its summed activity reaches the post's
     * qualitative objective. Idempotent: the period's non-validated quarterly-qualitative results are
     * dropped first. The per-criterion amount, the complement, the overshoot and the absence threshold
     * are plan parameters (section 8.14 — "ce sont des paramètres", DEC 2026-10-13).
     *
     * @param entityCode     the entity code
     * @param quarterEndMonth the last month of the quarter being paid
     * @return the number of results written
     */
    @Transactional
    public int runQuarterlyQualitative(String entityCode, LocalDate quarterEndMonth) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, quarterEndMonth);
        LocalDate from = quarterEndMonth.minusMonths(2).withDayOfMonth(1);
        LocalDate to = quarterEndMonth.withDayOfMonth(quarterEndMonth.lengthOfMonth());
        VariableResult.delete("period = ?1 and component = ?2 and status = ?3", period,
                VariableComponent.QUARTERLY_QUALITATIVE, VariableResultStatus.CALCULATED);
        int count = 0;
        for (Employee employee : activeEmployees(entity, quarterEndMonth)) {
            VariablePlan plan = planOf(entity, employee, VariableComponent.QUARTERLY_QUALITATIVE, quarterEndMonth);
            if (plan == null) {
                continue;
            }
            computeQuarterlyQualitative(period, employee, plan, from, to);
            count++;
        }
        return count;
    }

    /**
     * Computes and persists one employee's quarterly qualitative result (FDV-07).
     *
     * @param period   the quarter-end period the result is booked in
     * @param employee the employee
     * @param plan     the quarterly-qualitative plan of the post
     * @param from     the quarter start
     * @param to       the quarter end
     */
    private void computeQuarterlyQualitative(Period period, Employee employee, VariablePlan plan, LocalDate from,
                                             LocalDate to) {
        com.fasterxml.jackson.databind.JsonNode params = readParameters(mapper, plan.parameters);
        BigDecimal criterionAmount = decimalParam(params, "criterionAmount");
        BigDecimal complement = decimalParam(params, "developmentComplement");
        BigDecimal overshoot = decimalParam(params, "developmentOvershoot");
        BigDecimal absenceBlocking = decimalParam(params, "absenceDaysBlocking");
        BigDecimal threshold = qualitativeThreshold(employee, from);

        int metCount = 0;
        boolean complementDue = false;
        for (String criterion : QUALITATIVE_CRITERIA) {
            BigDecimal value = activityValue(employee, criterion, from, to);
            if (threshold.signum() > 0 && value.compareTo(threshold) >= 0) {
                metCount++;
            }
            if (criterion.equals(DEVELOPMENT_CRITERION) && threshold.signum() > 0
                    && value.compareTo(threshold.add(overshoot)) >= 0) {
                complementDue = true;
            }
        }
        BigDecimal absence = absenceDays(employee, from, to);
        boolean blocked = absenceBlocking.signum() > 0 && absence.compareTo(absenceBlocking) >= 0;
        BigDecimal amount = blocked ? BigDecimal.ZERO
                : criterionAmount.multiply(BigDecimal.valueOf(metCount))
                        .add(complementDue ? complement : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = BigDecimal.valueOf(metCount)
                .divide(BigDecimal.valueOf(QUALITATIVE_CRITERIA.size()), 6, RoundingMode.HALF_UP);

        VariableResult result = new VariableResult();
        result.period = period;
        result.employee = employee;
        result.plan = plan;
        result.component = VariableComponent.QUARTERLY_QUALITATIVE;
        result.achievementRate = rate;
        result.amount = amount;
        result.status = VariableResultStatus.CALCULATED;
        result.explanation = "{\"criteriaMet\":" + metCount + ",\"complement\":" + complementDue
                + ",\"absenceDays\":" + absence.toPlainString() + ",\"blocked\":" + blocked + "}";
        result.persist();
    }

    /**
     * The per-criterion threshold of an employee's qualitative objective in force at a date, or zero.
     *
     * @param employee the employee
     * @param date     the date the objective must be in force at
     * @return the threshold, or zero when no qualitative objective applies
     */
    private BigDecimal qualitativeThreshold(Employee employee, LocalDate date) {
        SalesObjective objective = SalesObjective.<SalesObjective>find(
                "employee = ?1 and objectiveType = ?2 and periodFrom <= ?3 and periodTo >= ?3 order by versionNo desc",
                employee, ObjectiveType.QUALITATIVE, date).firstResult();
        return objective == null ? BigDecimal.ZERO : objective.targetValue;
    }

    /**
     * The summed activity of an employee for a criterion dimension over a window.
     *
     * @param employee  the employee
     * @param criterion the activity dimension
     * @param from      the window start
     * @param to        the window end
     * @return the summed activity value
     */
    private BigDecimal activityValue(Employee employee, String criterion, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (EmployeeMeasure measure : EmployeeMeasure.<EmployeeMeasure>list(
                "employee = ?1 and measureType = ?2 and dimension = ?3 and periodFrom >= ?4 and periodFrom <= ?5",
                employee, MeasureType.ACTIVITY, criterion, from, to)) {
            total = total.add(measure.value);
        }
        return total;
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
     * Simulates a commercial's monthly individual variable (FDV-16) without persisting anything: given
     * a hypothetical weighted production (or, when none is supplied, the production actually measured),
     * it tells whether the objective would be met, the achievement rate, the instalment that would be
     * paid and a threshold alert. The simulation shares the real computation's rule.
     *
     * @param entityCode   the entity code
     * @param externalId   the commercial's external id
     * @param periodMonth  the month to simulate
     * @param hypothetical a hypothetical weighted production, or null to use the measured one
     * @return the simulation result
     */
    public SimulationResult simulateMonthly(String entityCode, String externalId, LocalDate periodMonth,
                                            BigDecimal hypothetical) {
        LegalEntity entity = entity(entityCode);
        Employee employee = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity, externalId)
                .firstResult();
        if (employee == null) {
            throw new ResourceNotFoundException("Collaborateur inconnu : " + externalId);
        }
        LocalDate from = periodMonth.withDayOfMonth(1);
        LocalDate to = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());
        BigDecimal weighted = hypothetical != null ? hypothetical : weightedProduction(employee, from, to);
        VariablePlan plan = VariablePlan.<VariablePlan>find(
                "entity = ?1 and jobCode = ?2 and component = ?3 and validFrom <= ?4"
                        + " and (validTo is null or validTo >= ?4)",
                entity, employee.jobCode, VariableComponent.MONTHLY_INDIVIDUAL, periodMonth).firstResult();
        if (plan == null) {
            return new SimulationResult(weighted, null, BigDecimal.ZERO, BigDecimal.ZERO, false,
                    "Aucun plan de variable pour ce poste.");
        }
        SalesObjective objective = SalesObjective.<SalesObjective>find(
                "employee = ?1 and objectiveType = ?2 and periodFrom <= ?3 and periodTo >= ?3 order by versionNo desc",
                employee, ObjectiveType.INDIVIDUAL_QUANTITATIVE, from).firstResult();
        BigDecimal target = objective == null ? null : objective.targetValue;
        boolean achieved = target != null && target.signum() > 0 && weighted.compareTo(target) >= 0;
        BigDecimal rate = target == null || target.signum() == 0 ? BigDecimal.ZERO
                : weighted.divide(target, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal amount = achieved ? plan.instalmentAmount : BigDecimal.ZERO;
        String alert;
        if (target == null) {
            alert = "Aucun objectif défini pour ce mois.";
        } else if (achieved) {
            alert = "Objectif atteint : la mensualité de " + plan.instalmentAmount.toPlainString() + " € serait versée.";
        } else {
            alert = "Il manque " + target.subtract(weighted).toPlainString()
                    + " € de production pondérée pour atteindre l'objectif.";
        }
        return new SimulationResult(weighted, target, rate, amount, achieved, alert);
    }

    /**
     * The result of a variable simulation (FDV-16).
     *
     * @param weightedProduction the weighted production used
     * @param target             the objective target, or null when none
     * @param achievementRate    the achievement rate (0 to 1)
     * @param amount             the instalment that would be paid
     * @param achieved           whether the objective would be met
     * @param alert              the threshold alert message
     */
    public record SimulationResult(BigDecimal weightedProduction, BigDecimal target, BigDecimal achievementRate,
                                   BigDecimal amount, boolean achieved, String alert) {
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
            VariableComponent.ANNUAL_MANAGER, "Animateurs et responsables",
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
     * The plan of a post and component in force at a date, or null when none.
     *
     * @param entity    the entity
     * @param employee  the employee whose post selects the plan
     * @param component the variable component
     * @param date      the date the plan must be in force at
     * @return the plan, or null
     */
    private VariablePlan planOf(LegalEntity entity, Employee employee, VariableComponent component, LocalDate date) {
        return VariablePlan.<VariablePlan>find(
                "entity = ?1 and jobCode = ?2 and component = ?3 and validFrom <= ?4"
                        + " and (validTo is null or validTo >= ?4)",
                entity, employee.jobCode, component, date).firstResult();
    }

    /**
     * Parses a plan's parameters payload into a JSON node; a null or blank payload yields an empty
     * object. A malformed payload is rejected (it cannot occur through {@link #savePlan}, which
     * validates, but is guarded for direct writers).
     *
     * @param mapper     the JSON mapper
     * @param parameters the parameters payload, possibly null or blank
     * @return the parsed node, never null
     */
    static com.fasterxml.jackson.databind.JsonNode readParameters(
            com.fasterxml.jackson.databind.ObjectMapper mapper, String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(parameters);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Paramètres de plan illisibles : " + e.getOriginalMessage());
        }
    }

    /**
     * Reads a decimal parameter from a parsed parameters node, defaulting to zero when absent or null.
     *
     * @param params the parsed parameters node
     * @param field  the field name
     * @return the decimal value, or zero
     */
    static BigDecimal decimalParam(com.fasterxml.jackson.databind.JsonNode params, String field) {
        com.fasterxml.jackson.databind.JsonNode node = params.get(field);
        return node == null || node.isNull() ? BigDecimal.ZERO : new BigDecimal(node.asText());
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
