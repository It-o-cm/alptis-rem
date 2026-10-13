package fr.alptis.remuneration.cycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlChecklistItem;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.EndReason;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.SignatureStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Control service (section 8.6, CTL-02/03/05): runs the active controls of a period's entity across
 * the ten delivered families — completeness, data quality, calculation, advance, clawback,
 * supplement, account, payment, accounting and external flow — recording one anomaly per offending
 * object with its observed value and the definition's severity, and tracing the decision an operator
 * takes on each anomaly (fixed, accepted, deferred). A control compares, it does not judge: each
 * check only produces a result, the severity decides whether the cycle is blocked. Thresholds and
 * perimeters are read from the control definition's JSON parameters (CTL-01), with safe defaults.
 */
@ApplicationScoped
public class ControlService {

    /** The abnormal premium threshold parameter (data quality); zero disables the upper bound. */
    private static final String MAX_PREMIUM = "maxPremium";
    /** The per-source volume deviation parameter (completeness); default 50 %. */
    private static final String DEVIATION = "deviation";
    /** The nature-total deviation parameter (calculation); default 30 %. */
    private static final String NATURE_DEVIATION = "natureDeviation";
    /** The per-distributor variation parameter (calculation); default 30 %. */
    private static final String VARIATION = "variation";
    /** The average-rate break parameter (calculation); default 20 %. */
    private static final String RATE_BREAK = "rateBreak";
    /** The top-distributor review size parameter (calculation); default 20. */
    private static final String TOP = "top";
    /** The receivable cap parameter (advance). */
    private static final String RECEIVABLE_CAP = "cap";
    /** The debit-age parameter in months (account); default 3. */
    private static final String DEBIT_MONTHS = "months";
    /** The early-resiliation window parameter in months (clawback); default 12. */
    private static final String EARLY_MONTHS = "earlyMonths";
    /** The expected inbound flow codes parameter (completeness). */
    private static final String FLOWS = "flows";

    /** The default expected inbound flows received every month. */
    private static final List<String> DEFAULT_FLOWS = List.of("coverages", "events");

    private final AccountService accounts;
    private final DateTimeProvider clock;
    private final ObjectMapper mapper;

    /**
     * Creates the service.
     *
     * @param accounts the distributor-account service
     * @param clock    the application date/time provider
     * @param mapper    the JSON mapper used to read control parameters
     */
    @Inject
    public ControlService(AccountService accounts, DateTimeProvider clock, ObjectMapper mapper) {
        this.accounts = accounts;
        this.clock = clock;
        this.mapper = mapper;
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
        // Drop the undecided anomalies of a previous run, but keep the engine's CAL-13
        // expected-commission records, which the external-flow control reads rather than produces.
        ControlResult.delete("period = ?1 and decision = ?2 and control.code <> ?3", period, ControlDecision.OPEN,
                "CAL-13");
        int count = 0;
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list(
                "entity = ?1 and active = true", entity)) {
            count += switch (control.code) {
                case "COMP-FLOWS" -> flowsCheck(entity, period, control);
                case "COMP-VOLUME" -> volumeCheck(entity, period, control);
                case "DATA-PREMIUM" -> premiumCheck(entity, period, control);
                case "DATA-FUTURE" -> futureDateCheck(entity, period, control);
                case "DATA-NO-DIST" -> noDistributorCheck(entity, period, control);
                case "DATA-DIST-INACTIVE" -> inactiveDistributorCheck(entity, period, control);
                case "DATA-IBAN" -> ibanCheck(entity, period, control);
                case "DATA-RATE-OFF-GRID" -> holdCheck(entity, period, control, HoldReason.RATE_OUT_OF_GRID);
                case "CALC-NO-RULE" -> holdCheck(entity, period, control, HoldReason.NO_RULE);
                case "CALC-RATE-OFF-SCALE" -> holdCheck(entity, period, control, HoldReason.OUT_OF_BOUNDS);
                case "CALC-NATURE-TOTAL" -> natureTotalCheck(entity, period, control);
                case "CALC-DIST-VARIATION" -> distributorVariationCheck(entity, period, control);
                case "CALC-AVG-RATE" -> averageRateCheck(entity, period, control);
                case "CALC-TOP-20" -> topDistributorsCheck(entity, period, control);
                case "ADV-NO-DISCOUNT" -> noDiscountCheck(entity, period, control);
                case "ADV-NO-MEMBER" -> agreementWithoutMemberCheck(entity, period, control);
                case "ADV-SETTLE-IN-PAY" -> settlementInPaymentCheck(entity, period, control);
                case "ADV-RECEIVABLE-CAP" -> receivableCapCheck(entity, period, control);
                case "CLAWBACK-EXCESS" -> clawbackCheck(period, control);
                case "CLAWBACK-MISSING" -> missingClawbackCheck(entity, period, control);
                case "SUPP-NO-AGREEMENT" -> supplementWithoutAgreementCheck(entity, period, control);
                case "SUPP-MISSING-INDICATOR" -> missingIndicatorCheck(entity, period, control);
                case "SUPP-QUALITY-THRESHOLD" -> qualityThresholdCheck(entity, period, control);
                case "ACCOUNT-DEBTOR" -> debtorCheck(entity, control, period);
                case "ACCOUNT-OLD-DEBIT" -> oldDebitCheck(entity, period, control);
                case "ACCOUNT-COMPENSATION" -> compensationCheck(entity, period, control);
                case "PAY-LOT-BALANCE" -> lotBalanceCheck(entity, period, control);
                case "PAY-ORDER-NO-STMT" -> orderWithoutStatementCheck(entity, period, control);
                case "PAY-PAYEE-INACTIVE" -> inactivePayeeCheck(entity, period, control);
                case "ACC-BALANCED" -> balancedEntriesCheck(entity, period, control);
                case "ACC-LINES-MATCH" -> entriesMatchLinesCheck(entity, period, control);
                case "EXT-EXTERNAL-MISMATCH" -> externalMismatchCheck(entity, period, control);
                case "EXT-INSURER-MISMATCH" -> insurerMismatchCheck(period, control);
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

    // ----------------------------------------------------------------------------------------------
    // Completeness
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags any expected inbound flow not received for the period's month (completeness).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int flowsCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (String flow : listParam(control, FLOWS, DEFAULT_FLOWS)) {
            if (!flowReceived(entity, period, flow)) {
                anomaly(period, control, "FLOW", null, flow, "reçu");
                count++;
            }
        }
        return count;
    }

    /**
     * Tells whether an inbound flow was received for the entity in the period's month.
     *
     * @param entity the entity
     * @param period the period
     * @param flow   the flow code
     * @return true when at least one import batch of that flow landed in the month
     */
    private boolean flowReceived(LegalEntity entity, Period period, String flow) {
        LocalDate month = period.periodMonth;
        for (ImportBatch batch : ImportBatch.<ImportBatch>list("entity = ?1 and flowCode = ?2", entity, flow)) {
            if (batch.receivedAt != null && batch.receivedAt.getYear() == month.getYear()
                    && batch.receivedAt.getMonthValue() == month.getMonthValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flags any source whose event count deviates from the previous month beyond the threshold
     * (completeness).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int volumeCheck(LegalEntity entity, Period period, ControlDefinition control) {
        Period previous = previousPeriod(entity, period);
        if (previous == null) {
            return 0;
        }
        BigDecimal threshold = decimalParam(control, DEVIATION, "0.50");
        Map<String, Long> current = eventCountBySource(entity, period);
        Map<String, Long> prior = eventCountBySource(entity, previous);
        int count = 0;
        for (String source : current.keySet()) {
            long now = current.get(source);
            long before = prior.getOrDefault(source, 0L);
            if (before > 0 && deviation(now, before).compareTo(threshold) > 0) {
                anomaly(period, control, "SOURCE", null, source + "=" + now, "~" + before);
                count++;
            }
        }
        return count;
    }

    /**
     * Counts the period's events by source system.
     *
     * @param entity the entity
     * @param period the period
     * @return the event count by source system
     */
    private static Map<String, Long> eventCountBySource(LegalEntity entity, Period period) {
        Map<String, Long> counts = new HashMap<>();
        for (BusinessEvent event : events(entity, period)) {
            counts.merge(event.sourceSystem == null ? "" : event.sourceSystem, 1L, Long::sum);
        }
        return counts;
    }

    // ----------------------------------------------------------------------------------------------
    // Data quality
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags the period's events whose premium is null, non-positive or abnormally high (data quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int premiumCheck(LegalEntity entity, Period period, ControlDefinition control) {
        BigDecimal max = decimalParam(control, MAX_PREMIUM, "0");
        int count = 0;
        for (BusinessEvent event : events(entity, period)) {
            if (event.basisAmount == null || event.basisAmount.signum() <= 0) {
                anomaly(period, control, "EVENT", event.getId(),
                        event.basisAmount == null ? "null" : event.basisAmount.toPlainString(), "> 0");
                count++;
            } else if (max.signum() > 0 && event.basisAmount.compareTo(max) > 0) {
                anomaly(period, control, "EVENT", event.getId(), event.basisAmount.toPlainString(),
                        "<= " + max.toPlainString());
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
     * Flags a coverage of the period that bears no distributor assignment covering the month (data
     * quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int noDistributorCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (Coverage coverage : periodCoverages(entity, period)) {
            if (activeAssignments(coverage, period.periodMonth).isEmpty()) {
                anomaly(period, control, "COVERAGE", coverage.getId(), "aucun distributeur", ">= 1");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a coverage assignment whose production code points to a distributor absent from the
     * reference base or not active in the entity (data quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int inactiveDistributorCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (Coverage coverage : periodCoverages(entity, period)) {
            for (CoverageAssignment assignment : activeAssignments(coverage, period.periodMonth)) {
                Distributor distributor = assignment.productionCode == null ? null
                        : assignment.productionCode.distributor;
                if (distributor == null || !isActive(entity, distributor, period.periodMonth)) {
                    anomaly(period, control, "COVERAGE", coverage.getId(),
                            distributor == null ? "inconnu" : "inactif", "actif");
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Flags an account that will be paid but bears no valid bank details for the period's month (data
     * quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int ibanCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            if (accounts.balance(account).signum() > 0 && !hasIban(entity, account, period.periodMonth)) {
                anomaly(period, control, "ACCOUNT", account.getId(), "IBAN manquant", "présent");
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Calculation
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags the period's events held for a given reason (calculation / data quality).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @param reason  the hold reason
     * @return the number of anomalies
     */
    private int holdCheck(LegalEntity entity, Period period, ControlDefinition control, HoldReason reason) {
        int count = 0;
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                "entity = ?1 and eligiblePeriod = ?2 and processingStatus = ?3 and holdReason = ?4",
                entity, period, ProcessingStatus.ON_HOLD, reason)) {
            anomaly(period, control, "EVENT", event.getId(), "ON_HOLD", reason.name());
            count++;
        }
        return count;
    }

    /**
     * Flags a line nature whose period total deviates from the previous month or the same month of the
     * previous year beyond the threshold (calculation).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int natureTotalCheck(LegalEntity entity, Period period, ControlDefinition control) {
        BigDecimal threshold = decimalParam(control, NATURE_DEVIATION, "0.30");
        Map<LineNature, BigDecimal> current = totalsByNature(period);
        Map<LineNature, BigDecimal> lastMonth = totalsByNature(previousPeriod(entity, period));
        Map<LineNature, BigDecimal> lastYear = totalsByNature(yearAgoPeriod(entity, period));
        int count = 0;
        for (Map.Entry<LineNature, BigDecimal> entry : current.entrySet()) {
            LineNature nature = entry.getKey();
            boolean offMonth = exceeds(entry.getValue(), lastMonth.get(nature), threshold);
            boolean offYear = exceeds(entry.getValue(), lastYear.get(nature), threshold);
            if (offMonth || offYear) {
                anomaly(period, control, "NATURE", null, nature.name() + "=" + entry.getValue().toPlainString(),
                        "~ M-1 / M-12");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a distributor whose period total varies from the previous month beyond the threshold
     * (calculation).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int distributorVariationCheck(LegalEntity entity, Period period, ControlDefinition control) {
        BigDecimal threshold = decimalParam(control, VARIATION, "0.30");
        Map<Long, BigDecimal> current = totalsByDistributor(period);
        Map<Long, BigDecimal> previous = totalsByDistributor(previousPeriod(entity, period));
        int count = 0;
        for (Map.Entry<Long, BigDecimal> entry : current.entrySet()) {
            BigDecimal before = previous.get(entry.getKey());
            if (exceeds(entry.getValue(), before, threshold)) {
                anomaly(period, control, "DISTRIBUTOR", entry.getKey(), entry.getValue().toPlainString(),
                        "~ " + before.toPlainString());
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a distributor whose average rate breaks with its previous month beyond the threshold
     * (calculation).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int averageRateCheck(LegalEntity entity, Period period, ControlDefinition control) {
        BigDecimal threshold = decimalParam(control, RATE_BREAK, "0.20");
        Map<Long, BigDecimal> current = averageRateByDistributor(period);
        Map<Long, BigDecimal> previous = averageRateByDistributor(previousPeriod(entity, period));
        int count = 0;
        for (Map.Entry<Long, BigDecimal> entry : current.entrySet()) {
            BigDecimal before = previous.get(entry.getKey());
            if (exceeds(entry.getValue(), before, threshold)) {
                anomaly(period, control, "DISTRIBUTOR", entry.getKey(), entry.getValue().toPlainString(),
                        "~ " + before.toPlainString());
                count++;
            }
        }
        return count;
    }

    /**
     * Lists the period's top distributors by total for review (calculation, information).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies (reviewed distributors)
     */
    private int topDistributorsCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int top = decimalParam(control, TOP, "20").intValue();
        List<Map.Entry<Long, BigDecimal>> ranked = new ArrayList<>(totalsByDistributor(period).entrySet());
        ranked.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        int count = 0;
        for (Map.Entry<Long, BigDecimal> entry : ranked) {
            if (count >= top) {
                break;
            }
            anomaly(period, control, "DISTRIBUTOR", entry.getKey(), entry.getValue().toPlainString(), "à revoir");
            count++;
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Advance
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags a distributor newly under an active advance agreement for whom no advance position was
     * computed (advance).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int noDiscountCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (AdvanceAgreement agreement : activeAgreements(entity)) {
            if (agreement.distributor != null && advancePositions(agreement).isEmpty()) {
                anomaly(period, control, "AGREEMENT", agreement.getId(), "aucun escompte", ">= 1");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags an active advance agreement whose distributor has no active membership in the entity
     * (advance).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int agreementWithoutMemberCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (AdvanceAgreement agreement : activeAgreements(entity)) {
            if (agreement.distributor == null || !isActive(entity, agreement.distributor, period.periodMonth)) {
                anomaly(period, control, "AGREEMENT", agreement.getId(), "sans adhésion", "adhérent actif");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags an advance settlement line that is attached to a payment order (advance): a settlement
     * offsets the account, it is never paid out.
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int settlementInPaymentCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period = ?2 and nature in ?3", entity, period,
                List.of(LineNature.ADVANCE, LineNature.ADVANCE_ADJUSTMENT))) {
            if (line.paymentOrder != null) {
                anomaly(period, control, "LINE", line.getId(), "dans un paiement", "hors paiement");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags an advance position whose outstanding receivable exceeds the cap (advance). Without a
     * configured cap the control does nothing.
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int receivableCapCheck(LegalEntity entity, Period period, ControlDefinition control) {
        BigDecimal cap = decimalParam(control, RECEIVABLE_CAP, "0");
        if (cap.signum() <= 0) {
            return 0;
        }
        int count = 0;
        for (AdvancePosition position : AdvancePosition.<AdvancePosition>listAll()) {
            if (position.coverage == null || position.coverage.contract == null
                    || !entity.equals(position.coverage.contract.entity)) {
                continue;
            }
            BigDecimal receivable = outstanding(position);
            if (receivable.compareTo(cap) > 0) {
                anomaly(period, control, "ADVANCE", position.getId(), receivable.toPlainString(),
                        "<= " + cap.toPlainString());
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Clawback
    // ----------------------------------------------------------------------------------------------

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
        for (CommissionLine line : CommissionLine.<CommissionLine>list("period = ?1 and coverage is not null",
                period)) {
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
     * Flags a coverage ended early (within the window) for which no clawback line was produced
     * (reprises).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int missingClawbackCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int earlyMonths = decimalParam(control, EARLY_MONTHS, "12").intValue();
        Set<Long> clawed = new HashSet<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period = ?2 and nature = ?3 and coverage is not null", entity, period,
                LineNature.CLAWBACK)) {
            clawed.add(line.coverage.getId());
        }
        int count = 0;
        for (Coverage coverage : periodCoverages(entity, period)) {
            if (isEarlyTermination(coverage, earlyMonths) && !clawed.contains(coverage.getId())) {
                anomaly(period, control, "COVERAGE", coverage.getId(), "résiliée sans reprise", "reprise");
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Supplement
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags a supplement line whose rule carries no signed agreement (surcommissions).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int supplementWithoutAgreementCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period = ?2 and nature in ?3", entity, period,
                List.of(LineNature.RCD, LineNature.RCP, LineNature.COMPLEMENT))) {
            Agreement agreement = line.ruleVersion == null ? null : line.ruleVersion.agreement;
            if (agreement == null || agreement.signatureStatus != SignatureStatus.SIGNED) {
                anomaly(period, control, "LINE", line.getId(), "accord non signé", "signé");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a distributor bearing supplement lines but no quality indicator for the period, a missing
     * condition indicator (surcommissions, H-48).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int missingIndicatorCheck(LegalEntity entity, Period period, ControlDefinition control) {
        Set<Long> flagged = new HashSet<>();
        int count = 0;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period = ?2 and nature in ?3", entity, period,
                List.of(LineNature.RCD, LineNature.RCP))) {
            Distributor distributor = line.originDistributor;
            if (distributor == null || flagged.contains(distributor.getId())) {
                continue;
            }
            if (!hasIndicator(entity, distributor, period.periodMonth)) {
                anomaly(period, control, "DISTRIBUTOR", distributor.getId(), "indicateur manquant", "présent");
                flagged.add(distributor.getId());
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a quality indicator crossing the amendment-termination threshold (surcommissions, section
     * 6.9): complaints or unpaid 5 %, not-pursued or new-business lapse 12 %, portfolio lapse 20 %.
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int qualityThresholdCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (QualityIndicator indicator : QualityIndicator.<QualityIndicator>list("entity = ?1", entity)) {
            BigDecimal limit = qualityLimit(indicator.indicatorType);
            if (limit != null && indicator.value != null && indicator.value.compareTo(limit) >= 0) {
                anomaly(period, control, "INDICATOR", indicator.getId(),
                        indicator.indicatorType.name() + "=" + indicator.value.toPlainString(),
                        "< " + limit.toPlainString());
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Account
    // ----------------------------------------------------------------------------------------------

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
     * Flags a debtor account whose debit has stood for longer than the configured number of months
     * (accounts).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int oldDebitCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int months = decimalParam(control, DEBIT_MONTHS, "3").intValue();
        LocalDate cutoff = period.periodMonth.minusMonths(months);
        int count = 0;
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            if (account.payeeType != PayeeType.DISTRIBUTOR) {
                continue;
            }
            Distributor distributor = Distributor.findById(account.payeeId);
            if (distributor == null || accounts.balance(account).signum() >= 0) {
                continue;
            }
            if (debtorSince(entity, distributor, cutoff)) {
                anomaly(period, control, "ACCOUNT", account.getId(), "débit > " + months + " mois",
                        "<= " + months + " mois");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a distributor holding at the same time a debtor account and a creditor account in the
     * entity, a compensation that was not performed (accounts, CPT-04).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int compensationCheck(LegalEntity entity, Period period, ControlDefinition control) {
        Map<Long, Boolean> debit = new HashMap<>();
        Map<Long, Boolean> credit = new HashMap<>();
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list("entity = ?1", entity)) {
            Long distributorId = owningDistributorId(account);
            if (distributorId == null) {
                continue;
            }
            int sign = accounts.balance(account).signum();
            if (sign < 0) {
                debit.put(distributorId, Boolean.TRUE);
            } else if (sign > 0) {
                credit.put(distributorId, Boolean.TRUE);
            }
        }
        int count = 0;
        for (Long distributorId : debit.keySet()) {
            if (credit.containsKey(distributorId)) {
                anomaly(period, control, "DISTRIBUTOR", distributorId, "compensation possible", "compensée");
                count++;
            }
        }
        return count;
    }

    /**
     * Resolves the distributor that owns an account: directly for a distributor account, through the
     * production code for a production-code account, null otherwise.
     *
     * @param account the account
     * @return the owning distributor id, or null
     */
    private static Long owningDistributorId(DistributorAccount account) {
        if (account.payeeId == null) {
            return null;
        }
        if (account.payeeType == PayeeType.DISTRIBUTOR) {
            return account.payeeId;
        }
        if (account.payeeType == PayeeType.PRODUCTION_CODE) {
            ProductionCode code = ProductionCode.findById(account.payeeId);
            return code == null || code.distributor == null ? null : code.distributor.getId();
        }
        return null;
    }

    // ----------------------------------------------------------------------------------------------
    // Payment
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags a payment batch whose total does not match the sum of its orders (payment, blocking).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int lotBalanceCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("entity = ?1 and period = ?2", entity, period)) {
            BigDecimal orders = BigDecimal.ZERO;
            for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch = ?1", batch)) {
                orders = orders.add(order.amount == null ? BigDecimal.ZERO : order.amount);
            }
            BigDecimal total = batch.totalAmount == null ? BigDecimal.ZERO : batch.totalAmount;
            if (total.compareTo(orders) != 0) {
                anomaly(period, control, "BATCH", batch.getId(), total.toPlainString(), orders.toPlainString());
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a payment order carrying no statement (payment).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int orderWithoutStatementCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("entity = ?1 and period = ?2", entity, period)) {
            for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch = ?1", batch)) {
                if (order.statement == null) {
                    anomaly(period, control, "ORDER", order.getId(), "sans bordereau", "bordereau");
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Flags a payment order whose payee is not active in the entity (payment).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int inactivePayeeCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("entity = ?1 and period = ?2", entity, period)) {
            for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch = ?1", batch)) {
                if (order.account != null && order.account.payeeType == PayeeType.DISTRIBUTOR
                        && !payeeActive(entity, order.account.payeeId, period.periodMonth)) {
                    anomaly(period, control, "ORDER", order.getId(), "bénéficiaire inactif", "actif");
                    count++;
                }
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Accounting
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags an unbalanced accounting entry: a missing debit or credit side, or a non-positive amount
     * (accounting, blocking).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int balancedEntriesCheck(LegalEntity entity, Period period, ControlDefinition control) {
        int count = 0;
        for (AccountingEntry entry : AccountingEntry.<AccountingEntry>list("entity = ?1 and period = ?2", entity,
                period)) {
            if (isBlank(entry.debitAccount) || isBlank(entry.creditAccount) || entry.amount == null
                    || entry.amount.signum() <= 0) {
                anomaly(period, control, "ENTRY", entry.getId(), "déséquilibrée", "équilibrée");
                count++;
            }
        }
        return count;
    }

    /**
     * Flags a mismatch between the accounting entries attached to commission lines and those lines
     * (accounting, blocking): per line, the sum of its earning/clawback/supplement entries must equal
     * the line amount.
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int entriesMatchLinesCheck(LegalEntity entity, Period period, ControlDefinition control) {
        Map<Long, BigDecimal> entryByLine = new HashMap<>();
        Map<Long, CommissionLine> lines = new HashMap<>();
        for (AccountingEntry entry : AccountingEntry.<AccountingEntry>list(
                "entity = ?1 and period = ?2 and commissionLine is not null and accountingEvent = ?3", entity,
                period, AccountingEvent.EARNING)) {
            entryByLine.merge(entry.commissionLine.getId(), entry.amount == null ? BigDecimal.ZERO : entry.amount,
                    BigDecimal::add);
            lines.put(entry.commissionLine.getId(), entry.commissionLine);
        }
        int count = 0;
        for (Map.Entry<Long, BigDecimal> entry : entryByLine.entrySet()) {
            BigDecimal expected = lines.get(entry.getKey()).amount.abs();
            if (entry.getValue().compareTo(expected) != 0) {
                anomaly(period, control, "LINE", entry.getKey(), entry.getValue().toPlainString(),
                        expected.toPlainString());
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // External flow
    // ----------------------------------------------------------------------------------------------

    /**
     * Flags an external commission line whose amount differs from its basis times its rate by more
     * than a cent (external flow, H-49).
     *
     * @param entity  the entity
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int externalMismatchCheck(LegalEntity entity, Period period, ControlDefinition control) {
        BigDecimal cent = new BigDecimal("0.01");
        int count = 0;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period = ?2 and nature = ?3", entity, period, LineNature.EXTERNAL)) {
            if (line.basisAmount == null || line.rate == null || line.amount == null) {
                continue;
            }
            BigDecimal expected = line.basisAmount.multiply(line.rate).setScale(2, RoundingMode.HALF_UP);
            if (line.amount.subtract(expected).abs().compareTo(cent) > 0) {
                anomaly(period, control, "LINE", line.getId(), line.amount.toPlainString(),
                        expected.toPlainString());
                count++;
            }
        }
        return count;
    }

    /**
     * Flags an insurer commission whose received amount differs from the expected one (external flow):
     * it re-surfaces the engine's CAL-13 expected/received comparison of the period.
     *
     * @param period  the period
     * @param control the control definition
     * @return the number of anomalies
     */
    private int insurerMismatchCheck(Period period, ControlDefinition control) {
        int count = 0;
        for (ControlResult expected : ControlResult.<ControlResult>list(
                "period = ?1 and control.code = ?2", period, "CAL-13")) {
            BigDecimal gap = new BigDecimal(blankToZero(expected.observedValue))
                    .subtract(new BigDecimal(blankToZero(expected.expectedValue)));
            if (gap.signum() != 0) {
                anomaly(period, control, "COVERAGE", expected.objectId, expected.observedValue,
                        expected.expectedValue);
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------
    // Shared helpers
    // ----------------------------------------------------------------------------------------------

    /**
     * Returns the events eligible to a period.
     *
     * @param entity the entity
     * @param period the period
     * @return the events
     */
    private static List<BusinessEvent> events(LegalEntity entity, Period period) {
        return BusinessEvent.list("entity = ?1 and eligiblePeriod = ?2", entity, period);
    }

    /**
     * Returns the distinct coverages carried by the period's events.
     *
     * @param entity the entity
     * @param period the period
     * @return the coverages
     */
    private static List<Coverage> periodCoverages(LegalEntity entity, Period period) {
        Map<Long, Coverage> coverages = new LinkedHashMap<>();
        for (BusinessEvent event : events(entity, period)) {
            if (event.coverage != null) {
                coverages.put(event.coverage.getId(), event.coverage);
            }
        }
        return new ArrayList<>(coverages.values());
    }

    /**
     * Returns the assignments of a coverage in force at a date.
     *
     * @param coverage the coverage
     * @param date     the date
     * @return the active assignments
     */
    private static List<CoverageAssignment> activeAssignments(Coverage coverage, LocalDate date) {
        List<CoverageAssignment> active = new ArrayList<>();
        for (CoverageAssignment assignment : CoverageAssignment.<CoverageAssignment>list("coverage = ?1", coverage)) {
            boolean started = assignment.validFrom == null || !assignment.validFrom.isAfter(date);
            boolean notEnded = assignment.validTo == null || !assignment.validTo.isBefore(date);
            if (started && notEnded) {
                active.add(assignment);
            }
        }
        return active;
    }

    /**
     * Tells whether a distributor is active in the entity at a date.
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @param date        the date
     * @return true when an active membership covers the date
     */
    private static boolean isActive(LegalEntity entity, Distributor distributor, LocalDate date) {
        for (DistributorEntity membership : DistributorEntity.<DistributorEntity>list(
                "entity = ?1 and distributor = ?2", entity, distributor)) {
            boolean started = membership.validFrom == null || !membership.validFrom.isAfter(date);
            boolean notEnded = membership.validTo == null || !membership.validTo.isBefore(date);
            if (started && notEnded && membership.status == PartnerStatus.ACTIVE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a payee distributor (by id) is active in the entity at a date.
     *
     * @param entity the entity
     * @param payeeId the distributor id of a distributor account (never null)
     * @param date   the date
     * @return true when active, treating an unknown id as inactive
     */
    private static boolean payeeActive(LegalEntity entity, Long payeeId, LocalDate date) {
        Distributor distributor = Distributor.findById(payeeId);
        return distributor != null && isActive(entity, distributor, date);
    }

    /**
     * Tells whether an account holder bears valid bank details for a date.
     *
     * @param entity  the entity
     * @param account the account
     * @param date    the date
     * @return true when a bank account with an IBAN covers the date
     */
    private static boolean hasIban(LegalEntity entity, DistributorAccount account, LocalDate date) {
        for (BankAccount bank : BankAccount.<BankAccount>list(
                "entity = ?1 and holderType = ?2 and holderId = ?3", entity, account.payeeType, account.payeeId)) {
            boolean started = bank.validFrom == null || !bank.validFrom.isAfter(date);
            boolean notEnded = bank.validTo == null || !bank.validTo.isBefore(date);
            if (started && notEnded && !isBlank(bank.iban)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a distributor bears any quality indicator for the period's month.
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @param month       the period month
     * @return true when at least one indicator covers the month
     */
    private static boolean hasIndicator(LegalEntity entity, Distributor distributor, LocalDate month) {
        LocalDate end = month.withDayOfMonth(month.lengthOfMonth());
        return QualityIndicator.count(
                "entity = ?1 and distributor = ?2 and periodTo >= ?3 and periodFrom <= ?4", entity, distributor,
                month, end) > 0;
    }

    /**
     * Tells whether a distributor's account has stood debtor since before a cutoff, by reading its
     * balance series.
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @param cutoff      the month before which a debit is "old"
     * @return true when the balance was already negative at or before the cutoff
     */
    private boolean debtorSince(LegalEntity entity, Distributor distributor, LocalDate cutoff) {
        for (AccountService.MonthBalance point : accounts.balanceSeries(entity, distributor)) {
            if (!point.month().isAfter(cutoff) && point.balance().signum() < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a coverage was terminated within the early window from its effect date.
     *
     * @param coverage    the coverage
     * @param earlyMonths the early-termination window in months
     * @return true when an early resiliation left the coverage without clawback candidacy
     */
    private static boolean isEarlyTermination(Coverage coverage, int earlyMonths) {
        if (coverage.endDate == null || coverage.effectDate == null || coverage.endReason != EndReason.TERMINATION) {
            return false;
        }
        return coverage.endDate.isBefore(coverage.effectDate.plusMonths(earlyMonths));
    }

    /**
     * Returns the active advance agreements of an entity.
     *
     * @param entity the entity
     * @return the active agreements
     */
    private static List<AdvanceAgreement> activeAgreements(LegalEntity entity) {
        return AdvanceAgreement.list("entity = ?1 and status = ?2", entity, AdvanceAgreementStatus.VALID);
    }

    /**
     * Returns the advance positions opened under an agreement.
     *
     * @param agreement the advance agreement
     * @return the positions
     */
    private static List<AdvancePosition> advancePositions(AdvanceAgreement agreement) {
        return AdvancePosition.list("advanceAgreement = ?1", agreement);
    }

    /**
     * Returns the outstanding receivable of an advance position: advanced minus offset, net of the
     * settlement adjustment.
     *
     * @param position the advance position
     * @return the outstanding amount
     */
    private static BigDecimal outstanding(AdvancePosition position) {
        BigDecimal advanced = nz(position.advancedAcquisition).add(nz(position.advancedServicing))
                .add(nz(position.advancedLinear));
        BigDecimal offset = nz(position.offsetAcquisition).add(nz(position.offsetServicing))
                .add(nz(position.offsetLinear));
        return advanced.subtract(offset).subtract(nz(position.adjustmentAmount));
    }

    /**
     * Sums the period's non-forecast line amounts by nature.
     *
     * @param period the period, possibly null
     * @return the total by nature, empty when the period is null
     */
    private static Map<LineNature, BigDecimal> totalsByNature(Period period) {
        Map<LineNature, BigDecimal> totals = new HashMap<>();
        if (period == null) {
            return totals;
        }
        for (CommissionLine line : CommissionLine.<CommissionLine>list("period = ?1", period)) {
            if (line.nature != null && line.amount != null) {
                totals.merge(line.nature, line.amount, BigDecimal::add);
            }
        }
        return totals;
    }

    /**
     * Sums the period's line amounts by origin distributor.
     *
     * @param period the period, possibly null
     * @return the total by distributor id, empty when the period is null
     */
    private static Map<Long, BigDecimal> totalsByDistributor(Period period) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        if (period == null) {
            return totals;
        }
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "period = ?1 and originDistributor is not null", period)) {
            if (line.amount != null) {
                totals.merge(line.originDistributor.getId(), line.amount, BigDecimal::add);
            }
        }
        return totals;
    }

    /**
     * Computes each distributor's average rate over the period as summed amount over summed basis.
     *
     * @param period the period, possibly null
     * @return the average rate by distributor id
     */
    private static Map<Long, BigDecimal> averageRateByDistributor(Period period) {
        Map<Long, BigDecimal> amount = new HashMap<>();
        Map<Long, BigDecimal> basis = new HashMap<>();
        Map<Long, BigDecimal> rates = new HashMap<>();
        if (period == null) {
            return rates;
        }
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "period = ?1 and originDistributor is not null", period)) {
            if (line.amount != null && line.basisAmount != null) {
                Long id = line.originDistributor.getId();
                amount.merge(id, line.amount, BigDecimal::add);
                basis.merge(id, line.basisAmount, BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : basis.entrySet()) {
            if (entry.getValue().signum() != 0) {
                rates.put(entry.getKey(),
                        amount.get(entry.getKey()).divide(entry.getValue(), 6, RoundingMode.HALF_UP));
            }
        }
        return rates;
    }

    /**
     * Tells whether a current value deviates from a reference beyond a threshold; a null or zero
     * reference never triggers.
     *
     * @param current   the current value
     * @param reference the reference value, possibly null
     * @param threshold the relative threshold
     * @return true when the relative gap exceeds the threshold
     */
    private static boolean exceeds(BigDecimal current, BigDecimal reference, BigDecimal threshold) {
        if (reference == null || reference.signum() == 0) {
            return false;
        }
        return relativeGap(current, reference).compareTo(threshold) > 0;
    }

    /**
     * Returns the relative gap between two values as the absolute difference over the reference.
     *
     * @param current   the current value
     * @param reference the non-zero reference
     * @return the relative gap
     */
    private static BigDecimal relativeGap(BigDecimal current, BigDecimal reference) {
        return current.subtract(reference).abs().divide(reference.abs(), 6, RoundingMode.HALF_UP);
    }

    /**
     * Returns the relative deviation between two counts, the reference being non-zero.
     *
     * @param now    the current count
     * @param before the previous count
     * @return the relative deviation
     */
    private static BigDecimal deviation(long now, long before) {
        return BigDecimal.valueOf(Math.abs(now - before)).divide(BigDecimal.valueOf(before), 6, RoundingMode.HALF_UP);
    }

    /**
     * Returns the entity's period of the previous month, or null.
     *
     * @param entity the entity
     * @param period the period
     * @return the previous period, or null
     */
    private static Period previousPeriod(LegalEntity entity, Period period) {
        return Period.find("entity = ?1 and periodMonth = ?2", entity, period.periodMonth.minusMonths(1))
                .firstResult();
    }

    /**
     * Returns the entity's period of the same month a year earlier, or null.
     *
     * @param entity the entity
     * @param period the period
     * @return the year-ago period, or null
     */
    private static Period yearAgoPeriod(LegalEntity entity, Period period) {
        return Period.find("entity = ?1 and periodMonth = ?2", entity, period.periodMonth.minusYears(1))
                .firstResult();
    }

    /**
     * Returns the quality-indicator termination limit of a type (section 6.9), or null when the type
     * carries no amendment threshold.
     *
     * @param type the indicator type
     * @return the limit, or null
     */
    private static BigDecimal qualityLimit(QualityIndicatorType type) {
        return switch (type) {
            case COMPLAINT_RATE, UNPAID_RATE -> new BigDecimal("0.05");
            case NOT_PURSUED_RATE, NEW_BUSINESS_LAPSE_RATE -> new BigDecimal("0.12");
            case PORTFOLIO_LAPSE_RATE -> new BigDecimal("0.20");
            default -> null;
        };
    }

    /**
     * Reads a decimal parameter from a control's JSON parameters, defaulting when absent.
     *
     * @param control      the control definition
     * @param field        the field name
     * @param defaultValue the default value when absent
     * @return the decimal value
     */
    private BigDecimal decimalParam(ControlDefinition control, String field, String defaultValue) {
        JsonNode node = parameters(control).get(field);
        return node == null || node.isNull() ? new BigDecimal(defaultValue) : new BigDecimal(node.asText());
    }

    /**
     * Reads a string-list parameter from a control's JSON parameters, defaulting when absent or empty.
     *
     * @param control      the control definition
     * @param field        the field name
     * @param defaultValue the default list when absent
     * @return the list of strings
     */
    private List<String> listParam(ControlDefinition control, String field, List<String> defaultValue) {
        JsonNode node = parameters(control).get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            return defaultValue;
        }
        List<String> values = new ArrayList<>();
        node.forEach(element -> values.add(element.asText()));
        return values;
    }

    /**
     * Parses a control's JSON parameters into a node; a null, blank or malformed payload yields an
     * empty object so a control never fails on bad configuration.
     *
     * @param control the control definition
     * @return the parsed node, never null
     */
    private JsonNode parameters(ControlDefinition control) {
        return readParams(mapper, control.parameters);
    }

    /**
     * Parses a parameters payload into a node; a null, blank or malformed payload yields an empty
     * object so a control never fails on bad configuration. The JSON column normally guarantees valid
     * input; the malformed guard is exercised directly by a unit test.
     *
     * @param mapper     the JSON mapper
     * @param parameters the payload, possibly null, blank or malformed
     * @return the parsed node, never null
     */
    static JsonNode readParams(ObjectMapper mapper, String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(parameters);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    /**
     * Tells whether a string is null or blank.
     *
     * @param value the value
     * @return true when null or blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns a decimal, treating null as zero.
     *
     * @param value the value, possibly null
     * @return the value or zero
     */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Returns a numeric string, treating null or blank as zero.
     *
     * @param value the value, possibly null or blank
     * @return the value or "0"
     */
    private static String blankToZero(String value) {
        return isBlank(value) ? "0" : value;
    }

    /**
     * Records one anomaly with its observed and expected values.
     *
     * @param period     the period
     * @param control    the control definition
     * @param objectType the kind of object
     * @param objectId   the object id, possibly null
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
