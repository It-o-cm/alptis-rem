package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.accounts.AccountService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Control service (section 8.6, CTL-02/03/05): runs the active controls of a period's entity,
 * recording one anomaly per offending object with its observed value and the definition's severity,
 * and tracing the decision an operator takes on each anomaly (fixed, accepted, deferred). A control
 * compares, it does not judge: each check only produces a result, the severity decides whether the
 * cycle is blocked.
 */
@ApplicationScoped
public class ControlService {

    private final AccountService accounts;
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param accounts the distributor-account service
     * @param clock    the application date/time provider
     */
    @Inject
    public ControlService(AccountService accounts, DateTimeProvider clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Runs the entity's active controls over a period, replacing the undecided results of a previous
     * run, and returns the number of anomalies found.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the number of anomalies recorded
     */
    @Transactional
    public int run(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + periodMonth);
        }
        ControlResult.delete("period = ?1 and decision = ?2", period, ControlDecision.OPEN);
        int count = 0;
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 and active = true", entity)) {
            count += switch (control.code) {
                case "DATA-PREMIUM" -> premiumCheck(entity, period, control);
                case "DATA-FUTURE" -> futureDateCheck(entity, period, control);
                case "CALC-NO-RULE" -> noRuleCheck(entity, period, control);
                case "CLAWBACK-EXCESS" -> clawbackCheck(period, control);
                case "ACCOUNT-DEBTOR" -> debtorCheck(entity, control, period);
                default -> 0;
            };
        }
        return count;
    }

    /**
     * Records a decision on an anomaly (CTL-05): its author, date and justification.
     *
     * @param resultId      the anomaly id
     * @param decision      the decision taken
     * @param userId        the deciding user
     * @param justification the justification
     * @return the decided anomaly
     */
    @Transactional
    public ControlResult decide(Long resultId, ControlDecision decision, String userId, String justification) {
        ControlResult result = ControlResult.findById(resultId);
        if (result == null) {
            throw new ResourceNotFoundException("Anomalie inconnue : " + resultId);
        }
        result.decision = decision;
        result.decidedBy = userId;
        result.decidedAt = clock.now();
        result.justification = justification;
        return result;
    }

    /**
     * Flags the period's events whose premium is null, zero or negative (data quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int premiumCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (BusinessEvent event : events(entity, period)) {
            if (event.basisAmount == null || event.basisAmount.signum() <= 0) {
                anomaly(period, control, "EVENT", event.getId(),
                        event.basisAmount == null ? "null" : event.basisAmount.toPlainString(), "> 0");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags the period's events dated in the future (data quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int futureDateCheck(LegalEntity entity, Period period, ControlDefinition control) {
        LocalDate today = clock.now().toLocalDate();
        int count = 0;
        for (BusinessEvent event : events(entity, period)) {
            if (event.eventDate != null && event.eventDate.isAfter(today)) {
                anomaly(period, control, "EVENT", event.getId(), event.eventDate.toString(), "<= " + today);
                count++;
            }
        }
        return count;
    }

    /**
     * Flags the period's events held for want of a rule (calculation, P8).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int noRuleCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                "entity = ?1 and eligiblePeriod = ?2 and processingStatus = ?3 and holdReason = ?4",
                entity, period, ProcessingStatus.ON_HOLD, HoldReason.NO_RULE)) {
            anomaly(period, control, "EVENT", event.getId(), "ON_HOLD", "NO_RULE");
            count++;
        }
        return count;
    }

    /**
     * Flags a coverage whose clawbacks of the period exceed its acquired commissions (reprises).
     *
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int clawbackCheck(Period period, ControlDefinition control) {
        Map<Long, BigDecimal> acquired = new HashMap<>();
        Map<Long, BigDecimal> clawed = new HashMap<>();
        Map<Long, Coverage> coverages = new HashMap<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("period = ?1 and coverage is not null", period)) {
            coverages.put(line.coverage.getId(), line.coverage);
            if (line.nature == LineNature.CLAWBACK) {
                clawed.merge(line.coverage.getId(), line.amount.abs(), BigDecimal::add);
            } else if (line.nature == LineNature.LINEAR || line.nature == LineNature.ACQUISITION
                    || line.nature == LineNature.SERVICING) {
                acquired.merge(line.coverage.getId(), line.amount, BigDecimal::add);
            }
        }
        int count = 0;
        for (Map.Entry<Long, BigDecimal> entry : clawed.entrySet()) {
            BigDecimal earned = acquired.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(earned) > 0) {
                anomaly(period, control, "COVERAGE", entry.getKey(), entry.getValue().toPlainString(),
                        "<= " + earned.toPlainString());
                count++;
            }
        }
        return count;
    }

    /**
     * Flags the entity's accounts with a debtor balance (accounts).
     *
     * @param entity  the entity
     * @param control the control definition
     * @param period  the period
     * @return the number of anomalies
     */
    private int debtorCheck(LegalEntity entity, ControlDefinition control, Period period) {
        int count = 0;
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            BigDecimal balance = accounts.balance(account);
            if (balance.signum() < 0) {
                anomaly(period, control, "ACCOUNT", account.getId(), balance.toPlainString(), ">= 0");
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the events eligible to a period.
     *
     * @param entity the entity
     * @param period the period
     * @return the events
     */
    private static java.util.List<BusinessEvent> events(LegalEntity entity, Period period) {
        return BusinessEvent.list("entity = ?1 and eligiblePeriod = ?2", entity, period);
    }

    /**
     * Records one anomaly with its observed and expected values.
     *
     * @param period     the period
     * @param control    the control definition
     * @param objectType the kind of object
     * @param objectId   the object id
     * @param observed   the observed value
     * @param expected   the expected value
     */
    private void anomaly(Period period, ControlDefinition control, String objectType, Long objectId, String observed,
                         String expected) {
        ControlResult result = new ControlResult();
        result.period = period;
        result.control = control;
        result.objectType = objectType;
        result.objectId = objectId;
        result.severity = control.severity == null ? ControlSeverity.WARNING : control.severity;
        result.observedValue = observed;
        result.expectedValue = expected;
        result.decision = ControlDecision.OPEN;
        result.setCreatedAt(clock.now());
        result.persist();
    }
}
