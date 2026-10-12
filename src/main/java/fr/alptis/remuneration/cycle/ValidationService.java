package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.EntityParameter;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business validation of a period (WKF-02, section 7.2 step 7): it presents the period's totals, the
 * gap with the previous month and whether adjustments are present, and requires a second validator
 * when a configurable rule is met (total over a threshold, gap over a threshold, or any adjustment).
 * Validation itself is the {@code CONTROLLED → BUSINESS_VALIDATED} transition, driven here after the
 * required levels are gathered.
 */
@ApplicationScoped
public class ValidationService {

    /** Entity parameter key for the second-level total threshold. */
    static final String TOTAL_THRESHOLD_KEY = "validation.second.total";

    /** Entity parameter key for the second-level gap threshold (fraction). */
    static final String GAP_THRESHOLD_KEY = "validation.second.gap";

    /** Default total above which a second level is required. */
    private static final BigDecimal DEFAULT_TOTAL = new BigDecimal("100000");

    /** Default month-on-month gap (20%) above which a second level is required. */
    private static final BigDecimal DEFAULT_GAP = new BigDecimal("0.20");

    private final PeriodService periods;
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param periods the period cycle service
     * @param clock   the application date/time provider
     */
    @Inject
    public ValidationService(PeriodService periods, DateTimeProvider clock) {
        this.periods = periods;
        this.clock = clock;
    }

    /**
     * Returns the validation summary of a period (WKF-02).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the validation summary
     */
    @Transactional
    public ValidationSummary summary(String entityCode, LocalDate month) {
        LegalEntity entity = requireEntity(entityCode);
        Period period = requirePeriod(entity, month);
        return summaryOf(entity, period);
    }

    /**
     * Gives a validation level on a period (WKF-02). When a second level is required, the first call
     * records the first validator and leaves the period controlled; a second call by a different
     * validator moves it to business-validated. When no second level is required, a single call moves
     * it. A second validation by the same user is refused.
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param userId     the validating user
     * @param roles      the user's roles
     * @return the refreshed summary
     */
    @Transactional
    public ValidationSummary validate(String entityCode, LocalDate month, String userId, Set<Role> roles) {
        LegalEntity entity = requireEntity(entityCode);
        Period period = requirePeriod(entity, month);
        boolean second = secondLevelRequired(entity, period);
        if (second && period.firstLevelValidatedBy == null) {
            period.firstLevelValidatedBy = userId;
            period.firstLevelValidatedAt = clock.now();
            return summaryOf(entity, period);
        }
        if (second && userId.equals(period.firstLevelValidatedBy)) {
            throw new ValidationException("Le second niveau doit être validé par un autre utilisateur.");
        }
        periods.transition(entityCode, month, PeriodState.BUSINESS_VALIDATED, roles, userId, null);
        return summaryOf(entity, period);
    }

    /**
     * Builds the summary of a period.
     *
     * @param entity the entity
     * @param period the period
     * @return the summary
     */
    private ValidationSummary summaryOf(LegalEntity entity, Period period) {
        BigDecimal currentTotal = totalOf(period);
        Period previous = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity,
                period.periodMonth.minusMonths(1)).firstResult();
        BigDecimal previousTotal = previous == null ? BigDecimal.ZERO : totalOf(previous);
        long adjustmentCount = Adjustment.count("period = ?1", period);
        return new ValidationSummary(period.periodMonth, period.state.name(), currentTotal, previousTotal,
                gap(currentTotal, previousTotal), adjustmentCount, secondLevelRequired(entity, period),
                period.firstLevelValidatedBy, byNature(period));
    }

    /**
     * Sums the commission lines of a period.
     *
     * @param period the period
     * @return the total
     */
    private static BigDecimal totalOf(Period period) {
        return CommissionLine.<CommissionLine>list("period = ?1", period).stream()
                .map(l -> l.amount == null ? BigDecimal.ZERO : l.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The absolute month-on-month gap as a fraction of the previous total (0 when the previous total
     * is nil and the current one too, 1 when the previous is nil but the current is not).
     *
     * @param current  the current total
     * @param previous the previous total
     * @return the gap fraction
     */
    private static BigDecimal gap(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        return current.subtract(previous).abs().divide(previous.abs(), 4, RoundingMode.HALF_UP);
    }

    /**
     * Tells whether a second validation level is required for a period (WKF-02).
     *
     * @param entity the entity
     * @param period the period
     * @return {@code true} when the total, the gap or an adjustment triggers a second level
     */
    private boolean secondLevelRequired(LegalEntity entity, Period period) {
        BigDecimal current = totalOf(period);
        Period previous = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity,
                period.periodMonth.minusMonths(1)).firstResult();
        BigDecimal previousTotal = previous == null ? BigDecimal.ZERO : totalOf(previous);
        boolean overTotal = current.abs().compareTo(threshold(entity, TOTAL_THRESHOLD_KEY, DEFAULT_TOTAL)) >= 0;
        boolean overGap = gap(current, previousTotal).compareTo(threshold(entity, GAP_THRESHOLD_KEY, DEFAULT_GAP)) >= 0;
        boolean hasAdjustments = Adjustment.count("period = ?1", period) > 0;
        return overTotal || overGap || hasAdjustments;
    }

    /**
     * Reads a decimal entity parameter, or the default when absent.
     *
     * @param entity       the entity
     * @param key          the parameter key
     * @param defaultValue the default
     * @return the parameter value
     */
    private static BigDecimal threshold(LegalEntity entity, String key, BigDecimal defaultValue) {
        EntityParameter parameter = EntityParameter.find("entity = ?1 and paramKey = ?2", entity, key).firstResult();
        return parameter == null ? defaultValue : new BigDecimal(parameter.paramValue);
    }

    /**
     * Sums a period's commission lines by nature.
     *
     * @param period the period
     * @return the per-nature totals
     */
    private static List<NatureTotal> byNature(Period period) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("period = ?1 order by nature", period)) {
            BigDecimal amount = line.amount == null ? BigDecimal.ZERO : line.amount;
            totals.merge(line.nature.name(), amount, BigDecimal::add);
        }
        List<NatureTotal> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            rows.add(new NatureTotal(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    /**
     * Finds the entity or fails.
     *
     * @param entityCode the entity code
     * @return the entity
     */
    private static LegalEntity requireEntity(String entityCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        return entity;
    }

    /**
     * Finds the period or fails.
     *
     * @param entity the entity
     * @param month  the month
     * @return the period
     */
    private static Period requirePeriod(LegalEntity entity, LocalDate month) {
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, month).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + month);
        }
        return period;
    }

    /**
     * The business-validation summary of a period (WKF-02).
     *
     * @param month                the period month
     * @param state                the period state code
     * @param currentTotal         the period's commission total
     * @param previousTotal        the previous month's total
     * @param gap                  the month-on-month gap fraction
     * @param adjustmentCount      the number of adjustments on the period
     * @param secondLevelRequired  whether a second validation level is required
     * @param firstLevelValidatedBy the first-level validator, or null
     * @param byNature             the totals by line nature
     */
    public record ValidationSummary(LocalDate month, String state, BigDecimal currentTotal, BigDecimal previousTotal,
                                    BigDecimal gap, long adjustmentCount, boolean secondLevelRequired,
                                    String firstLevelValidatedBy, List<NatureTotal> byNature) {
    }

    /**
     * A per-nature total of a period.
     *
     * @param nature the line nature code
     * @param total  the total amount
     */
    public record NatureTotal(String nature, BigDecimal total) {
    }
}
