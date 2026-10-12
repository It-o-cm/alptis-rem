package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlChecklistItem;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        LegalEntity entity = requireEntity(entityCode);
        Period period = requirePeriod(entity, periodMonth);
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
     * Returns the manual control checklist of a period (CTL-03): one item per active control, carrying
     * whether the operator has ticked it as reviewed and by whom.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the checklist items, by control code
     */
    @Transactional
    public List<ChecklistItemView> checklist(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = requireEntity(entityCode);
        Period period = requirePeriod(entity, periodMonth);
        List<ChecklistItemView> items = new ArrayList<>();
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 and active = true order by code", entity)) {
            ControlChecklistItem item = ControlChecklistItem.find("period = ?1 and controlCode = ?2", period,
                    control.code).firstResult();
            boolean checked = item != null && Boolean.TRUE.equals(item.checked);
            items.add(new ChecklistItemView(control.code, control.label, checked,
                    item == null ? null : item.checkedBy));
        }
        return items;
    }

    /**
     * Ticks or un-ticks a control in a period's checklist (CTL-03), recording who and when.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @param controlCode the control code
     * @param checked     the new ticked state
     * @param userId      the operator
     * @return the updated checklist item view
     */
    @Transactional
    public ChecklistItemView check(String entityCode, LocalDate periodMonth, String controlCode, boolean checked,
                                   String userId) {
        LegalEntity entity = requireEntity(entityCode);
        Period period = requirePeriod(entity, periodMonth);
        ControlDefinition control = ControlDefinition.<ControlDefinition>find("entity = ?1 and code = ?2", entity,
                controlCode).firstResult();
        if (control == null) {
            throw new ResourceNotFoundException("Contrôle inconnu : " + controlCode);
        }
        ControlChecklistItem item = ControlChecklistItem.find("period = ?1 and controlCode = ?2", period,
                controlCode).firstResult();
        if (item == null) {
            item = new ControlChecklistItem();
            item.entity = entity;
            item.period = period;
            item.controlCode = controlCode;
            item.setCreatedAt(clock.now());
            item.persist();
        }
        item.checked = checked;
        item.checkedBy = checked ? userId : null;
        item.checkedAt = checked ? clock.now() : null;
        return new ChecklistItemView(controlCode, control.label, checked, item.checkedBy);
    }

    /**
     * Edits a control by creating a new version (CTL-01, CTL-04): the current version is deactivated
     * and closed, and a new active version carries the edited severity, activation and parameters
     * (threshold, perimeter). The engine then runs the new version.
     *
     * @param entityCode  the entity code
     * @param code        the control code
     * @param severity    the new severity
     * @param active      the new activation
     * @param parameters  the new parameters JSON (threshold, perimeter), possibly null
     * @return the new current version as a catalogue entry
     */
    @Transactional
    public CycleQueryService.CatalogEntry editControl(String entityCode, String code, ControlSeverity severity,
                                                      boolean active, String parameters) {
        LegalEntity entity = requireEntity(entityCode);
        ControlDefinition current = null;
        for (ControlDefinition c : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 and code = ?2 order by versionNo", entity, code)) {
            if (current == null || c.version() > current.version()) {
                current = c;
            }
        }
        if (current == null) {
            throw new ResourceNotFoundException("Contrôle inconnu : " + code);
        }
        current.active = Boolean.FALSE;
        current.validTo = clock.today();
        ControlDefinition next = new ControlDefinition();
        next.entity = entity;
        next.code = code;
        next.label = current.label;
        next.family = current.family;
        next.severity = severity;
        next.active = active;
        next.parameters = parameters;
        next.versionNo = current.version() + 1;
        next.validFrom = clock.today();
        next.setCreatedAt(clock.now());
        next.persist();
        return new CycleQueryService.CatalogEntry(next.code, next.label, next.family, next.severity,
                Boolean.TRUE.equals(next.active), next.version(), next.parameters);
    }

    /**
     * Finds an entity or fails.
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
     * Finds an entity's period or fails.
     *
     * @param entity      the entity
     * @param periodMonth the period month
     * @return the period
     */
    private static Period requirePeriod(LegalEntity entity, LocalDate periodMonth) {
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + periodMonth);
        }
        return period;
    }

    /**
     * One control of a period's checklist.
     *
     * @param code      the control code
     * @param label     the control label
     * @param checked   whether it is ticked as reviewed
     * @param checkedBy who ticked it, or null
     */
    public record ChecklistItemView(String code, String label, boolean checked, String checkedBy) {
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
