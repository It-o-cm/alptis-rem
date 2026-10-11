package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.AdvanceDebtHandling;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.TransferStatus;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.ConditionOperator;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.EnrollmentStatus;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.ProductionCounter;
import fr.alptis.remuneration.domain.ConditionFailureMode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.RuleCondition;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.SurcommissionAllocation;
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
import fr.alptis.remuneration.domain.Scale;
import fr.alptis.remuneration.domain.ScaleTier;
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
        Period period = findOrCreatePeriod(entity, periodMonth);

        List<RuleVersion> versions = activeVersions(entity);

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

        // Apply the portfolio transfers that take effect by this period (REF-09, CR-80): reassign the
        // ceded garanties to the transferee, so their later premiums go to it, and settle the discount
        // receivable on the transferor when it repays it.
        applyPortfolioTransfers(entity, period, run);

        List<BusinessEvent> events = BusinessEvent.list(
                "entity = ?1 and processingStatus = ?2 order by coverage.id, eventDate, receivedDate, sourceKey",
                entity, ProcessingStatus.PENDING);

        // Group the events by coverage (D4): one work unit per garantie, the rest handled on their own.
        java.util.Map<Long, List<BusinessEvent>> byCoverage = new java.util.LinkedHashMap<>();
        List<BusinessEvent> coverless = new ArrayList<>();
        for (BusinessEvent event : events) {
            // Record the period this event was calculated in, so the period controls (data quality,
            // no-rule) can find the events they must check (section 8.6).
            event.eligiblePeriod = period;
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
     * Runs a partial recalculation before validation (CAL-12, decision D13): re-processes the
     * garanties designated by a distributor, a product or a rule, dropping their non-validated lines
     * and replaying the premium path, without touching the rest of the period. The cumuls and account
     * steps (times 2 and 3) are replayed by the next full run or at validation.
     *
     * @param entityCode  the entity code
     * @param periodMonth the processing month
     * @param scopeType   the scope kind: {@code DISTRIBUTOR}, {@code PRODUCT} or {@code RULE}
     * @param scopeValue  the scoped code
     * @return the recorded calculation run
     */
    @Transactional
    public CalculationRun runPartial(String entityCode, LocalDate periodMonth, String scopeType,
                                     String scopeValue) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new fr.alptis.remuneration.domain.ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        Period period = findOrCreatePeriod(entity, periodMonth);
        List<RuleVersion> versions = activeVersions(entity);
        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = RunType.ACTUAL;
        run.status = RunStatus.RUNNING;
        run.startedAt = clock.now();
        run.launchedBy = "engine-partial";
        run.configHash = HashUtil.sha256(versions.stream().map(v -> String.valueOf(v.getId())).sorted().toList()
                .toString());
        run.parameters = "{\"partial\":{\"type\":\"" + scopeType + "\",\"value\":\"" + scopeValue + "\"}}";
        run.setCreatedAt(clock.now());
        run.persist();

        List<Coverage> coverages = scopedCoverages(entity, period, scopeType, scopeValue);
        int lineCount = 0;
        for (Coverage coverage : coverages) {
            // D13: drop this coverage's non-validated lines, then replay its events in order.
            CommissionLine.delete("period = ?1 and coverage = ?2 and status in ?3", period, coverage,
                    List.of(LineStatus.EARNED, LineStatus.FORECAST));
            List<BusinessEvent> events = BusinessEvent.list(
                    "entity = ?1 and coverage = ?2 order by eventDate, receivedDate, sourceKey", entity, coverage);
            for (BusinessEvent event : events) {
                event.processingStatus = ProcessingStatus.PENDING;
            }
            lineCount += processUnit(period, run, coverage, events, versions);
        }

        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"coverages\":" + coverages.size() + ",\"lines\":" + lineCount + "}";
        return run;
    }

    /**
     * Resolves the coverages designated by a partial-recalc scope.
     *
     * @param entity     the entity
     * @param period     the period
     * @param scopeType  the scope kind
     * @param scopeValue the scoped code
     * @return the scoped coverages
     */
    private List<Coverage> scopedCoverages(LegalEntity entity, Period period, String scopeType,
                                           String scopeValue) {
        return switch (scopeType) {
            case "DISTRIBUTOR" -> CoverageAssignment.<CoverageAssignment>list(
                    "productionCode.distributor.partnerCode = ?1 and productionCode.entity = ?2",
                    scopeValue, entity).stream().map(a -> a.coverage).distinct().toList();
            case "PRODUCT" -> Coverage.<Coverage>list(
                    "contract.entity = ?1 and contract.product.code = ?2", entity, scopeValue);
            case "RULE" -> CommissionLine.<CommissionLine>list(
                    "period = ?1 and ruleVersion.rule.code = ?2 and coverage is not null", period, scopeValue)
                    .stream().map(l -> l.coverage).distinct().toList();
            default -> throw new fr.alptis.remuneration.domain.ValidationException(
                    "Périmètre de recalcul inconnu : " + scopeType);
        };
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
        List<RuleVersion> savingsRules = savingsDiscountRules(coverage, versions);
        RuleVersion outboundSavings = savingsRuleOf(savingsRules, Direction.OUTBOUND);
        RuleVersion inboundSavings = savingsRuleOf(savingsRules, Direction.INBOUND);
        boolean savings = outboundSavings != null || inboundSavings != null;
        AdvanceState advance = savings ? null : openAdvanceIfEligible(period, run, coverage);
        if (advance != null) {
            count += advance.versementLines;
        }
        if (savings) {
            count += openSavingsDiscount(period, run, coverage, outboundSavings);
        }
        for (BusinessEvent event : events) {
            if (event.eventType == EventType.PREMIUM_CANCELLED) {
                count += cancel(period, run, event);
            } else if (event.eventType == EventType.UNPAID_NOTICE) {
                count += unpaid(period, run, event, coverage, advance);
            } else if (savings && event.eventType == EventType.SCHEDULED_PAYMENT) {
                // The retirement discount covers the scheduled payments of years one to five, so they
                // produce no line and are marked treated, not held under P8 (section 6.6).
                event.processingStatus = ProcessingStatus.PROCESSED;
            } else if (savings && isSavingsRecall(event.eventType)) {
                count += savingsRecall(period, run, coverage, event, outboundSavings, inboundSavings);
            } else if (isTerminal(event.eventType)) {
                count += rupture(period, run, event, coverage, advance);
                count += clawback(period, run, event, coverage);
                count += rcdReprise(period, run, event, coverage);
            } else if (event.eventType == EventType.EARLY_REPAYMENT) {
                count += earlyRepayment(period, run, event, coverage, versions);
            } else {
                List<CommissionLine> written = processPremium(period, run, event, coverage, versions);
                count += written.size();
                count += apureAndSettle(period, run, coverage, advance, written);
            }
        }
        if (savings) {
            count += savingsReadjustAndSettle(period, run, coverage, outboundSavings, inboundSavings);
        } else {
            count += settleAtDueDate(period, run, coverage, advance);
        }
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
            if (eventDriven(version.rule.family)
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
        // P12: a premium whose covered period straddles a change of the garantie's remuneration choice
        // is split pro rata of the days under each choice; otherwise it stays on the event-date choice.
        for (RateSegment segment : proratedSegments(coverage, event, choice)) {
            for (RuleVersion version : kept) {
                if (belowMinRank(event, version)) {
                    continue;
                }
                ComputingFamily family = families.get(version.rule.family);
                CalculationInput input = input(event, coverage, segment.choice(), version, segment.basis());
                for (ProposedLine proposed : family.propose(input)) {
                    written.addAll(writeLine(period, run, event, coverage, version, proposed));
                }
            }
        }
        event.processingStatus = ProcessingStatus.PROCESSED;
        return written;
    }

    /**
     * Tells whether a family is driven by a single business event, and so is a candidate in the
     * per-event premium loop. The advance is driven by the échéance and the discount lifecycle, and
     * the tiered complement by the annual close, so both are handled apart.
     *
     * @param family the rule family
     * @return {@code true} for an event-driven family
     */
    private static boolean eventDriven(RuleFamily family) {
        return family != RuleFamily.ADVANCE && family != RuleFamily.TIERED && family != RuleFamily.PORTFOLIO;
    }

    /**
     * Tells whether a linear rule declares a minimum rank in the calendar year (section 6.6: the
     * second arbitrage of the year, CR-83) that the event has not yet reached, in which case the rule
     * produces no line for it.
     *
     * @param event   the event
     * @param version the rule version
     * @return {@code true} when the event is below the rule's minimum rank
     */
    private static boolean belowMinRank(BusinessEvent event, RuleVersion version) {
        if (version.rule.family != RuleFamily.LINEAR) {
            return false;
        }
        Integer min = fr.alptis.remuneration.engine.families.LinearFamily.minRankInCalendarYear(version.parameters);
        return min != null && calendarYearRank(event) < min;
    }

    /**
     * Returns the rank of an event among the same contract's events of the same type in its calendar
     * year, counting those at or before it by date then by identity, so a rerun is stable (D8).
     *
     * @param event the event
     * @return the one-based rank in the calendar year
     */
    private static long calendarYearRank(BusinessEvent event) {
        LocalDate yearStart = event.eventDate.withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
        return BusinessEvent.count(
                "contract = ?1 and eventType = ?2 and eventDate >= ?3 and eventDate <= ?4 "
                        + "and (eventDate < ?5 or (eventDate = ?5 and id <= ?6))",
                event.contract, event.eventType, yearStart, yearEnd, event.eventDate, event.getId());
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
                inverse.account = source.account;
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
        String explanation = "{\"family\":\"CLAWBACK\",\"base\":" + base.toPlainString()
                + ",\"rate\":" + rate.toPlainString() + ",\"amount\":" + amount.negate().toPlainString() + "}";
        return writeShared(period, run, event, coverage, version, null, version.rule.plan.direction,
                LineNature.CLAWBACK, null, null, amount.negate(), explanation).size();
    }

    /**
     * Handles the early repayment of a borrower loan (section 6.5, CR-81): a moteur treatment outside
     * the reprise policy. Within the rule's early-repayment window, the commission is recomputed at the
     * rule's standard linear rate on the premiums collected so far, and the gap with what was already
     * paid is written as a {@code CLAWBACK} line (negative when too much was advanced). Beyond the
     * window, nothing is clawed back.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the early-repayment event
     * @param coverage the coverage
     * @param versions the active versions of the entity
     * @return the number of clawback lines written
     */
    private int earlyRepayment(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                               List<RuleVersion> versions) {
        event.processingStatus = ProcessingStatus.PROCESSED;
        Map<String, String> attributes = attributes(event, coverage, choiceAt(coverage, coverage.effectDate));
        attributes.put("eventType", EventType.PREMIUM_COLLECTED.name());
        RuleVersion rule = emprunteurRule(versions, attributes);
        if (rule == null) {
            return 0;
        }
        int window = fr.alptis.remuneration.engine.families.UpfrontFamily.earlyRepaymentMonths(rule.parameters);
        if (ChronoUnit.MONTHS.between(coverage.effectDate, event.eventDate) > window) {
            return 0;
        }
        BigDecimal recalcRate = fr.alptis.remuneration.engine.families.UpfrontFamily.recalculationRate(rule.parameters);
        BigDecimal collected = BigDecimal.ZERO;
        for (BusinessEvent premium : BusinessEvent.<BusinessEvent>list(
                "coverage = ?1 and eventType = ?2 and basisAmount is not null",
                coverage, EventType.PREMIUM_COLLECTED)) {
            collected = collected.add(premium.basisAmount);
        }
        BigDecimal paid = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list("coverage = ?1 and status in ?2", coverage,
                List.of(LineStatus.EARNED, LineStatus.PAYABLE, LineStatus.PAID))) {
            paid = paid.add(line.amount);
        }
        BigDecimal recalc = collected.multiply(recalcRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = recalc.subtract(paid);
        if (delta.signum() == 0) {
            return 0;
        }
        String explanation = "{\"family\":\"EARLY_REPAYMENT\",\"collected\":" + collected.toPlainString()
                + ",\"recalcRate\":" + recalcRate.toPlainString() + ",\"recalc\":" + recalc.toPlainString()
                + ",\"paid\":" + paid.toPlainString() + ",\"delta\":" + delta.toPlainString() + "}";
        return writeShared(period, run, event, coverage, rule, null, rule.rule.plan.direction,
                LineNature.CLAWBACK, null, null, delta, explanation).size();
    }

    /**
     * Finds the active upfront rule that carries the borrower early-repayment parameters and matches
     * the premium context of the coverage, or null when there is none.
     *
     * @param versions   the active versions
     * @param attributes the premium-context attributes of the coverage
     * @return the matching borrower rule version, or null
     */
    private RuleVersion emprunteurRule(List<RuleVersion> versions, Map<String, String> attributes) {
        for (RuleVersion version : versions) {
            if (version.rule.family != RuleFamily.UPFRONT) {
                continue;
            }
            if (fr.alptis.remuneration.engine.families.UpfrontFamily.earlyRepaymentMonths(version.parameters) == null) {
                continue;
            }
            if (fr.alptis.remuneration.engine.families.UpfrontFamily.recalculationRate(version.parameters) == null) {
                continue;
            }
            if (scopeMatcher.matches(version.scope, attributes)) {
                return version;
            }
        }
        return null;
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
    /**
     * Applies the portfolio transfers that take effect on or before the end of the period and are not
     * yet processed (REF-09, section 6.4, CR-80). For each ceded garantie: when the transferor repays
     * the discount receivable, the active discount position is cancelled and a regularisation line of
     * the remaining gap is booked on the transferor's account; then the main assignment is moved to the
     * transferee from the effect date, so later premiums — and, when the receivable is taken over, the
     * continuing apurement — are attributed to it. When the receivable is taken over, no entry is passed
     * at the transfer.
     *
     * @param entity the entity
     * @param period the period
     * @param run    the run
     */
    private void applyPortfolioTransfers(LegalEntity entity, Period period, CalculationRun run) {
        LocalDate periodEnd = period.periodMonth.plusMonths(1).minusDays(1);
        List<PortfolioTransfer> transfers = PortfolioTransfer.<PortfolioTransfer>list(
                "entity = ?1 and status = ?2 and effectDate <= ?3 order by effectDate, id",
                entity, TransferStatus.PENDING, periodEnd);
        for (PortfolioTransfer transfer : transfers) {
            for (CoverageAssignment main : cededMainAssignments(entity, transfer)) {
                Coverage coverage = main.coverage;
                AdvancePosition pos = AdvancePosition.<AdvancePosition>find("coverage = ?1", coverage).firstResult();
                boolean repaid = transfer.advanceDebtHandling == AdvanceDebtHandling.REPAID_BY_TRANSFEROR;
                if (repaid && pos != null && pos.status == AdvanceStatus.ACTIVE) {
                    repayAdvanceOnTransfer(period, run, coverage, pos);
                }
                reassignMain(main, transfer);
            }
            transfer.status = TransferStatus.PROCESSED;
        }
    }

    /**
     * Returns the current main assignments of the transferor caught by a transfer's scope (all of them
     * when the scope is empty).
     *
     * @param entity   the entity
     * @param transfer the transfer
     * @return the ceded main assignments
     */
    private List<CoverageAssignment> cededMainAssignments(LegalEntity entity, PortfolioTransfer transfer) {
        if (transfer.scope == null) {
            return CoverageAssignment.list(
                    "role = ?1 and validTo is null and productionCode.distributor = ?2 and productionCode.entity = ?3",
                    fr.alptis.remuneration.domain.AssignmentRole.MAIN, transfer.fromDistributor, entity);
        }
        return CoverageAssignment.list(
                "role = ?1 and validTo is null and productionCode.distributor = ?2 and productionCode.entity = ?3 "
                        + "and productionCode.code in ?4",
                fr.alptis.remuneration.domain.AssignmentRole.MAIN, transfer.fromDistributor, entity,
                scopeCodes(transfer.scope));
    }

    /**
     * Reads a transfer scope — a JSON array of production-code strings — into a list, empty when the
     * scope is malformed.
     *
     * @param scope the scope JSON
     * @return the production-code list
     */
    List<String> scopeCodes(String scope) {
        List<String> codes = new ArrayList<>();
        for (JsonNode node : readArrayNode(scope)) {
            codes.add(node.asText());
        }
        return codes;
    }

    /**
     * Parses a JSON array, returning an empty iterable when the text is not a JSON array.
     *
     * @param json the JSON text
     * @return the array node, or an empty node
     */
    private JsonNode readArrayNode(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node.isArray() ? node : mapper.createArrayNode();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return mapper.createArrayNode();
        }
    }

    /**
     * Cancels the active discount position of a ceded garantie whose receivable the transferor repays,
     * and books the remaining gap (offset cumul minus advanced) as a regularisation line on the
     * transferor's account (section 6.4: the cession is a rupture event).
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param pos      the active discount position
     */
    private void repayAdvanceOnTransfer(Period period, CalculationRun run, Coverage coverage, AdvancePosition pos) {
        BigDecimal advanced = CommissionLine.<CommissionLine>list("advancePosition = ?1 and nature = ?2",
                pos, LineNature.ADVANCE).stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal offset = CommissionLine.<CommissionLine>list("advancePosition = ?1 and status = ?2",
                pos, LineStatus.OFFSET).stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = offset.subtract(advanced);
        pos.status = AdvanceStatus.CANCELLED;
        pos.closedAt = clock.now();
        pos.closeReason = "PORTFOLIO_TRANSFER_REPAID";
        String explanation = "{\"family\":\"ADVANCE_ADJUSTMENT\",\"trigger\":\"PORTFOLIO_TRANSFER\",\"delta\":"
                + delta.toPlainString() + "}";
        writeShared(period, run, null, coverage, null, pos, Direction.OUTBOUND, LineNature.ADVANCE_ADJUSTMENT,
                null, null, delta, explanation);
    }

    /**
     * Moves a main assignment to the transferee from the transfer's effect date: the current assignment
     * is closed the day before, and a new one is opened on the transferee's first production code. When
     * the transferee has no production code, the assignment is left unchanged.
     *
     * @param main     the transferor's current main assignment
     * @param transfer the transfer
     */
    private void reassignMain(CoverageAssignment main, PortfolioTransfer transfer) {
        ProductionCode target = ProductionCode.<ProductionCode>find(
                "entity = ?1 and distributor = ?2 order by code", main.productionCode.entity,
                transfer.toDistributor).firstResult();
        if (target == null) {
            return;
        }
        main.validTo = transfer.effectDate.minusDays(1);
        CoverageAssignment next = new CoverageAssignment();
        next.coverage = main.coverage;
        next.productionCode = target;
        next.role = fr.alptis.remuneration.domain.AssignmentRole.MAIN;
        next.share = main.share;
        next.origin = fr.alptis.remuneration.domain.AssignmentOrigin.TRANSFER;
        next.validFrom = transfer.effectDate;
        next.setCreatedAt(clock.now());
        next.persist();
    }

    private AdvanceState openAdvanceIfEligible(Period period, CalculationRun run, Coverage coverage) {
        // A garantie carries at most one discount position over its life (uq_advance_position_coverage).
        // When one already exists, the unit does not open a second: the position is reconstructed from
        // its validated lines (D8) and reused so its apurement continues from one month to the next.
        AdvancePosition existing = AdvancePosition.<AdvancePosition>find("coverage = ?1", coverage).firstResult();
        if (existing != null) {
            if (existing.status != AdvanceStatus.ACTIVE) {
                // Settled or cancelled in an earlier period: the discount is closed and the premiums of
                // this period are paid normally.
                return null;
            }
            BigDecimal advanced = CommissionLine.<CommissionLine>list("advancePosition = ?1 and nature = ?2",
                    existing, LineNature.ADVANCE).stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (advanced.signum() != 0) {
                // Versement written in an earlier period: reconstruct the running state and reuse the
                // position without writing the versement again.
                AdvanceState carried = new AdvanceState();
                carried.position = existing;
                carried.advanced = advanced;
                carried.offset = CommissionLine.<CommissionLine>list("advancePosition = ?1 and status = ?2",
                        existing, LineStatus.OFFSET).stream().map(l -> l.amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                carried.versementLines = 0;
                return carried;
            }
            // The position was opened in this same not-yet-validated period, whose versement lines a
            // rerun has just dropped (D13): re-grant the versement into the same position, so no new
            // position is created and the convention cap is not counted twice.
            return grantVersement(period, run, coverage, choiceAt(coverage, coverage.effectDate), existing);
        }

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

        AdvancePosition position = new AdvancePosition();
        position.advanceAgreement = agreement;
        return grantVersement(period, run, coverage, choice, position);
    }

    /**
     * Grants (or re-grants, on a same-period recompute) the discount versement into a position: the
     * advance amount is proposed by the advance family on the annualised premium, the position's cumul
     * columns are reset, the position is persisted when new, and the {@code ADVANCE} lines are written.
     *
     * @param period   the period
     * @param run      the run
     * @param coverage the coverage
     * @param choice   the remuneration choice at the effect date
     * @param position the position to grant into (new or reused)
     * @return the opened advance state
     */
    private AdvanceState grantVersement(Period period, CalculationRun run, Coverage coverage,
                                        RemunerationChoice choice, AdvancePosition position) {
        CalculationInput input = new CalculationInput(EventType.PREMIUM_COLLECTED, null, choice.linearRate,
                choice.acquisitionRate, choice.servicingRate, coverage.effectDate, coverage.effectDate, null, null,
                null, null, null, coverage.annualPremium, LineNature.ADVANCE, null, null);
        List<ProposedLine> advanceLines = families.get(RuleFamily.ADVANCE).propose(input);

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
        if (position.getId() == null) {
            position.setCreatedAt(clock.now());
            position.persist();
        }

        AdvanceState state = new AdvanceState();
        state.position = position;
        state.advanced = BigDecimal.ZERO;
        state.offset = BigDecimal.ZERO;
        for (ProposedLine proposed : advanceLines) {
            List<CommissionLine> lines = writeShared(period, run, null, coverage, null, position, Direction.OUTBOUND,
                    LineNature.ADVANCE, null, null, proposed.amount(), proposed.explanation());
            state.advanced = state.advanced.add(proposed.amount());
            state.versementLines += lines.size();
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
                // Materialise the offset cumul on the position by nature (D8), so the discount
                // receivable (advanced − offset) stays right from one month to the next.
                addOffsetByNature(advance.position, line.nature, line.amount);
            }
        }
        BigDecimal delta = advance.offset.subtract(advance.advanced);
        if (delta.signum() >= 0) {
            return settle(period, run, coverage, advance, delta, "SETTLED_BY_OFFSET");
        }
        return 0;
    }

    /**
     * Adds an offset amount to the position's cumul column that matches the offset line's nature, so
     * the receivable it materialises (advanced minus offset) stays exact across periods.
     *
     * @param position the discount position
     * @param nature   the offset line's nature
     * @param amount   the offset amount
     */
    private static void addOffsetByNature(AdvancePosition position, LineNature nature, BigDecimal amount) {
        switch (nature) {
            case ACQUISITION -> position.offsetAcquisition = position.offsetAcquisition.add(amount);
            case SERVICING -> position.offsetServicing = position.offsetServicing.add(amount);
            default -> position.offsetLinear = position.offsetLinear.add(amount);
        }
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
            // The residual under tolerance is written off, not clawed back: no commission line, but an
            // ADVANCE_WRITE_OFF accounting entry is booked for it at business validation (section 10.2,
            // CR-78). The amount is carried on the position so the accounting can find it.
            advance.position.adjustmentAmount = delta.abs();
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
        String explanation = "{\"family\":\"ADVANCE_ADJUSTMENT\",\"delta\":" + amount.toPlainString() + "}";
        return writeShared(period, run, null, coverage, null, advance.position, Direction.OUTBOUND,
                LineNature.ADVANCE_ADJUSTMENT, null, null, amount, explanation).size();
    }

    /**
     * Returns the active rule versions of the entity that drive a retirement-savings discount on the
     * coverage (section 6.6): an advance family rule whose settlement rule is the fifth anniversary,
     * matching the coverage's perimeter, in any direction (owed to the distributor or expected from
     * the insurer).
     *
     * @param coverage the coverage
     * @param versions the active versions of the entity
     * @return the matching savings-discount rules (possibly empty)
     */
    private List<RuleVersion> savingsDiscountRules(Coverage coverage, List<RuleVersion> versions) {
        Map<String, String> attributes = coverageAttributes(coverage);
        List<RuleVersion> matches = new ArrayList<>();
        for (RuleVersion version : versions) {
            if (version.rule.family == RuleFamily.ADVANCE && isSavingsRule(version)
                    && activeAt(version, coverage.effectDate) && scopeMatcher.matches(version.scope, attributes)) {
                matches.add(version);
            }
        }
        return matches;
    }

    /**
     * Returns the first savings rule of the given direction among the matches, or null.
     *
     * @param savingsRules the matching savings rules
     * @param direction    the wanted direction
     * @return the rule of that direction, or null
     */
    private static RuleVersion savingsRuleOf(List<RuleVersion> savingsRules, Direction direction) {
        for (RuleVersion version : savingsRules) {
            if (version.rule.plan.direction == direction) {
                return version;
            }
        }
        return null;
    }

    /**
     * Tells whether an advance rule is a retirement-savings discount, settled at the fifth anniversary
     * without a convention (section 6.6).
     *
     * @param version the rule version
     * @return {@code true} for a savings discount rule
     */
    private boolean isSavingsRule(RuleVersion version) {
        return "FIFTH_ANNIVERSARY".equals(paramText(mapper, version.parameters, "settlementRule"));
    }

    /**
     * Reads a text parameter, returning null when the parameters or the field are absent, blank or
     * malformed.
     *
     * @param mapper the Jackson mapper
     * @param params the parameters JSON
     * @param field  the field name
     * @return the text value, or null
     */
    static String paramText(ObjectMapper mapper, String params, String field) {
        JsonNode node = readObject(mapper, params);
        return node == null ? null : node.path(field).asText(null);
    }

    /**
     * Reads a boolean parameter, defaulting to false when the parameters or the field are absent,
     * blank or malformed.
     *
     * @param mapper the Jackson mapper
     * @param params the parameters JSON
     * @param field  the field name
     * @return the boolean value, or false
     */
    static boolean paramFlag(ObjectMapper mapper, String params, String field) {
        JsonNode node = readObject(mapper, params);
        return node != null && node.path(field).asBoolean(false);
    }

    /**
     * Reads a decimal parameter, returning null when the parameters or the field are absent, blank or
     * malformed.
     *
     * @param mapper the Jackson mapper
     * @param params the parameters JSON
     * @param field  the field name
     * @return the decimal value, or null
     */
    static BigDecimal paramDecimal(ObjectMapper mapper, String params, String field) {
        JsonNode node = readObject(mapper, params);
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null ? null : value.decimalValue();
    }

    /**
     * Reads a decimal parameter, defaulting to zero when absent.
     *
     * @param mapper the Jackson mapper
     * @param params the parameters JSON
     * @param field  the field name
     * @return the decimal value, or zero
     */
    static BigDecimal paramDecimalOrZero(ObjectMapper mapper, String params, String field) {
        BigDecimal value = paramDecimal(mapper, params, field);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Tells whether an event breaks a retirement discount and triggers its recall (section 6.6): a
     * plan suspension, a surrender or an outbound transfer.
     *
     * @param type the event type
     * @return {@code true} for a recall-triggering event
     */
    private static boolean isSavingsRecall(EventType type) {
        return type == EventType.PLAN_SUSPENDED || type == EventType.SURRENDER
                || type == EventType.OUTBOUND_TRANSFER;
    }

    /**
     * Opens the retirement discount of a coverage (section 6.6): on the first scheduled payment, once
     * the period reaches that payment's month and no position is open yet, the discount is paid on the
     * annualised scheduled payment at the rule's rate and the position is opened, settled at the fifth
     * anniversary of the first payment, without a convention and without offsetting.
     *
     * @param period          the period
     * @param run             the run
     * @param coverage        the coverage
     * @param outboundSavings the outbound savings rule, or null when only the insurer side applies
     * @return the number of advance lines written (0 or 1)
     */
    private int openSavingsDiscount(Period period, CalculationRun run, Coverage coverage, RuleVersion outboundSavings) {
        if (outboundSavings == null || activeSavingsPosition(coverage) != null) {
            return 0;
        }
        BusinessEvent first = firstScheduledPayment(coverage);
        if (first == null || period.periodMonth.isBefore(firstOfMonth(first.eventDate))) {
            return 0;
        }
        BigDecimal rate = advanceRate(outboundSavings);
        BigDecimal base = basisOf(first).multiply(BigDecimal.valueOf(12));
        BigDecimal advanced = base.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        AdvancePosition position = new AdvancePosition();
        position.coverage = coverage;
        position.status = AdvanceStatus.ACTIVE;
        position.paidAt = clock.now();
        position.settlementDueDate = first.eventDate.plusYears(5);
        position.advancedAcquisition = BigDecimal.ZERO;
        position.advancedServicing = BigDecimal.ZERO;
        position.advancedLinear = advanced;
        position.offsetAcquisition = BigDecimal.ZERO;
        position.offsetServicing = BigDecimal.ZERO;
        position.offsetLinear = BigDecimal.ZERO;
        position.setCreatedAt(clock.now());
        position.persist();

        String explanation = "{\"family\":\"ADVANCE\",\"savings\":true,\"annualisedPayment\":"
                + base.toPlainString() + ",\"rate\":" + rate.toPlainString() + ",\"amount\":"
                + advanced.toPlainString() + "}";
        writeSavingsLine(period, run, coverage, LineNature.ADVANCE, advanced, position, explanation);
        return 1;
    }

    /**
     * Recalls a retirement discount broken within its five years (section 6.6): the recall is the
     * advance less the scheduled payments encashed times the contract's linear rate; it is a
     * {@code CLAWBACK} line on the distributor side and an expected amount on the insurer side, and the
     * position leaves the discounted mode for good.
     *
     * @param period          the period
     * @param run             the run
     * @param coverage        the coverage
     * @param event           the recall-triggering event
     * @param outboundSavings the outbound savings rule, or null
     * @param inboundSavings  the inbound (insurer) savings rule, or null
     * @return the number of clawback lines written (0 or 1)
     */
    private int savingsRecall(Period period, CalculationRun run, Coverage coverage, BusinessEvent event,
                              RuleVersion outboundSavings, RuleVersion inboundSavings) {
        event.processingStatus = ProcessingStatus.PROCESSED;
        RemunerationChoice choice = choiceAt(coverage, event.eventDate);
        BigDecimal paid = scheduledSum(coverage, event.eventDate);
        int count = 0;
        AdvancePosition position = activeSavingsPosition(coverage);
        if (outboundSavings != null && position != null) {
            BigDecimal recall = position.advancedLinear
                    .subtract(paid.multiply(recallLinearRate(outboundSavings, choice)))
                    .setScale(2, RoundingMode.HALF_UP);
            position.status = AdvanceStatus.CANCELLED;
            position.closedAt = clock.now();
            position.closeReason = event.eventType.name();
            if (recall.signum() != 0) {
                String explanation = "{\"family\":\"CLAWBACK\",\"savingsRecall\":true,\"advanced\":"
                        + position.advancedLinear.toPlainString() + ",\"paid\":" + paid.toPlainString()
                        + ",\"amount\":" + recall.negate().toPlainString() + "}";
                writeSavingsLine(period, run, coverage, LineNature.CLAWBACK, recall.negate(), position, explanation);
                count++;
            }
        }
        if (inboundSavings != null) {
            BusinessEvent first = firstScheduledPayment(coverage);
            if (first != null) {
                BigDecimal advanced = basisOf(first).multiply(BigDecimal.valueOf(12))
                        .multiply(advanceRate(inboundSavings)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal recall = advanced.subtract(paid.multiply(recallLinearRate(inboundSavings, choice)))
                        .setScale(2, RoundingMode.HALF_UP);
                recordExpected(period, coverage, "SAVINGS_RECALL", recall.negate());
            }
        }
        return count;
    }

    /**
     * Settles a retirement discount at its fifth anniversary without regularisation, or applies the
     * annual readjustment at an earlier anniversary (section 6.6).
     *
     * @param period          the period
     * @param run             the run
     * @param coverage        the coverage
     * @param outboundSavings the outbound savings rule, or null
     * @param inboundSavings  the inbound (insurer) savings rule, or null
     * @return the number of lines written
     */
    private int savingsReadjustAndSettle(Period period, CalculationRun run, Coverage coverage,
                                         RuleVersion outboundSavings, RuleVersion inboundSavings) {
        AdvancePosition position = activeSavingsPosition(coverage);
        if (position != null && !period.periodMonth.isBefore(firstOfMonth(position.settlementDueDate))) {
            position.status = AdvanceStatus.SETTLED;
            position.closedAt = clock.now();
            position.closeReason = "FIFTH_ANNIVERSARY";
            return 0;
        }
        int count = 0;
        if (outboundSavings != null && position != null) {
            count += savingsReadjust(period, run, coverage, outboundSavings, position, Direction.OUTBOUND);
        }
        if (inboundSavings != null) {
            count += savingsReadjust(period, run, coverage, inboundSavings, null, Direction.INBOUND);
        }
        return count;
    }

    /**
     * Applies the annual readjustment of a retirement discount (section 6.6): at an anniversary of the
     * first scheduled payment within the first five years, the gap between the scheduled payments
     * encashed over twelve months and the initial annualised base, times the rate, is a discount
     * supplement ({@code ADVANCE}) or a reprise ({@code CLAWBACK}) when it exceeds the tolerance; on the
     * insurer side it is only an expected amount.
     *
     * @param period    the period
     * @param run       the run
     * @param coverage  the coverage
     * @param version   the savings rule
     * @param position  the discount position, or null on the insurer side
     * @param direction the direction of the rule
     * @return the number of lines written
     */
    private int savingsReadjust(Period period, CalculationRun run, Coverage coverage, RuleVersion version,
                                AdvancePosition position, Direction direction) {
        if (!annualReadjustment(version)) {
            return 0;
        }
        BigDecimal rate = advanceRate(version);
        LocalDate anchor;
        BigDecimal base;
        if (position != null) {
            anchor = position.settlementDueDate.minusYears(5);
            base = position.advancedLinear.divide(rate, 2, RoundingMode.HALF_UP);
        } else {
            BusinessEvent first = firstScheduledPayment(coverage);
            if (first == null) {
                return 0;
            }
            anchor = first.eventDate;
            base = basisOf(first).multiply(BigDecimal.valueOf(12));
        }
        LocalDate anniversary = anniversaryInPeriod(anchor, period);
        if (anniversary == null) {
            return 0;
        }
        BigDecimal collected = scheduledSumBetween(coverage, anniversary.minusYears(1), anniversary);
        BigDecimal gap = collected.subtract(base);
        if (base.signum() > 0 && gap.abs().divide(base, 10, RoundingMode.HALF_UP)
                .compareTo(readjustmentTolerance(version)) < 0) {
            return 0;
        }
        BigDecimal amount = gap.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0) {
            return 0;
        }
        if (direction == Direction.INBOUND) {
            recordExpected(period, coverage, "SAVINGS_READJUST", amount);
            return 0;
        }
        LineNature nature = amount.signum() > 0 ? LineNature.ADVANCE : LineNature.CLAWBACK;
        String explanation = "{\"family\":\"" + nature.name() + "\",\"savingsReadjust\":true,\"collected\":"
                + collected.toPlainString() + ",\"base\":" + base.toPlainString() + ",\"amount\":"
                + amount.toPlainString() + "}";
        writeSavingsLine(period, run, coverage, nature, amount, position, explanation);
        return 1;
    }

    /**
     * Returns the anniversary of the anchor date falling in the period's month within the first four
     * years (the fifth being the settlement), or null.
     *
     * @param anchor the first scheduled payment date
     * @param period the period
     * @return the matching anniversary date, or null
     */
    private static LocalDate anniversaryInPeriod(LocalDate anchor, Period period) {
        for (int year = 1; year <= 4; year++) {
            LocalDate anniversary = anchor.plusYears(year);
            if (firstOfMonth(anniversary).isEqual(period.periodMonth)) {
                return anniversary;
            }
        }
        return null;
    }

    /**
     * Returns the active savings discount position of a coverage (the one with no convention), or null.
     *
     * @param coverage the coverage
     * @return the active savings position, or null
     */
    private static AdvancePosition activeSavingsPosition(Coverage coverage) {
        return AdvancePosition.find("coverage = ?1 and status = ?2 and advanceAgreement is null",
                coverage, AdvanceStatus.ACTIVE).firstResult();
    }

    /**
     * Returns the coverage's earliest scheduled-payment event, the anchor of the retirement discount,
     * or null.
     *
     * @param coverage the coverage
     * @return the first scheduled-payment event, or null
     */
    private static BusinessEvent firstScheduledPayment(Coverage coverage) {
        return BusinessEvent.find("coverage = ?1 and eventType = ?2 order by eventDate, id",
                coverage, EventType.SCHEDULED_PAYMENT).firstResult();
    }

    /**
     * Returns the sum of the coverage's scheduled payments encashed up to a date.
     *
     * @param coverage the coverage
     * @param upTo     the inclusive upper date
     * @return the summed basis of the scheduled payments
     */
    private static BigDecimal scheduledSum(Coverage coverage, LocalDate upTo) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                "coverage = ?1 and eventType = ?2 and eventDate <= ?3", coverage, EventType.SCHEDULED_PAYMENT, upTo)) {
            sum = sum.add(basisOf(event));
        }
        return sum;
    }

    /**
     * Returns the sum of the coverage's scheduled payments encashed within a date range (inclusive).
     *
     * @param coverage the coverage
     * @param from     the inclusive lower date
     * @param to       the inclusive upper date
     * @return the summed basis of the scheduled payments in the range
     */
    private static BigDecimal scheduledSumBetween(Coverage coverage, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                "coverage = ?1 and eventType = ?2 and eventDate >= ?3 and eventDate <= ?4",
                coverage, EventType.SCHEDULED_PAYMENT, from, to)) {
            sum = sum.add(basisOf(event));
        }
        return sum;
    }

    /**
     * Returns the basis of an event, defaulting to zero when absent.
     *
     * @param event the event
     * @return the basis amount, or zero
     */
    private static BigDecimal basisOf(BusinessEvent event) {
        return event.basisAmount == null ? BigDecimal.ZERO : event.basisAmount;
    }

    /**
     * Reads the advance rate a savings rule imposes (section 6.6: 8% owed, 10% expected).
     *
     * @param version the savings rule
     * @return the rate, or zero when absent
     */
    private BigDecimal advanceRate(RuleVersion version) {
        return paramDecimalOrZero(mapper, version.parameters, "rate");
    }

    /**
     * Reads the linear rate used for a recall: the rule's own {@code recallRate} when present (the
     * insurer frais rate), otherwise the contract's linear rate carried by the choice (frais minus
     * half a point).
     *
     * @param version the savings rule
     * @param choice  the remuneration choice, possibly null
     * @return the recall linear rate
     */
    private BigDecimal recallLinearRate(RuleVersion version, RemunerationChoice choice) {
        BigDecimal rate = paramDecimal(mapper, version.parameters, "recallRate");
        if (rate != null) {
            return rate;
        }
        return choice == null || choice.linearRate == null ? BigDecimal.ZERO : choice.linearRate;
    }

    /**
     * Tells whether a savings rule applies the annual readjustment.
     *
     * @param version the savings rule
     * @return {@code true} when the readjustment is enabled
     */
    private boolean annualReadjustment(RuleVersion version) {
        return paramFlag(mapper, version.parameters, "annualReadjustment");
    }

    /**
     * Reads the readjustment tolerance of a savings rule as a fraction, defaulting to zero (H-15).
     *
     * @param version the savings rule
     * @return the tolerance fraction
     */
    private BigDecimal readjustmentTolerance(RuleVersion version) {
        return paramDecimalOrZero(mapper, version.parameters, "readjustmentTolerance");
    }

    /**
     * Writes a savings commission line (advance, clawback or readjustment) attributed to the
     * coverage's main production code.
     *
     * @param period      the period
     * @param run         the run
     * @param coverage    the coverage
     * @param nature      the line nature
     * @param amount      the signed amount
     * @param position    the discount position, possibly null
     * @param explanation the line explanation
     */
    private void writeSavingsLine(Period period, CalculationRun run, Coverage coverage, LineNature nature,
                                  BigDecimal amount, AdvancePosition position, String explanation) {
        writeShared(period, run, null, coverage, null, position, Direction.OUTBOUND, nature, null, null, amount,
                explanation);
    }

    /**
     * Records the expected insurer commission of a coverage (P17, CAL-13): the amount the twin inbound
     * rule computes creates no line and no accounting entry; it is kept with the result of the
     * "received commission different from expected" control (section 6.6).
     *
     * @param period   the period
     * @param coverage the coverage
     * @param tag      the kind of expected amount (versement, recall, readjustment, surcommission)
     * @param expected the expected amount
     */
    private void recordExpected(Period period, Coverage coverage, String tag, BigDecimal expected) {
        BigDecimal received = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list("coverage = ?1 and nature = ?2",
                coverage, LineNature.INSURER)) {
            received = received.add(line.amount == null ? BigDecimal.ZERO : line.amount);
        }
        ControlResult result = new ControlResult();
        result.period = period;
        result.control = expectedControl(period.entity);
        result.objectType = "COVERAGE";
        result.objectId = coverage.getId();
        result.severity = ControlSeverity.WARNING;
        result.observedValue = received.toPlainString();
        result.expectedValue = expected.toPlainString();
        result.decision = ControlDecision.OPEN;
        result.justification = tag;
        result.setCreatedAt(clock.now());
        result.persist();
    }

    /**
     * Returns the entity's "received commission different from expected" control definition, creating
     * it when absent (dev profile).
     *
     * @param entity the entity
     * @return the control definition
     */
    private ControlDefinition expectedControl(LegalEntity entity) {
        return control(entity, "CAL-13", "Commission reçue différente de l'attendu", ControlFamily.CALCULATION);
    }

    /**
     * Returns an entity's control definition by code, creating it when absent (dev profile).
     *
     * @param entity the entity
     * @param code   the control code
     * @param label  the control label
     * @param family the control family
     * @return the control definition
     */
    private ControlDefinition control(LegalEntity entity, String code, String label, ControlFamily family) {
        ControlDefinition control = ControlDefinition.find("entity = ?1 and code = ?2", entity, code).firstResult();
        if (control == null) {
            control = new ControlDefinition();
            control.entity = entity;
            control.code = code;
            control.label = label;
            control.family = family;
            control.severity = ControlSeverity.WARNING;
            control.active = Boolean.TRUE;
            control.setCreatedAt(clock.now());
            control.persist();
        }
        return control;
    }

    /**
     * Runs the annual close of an entity (section 6.6): the retirement complementary remuneration owed
     * to each distributor (tiered, whole measure on the year's net scheduled payments) and the
     * expected insurer surcommission (a share of the year's scheduled payments, capped).
     *
     * @param entityCode  the entity code
     * @param periodMonth the first day of the close month
     * @param year        the production year
     * @return the recorded calculation run
     */
    @Transactional
    public CalculationRun runAnnualClose(String entityCode, LocalDate periodMonth, int year) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = findOrCreatePeriod(entity, periodMonth);
        List<RuleVersion> versions = activeVersions(entity);
        CalculationRun run = enrollmentRun(period, "annual-close", year);

        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        CommissionLine.delete("period = ?1 and status in ?2 and nature = ?3", period,
                List.of(LineStatus.EARNED, LineStatus.FORECAST), LineNature.COMPLEMENT);
        int lineCount = 0;
        for (RuleVersion version : versions) {
            if (version.rule.family == RuleFamily.TIERED && version.outputNature == LineNature.COMPLEMENT) {
                lineCount += annualComplement(period, run, version, from, to);
            } else if (version.rule.family == RuleFamily.LINEAR
                    && version.rule.plan.direction == Direction.INBOUND && isAnnual(version)) {
                expectedSurcommission(period, version, from, to);
            }
        }
        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"lines\":" + lineCount + "}";
        return run;
    }

    /**
     * Computes the retirement complementary remuneration of a tiered annual rule (section 6.6, CR-35):
     * the distributor's whole net production of the year sets the tier, whose rate applies to each of
     * its coverages' production, one {@code COMPLEMENT} line per coverage.
     *
     * @param period  the close period
     * @param run     the run
     * @param version the tiered complement rule
     * @param from    the first day of the production year
     * @param to      the last day of the production year
     * @return the number of complement lines written
     */
    private int annualComplement(Period period, CalculationRun run, RuleVersion version, LocalDate from, LocalDate to) {
        String tiers = tiersJson(version);
        Map<Long, List<Coverage>> byDistributor = new LinkedHashMap<>();
        Map<Long, BigDecimal> coverageProduction = new LinkedHashMap<>();
        Map<Long, BigDecimal> distributorTotal = new LinkedHashMap<>();
        for (Coverage coverage : qualifyingCoverages(version, from, to)) {
            BigDecimal production = scheduledSumBetween(coverage, from, to);
            ProductionCode origin = mainProductionCode(coverage);
            Long distributorId = origin == null || origin.distributor == null ? 0L : origin.distributor.getId();
            byDistributor.computeIfAbsent(distributorId, k -> new ArrayList<>()).add(coverage);
            coverageProduction.put(coverage.getId(), production);
            distributorTotal.merge(distributorId, production, BigDecimal::add);
        }
        int count = 0;
        for (Map.Entry<Long, List<Coverage>> entry : byDistributor.entrySet()) {
            BigDecimal total = distributorTotal.get(entry.getKey());
            CalculationInput input = new CalculationInput(EventType.SCHEDULED_PAYMENT, total, null, null, null, null,
                    null, from, to, null, null, null, null, LineNature.COMPLEMENT, version.parameters, tiers);
            ProposedLine tier = families.get(RuleFamily.TIERED).propose(input).get(0);
            for (Coverage coverage : entry.getValue()) {
                BigDecimal production = coverageProduction.get(coverage.getId());
                BigDecimal amount = production.multiply(tier.rate()).setScale(2, RoundingMode.HALF_UP);
                String explanation = "{\"family\":\"TIERED\",\"complement\":true,\"production\":"
                        + production.toPlainString() + ",\"total\":" + total.toPlainString() + ",\"rate\":"
                        + tier.rate().toPlainString() + ",\"amount\":" + amount.toPlainString() + "}";
                writeSavingsLine(period, run, coverage, LineNature.COMPLEMENT, amount, null, explanation);
                count++;
            }
        }
        return count;
    }

    /**
     * Computes the expected insurer surcommission of an inbound annual rule (section 6.6, CR-36): a
     * share of the coverage's scheduled payments of the year, capped per contract, kept as an expected
     * amount with the control and never a line.
     *
     * @param period  the close period
     * @param version the inbound surcommission rule
     * @param from    the first day of the production year
     * @param to      the last day of the production year
     */
    private void expectedSurcommission(Period period, RuleVersion version, LocalDate from, LocalDate to) {
        BigDecimal rate = fr.alptis.remuneration.engine.families.LinearFamily.ruleRate(version.parameters);
        BigDecimal cap = annualCap(version);
        for (Coverage coverage : qualifyingCoverages(version, from, to)) {
            BigDecimal production = scheduledSumBetween(coverage, from, to);
            BigDecimal expected = production.multiply(rate == null ? BigDecimal.ZERO : rate)
                    .setScale(2, RoundingMode.HALF_UP);
            if (cap != null && expected.compareTo(cap) > 0) {
                expected = cap;
            }
            recordExpected(period, coverage, "SURCOMMISSION", expected);
        }
    }

    /**
     * Returns the entity's coverages whose perimeter the rule covers and that bear scheduled payments
     * in the year, ordered for a stable rerun (D8).
     *
     * @param version the rule
     * @param from    the first day of the year
     * @param to      the last day of the year
     * @return the qualifying coverages
     */
    private List<Coverage> qualifyingCoverages(RuleVersion version, LocalDate from, LocalDate to) {
        List<Coverage> coverages = new ArrayList<>();
        for (BusinessEvent first : BusinessEvent.<BusinessEvent>list(
                "eventType = ?1 and entity.code = ?2 and eventDate >= ?3 and eventDate <= ?4 order by coverage.id",
                EventType.SCHEDULED_PAYMENT, version.rule.plan.entity.code, from, to)) {
            Coverage coverage = first.coverage;
            if (coverage == null || coverages.contains(coverage)) {
                continue;
            }
            if (scopeMatcher.matches(version.scope, coverageAttributes(coverage))) {
                coverages.add(coverage);
            }
        }
        return coverages;
    }

    /**
     * Tells whether a rule is computed at the annual close.
     *
     * @param version the rule version
     * @return {@code true} when its calculation frequency is annual
     */
    private boolean isAnnual(RuleVersion version) {
        return "ANNUAL".equals(paramText(mapper, version.parameters, "calculationFrequency"));
    }

    /**
     * Reads the per-contract annual cap of a surcommission rule, or null when uncapped.
     *
     * @param version the rule version
     * @return the cap, or null
     */
    private BigDecimal annualCap(RuleVersion version) {
        return paramDecimal(mapper, version.parameters, "annualCap");
    }

    /**
     * Serialises a tiered rule's scale into a JSON array of lower bounds and rates for the family.
     *
     * @param version the tiered rule
     * @return the tiers JSON array string
     */
    private static String tiersJson(RuleVersion version) {
        Scale scale = Scale.find("ruleVersion", version).firstResult();
        StringBuilder json = new StringBuilder("[");
        if (scale != null) {
            boolean first = true;
            for (ScaleTier tier : ScaleTier.<ScaleTier>list("scale = ?1 order by lowerBound", scale)) {
                if (!first) {
                    json.append(",");
                }
                json.append("{\"lowerBound\":").append(tier.lowerBound.toPlainString())
                        .append(",\"rate\":").append(tier.rate.toPlainString());
                if (tier.upperBound != null) {
                    json.append(",\"upperBound\":").append(tier.upperBound.toPlainString());
                }
                if (Boolean.TRUE.equals(tier.fromFirstUnit)) {
                    json.append(",\"fromFirstUnit\":true");
                }
                json.append("}");
                first = false;
            }
        }
        return json.append("]").toString();
    }

    /**
     * Builds the scope-matching attributes of a coverage, without an event type (used to match the
     * savings and annual rules against a coverage).
     *
     * @param coverage the coverage
     * @return the attribute map
     */
    private static Map<String, String> coverageAttributes(Coverage coverage) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("entity", coverage.contract.entity.code);
        if (coverage.contract.product != null) {
            attributes.put("product", coverage.contract.product.code);
        }
        if (coverage.staffCategory != null) {
            attributes.put("college", coverage.staffCategory.name());
        }
        RemunerationChoice choice = choiceAt(coverage, coverage.effectDate);
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
     * Tells whether a line nature is offset against an advance (the base commission natures).
     *
     * @param nature the line nature
     * @return {@code true} for a linear, acquisition or servicing line
     */
    private static boolean offsettable(LineNature nature) {
        return nature == LineNature.LINEAR || nature == LineNature.ACQUISITION || nature == LineNature.SERVICING;
    }

    /**
     * Finds the entity's period for a month, creating it open when absent.
     *
     * @param entity      the entity
     * @param periodMonth the first day of the month
     * @return the period
     */
    private Period findOrCreatePeriod(LegalEntity entity, LocalDate periodMonth) {
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            period = new Period();
            period.entity = entity;
            period.periodMonth = periodMonth;
            period.state = PeriodState.OPEN;
            period.setCreatedAt(clock.now());
            period.persist();
        }
        return period;
    }

    /**
     * Runs the monthly development remuneration (RCD, section 6.7): for each enrolled distributor with
     * a signed amendment, the cumulative annualised production of the year's new business feeds the
     * scale, and the line is the gain of entitlement over the previous month. A failed payment
     * condition (the unpaid rate) forfeits the month's line; the entitlement counter still advances.
     *
     * @param entityCode  the entity code
     * @param periodMonth the first day of the processing month
     * @param year        the production year
     * @return the recorded calculation run
     */
    @Transactional
    public CalculationRun runDevelopment(String entityCode, LocalDate periodMonth, int year) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = findOrCreatePeriod(entity, periodMonth);
        CalculationRun run = enrollmentRun(period, "development", year);
        // D13: a rerun first removes the non-validated development lines and their allocations.
        SurcommissionAllocation.delete(
                "commissionLine.period = ?1 and commissionLine.status in ?2 and commissionLine.nature = ?3",
                period, List.of(LineStatus.EARNED, LineStatus.FORECAST), LineNature.RCD);
        CommissionLine.delete("period = ?1 and status in ?2 and nature = ?3", period,
                List.of(LineStatus.EARNED, LineStatus.FORECAST), LineNature.RCD);
        int month = periodMonth.getMonthValue();
        int lines = 0;
        for (RuleVersion version : activeVersions(entity)) {
            if (version.rule.family == RuleFamily.TIERED && version.outputNature == LineNature.RCD
                    && "MONTHLY".equals(paramText(mapper, version.parameters, "calculationFrequency"))) {
                for (Enrollment enrollment : Enrollment.<Enrollment>list("rule = ?1 and status = ?2",
                        version.rule, EnrollmentStatus.ACTIVE)) {
                    lines += developmentForEnrollment(period, run, version, enrollment, year, month);
                }
            }
        }
        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"lines\":" + lines + "}";
        return run;
    }

    /**
     * Computes one enrolled distributor's development remuneration for a month (section 6.7).
     *
     * @param period     the period
     * @param run        the run
     * @param version    the development rule
     * @param enrollment the distributor's enrolment
     * @param year       the production year
     * @param month      the month number
     * @return the number of lines written
     */
    private int developmentForEnrollment(Period period, CalculationRun run, RuleVersion version,
                                         Enrollment enrollment, int year, int month) {
        if (!signedAmendment(enrollment)) {
            controlAlert(period, "ENROLLMENT", enrollment.getId(), "Avenant non signé");
            return 0;
        }
        Distributor distributor = enrollment.distributor;
        PayeeType payeeType = enrollmentPayeeType(enrollment);
        Long payeeId = enrollmentPayeeId(enrollment);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        BigDecimal production = newBusinessAnnualised(version.scope, distributor, from, monthEnd);
        BigDecimal entitlement = marginalEntitlement(mapper, tiersJson(version), production);
        ProductionCounter previous = ProductionCounter.find(
                "rule = ?1 and payeeType = ?2 and payeeId = ?3 and counterYear = ?4 and counterMonth = ?5",
                version.rule, payeeType, payeeId, (short) year, (short) (month - 1)).firstResult();
        BigDecimal entitledBefore = previous == null ? BigDecimal.ZERO : previous.cumulativeEntitlement;
        BigDecimal monthly = entitlement.subtract(entitledBefore).setScale(2, RoundingMode.HALF_UP);
        // A failed payment condition (section 6.7): FORFEIT (default) advances the entitlement counter
        // past the month, so its gain is lost for good; DEFER freezes the counter at the prior month, so
        // the gain is caught up the next month the condition holds.
        ConditionFailureMode failure = paymentFailureMode(version, distributor, year, month);
        boolean defer = failure == ConditionFailureMode.DEFER;
        storeCounter(version.rule, payeeType, payeeId, enrollment, year, month, production,
                defer ? entitledBefore : entitlement);
        if (failure != null) {
            writeEnrollmentLine(period, run, LineNature.RCD, BigDecimal.ZERO, payeeType, payeeId, distributor,
                    "{\"family\":\"TIERED\",\"rcd\":true,\"" + (defer ? "deferred" : "forfeited") + "\":true}");
            return 1;
        }
        if (monthly.signum() == 0) {
            return 0;
        }
        String explanation = "{\"family\":\"TIERED\",\"rcd\":true,\"cumulative\":" + production.toPlainString()
                + ",\"entitlement\":" + entitlement.toPlainString() + ",\"amount\":" + monthly.toPlainString() + "}";
        CommissionLine line = writeEnrollmentLine(period, run, LineNature.RCD, monthly, payeeType, payeeId,
                distributor, explanation);
        allocateDevelopment(line, version.scope, distributor, monthly, firstOfMonth(monthEnd), monthEnd);
        return 1;
    }

    /**
     * Allocates a month's development remuneration among that month's new adhésions, pro rata of their
     * annualised premium, so the reprise can later target each (section 6.7).
     *
     * @param line        the development line
     * @param scope       the rule scope
     * @param distributor the distributor
     * @param amount      the month's amount
     * @param from        the first day of the month
     * @param to          the last day of the month
     */
    private void allocateDevelopment(CommissionLine line, String scope, Distributor distributor, BigDecimal amount,
                                     LocalDate from, LocalDate to) {
        List<Coverage> coverages = newBusinessCoverages(scope, distributor, from, to);
        BigDecimal total = BigDecimal.ZERO;
        for (Coverage coverage : coverages) {
            total = total.add(annualPremium(coverage));
        }
        if (total.signum() == 0) {
            return;
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < coverages.size(); i++) {
            Coverage coverage = coverages.get(i);
            BigDecimal premium = annualPremium(coverage);
            BigDecimal part = i == coverages.size() - 1 ? amount.subtract(allocated)
                    : amount.multiply(premium).divide(total, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(part);
            SurcommissionAllocation allocation = new SurcommissionAllocation();
            allocation.commissionLine = line;
            allocation.contract = coverage.contract;
            allocation.annualisedPremium = premium;
            allocation.allocatedAmount = part;
            allocation.setCreatedAt(clock.now());
            allocation.persist();
        }
    }

    /**
     * Claws back the development remuneration of an adhésion ended within its first two years
     * (section 6.7): the whole of its allocated share in the first twelve months, half from the
     * thirteenth to the twenty-fourth, nothing afterwards; a death triggers no reprise.
     *
     * @param period   the period
     * @param run      the run
     * @param event    the terminating event
     * @param coverage the coverage
     * @return the number of reprise lines written
     */
    private int rcdReprise(Period period, CalculationRun run, BusinessEvent event, Coverage coverage) {
        if (event.eventType == EventType.DEATH) {
            return 0;
        }
        List<SurcommissionAllocation> allocations = SurcommissionAllocation.list(
                "contract = ?1 and commissionLine.nature = ?2", coverage.contract, LineNature.RCD);
        if (allocations.isEmpty()) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(coverage.effectDate, event.eventDate);
        BigDecimal rate = months <= 12 ? BigDecimal.ONE : months <= 24 ? new BigDecimal("0.5") : BigDecimal.ZERO;
        if (rate.signum() == 0) {
            return 0;
        }
        int count = 0;
        for (SurcommissionAllocation allocation : allocations) {
            BigDecimal amount = allocation.allocatedAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP).negate();
            CommissionLine source = allocation.commissionLine;
            String explanation = "{\"family\":\"CLAWBACK\",\"rcdReprise\":true,\"months\":" + months
                    + ",\"amount\":" + amount.toPlainString() + "}";
            CommissionLine line = new CommissionLine();
            line.entity = period.entity;
            line.period = period;
            line.run = run;
            line.direction = Direction.OUTBOUND;
            line.status = LineStatus.EARNED;
            line.contract = coverage.contract;
            line.coverage = coverage;
            line.nature = LineNature.CLAWBACK;
            line.amount = amount;
            line.account = source.account;
            line.originProductionCode = source.originProductionCode;
            line.originDistributor = source.originDistributor;
            line.attachmentMonth = period.periodMonth;
            line.explanation = explanation;
            line.setCreatedAt(clock.now());
            line.persist();
            count++;
        }
        return count;
    }

    /**
     * Runs the annual portfolio remuneration (RCP, section 6.8): for each enrolled distributor with a
     * signed amendment whose quality and volume conditions all hold, a single line at the rate (flat
     * or chosen from a count scale) applied to the year's collected premiums; a failed condition makes
     * it nil and records the detail.
     *
     * @param entityCode  the entity code
     * @param periodMonth the first day of the close month
     * @param year        the production year
     * @return the recorded calculation run
     */
    @Transactional
    public CalculationRun runPortfolio(String entityCode, LocalDate periodMonth, int year) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        Period period = findOrCreatePeriod(entity, periodMonth);
        CalculationRun run = enrollmentRun(period, "portfolio", year);
        CommissionLine.delete("period = ?1 and status in ?2 and nature = ?3", period,
                List.of(LineStatus.EARNED, LineStatus.FORECAST), LineNature.RCP);
        int lines = 0;
        for (RuleVersion version : activeVersions(entity)) {
            if (version.rule.family == RuleFamily.PORTFOLIO) {
                for (Enrollment enrollment : Enrollment.<Enrollment>list("rule = ?1 and status = ?2",
                        version.rule, EnrollmentStatus.ACTIVE)) {
                    lines += portfolioForEnrollment(period, run, version, enrollment, year);
                }
            }
        }
        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"lines\":" + lines + "}";
        return run;
    }

    /**
     * Computes one enrolled distributor's portfolio remuneration (section 6.8).
     *
     * @param period     the period
     * @param run        the run
     * @param version    the portfolio rule
     * @param enrollment the distributor's enrolment
     * @param year       the production year
     * @return the number of lines written
     */
    private int portfolioForEnrollment(Period period, CalculationRun run, RuleVersion version, Enrollment enrollment,
                                       int year) {
        if (!signedAmendment(enrollment)) {
            controlAlert(period, "ENROLLMENT", enrollment.getId(), "Avenant non signé");
            return 0;
        }
        Distributor distributor = enrollment.distributor;
        PayeeType payeeType = enrollmentPayeeType(enrollment);
        Long payeeId = enrollmentPayeeId(enrollment);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        BigDecimal basis = collectedPremiums(version.scope, distributor, from, to);
        StringBuilder detail = new StringBuilder();
        boolean hold = conditionsHold(version, distributor, year, detail);
        BigDecimal rate = portfolioRate(version, distributor, from, to);
        BigDecimal effectiveRate = hold ? rate : BigDecimal.ZERO;
        CalculationInput input = new CalculationInput(EventType.PREMIUM_COLLECTED, basis, effectiveRate, null, null,
                null, null, from, to, null, null, null, null, LineNature.RCP, version.parameters, null);
        ProposedLine proposed = families.get(RuleFamily.PORTFOLIO).propose(input).get(0);
        String explanation = "{\"family\":\"PORTFOLIO\",\"rcp\":true,\"basis\":" + basis.toPlainString()
                + ",\"rate\":" + effectiveRate.toPlainString() + ",\"conditions\":\"" + detail + "\",\"amount\":"
                + proposed.amount().toPlainString() + "}";
        writeEnrollmentLine(period, run, LineNature.RCP, proposed.amount(), payeeType, payeeId, distributor,
                explanation);
        return 1;
    }

    /**
     * Returns the portfolio rate: the rate chosen from a new-business-count scale when the rule has
     * one, otherwise the flat rate parameter (section 6.8).
     *
     * @param version     the portfolio rule
     * @param distributor the distributor
     * @param from        the first day of the year
     * @param to          the last day of the year
     * @return the rate
     */
    private BigDecimal portfolioRate(RuleVersion version, Distributor distributor, LocalDate from, LocalDate to) {
        String tiers = tiersJson(version);
        if ("[]".equals(tiers)) {
            return paramDecimalOrZero(mapper, version.parameters, "rate");
        }
        long count = newBusinessCoverages(version.scope, distributor, from, to).size();
        return fr.alptis.remuneration.engine.families.TieredFamily.rateFor(BigDecimal.valueOf(count), tiers);
    }

    /**
     * Tells whether all of a rule's conditions hold for a distributor in a year, appending each
     * condition's verdict and observed value to the detail (section 6.8).
     *
     * @param version     the rule
     * @param distributor the distributor
     * @param year        the year
     * @param detail      the detail accumulator
     * @return {@code true} when every condition holds
     */
    private boolean conditionsHold(RuleVersion version, Distributor distributor, int year, StringBuilder detail) {
        boolean all = true;
        for (RuleCondition condition : RuleCondition.<RuleCondition>list("ruleVersion = ?1", version)) {
            BigDecimal observed = indicatorValue(condition, distributor, year);
            boolean ok = observed != null && compare(observed, condition.operator, condition.threshold);
            detail.append(condition.indicatorType).append('=').append(observed).append(ok ? "(ok) " : "(ko) ");
            if (!ok) {
                all = false;
            }
        }
        return all;
    }

    /**
     * Tells whether a rule's payment conditions — those on the unpaid rate — hold for a distributor
     * in a year (section 6.7).
     *
     * @param version     the rule
     * @param distributor the distributor
     * @param year        the year
     * @return {@code true} when the unpaid-rate conditions hold
     */
    private ConditionFailureMode paymentFailureMode(RuleVersion version, Distributor distributor, int year,
                                                    int month) {
        for (RuleCondition condition : RuleCondition.<RuleCondition>list("ruleVersion = ?1", version)) {
            if (condition.indicatorType == QualityIndicatorType.UNPAID_RATE) {
                BigDecimal observed = monthlyIndicatorValue(condition, distributor, year, month);
                if (observed == null || !compare(observed, condition.operator, condition.threshold)) {
                    return condition.onFailure == null ? ConditionFailureMode.FORFEIT : condition.onFailure;
                }
            }
        }
        return null;
    }

    /**
     * Returns the quality-indicator value a payment condition reads for a distributor in a given month
     * — the indicator whose validity covers that month (section 6.7) — or null when absent.
     *
     * @param condition   the condition
     * @param distributor the distributor
     * @param year        the year
     * @param month       the month number
     * @return the observed value, or null
     */
    private static BigDecimal monthlyIndicatorValue(RuleCondition condition, Distributor distributor, int year,
                                                    int month) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        QualityIndicator indicator = QualityIndicator.find(
                "distributor = ?1 and indicatorType = ?2 and periodFrom <= ?3 and periodTo >= ?4",
                distributor, condition.indicatorType, monthEnd, monthStart).firstResult();
        return indicator == null ? null : indicator.value;
    }

    /**
     * Returns the quality-indicator value a condition reads for a distributor in a year, or null when
     * absent.
     *
     * @param condition   the condition
     * @param distributor the distributor
     * @param year        the year
     * @return the observed value, or null
     */
    private static BigDecimal indicatorValue(RuleCondition condition, Distributor distributor, int year) {
        QualityIndicator indicator = QualityIndicator.find(
                "distributor = ?1 and indicatorType = ?2 and periodFrom >= ?3 and periodFrom <= ?4",
                distributor, condition.indicatorType, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .firstResult();
        return indicator == null ? null : indicator.value;
    }

    /**
     * Compares an observed value to a threshold with an operator.
     *
     * @param observed  the observed value
     * @param operator  the operator
     * @param threshold the threshold
     * @return the comparison result
     */
    static boolean compare(BigDecimal observed, ConditionOperator operator, BigDecimal threshold) {
        int cmp = observed.compareTo(threshold);
        return switch (operator) {
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
            case EQ -> cmp == 0;
        };
    }

    /**
     * Sums the annualised premiums of the distributor's new-business adhésions matching a scope within
     * a date range (section 6.7).
     *
     * @param scope       the rule scope
     * @param distributor the distributor
     * @param from        the range start
     * @param to          the range end
     * @return the summed annualised premiums
     */
    private BigDecimal newBusinessAnnualised(String scope, Distributor distributor, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Coverage coverage : newBusinessCoverages(scope, distributor, from, to)) {
            sum = sum.add(annualPremium(coverage));
        }
        return sum;
    }

    /**
     * Returns the distributor's new-business coverages (those with a subscription event in the range)
     * matching a scope, deduplicated and ordered for a stable rerun (D8).
     *
     * @param scope       the rule scope
     * @param distributor the distributor
     * @param from        the range start
     * @param to          the range end
     * @return the matching coverages
     */
    private List<Coverage> newBusinessCoverages(String scope, Distributor distributor, LocalDate from, LocalDate to) {
        List<Coverage> coverages = new ArrayList<>();
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                "eventType = ?1 and eventDate >= ?2 and eventDate <= ?3 order by coverage.id",
                EventType.SUBSCRIPTION, from, to)) {
            Coverage coverage = event.coverage;
            if (coverage == null || coverages.contains(coverage)) {
                continue;
            }
            ProductionCode origin = mainProductionCode(coverage);
            if (origin != null && origin.distributor == distributor
                    && scopeMatcher.matches(scope, coverageAttributes(coverage))) {
                coverages.add(coverage);
            }
        }
        return coverages;
    }

    /**
     * Sums the distributor's collected premiums matching a scope within a date range (section 6.8).
     *
     * @param scope       the rule scope
     * @param distributor the distributor
     * @param from        the range start
     * @param to          the range end
     * @return the summed collected premiums
     */
    private BigDecimal collectedPremiums(String scope, Distributor distributor, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                "eventType = ?1 and eventDate >= ?2 and eventDate <= ?3 order by coverage.id, id",
                EventType.PREMIUM_COLLECTED, from, to)) {
            Coverage coverage = event.coverage;
            if (coverage == null) {
                continue;
            }
            ProductionCode origin = mainProductionCode(coverage);
            if (origin != null && origin.distributor == distributor
                    && scopeMatcher.matches(scope, coverageAttributes(coverage))) {
                sum = sum.add(basisOf(event));
            }
        }
        return sum;
    }

    /**
     * Returns a coverage's annualised premium, or zero when absent.
     *
     * @param coverage the coverage
     * @return the annualised premium, or zero
     */
    private static BigDecimal annualPremium(Coverage coverage) {
        return coverage.annualPremium == null ? BigDecimal.ZERO : coverage.annualPremium;
    }

    /**
     * Computes the entitlement of a marginal scale at a measure (section 6.7): each tier's rate on its
     * own slice, unless a reached {@code fromFirstUnit} tier applies its rate to the whole measure.
     *
     * @param tiers   the tiers JSON array
     * @param measure the measure
     * @return the entitlement
     */
    static BigDecimal marginalEntitlement(ObjectMapper mapper, String tiers, BigDecimal measure) {
        JsonNode array = readObject(mapper, tiers);
        if (array == null || !array.isArray()) {
            return BigDecimal.ZERO;
        }
        BigDecimal wholeRate = null;
        BigDecimal wholeLower = null;
        for (JsonNode tier : array) {
            if (tier.path("fromFirstUnit").asBoolean(false)) {
                BigDecimal lower = tier.path("lowerBound").decimalValue();
                if (measure.compareTo(lower) >= 0 && (wholeLower == null || lower.compareTo(wholeLower) > 0)) {
                    wholeLower = lower;
                    wholeRate = tier.path("rate").decimalValue();
                }
            }
        }
        if (wholeRate != null) {
            return measure.multiply(wholeRate).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode tier : array) {
            BigDecimal lower = tier.path("lowerBound").decimalValue();
            if (measure.compareTo(lower) > 0) {
                BigDecimal upper = tier.has("upperBound") ? tier.path("upperBound").decimalValue() : measure;
                BigDecimal top = measure.min(upper);
                sum = sum.add(top.subtract(lower).multiply(tier.path("rate").decimalValue()));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tells whether an enrolment's amendment is signed (section 6.7).
     *
     * @param enrollment the enrolment
     * @return {@code true} when the amendment is signed
     */
    private static boolean signedAmendment(Enrollment enrollment) {
        Agreement agreement = enrollment.agreement;
        return agreement != null && agreement.signatureStatus == SignatureStatus.SIGNED;
    }

    /**
     * Returns the payee type of an enrolment: the group when enrolled as a group, else the distributor.
     *
     * @param enrollment the enrolment
     * @return the payee type
     */
    private static PayeeType enrollmentPayeeType(Enrollment enrollment) {
        return enrollment.group != null ? PayeeType.GROUP : PayeeType.DISTRIBUTOR;
    }

    /**
     * Returns the payee id of an enrolment: the group id when enrolled as a group, else the
     * distributor id.
     *
     * @param enrollment the enrolment
     * @return the payee id
     */
    private static Long enrollmentPayeeId(Enrollment enrollment) {
        return enrollment.group != null ? enrollment.group.getId() : enrollment.distributor.getId();
    }

    /**
     * Stores or updates the development entitlement counter of a month (section 6.7).
     *
     * @param rule        the rule
     * @param payeeType   the payee type
     * @param payeeId     the payee id
     * @param enrollment  the enrolment
     * @param year        the year
     * @param month       the month
     * @param production  the cumulative production
     * @param entitlement the cumulative entitlement
     */
    private void storeCounter(fr.alptis.remuneration.domain.Rule rule, PayeeType payeeType, Long payeeId,
                              Enrollment enrollment, int year, int month, BigDecimal production,
                              BigDecimal entitlement) {
        ProductionCounter counter = ProductionCounter.find(
                "rule = ?1 and payeeType = ?2 and payeeId = ?3 and counterYear = ?4 and counterMonth = ?5",
                rule, payeeType, payeeId, (short) year, (short) month).firstResult();
        if (counter == null) {
            counter = new ProductionCounter();
            counter.entity = rule.plan.entity;
            counter.rule = rule;
            counter.payeeType = payeeType;
            counter.payeeId = payeeId;
            counter.enrollment = enrollment;
            counter.counterYear = (short) year;
            counter.counterMonth = (short) month;
            counter.setCreatedAt(clock.now());
        }
        counter.cumulativeBasis = production;
        counter.cumulativeEntitlement = entitlement;
        counter.persist();
    }

    /**
     * Writes an enrolment-level commission line (RCD or RCP) to a distributor or group, with no single
     * coverage (section 6.7, 6.8).
     *
     * @param period      the period
     * @param run         the run
     * @param nature      the nature
     * @param amount      the amount
     * @param payeeType   the payee type
     * @param payeeId     the payee id
     * @param distributor the member distributor (kept for restitution), possibly null
     * @param explanation the explanation
     * @return the written line
     */
    private CommissionLine writeEnrollmentLine(Period period, CalculationRun run, LineNature nature, BigDecimal amount,
                                               PayeeType payeeType, Long payeeId, Distributor distributor,
                                               String explanation) {
        CommissionLine line = new CommissionLine();
        line.entity = period.entity;
        line.period = period;
        line.run = run;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = amount;
        line.originDistributor = distributor;
        line.account = accountForId(period.entity, payeeType, payeeId);
        line.attachmentMonth = period.periodMonth;
        line.explanation = explanation;
        line.setCreatedAt(clock.now());
        line.persist();
        return line;
    }

    /**
     * Starts an enrolment-driven calculation run on a period.
     *
     * @param period     the period
     * @param launchedBy the launcher label
     * @param year       the production year
     * @return the persisted run
     */
    private CalculationRun enrollmentRun(Period period, String launchedBy, int year) {
        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = RunType.ACTUAL;
        run.status = RunStatus.RUNNING;
        run.startedAt = clock.now();
        run.launchedBy = launchedBy;
        run.configHash = HashUtil.sha256(launchedBy + year);
        run.setCreatedAt(clock.now());
        run.persist();
        return run;
    }

    /**
     * Returns the entity's active rule versions.
     *
     * @param entity the entity
     * @return the active versions
     */
    private static List<RuleVersion> activeVersions(LegalEntity entity) {
        return RuleVersion.list("status = ?1 and rule.plan.entity = ?2",
                fr.alptis.remuneration.domain.VersionStatus.ACTIVE, entity);
    }

    /**
     * Records a control anomaly on an object of the period (section 6.7, 6.8).
     *
     * @param period     the period
     * @param objectType the object type
     * @param objectId   the object id
     * @param detail     the anomaly detail
     */
    private void controlAlert(Period period, String objectType, Long objectId, String detail) {
        ControlResult result = new ControlResult();
        result.period = period;
        result.control = control(period.entity, "ENR-01", "Inscription sans avenant signé",
                ControlFamily.SUPPLEMENT);
        result.objectType = objectType;
        result.objectId = objectId;
        result.severity = ControlSeverity.WARNING;
        result.decision = ControlDecision.OPEN;
        result.justification = detail;
        result.setCreatedAt(clock.now());
        result.persist();
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
    private List<CommissionLine> writeLine(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                                           RuleVersion version, ProposedLine proposed) {
        return writeShared(period, run, event, coverage, version, null, version.rule.plan.direction,
                proposed.nature(), proposed.basisAmount(), proposed.rate(), proposed.amount(), proposed.explanation());
    }

    /**
     * Writes the lines of one proposal shared among the beneficiaries (section 6.9): the amount is
     * first split between the co-brokers assigned to the garantie at the event date, then each
     * co-broker's part is split by its payment rule between itself and its group. Each final part is a
     * line of its own, keeping the originating production code (so restitutions show each member its
     * own amount) and carrying the payee account. Rounding leaves the last part the remainder (P13). A
     * garantie with no assignment yields a single line with no origin.
     *
     * @param period      the period
     * @param run         the run
     * @param event       the source event, or null
     * @param coverage    the coverage
     * @param version     the rule version, or null
     * @param position    the discount position, or null
     * @param direction   the line direction
     * @param nature      the line nature
     * @param basis       the basis amount, or null
     * @param rate        the rate, or null
     * @param amount      the total amount to share
     * @param explanation the line explanation
     * @return the written lines
     */
    private List<CommissionLine> writeShared(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                                             RuleVersion version, AdvancePosition position, Direction direction,
                                             LineNature nature, BigDecimal basis, BigDecimal rate, BigDecimal amount,
                                             String explanation) {
        LocalDate date = event != null ? event.eventDate : coverage.effectDate;
        List<CoverageAssignment> assignments = CoverageAssignment.list(
                "coverage = ?1 and (validFrom is null or validFrom <= ?2) and (validTo is null or validTo >= ?2) "
                        + "order by role, id", coverage, date);
        List<CommissionLine> out = new ArrayList<>();
        if (assignments.isEmpty()) {
            out.add(persistLine(period, run, event, coverage, version, position, direction, nature, basis, rate,
                    amount, null, null, explanation));
            return out;
        }
        List<BigDecimal> coShares = allocate(amount, assignments.stream().map(a -> share(a.share)).toList());
        for (int i = 0; i < assignments.size(); i++) {
            ProductionCode pc = assignments.get(i).productionCode;
            BigDecimal coAmount = coShares.get(i);
            List<PayeeRuleShare> shares = payeeShares(pc, date);
            if (shares.isEmpty()) {
                out.add(persistLine(period, run, event, coverage, version, position, direction, nature, basis, rate,
                        coAmount, pc, null, explanation));
            } else {
                List<BigDecimal> parts = allocate(coAmount, shares.stream().map(s -> share(s.share)).toList());
                for (int j = 0; j < shares.size(); j++) {
                    DistributorAccount account = accountFor(period.entity, shares.get(j));
                    out.add(persistLine(period, run, event, coverage, version, position, direction, nature, basis,
                            rate, parts.get(j), pc, account, explanation));
                }
            }
        }
        return out;
    }

    /**
     * Persists one commission line with its beneficiary and payee account.
     *
     * @param period      the period
     * @param run         the run
     * @param event       the source event, or null
     * @param coverage    the coverage
     * @param version     the rule version, or null
     * @param position    the discount position, or null
     * @param direction   the direction
     * @param nature      the nature
     * @param basis       the basis, or null
     * @param rate        the rate, or null
     * @param amount      the amount
     * @param pc          the originating production code, or null
     * @param account     the payee account, or null
     * @param explanation the explanation
     * @return the written line
     */
    private CommissionLine persistLine(Period period, CalculationRun run, BusinessEvent event, Coverage coverage,
                                       RuleVersion version, AdvancePosition position, Direction direction,
                                       LineNature nature, BigDecimal basis, BigDecimal rate, BigDecimal amount,
                                       ProductionCode pc, DistributorAccount account, String explanation) {
        CommissionLine line = new CommissionLine();
        line.entity = period.entity;
        line.period = period;
        line.run = run;
        line.direction = direction;
        line.status = LineStatus.EARNED;
        line.contract = coverage.contract;
        line.coverage = coverage;
        line.event = event;
        line.ruleVersion = version;
        line.advancePosition = position;
        line.nature = nature;
        line.basisAmount = basis;
        line.rate = rate;
        line.amount = amount;
        line.originProductionCode = pc;
        line.originDistributor = pc == null ? null : pc.distributor;
        line.account = account;
        line.attachmentMonth = period.periodMonth;
        line.explanation = explanation;
        line.setCreatedAt(clock.now());
        line.persist();
        return line;
    }

    /**
     * Allocates a total across fractional shares, rounding each to the cent and leaving the last share
     * the remainder so the sum stays exact (P13).
     *
     * @param total     the total amount
     * @param fractions the fractional shares, in order
     * @return the allocated amounts, in order
     */
    private static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> fractions) {
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < fractions.size() - 1; i++) {
            BigDecimal part = total.multiply(fractions.get(i)).setScale(2, RoundingMode.HALF_UP);
            out.add(part);
            allocated = allocated.add(part);
        }
        out.add(total.subtract(allocated).setScale(2, RoundingMode.HALF_UP));
        return out;
    }

    /**
     * Returns a share, treating a null as zero.
     *
     * @param share the share, possibly null
     * @return the share, or zero
     */
    private static BigDecimal share(BigDecimal share) {
        return share == null ? BigDecimal.ZERO : share;
    }

    /**
     * Returns the payment-rule shares in force for a production code at a date (section 6.9): the rule
     * on the production code if any, otherwise the one on its distributor; an empty list when no rule
     * applies, so the whole part stays on the production code.
     *
     * @param pc   the production code, possibly null
     * @param date the date
     * @return the payee shares, or an empty list
     */
    private static List<PayeeRuleShare> payeeShares(ProductionCode pc, LocalDate date) {
        if (pc == null) {
            return List.of();
        }
        PayeeRule rule = PayeeRule.find(
                "productionCode = ?1 and (validFrom is null or validFrom <= ?2) and (validTo is null or validTo >= ?2)",
                pc, date).firstResult();
        if (rule == null && pc.distributor != null) {
            rule = PayeeRule.find(
                    "productionCode is null and distributor = ?1 and (validFrom is null or validFrom <= ?2) "
                            + "and (validTo is null or validTo >= ?2)", pc.distributor, date).firstResult();
        }
        return rule == null ? List.of() : PayeeRuleShare.list("payeeRule = ?1 order by id", rule);
    }

    /**
     * Returns the payee account of a share, creating it when absent (dev profile).
     *
     * @param entity the entity
     * @param share  the payee share
     * @return the payee account
     */
    private DistributorAccount accountFor(LegalEntity entity, PayeeRuleShare share) {
        return accountForId(entity, share.payeeType, share.payeeId);
    }

    /**
     * Returns the payee account for an entity, type and payee id, creating it when absent (dev
     * profile).
     *
     * @param entity  the entity
     * @param type    the payee type
     * @param payeeId the payee id
     * @return the payee account
     */
    private DistributorAccount accountForId(LegalEntity entity, PayeeType type, Long payeeId) {
        DistributorAccount account = DistributorAccount.find(
                "entity = ?1 and payeeType = ?2 and payeeId = ?3", entity, type, payeeId).firstResult();
        if (account == null) {
            account = new DistributorAccount();
            account.entity = entity;
            account.payeeType = type;
            account.payeeId = payeeId;
            account.setCreatedAt(clock.now());
            account.persist();
        }
        return account;
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
                || fr.alptis.remuneration.engine.families.LinearFamily.ruleRate(version.parameters) != null) {
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
                                          RuleVersion version, BigDecimal basis) {
        return new CalculationInput(event.eventType, basis,
                choice == null ? null : choice.linearRate,
                choice == null ? null : choice.acquisitionRate,
                choice == null ? null : choice.servicingRate,
                coverage.effectDate,
                event.eventDate, event.periodFrom, event.periodTo,
                event.precomputedBasis, event.precomputedRate, event.precomputedAmount,
                coverage.annualPremium, version.outputNature, version.parameters, null);
    }

    /** One pro-rata share of a premium's basis attributed to a remuneration choice (P12). */
    private record RateSegment(BigDecimal basis, RemunerationChoice choice) {
    }

    /**
     * Splits a premium across the remuneration choices in force during its covered period (P12): when
     * the garantie's choice changes between {@code periodFrom} and {@code periodTo}, the basis is shared
     * pro rata of the days under each choice, the last segment taking the rounding remainder (P13). With
     * no covered period or a single choice, the whole basis stays on the event-date choice (H-45).
     *
     * @param coverage the coverage
     * @param event    the premium event
     * @param atEvent  the choice in force at the event date
     * @return the basis segments, each with its choice
     */
    private static List<RateSegment> proratedSegments(Coverage coverage, BusinessEvent event,
                                                      RemunerationChoice atEvent) {
        BigDecimal basis = event.basisAmount;
        List<RemunerationChoice> choices = event.periodFrom == null || event.periodTo == null
                ? List.of()
                : RemunerationChoice.<RemunerationChoice>list(
                        "coverage = ?1 and (validFrom is null or validFrom <= ?2) "
                                + "and (validTo is null or validTo >= ?3) order by validFrom",
                        coverage, event.periodTo, event.periodFrom);
        if (choices.size() <= 1) {
            return List.of(new RateSegment(basis, atEvent));
        }
        // The choices are contiguous over the window: each change starts the next segment, so a
        // segment runs from its start to the day before the next choice takes effect (P13 rounds, the
        // last segment taking the remainder).
        long totalDays = ChronoUnit.DAYS.between(event.periodFrom, event.periodTo) + 1;
        List<RateSegment> segments = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < choices.size(); i++) {
            BigDecimal part;
            if (i == choices.size() - 1) {
                part = basis.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
            } else {
                LocalDate start = i == 0 ? event.periodFrom : choices.get(i).validFrom;
                long days = ChronoUnit.DAYS.between(start, choices.get(i + 1).validFrom);
                part = basis.multiply(BigDecimal.valueOf(days))
                        .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
                allocated = allocated.add(part);
            }
            segments.add(new RateSegment(part, choices.get(i)));
        }
        return segments;
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
