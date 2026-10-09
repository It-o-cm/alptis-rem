package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

        // Group the events by coverage (D4): one work unit per garantie, the rest handled on their own.
        java.util.Map<Long, List<BusinessEvent>> byCoverage = new java.util.LinkedHashMap<>();
        List<BusinessEvent> coverless = new ArrayList<>();
        for (BusinessEvent event : events) {
            if (event.coverage == null) {
                coverless.add(event);
            } else {
                byCoverage.computeIfAbsent(event.coverage.getId(), k -> new ArrayList<>()).add(event);
            }
        }

        int lineCount = 0;
        for (List<BusinessEvent> unit : byCoverage.values()) {
            lineCount += processUnit(period, run, unit.get(0).coverage, unit, versions);
        }
        // Discount versement is triggered by the échéance (D3), so a garantie eligible for the
        // advance is processed even when it carries no event this period.
        for (Coverage coverage : advanceCandidates(entity, byCoverage.keySet())) {
            lineCount += processUnit(period, run, coverage, List.of(), versions);
        }
        for (BusinessEvent event : coverless) {
            lineCount += hold(event, HoldReason.NO_RULE);
        }

        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"events\":" + events.size() + ",\"lines\":" + lineCount + "}";
        return run;
    }

    /**
     * Returns the entity's coverages that requested the advance and have no position yet and no event
     * this period, so their versement échéance can still be processed (D3).
     *
     * @param entity    the entity
     * @param withEvents the ids of coverages already processed from their events
     * @return the candidate coverages for a versement
     */
    private List<Coverage> advanceCandidates(LegalEntity entity, java.util.Set<Long> withEvents) {
        List<Coverage> candidates = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<RemunerationChoice> choices = RemunerationChoice.list(
                "advanceRequested = true and coverage.contract.entity = ?1", entity);
        for (RemunerationChoice choice : choices) {
            Coverage coverage = choice.coverage;
            if (withEvents.contains(coverage.getId()) || !seen.add(coverage.getId())) {
                continue;
            }
            if (AdvancePosition.count("coverage = ?1", coverage) == 0) {
                candidates.add(coverage);
            }
        }
        return candidates;
    }

    /**
     * Processes one coverage as a work unit (D4): opens the discount advance if the garantie becomes
     * eligible (section 6.4), processes its events in order (premiums produce lines then feed the
     * apurement, cancellations invert, terminations claw back and may break the advance), and settles
     * the advance at the end of the unit.
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param events   the coverage's events, in order
     * @param versions the active versions of the entity
     * @return the number of lines written for the unit
     */
    private int processUnit(Period period, CalculationRun run, Coverage coverage, List<BusinessEvent> events,
                            List<RuleVersion> versions) {
        if (Boolean.FALSE.equals(coverage.commissionable)) {
            for (BusinessEvent event : events) {
                event.processingStatus = ProcessingStatus.NOT_COMMISSIONABLE;
            }
            return 0;
        }
        int count = 0;
        AdvanceState advance = openAdvanceIfEligible(period, run, coverage);
        if (advance != null) {
            count += advance.versementLines;
        }
        for (BusinessEvent event : events) {
            if (event.eventType == EventType.PREMIUM_CANCELLED) {
                count += cancel(period, run, event);
            } else if (event.eventType == EventType.UNPAID_NOTICE) {
                count += unpaid(period, run, event, coverage, advance);
            } else if (isTerminal(event.eventType)) {
                count += rupture(period, run, event, coverage, advance);
                count += clawback(period, run, event, coverage);
            } else {
                List<CommissionLine> written = processPremium(period, run, event, coverage, versions);
                count += written.size();
                count += apureAndSettle(period, run, coverage, advance, written);
            }
        }
        count += settleAtDueDate(period, run, coverage, advance);
        return count;
    }

    /**
     * Qualifies, selects, arbitrates, rate-controls and computes one premium event, returning the
     * lines it produced (empty when the event is held).
     *
     * @param period   the period
     * @param run      the run
     * @param event    the premium event
     * @param coverage the coverage
     * @param versions the active versions of the entity
     * @return the written lines
     */
    private List<CommissionLine> processPremium(Period period, CalculationRun run, BusinessEvent event,
                                                Coverage coverage, List<RuleVersion> versions) {
        RemunerationChoice choice = choiceAt(coverage, event.eventDate);
        Map<String, String> attributes = attributes(event, coverage, choice);
        List<RuleVersion> candidates = new ArrayList<>();
        for (RuleVersion version : versions) {
            if (version.rule.family != RuleFamily.ADVANCE && families.containsKey(version.rule.family)
                    && activeAt(version, event.eventDate) && scopeMatcher.matches(version.scope, attributes)) {
                candidates.add(version);
            }
        }

        List<RuleVersion> kept = arbitrate(candidates);
        if (kept == null) {
            hold(event, HoldReason.CONCURRENT_RULES);
            return List.of();
        }
        if (kept.stream().noneMatch(v -> "BASE".equals(v.exclusionGroup))) {
            hold(event, HoldReason.NO_RULE);
            return List.of();
        }
        for (RuleVersion version : kept) {
            if (!rateInGrid(version, choice)) {
                hold(event, HoldReason.RATE_OUT_OF_GRID);
                return List.of();
            }
        }

        List<CommissionLine> written = new ArrayList<>();
        for (RuleVersion version : kept) {
            ComputingFamily family = families.get(version.rule.family);
            CalculationInput input = input(event, coverage, choice, version);
            for (ProposedLine proposed : family.propose(input)) {
                written.add(writeLine(period, run, event, coverage, version, proposed));
            }
        }
        event.processingStatus = ProcessingStatus.PROCESSED;
        return written;
    }

    /**
     * Returns the remuneration choice in force on a coverage at a date.
     *
     * @param coverage the coverage
     * @param date     the date
     * @return the choice, or null
     */
    private static RemunerationChoice choiceAt(Coverage coverage, LocalDate date) {
        return RemunerationChoice.find(
                "coverage = ?1 and (validFrom is null or validFrom <= ?2) and (validTo is null or validTo >= ?2) "
                        + "order by validFrom desc", coverage, date).firstResult();
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
     * Tells whether an event type ends a garantie and may trigger a clawback (section 6.3).
     *
     * @param type the event type
     * @return {@code true} for a termination, withdrawal, not-pursued or death
     */
    private static boolean isTerminal(EventType type) {
        return type == EventType.TERMINATION || type == EventType.WITHDRAWAL
                || type == EventType.NOT_PURSUED || type == EventType.DEATH;
    }

    /**
     * Claws back the acquired commissions of a garantie that ends early (section 6.3): the age-based
     * policy of the origin rule version on a termination or withdrawal, the whole of the acquired
     * lines on a not-pursued, nothing on a death.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the terminating event
     * @param coverage the coverage
     * @return the number of clawback lines written
     */
    private int clawback(Period period, CalculationRun run, BusinessEvent event, Coverage coverage) {
        event.processingStatus = ProcessingStatus.PROCESSED;
        if (event.eventType == EventType.DEATH) {
            return 0;
        }
        List<CommissionLine> acquired = CommissionLine.list("coverage = ?1 and status in ?2", coverage,
                List.of(LineStatus.EARNED, LineStatus.OFFSET));
        RuleVersion version = acquired.stream().map(l -> l.ruleVersion).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (version == null) {
            return 0;
        }
        java.util.Set<LineNature> natures;
        BigDecimal rate;
        if (event.eventType == EventType.NOT_PURSUED) {
            natures = java.util.EnumSet.of(LineNature.LINEAR, LineNature.ACQUISITION, LineNature.SERVICING,
                    LineNature.FEE);
            rate = BigDecimal.ONE;
        } else {
            JsonNode policy = clawbackPolicy(version);
            if (policy == null || !triggerMatches(policy, event.eventType)) {
                return 0;
            }
            long age = ChronoUnit.DAYS.between(coverage.effectDate, event.eventDate);
            rate = clawbackRate(policy, age);
            if (rate.signum() == 0) {
                return 0;
            }
            natures = clawbackNatures(policy);
        }
        BigDecimal base = BigDecimal.ZERO;
        for (CommissionLine line : acquired) {
            if (natures.contains(line.nature)) {
                base = base.add(line.amount);
            }
        }
        BigDecimal amount = base.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0) {
            return 0;
        }
        CommissionLine clawback = baseLine(period, run, event, coverage, version);
        clawback.nature = LineNature.CLAWBACK;
        clawback.amount = amount.negate();
        ProductionCode origin = mainProductionCode(coverage);
        clawback.originProductionCode = origin;
        clawback.originDistributor = origin == null ? null : origin.distributor;
        clawback.explanation = "{\"family\":\"CLAWBACK\",\"base\":" + base.toPlainString()
                + ",\"rate\":" + rate.toPlainString() + ",\"amount\":" + amount.negate().toPlainString() + "}";
        clawback.persist();
        return 1;
    }

    /**
     * Reads the clawback policy of a rule version, or null when it has none.
     *
     * @param version the rule version
     * @return the policy node, or null
     */
    private JsonNode clawbackPolicy(RuleVersion version) {
        return readObject(mapper, version.clawbackPolicy);
    }

    /**
     * Parses a JSON object string, returning null when it is null, blank or malformed.
     *
     * @param mapper the Jackson mapper
     * @param json   the JSON string, possibly null
     * @return the parsed node, or null
     */
    static JsonNode readObject(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Tells whether a clawback policy lists the given event type among its triggers (empty triggers
     * mean any termination).
     *
     * @param policy the policy node
     * @param type   the event type
     * @return {@code true} if the trigger matches
     */
    private static boolean triggerMatches(JsonNode policy, EventType type) {
        JsonNode triggers = policy.get("triggers");
        if (triggers == null || !triggers.isArray() || triggers.isEmpty()) {
            return true;
        }
        for (JsonNode trigger : triggers) {
            if (type.name().equals(trigger.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the clawback rate for an age from a policy: the matching age step, the pro-rata share
     * or none.
     *
     * @param policy the policy node
     * @param age    the garantie age in days
     * @return the clawback rate (zero when nothing is clawed back)
     */
    private static BigDecimal clawbackRate(JsonNode policy, long age) {
        String type = policy.path("type").asText("");
        if ("AGE_STEPS".equals(type)) {
            JsonNode steps = policy.get("steps");
            if (steps != null && steps.isArray()) {
                for (JsonNode step : steps) {
                    if (age <= step.path("maxDays").asLong(Long.MAX_VALUE)) {
                        return step.path("rate").decimalValue();
                    }
                }
            }
            return BigDecimal.ZERO;
        }
        if ("PRO_RATA".equals(type)) {
            int months = policy.path("months").asInt(0);
            long elapsed = age / 30;
            if (months <= 0 || elapsed >= months) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(months - elapsed).divide(BigDecimal.valueOf(months), 10, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Returns the line natures a clawback policy targets, defaulting to acquisition.
     *
     * @param policy the policy node
     * @return the targeted natures
     */
    private static java.util.Set<LineNature> clawbackNatures(JsonNode policy) {
        JsonNode natures = policy.get("natures");
        java.util.Set<LineNature> out = java.util.EnumSet.noneOf(LineNature.class);
        if (natures != null && natures.isArray()) {
            natures.forEach(n -> out.add(LineNature.valueOf(n.asText())));
        }
        if (out.isEmpty()) {
            out.add(LineNature.ACQUISITION);
        }
        return out;
    }

    /**
     * Opens the discount advance for a coverage if it becomes eligible (section 6.4): a requested
     * advance, a valid convention covering the distributor and the effect date, not a single premium,
     * and the convention cap not reached (D9). The advance amount is computed on the annualised
     * premium by the advance family, the {@code ADVANCE} lines are written and the position is opened.
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @return the opened advance state, or null when the garantie is not eligible
     */
    private AdvanceState openAdvanceIfEligible(Period period, CalculationRun run, Coverage coverage) {
        RemunerationChoice choice = choiceAt(coverage, coverage.effectDate);
        if (choice == null || !Boolean.TRUE.equals(choice.advanceRequested)
                || Boolean.TRUE.equals(coverage.singlePremium)) {
            return null;
        }
        fr.alptis.remuneration.domain.ProductionCode origin = mainProductionCode(coverage);
        if (origin == null || origin.distributor == null) {
            return null;
        }
        fr.alptis.remuneration.domain.AdvanceAgreement agreement =
                fr.alptis.remuneration.domain.AdvanceAgreement.find(
                        "distributor = ?1 and status = ?2", origin.distributor,
                        fr.alptis.remuneration.domain.AdvanceAgreementStatus.VALID).firstResult();
        if (agreement == null || !withinAdvanceWindow(agreement, coverage)) {
            return null;
        }
        long taken = fr.alptis.remuneration.domain.AdvancePosition.count("advanceAgreement = ?1", agreement);
        if (agreement.maxContracts != null && taken >= agreement.maxContracts) {
            return null;
        }
        // Versement timing (section 6.4): paid in the first period whose month reaches that of the
        // effect date minus the anticipation delay.
        int anticipation = agreement.anticipationDays == null ? 0 : agreement.anticipationDays;
        if (period.periodMonth.isBefore(firstOfMonth(coverage.effectDate.minusDays(anticipation)))) {
            return null;
        }

        CalculationInput input = new CalculationInput(EventType.PREMIUM_COLLECTED, null, choice.linearRate,
                choice.acquisitionRate, choice.servicingRate, coverage.effectDate, coverage.effectDate, null, null,
                null, null, null, coverage.annualPremium, LineNature.ADVANCE, null);
        List<ProposedLine> advanceLines = families.get(RuleFamily.ADVANCE).propose(input);

        AdvancePosition position = new AdvancePosition();
        position.advanceAgreement = agreement;
        position.coverage = coverage;
        position.status = AdvanceStatus.ACTIVE;
        position.paidAt = clock.now();
        position.settlementDueDate = coverage.effectDate.plusDays(period.entity.advanceSettlementDays);
        position.advancedAcquisition = BigDecimal.ZERO;
        position.advancedServicing = BigDecimal.ZERO;
        position.advancedLinear = BigDecimal.ZERO;
        position.offsetAcquisition = BigDecimal.ZERO;
        position.offsetServicing = BigDecimal.ZERO;
        position.offsetLinear = BigDecimal.ZERO;
        position.setCreatedAt(clock.now());
        position.persist();

        AdvanceState state = new AdvanceState();
        state.position = position;
        state.advanced = BigDecimal.ZERO;
        state.offset = BigDecimal.ZERO;
        fr.alptis.remuneration.domain.ProductionCode pc = origin;
        for (ProposedLine proposed : advanceLines) {
            CommissionLine line = new CommissionLine();
            line.entity = period.entity;
            line.period = period;
            line.run = run;
            line.direction = Direction.OUTBOUND;
            line.status = LineStatus.EARNED;
            line.contract = coverage.contract;
            line.coverage = coverage;
            line.ruleVersion = null;
            line.nature = LineNature.ADVANCE;
            line.amount = proposed.amount();
            line.explanation = proposed.explanation();
            line.advancePosition = position;
            line.originProductionCode = pc;
            line.originDistributor = pc.distributor;
            line.attachmentMonth = period.periodMonth;
            line.setCreatedAt(clock.now());
            line.persist();
            state.advanced = state.advanced.add(proposed.amount());
            state.versementLines++;
        }
        position.advancedAcquisition = state.advanced;
        return state;
    }

    /**
     * Tells whether the coverage's effect date and registration date fall within the convention's
     * window (section 6.4, conditions 3 and 4).
     *
     * @param agreement the discount convention
     * @param coverage  the coverage
     * @return {@code true} if within the window
     */
    private static boolean withinAdvanceWindow(fr.alptis.remuneration.domain.AdvanceAgreement agreement,
                                               Coverage coverage) {
        LocalDate effect = coverage.effectDate;
        if (effect == null || agreement.validFrom == null) {
            return false;
        }
        int anticipation = agreement.anticipationDays == null ? 0 : agreement.anticipationDays;
        LocalDate effectLimit = agreement.validTo == null ? null : agreement.validTo.plusDays(anticipation);
        if (effect.isBefore(agreement.validFrom) || (effectLimit != null && effect.isAfter(effectLimit))) {
            return false;
        }
        LocalDate registration = coverage.contract.registrationDate;
        LocalDate registrationLimit = agreement.validTo == null ? null : agreement.validTo.plusDays(5);
        return registration == null
                || (!registration.isBefore(agreement.validFrom)
                && (registrationLimit == null || !registration.isAfter(registrationLimit)));
    }

    /**
     * Offsets the just-written earned lines against the advance while it is active, then settles the
     * advance when the offset cumul catches up (section 6.4).
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param advance  the advance state, possibly null
     * @param written  the lines written for the current premium
     * @return the number of extra lines written (a top-up adjustment)
     */
    private int apureAndSettle(Period period, CalculationRun run, Coverage coverage, AdvanceState advance,
                               List<CommissionLine> written) {
        if (advance == null || !advance.active()) {
            return 0;
        }
        LocalDate anniversary = coverage.effectDate.plusDays(365);
        for (CommissionLine line : written) {
            if (offsettable(line.nature) && line.event.eventDate.isBefore(anniversary)) {
                line.status = LineStatus.OFFSET;
                line.advancePosition = advance.position;
                advance.offset = advance.offset.add(line.amount);
            }
        }
        BigDecimal delta = advance.offset.subtract(advance.advanced);
        if (delta.signum() >= 0) {
            return settle(period, run, coverage, advance, delta, "SETTLED_BY_OFFSET");
        }
        return 0;
    }

    /**
     * Handles an unpaid notice (section 6.4): it breaks the advance when its rank reaches the
     * convention's threshold; otherwise it leaves the advance untouched.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the unpaid-notice event
     * @param coverage the coverage
     * @param advance  the advance state, possibly null
     * @return the number of adjustment lines written
     */
    private int unpaid(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                       AdvanceState advance) {
        event.processingStatus = ProcessingStatus.PROCESSED;
        if (advance == null || !advance.active()) {
            return 0;
        }
        Short threshold = advance.position.advanceAgreement.unpaidRankThreshold;
        if (event.unpaidRank == null || threshold == null || event.unpaidRank < threshold) {
            return 0;
        }
        return rupture(period, run, event, coverage, advance);
    }

    /**
     * Breaks the advance on a rupture event (section 6.4): the position is cancelled and a
     * regularisation line of the remaining gap is written immediately. A death is not a rupture.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the terminating event
     * @param coverage the coverage
     * @param advance  the advance state, possibly null
     * @return the number of adjustment lines written
     */
    private int rupture(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                        AdvanceState advance) {
        if (advance == null || !advance.active() || event.eventType == EventType.DEATH) {
            return 0;
        }
        BigDecimal delta = advance.offset.subtract(advance.advanced);
        advance.position.status = AdvanceStatus.CANCELLED;
        advance.position.closedAt = clock.now();
        advance.position.closeReason = event.eventType.name();
        return writeAdjustment(period, run, coverage, advance, delta);
    }

    /**
     * Settles the advance at its due date (section 6.4): a shortfall is clawed back as an undue
     * advance unless it is under the entity's settlement tolerance, in which case it is written off.
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param advance  the advance state, possibly null
     * @return the number of adjustment lines written
     */
    private int settleAtDueDate(Period period, CalculationRun run, Coverage coverage, AdvanceState advance) {
        if (advance == null || !advance.active()
                || period.periodMonth.isBefore(firstOfMonth(advance.position.settlementDueDate))) {
            return 0;
        }
        // While active at the due date, the offset cumul is always short of the advanced amount, so
        // the gap is negative; a gap under the tolerance is written off (section 6.4).
        BigDecimal delta = advance.offset.subtract(advance.advanced);
        BigDecimal tolerance = period.entity.advanceSettlementTolerance;
        if (delta.abs().compareTo(tolerance) < 0) {
            advance.position.status = AdvanceStatus.SETTLED;
            advance.position.closedAt = clock.now();
            advance.position.closeReason = "WRITE_OFF";
            return 0;
        }
        return settle(period, run, coverage, advance, delta, "SETTLED_AT_DUE_DATE");
    }

    /**
     * Marks the advance settled and writes a top-up (positive delta) or undue-advance clawback
     * (negative delta) adjustment when the delta is non-zero.
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param advance  the advance state
     * @param delta    the gap between offset cumul and advanced amount
     * @param reason   the settlement reason recorded on the position
     * @return the number of adjustment lines written
     */
    private int settle(Period period, CalculationRun run, Coverage coverage, AdvanceState advance, BigDecimal delta,
                       String reason) {
        advance.position.status = AdvanceStatus.SETTLED;
        advance.position.closedAt = clock.now();
        advance.position.closeReason = reason;
        if (delta.signum() == 0) {
            return 0;
        }
        return writeAdjustment(period, run, coverage, advance, delta);
    }

    /**
     * Writes an {@code ADVANCE_ADJUSTMENT} line of the given signed amount, attributed to the
     * coverage's main production code.
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param advance  the advance state
     * @param amount   the signed adjustment amount
     * @return 1 once the line is written
     */
    private int writeAdjustment(Period period, CalculationRun run, Coverage coverage, AdvanceState advance,
                                BigDecimal amount) {
        ProductionCode origin = mainProductionCode(coverage);
        CommissionLine line = new CommissionLine();
        line.entity = period.entity;
        line.period = period;
        line.run = run;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.contract = coverage.contract;
        line.coverage = coverage;
        line.nature = LineNature.ADVANCE_ADJUSTMENT;
        line.amount = amount;
        line.advancePosition = advance.position;
        line.originProductionCode = origin;
        line.originDistributor = origin.distributor;
        line.attachmentMonth = period.periodMonth;
        line.explanation = "{\"family\":\"ADVANCE_ADJUSTMENT\",\"delta\":" + amount.toPlainString() + "}";
        line.setCreatedAt(clock.now());
        line.persist();
        return 1;
    }

    /**
     * Tells whether a line nature is offset against an advance (the base commission natures).
     *
     * @param nature the line nature
     * @return {@code true} for a linear, acquisition or servicing line
     */
    private static boolean offsettable(LineNature nature) {
        return nature == LineNature.LINEAR || nature == LineNature.ACQUISITION || nature == LineNature.SERVICING;
    }

    /**
     * Returns the first day of the month of a date.
     *
     * @param date the date
     * @return the first day of its month
     */
    private static LocalDate firstOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    /** In-memory discount state of a coverage work unit (D7): the position and its running cumuls. */
    private static final class AdvanceState {
        /** The persistent position. */
        private AdvancePosition position;
        /** The total amount advanced. */
        private BigDecimal advanced = BigDecimal.ZERO;
        /** The running offset cumul. */
        private BigDecimal offset = BigDecimal.ZERO;
        /** The number of ADVANCE lines written at versement. */
        private int versementLines;

        /**
         * Tells whether the advance is still active.
         *
         * @return {@code true} if the position is active
         */
        private boolean active() {
            return position.status == AdvanceStatus.ACTIVE;
        }
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
     * @return the written line
     */
    private CommissionLine writeLine(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
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
        return line;
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
        if (version.rule.family != RuleFamily.LINEAR
                || fr.alptis.remuneration.engine.families.LinearFamily.fixedRate(version.parameters) != null) {
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
                coverage.annualPremium, version.outputNature, version.parameters);
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
