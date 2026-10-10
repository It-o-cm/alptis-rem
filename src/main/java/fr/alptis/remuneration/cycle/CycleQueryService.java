package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.PeriodTransition;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read model of the cycle screens (WKF-04, section 7): the advancement of the open periods of the
 * visible entities, and the detail of one period — its state, the transitions it has gone through,
 * and what is left to treat (lines, open anomalies, blocking anomalies).
 */
@ApplicationScoped
public class CycleQueryService {

    /** The states in which a period is still open (not closed). */
    private static final List<PeriodState> OPEN_STATES = List.of(PeriodState.OPEN, PeriodState.LOADED,
            PeriodState.CALCULATED, PeriodState.CONTROLLED, PeriodState.BUSINESS_VALIDATED,
            PeriodState.PAYMENT_PREPARED, PeriodState.FINANCE_VALIDATED, PeriodState.PAID);

    /**
     * Returns the advancement of the open periods of the visible entities (WKF-04).
     *
     * @param visibleCodes the entity codes the user may see, or null for all
     * @return the period summaries, newest month first
     */
    public List<PeriodSummary> openPeriods(List<String> visibleCodes) {
        List<PeriodSummary> summaries = new ArrayList<>();
        List<Period> periods = Period.list("state in ?1 order by periodMonth desc, entity.code", OPEN_STATES);
        for (Period period : periods) {
            if (visibleCodes == null || visibleCodes.contains(period.entity.code)) {
                summaries.add(summary(period));
            }
        }
        return summaries;
    }

    /**
     * Returns the detail of one entity's period.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the period detail
     */
    public PeriodDetail detail(String entityCode, LocalDate periodMonth) {
        Period period = period(entityCode, periodMonth);
        List<TransitionView> transitions = new ArrayList<>();
        for (PeriodTransition transition : PeriodTransition.<PeriodTransition>list(
                "period = ?1 order by occurredAt", period)) {
            transitions.add(new TransitionView(transition.fromState, transition.toState, transition.userId,
                    transition.reason));
        }
        long lines = CommissionLine.count("period = ?1", period);
        long openAnomalies = ControlResult.count("period = ?1 and decision = ?2", period, ControlDecision.OPEN);
        long blocking = ControlResult.count("period = ?1 and severity = ?2 and decision = ?3", period,
                ControlSeverity.BLOCKING, ControlDecision.OPEN);
        return new PeriodDetail(summary(period), transitions, lines, openAnomalies, blocking);
    }

    /**
     * Returns the anomalies of an entity's period, newest first (CTL-03).
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the anomaly views
     */
    public List<AnomalyView> anomalies(String entityCode, LocalDate periodMonth) {
        Period period = period(entityCode, periodMonth);
        List<AnomalyView> views = new ArrayList<>();
        for (ControlResult result : ControlResult.<ControlResult>list("period = ?1 order by id desc", period)) {
            views.add(new AnomalyView(result.getId(), result.control.code,
                    result.severity, result.objectType, result.objectId, result.observedValue, result.expectedValue,
                    result.decision, result.decidedBy, result.justification));
        }
        return views;
    }

    /**
     * Counts a period's anomalies by severity and by control family, for the controls chart. The
     * interface only displays these counts — it does not compute them.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the anomaly breakdown
     */
    public AnomalyBreakdown anomalyBreakdown(String entityCode, LocalDate periodMonth) {
        Period period = period(entityCode, periodMonth);
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        Map<String, Long> byFamily = new LinkedHashMap<>();
        for (ControlResult result : ControlResult.<ControlResult>list("period = ?1 order by id", period)) {
            bySeverity.merge(result.severity.name(), 1L, Long::sum);
            ControlFamily family = result.control.family;
            byFamily.merge(family == null ? "—" : family.name(), 1L, Long::sum);
        }
        return new AnomalyBreakdown(counts(bySeverity), counts(byFamily));
    }

    /**
     * Converts a count map to count rows in insertion order.
     *
     * @param map the map
     * @return the count rows
     */
    private static List<Count> counts(Map<String, Long> map) {
        List<Count> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            list.add(new Count(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    /**
     * Returns the control catalogue of an entity (CTL-01).
     *
     * @param entityCode the entity code
     * @return the control definitions
     */
    public List<CatalogEntry> catalog(String entityCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        List<CatalogEntry> entries = new ArrayList<>();
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 order by code", entity)) {
            entries.add(new CatalogEntry(control.code, control.label, control.family, control.severity,
                    Boolean.TRUE.equals(control.active)));
        }
        return entries;
    }

    /**
     * Returns the adjustments of an entity's period, newest first (REG-01).
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the adjustment views
     */
    public List<AdjustmentView> adjustments(String entityCode, LocalDate periodMonth) {
        Period period = period(entityCode, periodMonth);
        List<AdjustmentView> views = new ArrayList<>();
        for (fr.alptis.remuneration.domain.Adjustment adjustment
                : fr.alptis.remuneration.domain.Adjustment.<fr.alptis.remuneration.domain.Adjustment>list(
                "period = ?1 order by id desc", period)) {
            views.add(new AdjustmentView(adjustment.getId(), adjustment.adjustmentType, adjustment.amount,
                    adjustment.reason, adjustment.status, adjustment.getCreatedBy(), adjustment.approvedBy));
        }
        return views;
    }

    /**
     * Returns an entity's period, or fails when unknown.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the period
     */
    private static Period period(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = entity == null ? null
                : Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + entityCode + " " + periodMonth);
        }
        return period;
    }

    /**
     * Builds a period summary.
     *
     * @param period the period
     * @return the summary
     */
    private static PeriodSummary summary(Period period) {
        return new PeriodSummary(period.entity.code, period.entity.name, period.periodMonth, period.state);
    }

    /**
     * Advancement of one period.
     *
     * @param entityCode the entity code
     * @param entityName the entity name
     * @param month      the period month
     * @param state      the period state
     */
    public record PeriodSummary(String entityCode, String entityName, LocalDate month, PeriodState state) {
    }

    /**
     * One recorded transition of a period.
     *
     * @param fromState the previous state
     * @param toState   the new state
     * @param userId    the acting user
     * @param reason    the reason, possibly null
     */
    public record TransitionView(PeriodState fromState, PeriodState toState, String userId, String reason) {
    }

    /**
     * Detail of one period: its summary, its transitions and what is left to treat.
     *
     * @param period        the period summary
     * @param transitions   the transitions gone through
     * @param lines         the number of commission lines
     * @param openAnomalies the number of open anomalies
     * @param blocking      the number of open blocking anomalies
     */
    public record PeriodDetail(PeriodSummary period, List<TransitionView> transitions, long lines, long openAnomalies,
                               long blocking) {
    }

    /**
     * One anomaly of the control list.
     *
     * @param id            the anomaly id
     * @param controlCode   the control code, possibly null
     * @param severity      the severity
     * @param objectType    the kind of flagged object
     * @param objectId      the flagged object id
     * @param observedValue the observed value
     * @param expectedValue the expected value
     * @param decision      the decision taken
     * @param decidedBy     the deciding user, possibly null
     * @param justification the justification, possibly null
     */
    public record AnomalyView(Long id, String controlCode, ControlSeverity severity, String objectType, Long objectId,
                              String observedValue, String expectedValue, ControlDecision decision, String decidedBy,
                              String justification) {
    }

    /**
     * The anomaly counts of a period, by severity and by control family.
     *
     * @param bySeverity the counts by severity
     * @param byFamily   the counts by control family
     */
    public record AnomalyBreakdown(List<Count> bySeverity, List<Count> byFamily) {
    }

    /**
     * One labelled count.
     *
     * @param label the label
     * @param count the count
     */
    public record Count(String label, long count) {
    }

    /**
     * One control of the catalogue.
     *
     * @param code     the control code
     * @param label    the control label
     * @param family   the control family
     * @param severity the severity
     * @param active   whether the control is active
     */
    public record CatalogEntry(String code, String label, ControlFamily family, ControlSeverity severity,
                               boolean active) {
    }

    /**
     * One adjustment of the list.
     *
     * @param id        the adjustment id
     * @param type      the adjustment type
     * @param amount    the signed amount
     * @param reason    the reason
     * @param status    the status
     * @param submitter the submitting user
     * @param approver  the approving user, possibly null
     */
    public record AdjustmentView(Long id, fr.alptis.remuneration.domain.AdjustmentType type, java.math.BigDecimal amount,
                                 String reason, fr.alptis.remuneration.domain.AdjustmentStatus status, String submitter,
                                 String approver) {
    }
}
