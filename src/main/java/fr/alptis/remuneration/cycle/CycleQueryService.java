package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.PeriodTransition;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
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
        // Reste à traiter (section 7.3): held events are listed without blocking the period, pending
        // events are the entity's backlog not yet calculated.
        long heldEvents = BusinessEvent.count("entity = ?1 and processingStatus = ?2", period.entity,
                ProcessingStatus.ON_HOLD);
        long pendingEvents = BusinessEvent.count("entity = ?1 and processingStatus = ?2", period.entity,
                ProcessingStatus.PENDING);
        return new PeriodDetail(summary(period), transitions, lines, openAnomalies, blocking, heldEvents,
                pendingEvents, calendar(period.state), lots(period));
    }

    /**
     * Builds a period's payment lots (section 7.1): one row per batch of the period, with its order
     * count and total.
     *
     * @param period the period
     * @return the lot views, by batch type
     */
    private static List<LotView> lots(Period period) {
        List<LotView> lots = new ArrayList<>();
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("period = ?1 order by batchType", period)) {
            long orders = PaymentOrder.count("batch = ?1", batch);
            lots.add(new LotView(batch.batchReference, batch.batchType.name(), batch.status.name(), orders,
                    batch.totalAmount));
        }
        return lots;
    }

    /**
     * Builds the target calendar of a period (section 7.3, default milestones of hypothesis H-55): each
     * milestone is reached once the period's state has passed the state that milestone targets.
     *
     * @param state the period's current state
     * @return the calendar milestones, in order
     */
    private static List<CalendarMilestone> calendar(PeriodState state) {
        List<CalendarMilestone> milestones = new ArrayList<>();
        milestones.add(milestone("Jour ouvré 1", "Gel des données, calcul prévisionnel et réel",
                PeriodState.CALCULATED, state));
        milestones.add(milestone("Jour ouvré 2", "Contrôles automatiques et manuels",
                PeriodState.CONTROLLED, state));
        milestones.add(milestone("Jour ouvré 3", "Validation métier, préparation du paiement",
                PeriodState.PAYMENT_PREPARED, state));
        milestones.add(milestone("Jour ouvré 4", "Contrôle Finance et émission du virement",
                PeriodState.FINANCE_VALIDATED, state));
        milestones.add(milestone("Jours ouvrés 5 à 7", "Exécution des virements", PeriodState.PAID, state));
        milestones.add(milestone("Lendemain du paiement", "Publication des bordereaux", PeriodState.PAID, state));
        milestones.add(milestone("Dernier jour ouvré", "Clôture et extraction comptable",
                PeriodState.CLOSED, state));
        return milestones;
    }

    /**
     * Builds one calendar milestone, marking it reached when the current state is at or past its target.
     *
     * @param businessDay the business day label
     * @param label       the milestone label
     * @param target      the state the milestone targets
     * @param current     the period's current state
     * @return the milestone
     */
    private static CalendarMilestone milestone(String businessDay, String label, PeriodState target,
                                               PeriodState current) {
        return new CalendarMilestone(businessDay, label, current.ordinal() >= target.ordinal());
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
        // Keep the current (highest) version of each code; earlier versions are history (CTL-04).
        Map<String, ControlDefinition> current = new LinkedHashMap<>();
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 order by code, versionNo", entity)) {
            ControlDefinition kept = current.get(control.code);
            if (kept == null || control.version() > kept.version()) {
                current.put(control.code, control);
            }
        }
        List<CatalogEntry> entries = new ArrayList<>();
        for (ControlDefinition control : current.values()) {
            entries.add(toCatalogEntry(control));
        }
        return entries;
    }

    /**
     * Returns the version history of one control, newest version first (CTL-04).
     *
     * @param entityCode  the entity code
     * @param controlCode the control code
     * @return the control versions
     */
    public List<CatalogEntry> controlVersions(String entityCode, String controlCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        List<CatalogEntry> versions = new ArrayList<>();
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 and code = ?2 order by versionNo desc", entity, controlCode)) {
            versions.add(toCatalogEntry(control));
        }
        return versions;
    }

    /**
     * Maps a control definition to its catalogue entry.
     *
     * @param control the control
     * @return the catalogue entry
     */
    private static CatalogEntry toCatalogEntry(ControlDefinition control) {
        return new CatalogEntry(control.code, control.label, control.family, control.severity,
                Boolean.TRUE.equals(control.active), control.version(), control.parameters);
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
                    adjustment.reason, adjustment.attachmentRef,
                    adjustment.correctedLine == null ? null : adjustment.correctedLine.getId(),
                    adjustment.status, adjustment.getCreatedBy(), adjustment.approvedBy));
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
                               long blocking, long heldEvents, long pendingEvents,
                               List<CalendarMilestone> calendar, List<LotView> lots) {
    }

    /**
     * One milestone of a period's target calendar (section 7.3).
     *
     * @param businessDay the business day label of month M+1
     * @param milestone   the milestone label
     * @param reached     whether the period has passed this milestone
     */
    public record CalendarMilestone(String businessDay, String milestone, boolean reached) {
    }

    /**
     * One payment lot of a period (section 7.1).
     *
     * @param reference   the batch reference
     * @param batchType   the batch type code
     * @param status      the batch status code
     * @param orderCount  the number of payment orders in the lot
     * @param totalAmount the lot total
     */
    public record LotView(String reference, String batchType, String status, long orderCount,
                          BigDecimal totalAmount) {
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
                               boolean active, int versionNo, String parameters) {
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
                                 String reason, String attachmentRef, Long correctedLineId,
                                 fr.alptis.remuneration.domain.AdjustmentStatus status, String submitter,
                                 String approver) {
    }
}
