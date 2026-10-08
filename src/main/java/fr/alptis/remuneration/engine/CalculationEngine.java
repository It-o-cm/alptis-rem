package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.RunStatus;
import fr.alptis.remuneration.domain.RunType;
import fr.alptis.remuneration.engine.families.CalculationInput;
import fr.alptis.remuneration.engine.families.ComputingFamily;
import fr.alptis.remuneration.engine.families.ProposedLine;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core of the calculation engine (section 12.3): the first time of the real calculation, where the
 * work unit is the garantie (D4). For each coverage it processes the events in chronological order
 * (D5), selects the active rule versions whose perimeter covers the event, arbitrates them by
 * exclusion group and priority (P7), controls the rate against the grid (P9), asks each kept family
 * for its proposal (D2), and writes the resulting commission lines with their explanation (D14). A
 * cancellation produces the inverse of the origin lines (P11). A rerun first deletes the
 * non-validated lines of the period (D13), so a tenfold rerun yields the same result (D8/NFR-19).
 *
 * <p>This step delivers the linear, fixed and external families and the base commissions; the
 * upfront, advance, tiered, portfolio, share and outstanding families join at the later steps.</p>
 */
@ApplicationScoped
public class CalculationEngine {

    /** The computing families, by code. */
    private final Map<RuleFamily, ComputingFamily> families = new EnumMap<>(RuleFamily.class);

    private final ScopeMatcher scopeMatcher;
    private final DateTimeProvider clock;
    private final ObjectMapper mapper;

    /**
     * Creates the engine, indexing the computing families discovered by CDI.
     *
     * @param computingFamilies the computing family beans
     * @param scopeMatcher      the perimeter matcher
     * @param clock             the application date/time provider
     * @param mapper            the Jackson mapper
     */
    @Inject
    public CalculationEngine(Instance<ComputingFamily> computingFamilies, ScopeMatcher scopeMatcher,
                             DateTimeProvider clock, ObjectMapper mapper) {
        for (ComputingFamily family : computingFamilies) {
            families.put(family.code(), family);
        }
        this.scopeMatcher = scopeMatcher;
        this.clock = clock;
        this.mapper = mapper;
    }

    /**
     * Runs the real calculation of an entity's period over its pending events.
     *
     * @param entityCode  the entity code
     * @param periodMonth the first day of the processing month
     * @return the recorded calculation run
     */
    @Transactional
    public CalculationRun runReal(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            period = new Period();
            period.entity = entity;
            period.periodMonth = periodMonth;
            period.state = PeriodState.OPEN;
            period.setCreatedAt(clock.now());
            period.persist();
        }

        List<RuleVersion> versions = RuleVersion.list(
                "status = ?1 and rule.plan.entity = ?2",
                fr.alptis.remuneration.domain.VersionStatus.ACTIVE, entity);

        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = RunType.ACTUAL;
        run.status = RunStatus.RUNNING;
        run.startedAt = clock.now();
        run.launchedBy = "engine";
        run.configHash = HashUtil.sha256(versions.stream().map(v -> String.valueOf(v.getId())).sorted().toList()
                .toString());
        run.setCreatedAt(clock.now());
        run.persist();

        // D13: a rerun first removes the non-validated lines it had produced for the period.
        CommissionLine.delete("period = ?1 and status in ?2", period,
                List.of(LineStatus.EARNED, LineStatus.FORECAST));

        List<BusinessEvent> events = BusinessEvent.list(
                "entity = ?1 and processingStatus = ?2 order by coverage.id, eventDate, receivedDate, sourceKey",
                entity, ProcessingStatus.PENDING);

        int lineCount = 0;
        for (BusinessEvent event : events) {
            lineCount += process(period, run, event, versions);
        }

        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"events\":" + events.size() + ",\"lines\":" + lineCount + "}";
        return run;
    }

    /**
     * Processes one event: qualification, selection, arbitration, rate control, proposal and line
     * creation, or a hold with its anomaly.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the event
     * @param versions the active versions of the entity
     * @return the number of lines written for the event
     */
    private int process(Period period, CalculationRun run, BusinessEvent event, List<RuleVersion> versions) {
        Coverage coverage = event.coverage;
        if (coverage == null) {
            return hold(event, HoldReason.NO_RULE);
        }
        if (Boolean.FALSE.equals(coverage.commissionable)) {
            event.processingStatus = ProcessingStatus.NOT_COMMISSIONABLE;
            return 0;
        }
        if (event.eventType == EventType.PREMIUM_CANCELLED) {
            return cancel(period, run, event);
        }

        RemunerationChoice choice = RemunerationChoice.find(
                "coverage = ?1 and (validFrom is null or validFrom <= ?2) and (validTo is null or validTo >= ?2) "
                        + "order by validFrom desc", coverage, event.eventDate).firstResult();
        Map<String, String> attributes = attributes(event, coverage, choice);
        List<RuleVersion> candidates = new ArrayList<>();
        for (RuleVersion version : versions) {
            if (families.containsKey(version.rule.family) && activeAt(version, event.eventDate)
                    && scopeMatcher.matches(version.scope, attributes)) {
                candidates.add(version);
            }
        }

        List<RuleVersion> kept = arbitrate(candidates);
        if (kept == null) {
            return hold(event, HoldReason.CONCURRENT_RULES);
        }
        if (kept.stream().noneMatch(v -> "BASE".equals(v.exclusionGroup))) {
            return hold(event, HoldReason.NO_RULE);
        }
        for (RuleVersion version : kept) {
            if (!rateInGrid(version, choice)) {
                return hold(event, HoldReason.RATE_OUT_OF_GRID);
            }
        }

        int count = 0;
        for (RuleVersion version : kept) {
            ComputingFamily family = families.get(version.rule.family);
            CalculationInput input = input(event, coverage, choice, version);
            for (ProposedLine proposed : family.propose(input)) {
                writeLine(period, run, event, coverage, version, proposed);
                count++;
            }
        }
        event.processingStatus = ProcessingStatus.PROCESSED;
        return count;
    }

    /**
     * Produces the inverse of the lines born of the cancelled event (P11, D12).
     *
     * @param period the period
     * @param run    the run
     * @param event  the cancellation event
     * @return the number of inverse lines written
     */
    private int cancel(Period period, CalculationRun run, BusinessEvent event) {
        BusinessEvent origin = event.reversesEvent;
        int count = 0;
        if (origin != null) {
            List<CommissionLine> originLines = CommissionLine.list(
                    "event = ?1 and status <> ?2", origin, LineStatus.CANCELLED);
            for (CommissionLine source : originLines) {
                CommissionLine inverse = baseLine(period, run, event, source.coverage, source.ruleVersion);
                inverse.nature = source.nature;
                inverse.basisAmount = negate(source.basisAmount);
                inverse.rate = source.rate;
                inverse.amount = source.amount.negate();
                inverse.originLine = source;
                inverse.originProductionCode = source.originProductionCode;
                inverse.originDistributor = source.originDistributor;
                inverse.explanation = "{\"reversalOf\":" + source.getId() + "}";
                inverse.persist();
                count++;
            }
        }
        event.processingStatus = ProcessingStatus.PROCESSED;
        return count;
    }

    /**
     * Writes one commission line from a family proposal, attributed to the coverage's main
     * production code (P10).
     *
     * @param period   the period
     * @param run      the run
     * @param event    the source event
     * @param coverage the coverage
     * @param version  the rule version
     * @param proposed the proposed line
     */
    private void writeLine(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                           RuleVersion version, ProposedLine proposed) {
        CommissionLine line = baseLine(period, run, event, coverage, version);
        line.nature = proposed.nature();
        line.basisAmount = proposed.basisAmount();
        line.rate = proposed.rate();
        line.amount = proposed.amount();
        line.explanation = proposed.explanation();
        ProductionCode origin = mainProductionCode(coverage);
        line.originProductionCode = origin;
        line.originDistributor = origin == null ? null : origin.distributor;
        line.persist();
    }

    /**
     * Builds a commission line with the fields common to a normal and an inverse line.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the source event
     * @param coverage the coverage
     * @param version  the rule version
     * @return a new, unpersisted line
     */
    private CommissionLine baseLine(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                                    RuleVersion version) {
        CommissionLine line = new CommissionLine();
        line.entity = period.entity;
        line.period = period;
        line.run = run;
        line.direction = version.rule.plan.direction;
        line.status = LineStatus.EARNED;
        line.contract = coverage.contract;
        line.coverage = coverage;
        line.event = event;
        line.ruleVersion = version;
        line.attachmentMonth = period.periodMonth;
        line.setCreatedAt(clock.now());
        return line;
    }

    /**
     * Holds an event with an anomaly and writes no line.
     *
     * @param event  the event
     * @param reason the hold reason
     * @return zero
     */
    private static int hold(BusinessEvent event, HoldReason reason) {
        event.processingStatus = ProcessingStatus.ON_HOLD;
        event.holdReason = reason;
        return 0;
    }

    /**
     * Keeps, per exclusion group, the version of the highest priority; a tie holds the event.
     *
     * @param candidates the candidate versions
     * @return the kept versions, or null when two versions tie in a group
     */
    private static List<RuleVersion> arbitrate(List<RuleVersion> candidates) {
        Map<String, RuleVersion> best = new LinkedHashMap<>();
        Map<String, Boolean> tie = new LinkedHashMap<>();
        for (RuleVersion version : candidates) {
            String group = version.exclusionGroup;
            RuleVersion current = best.get(group);
            if (current == null || priority(version) > priority(current)) {
                best.put(group, version);
                tie.put(group, Boolean.FALSE);
            } else if (priority(version) == priority(current)) {
                tie.put(group, Boolean.TRUE);
            }
        }
        if (tie.containsValue(Boolean.TRUE)) {
            return null;
        }
        return new ArrayList<>(best.values());
    }

    /**
     * Returns a version's priority, defaulting to zero.
     *
     * @param version the version
     * @return the priority
     */
    private static int priority(RuleVersion version) {
        return version.priority == null ? 0 : version.priority;
    }

    /**
     * Tells whether a version is active at a date, treating null bounds as open-ended.
     *
     * @param version the version
     * @param date    the event date
     * @return {@code true} if the version is active at the date
     */
    private static boolean activeAt(RuleVersion version, LocalDate date) {
        boolean afterStart = version.validFrom == null || !date.isBefore(version.validFrom);
        boolean beforeEnd = version.validTo == null || !date.isAfter(version.validTo);
        return afterStart && beforeEnd;
    }

    /**
     * Controls that the rate carried by the garantie belongs to the rule's grid (P9), for the linear
     * family; other families carry no rate grid at this step.
     *
     * @param version the rule version
     * @param choice  the coverage's remuneration choice, possibly null
     * @return {@code true} if the rate is acceptable
     */
    private boolean rateInGrid(RuleVersion version, RemunerationChoice choice) {
        if (version.rule.family != RuleFamily.LINEAR) {
            return true;
        }
        BigDecimal chosen = choice == null ? null : choice.linearRate;
        if (chosen == null) {
            return false;
        }
        for (JsonNode allowed : readArray(mapper, version.parameters, "allowedRates")) {
            if (allowed.isNumber() && allowed.decimalValue().compareTo(chosen) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads an array from a parameters JSON, returning an empty list when absent, not an array or
     * malformed.
     *
     * @param mapper     the Jackson mapper
     * @param parameters the parameters JSON
     * @param field      the array field name
     * @return the array node's elements
     */
    static List<JsonNode> readArray(ObjectMapper mapper, String parameters, String field) {
        if (parameters == null || parameters.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = mapper.readTree(parameters).get(field);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<JsonNode> out = new ArrayList<>();
            node.forEach(out::add);
            return out;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
    }

    /**
     * Builds the calculation input a family reads from the event, coverage and remuneration choice.
     *
     * @param event    the event
     * @param coverage the coverage
     * @param choice   the remuneration choice, possibly null
     * @param version  the rule version
     * @return the calculation input
     */
    private static CalculationInput input(BusinessEvent event, Coverage coverage, RemunerationChoice choice,
                                          RuleVersion version) {
        return new CalculationInput(event.eventType, event.basisAmount,
                choice == null ? null : choice.linearRate,
                choice == null ? null : choice.acquisitionRate,
                choice == null ? null : choice.servicingRate,
                coverage.effectDate,
                event.eventDate, event.periodFrom, event.periodTo,
                event.precomputedBasis, event.precomputedRate, event.precomputedAmount,
                version.outputNature, version.parameters);
    }

    /**
     * Builds the scope-matching attributes of an event (entity, product, event type, mode, college).
     *
     * @param event    the event
     * @param coverage the coverage
     * @param choice   the remuneration choice, possibly null
     * @return the attribute map
     */
    private static Map<String, String> attributes(BusinessEvent event, Coverage coverage, RemunerationChoice choice) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("entity", event.entity.code);
        attributes.put("eventType", event.eventType.name());
        if (coverage.contract.product != null) {
            attributes.put("product", coverage.contract.product.code);
        }
        if (coverage.staffCategory != null) {
            attributes.put("college", coverage.staffCategory.name());
        }
        if (choice != null && choice.mode != null) {
            attributes.put("remunerationMode", choice.mode.name());
        }
        ProductionCode origin = mainProductionCode(coverage);
        if (origin != null && origin.distributor != null) {
            attributes.put("distributor", origin.distributor.partnerCode);
        }
        return attributes;
    }

    /**
     * Returns the coverage's current main production code, or null.
     *
     * @param coverage the coverage
     * @return the main production code, or null
     */
    private static ProductionCode mainProductionCode(Coverage coverage) {
        CoverageAssignment assignment = CoverageAssignment.find(
                "coverage = ?1 and role = ?2 and validTo is null", coverage,
                fr.alptis.remuneration.domain.AssignmentRole.MAIN).firstResult();
        return assignment == null ? null : assignment.productionCode;
    }

    /**
     * Negates a nullable amount.
     *
     * @param amount the amount, possibly null
     * @return the negated amount, or null
     */
    private static BigDecimal negate(BigDecimal amount) {
        return amount == null ? null : amount.negate();
    }
}
