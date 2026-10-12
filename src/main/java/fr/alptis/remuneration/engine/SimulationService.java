package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.SimulationHistory;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.RunStatus;
import fr.alptis.remuneration.domain.RunType;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulation of a rule version on a past period (PAR-13, choix structurant 6, decision D7). A
 * simulation replays the real lines of the period under a project version, applying a substitution
 * table to the grid rates it changes — an old rate (or couple) mapped to a new one — and holding the
 * events whose rate the version drops without a substitution, counted apart, as the real calculation
 * would under P9. The simulated lines are written in a separate space (a run of type
 * {@link RunType#SIMULATION}), touching neither the accounts nor the accounting entries; the result
 * compares the simulated total to the real total. Replaying with the period's own configuration
 * gives the same lines to the cent (CR-69).
 */
@ApplicationScoped
public class SimulationService {

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public SimulationService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * One substitution: an old grid rate mapped to a new one. A null new rate means the version drops
     * the rate from its grid, so the carrying events are held (P9).
     *
     * @param oldRate the rate carried by the real lines
     * @param newRate the rate applied by the simulated version, or null to drop it
     */
    public record Substitution(BigDecimal oldRate, BigDecimal newRate) {
    }

    /**
     * Simulates a rule version on a period and compares to the real lines.
     *
     * @param entityCode      the entity code
     * @param periodMonth     the replayed period month
     * @param ruleCode        the code of the simulated rule
     * @param versionNo       the version number to simulate (taken whatever its status and dates)
     * @param substitutions   the substitution table (old rate to new rate)
     * @param scopeDistributor the partner code to limit the scope to, or null for every distributor
     * @return the simulation result
     */
    @Transactional
    public SimulationResult simulate(String entityCode, LocalDate periodMonth, String ruleCode,
                                     Integer versionNo, List<Substitution> substitutions,
                                     String scopeDistributor) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, periodMonth);
        Rule rule = Rule.<Rule>find("code = ?1 and plan.entity = ?2", ruleCode, entity).firstResult();
        if (rule == null) {
            throw new ResourceNotFoundException("Règle inconnue : " + ruleCode);
        }
        RuleVersion simVersion = RuleVersion.<RuleVersion>find("rule = ?1 and versionNo = ?2", rule, versionNo)
                .firstResult();
        if (simVersion == null) {
            throw new ValidationException("Version à simuler introuvable : " + ruleCode + " v" + versionNo);
        }
        List<Substitution> table = substitutions == null ? List.of() : substitutions;

        // A separate space: drop any previous simulation of the period and open a simulation run.
        CommissionLine.delete("period = ?1 and run.runType = ?2", period, RunType.SIMULATION);
        CalculationRun.delete("period = ?1 and runType = ?2", period, RunType.SIMULATION);
        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = RunType.SIMULATION;
        run.status = RunStatus.RUNNING;
        run.startedAt = clock.now();
        run.launchedBy = "simulation";
        run.configHash = HashUtil.sha256(ruleCode + ":" + versionNo + ":" + table);
        run.parameters = parameters(ruleCode, versionNo, table, scopeDistributor);
        run.setCreatedAt(clock.now());
        run.persist();

        List<CommissionLine> realLines = CommissionLine.list("period = ?1 and run.runType = ?2", period,
                RunType.ACTUAL);
        Map<String, BigDecimal> realByNature = new LinkedHashMap<>();
        Map<String, BigDecimal> simByNature = new LinkedHashMap<>();
        Map<String, BigDecimal[]> byDistributor = new LinkedHashMap<>();
        int replayed = 0;
        int modified = 0;
        int held = 0;

        for (CommissionLine real : realLines) {
            add(realByNature, real.nature, real.amount);
            BigDecimal simAmount = simulatedAmount(real, rule, scopeDistributor, table);
            if (simAmount == null) {
                held++;
                recordDistributor(byDistributor, real, real.amount, BigDecimal.ZERO);
                continue;
            }
            writeSimLine(entity, period, run, simVersion, rule, real, simAmount);
            add(simByNature, real.nature, simAmount);
            recordDistributor(byDistributor, real, real.amount, simAmount);
            replayed++;
            if (simAmount.compareTo(real.amount) != 0) {
                modified++;
            }
        }

        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.counters = "{\"replayed\":" + replayed + ",\"modified\":" + modified + ",\"held\":" + held + "}";

        SimulationResult simResult = result(run, realByNature, simByNature, byDistributor, replayed, modified, held);
        // Keep an append-only history row so the screen can show past simulations (PAR-13).
        SimulationHistory history = new SimulationHistory();
        history.entity = entity;
        history.period = period;
        history.ruleCode = ruleCode;
        history.versionNo = versionNo;
        history.scopeDistributor = scopeDistributor;
        history.realTotal = simResult.realTotal();
        history.simulatedTotal = simResult.simulatedTotal();
        history.ecart = simResult.ecart();
        history.linesReplayed = replayed;
        history.linesModified = modified;
        history.held = held;
        history.launchedBy = "simulation";
        history.runAt = clock.now();
        history.setCreatedAt(clock.now());
        history.persist();
        return simResult;
    }

    /**
     * Lists the past simulations of an entity, most recent first (PAR-13).
     *
     * @param entityCode the entity code
     * @return the simulation history views
     */
    @Transactional
    public List<SimulationHistoryView> history(String entityCode) {
        LegalEntity entity = entity(entityCode);
        return SimulationHistory.<SimulationHistory>list("entity = ?1 order by runAt desc", entity).stream()
                .map(h -> new SimulationHistoryView(
                        h.period == null ? null : h.period.periodMonth.toString(),
                        h.ruleCode, h.versionNo, h.scopeDistributor, h.realTotal, h.simulatedTotal, h.ecart,
                        h.linesReplayed, h.linesModified, h.held,
                        h.runAt == null ? null : h.runAt.toString()))
                .toList();
    }

    /**
     * Returns the simulated amount of a real line: unchanged when out of scope, recomputed from the
     * substituted rate when in scope, or null when the version drops the rate (the event is held).
     *
     * @param real             the real line
     * @param rule             the simulated rule
     * @param scopeDistributor the distributor scope, or null
     * @param table            the substitution table
     * @return the simulated amount, or null when held
     */
    private BigDecimal simulatedAmount(CommissionLine real, Rule rule, String scopeDistributor,
                                       List<Substitution> table) {
        if (!inScope(real, rule, scopeDistributor) || real.rate == null || real.basisAmount == null) {
            return real.amount;
        }
        for (Substitution substitution : table) {
            if (substitution.oldRate().compareTo(real.rate) == 0) {
                if (substitution.newRate() == null) {
                    return null;
                }
                return real.basisAmount.multiply(substitution.newRate()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return real.amount;
    }

    /**
     * Tells whether a real line is in the simulated version's scope: produced by the simulated rule,
     * and by the scoped distributor when one is given.
     *
     * @param real             the real line
     * @param rule             the simulated rule
     * @param scopeDistributor the distributor scope, or null
     * @return true when the line is in scope
     */
    private boolean inScope(CommissionLine real, Rule rule, String scopeDistributor) {
        if (real.ruleVersion == null || !real.ruleVersion.rule.getId().equals(rule.getId())) {
            return false;
        }
        return scopeDistributor == null
                || (real.originDistributor != null && scopeDistributor.equals(real.originDistributor.partnerCode));
    }

    /**
     * Writes a simulated line copied from a real line, in the simulation space, with the simulated
     * version and amount.
     *
     * @param entity     the entity
     * @param period     the period
     * @param run        the simulation run
     * @param simVersion the simulated version
     * @param rule       the simulated rule
     * @param real       the real line
     * @param simAmount  the simulated amount
     */
    private void writeSimLine(LegalEntity entity, Period period, CalculationRun run, RuleVersion simVersion,
                              Rule rule, CommissionLine real, BigDecimal simAmount) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.run = run;
        line.nature = real.nature;
        line.direction = real.direction;
        line.status = real.status;
        line.contract = real.contract;
        line.coverage = real.coverage;
        line.event = real.event;
        line.ruleVersion = inScope(real, rule, null) ? simVersion : real.ruleVersion;
        line.basisAmount = real.basisAmount;
        line.rate = simRate(real, rule, simAmount);
        line.amount = simAmount;
        line.originProductionCode = real.originProductionCode;
        line.originDistributor = real.originDistributor;
        line.attachmentMonth = real.attachmentMonth;
        line.premiumFiscalYear = real.premiumFiscalYear;
        line.explanation = real.explanation;
        line.setCreatedAt(clock.now());
        line.persist();
    }

    /**
     * The rate of a simulated line: derived from the simulated amount and basis when in scope and
     * recomputable, otherwise the real rate.
     *
     * @param real      the real line
     * @param rule      the simulated rule
     * @param simAmount the simulated amount
     * @return the simulated rate
     */
    private BigDecimal simRate(CommissionLine real, Rule rule, BigDecimal simAmount) {
        if (inScope(real, rule, null) && real.basisAmount != null && real.basisAmount.signum() != 0) {
            return simAmount.divide(real.basisAmount, 6, RoundingMode.HALF_UP);
        }
        return real.rate;
    }

    /**
     * Adds an amount to a nature bucket.
     *
     * @param buckets the buckets
     * @param nature  the nature
     * @param amount  the amount
     */
    private void add(Map<String, BigDecimal> buckets, LineNature nature, BigDecimal amount) {
        buckets.merge(nature.name(), amount, BigDecimal::add);
    }

    /**
     * Accumulates a distributor's real and simulated amounts.
     *
     * @param byDistributor the distributor buckets (real, simulated)
     * @param real          the real line
     * @param realAmount    the real amount
     * @param simAmount     the simulated amount
     */
    private void recordDistributor(Map<String, BigDecimal[]> byDistributor, CommissionLine real,
                                   BigDecimal realAmount, BigDecimal simAmount) {
        String key = real.originDistributor == null ? "—" : real.originDistributor.partnerCode;
        BigDecimal[] totals = byDistributor.computeIfAbsent(key,
                k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        totals[0] = totals[0].add(realAmount);
        totals[1] = totals[1].add(simAmount);
    }

    /**
     * Builds the simulation result from the accumulated buckets.
     *
     * @param run           the simulation run
     * @param realByNature  the real amounts by nature
     * @param simByNature   the simulated amounts by nature
     * @param byDistributor the amounts by distributor
     * @param replayed      the number of replayed lines
     * @param modified      the number of modified lines
     * @param held          the number of held events
     * @return the simulation result
     */
    private SimulationResult result(CalculationRun run, Map<String, BigDecimal> realByNature,
                                    Map<String, BigDecimal> simByNature, Map<String, BigDecimal[]> byDistributor,
                                    int replayed, int modified, int held) {
        BigDecimal realTotal = realByNature.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simTotal = simByNature.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<NatureComparison> byNature = new ArrayList<>();
        for (String nature : realByNature.keySet()) {
            BigDecimal realAmount = realByNature.get(nature);
            BigDecimal simAmount = simByNature.getOrDefault(nature, BigDecimal.ZERO);
            byNature.add(new NatureComparison(nature, realAmount, simAmount, simAmount.subtract(realAmount)));
        }
        List<DistributorImpact> top = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : byDistributor.entrySet()) {
            BigDecimal[] totals = entry.getValue();
            top.add(new DistributorImpact(entry.getKey(), totals[0], totals[1], totals[1].subtract(totals[0])));
        }
        top.sort(Comparator.comparing((DistributorImpact d) -> d.ecart().abs()).reversed());
        List<DistributorImpact> topThree = top.stream().filter(d -> d.ecart().signum() != 0).limit(3).toList();
        return new SimulationResult(run.getId(), realTotal, simTotal, simTotal.subtract(realTotal), replayed,
                modified, held, byNature, topThree);
    }

    /**
     * Builds the run parameters JSON (the simulated version, the substitution table and the scope).
     *
     * @param ruleCode         the simulated rule code
     * @param versionNo        the simulated version number
     * @param table            the substitution table
     * @param scopeDistributor the distributor scope, or null
     * @return the parameters JSON
     */
    private String parameters(String ruleCode, Integer versionNo, List<Substitution> table,
                              String scopeDistributor) {
        StringBuilder subs = new StringBuilder("[");
        for (int i = 0; i < table.size(); i++) {
            Substitution s = table.get(i);
            if (i > 0) {
                subs.append(',');
            }
            subs.append("{\"old\":").append(s.oldRate().toPlainString()).append(",\"new\":")
                    .append(s.newRate() == null ? "null" : s.newRate().toPlainString()).append('}');
        }
        subs.append(']');
        return "{\"rule\":\"" + ruleCode + "\",\"version\":" + versionNo + ",\"scope\":"
                + (scopeDistributor == null ? "null" : "\"" + scopeDistributor + "\"")
                + ",\"substitutions\":" + subs + "}";
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
     * @param periodMonth the period month
     * @return the period
     */
    private Period period(LegalEntity entity, LocalDate periodMonth) {
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue");
        }
        return period;
    }

    /**
     * A comparison of the real and simulated amounts of one nature.
     *
     * @param nature    the line nature
     * @param real      the real amount
     * @param simulated the simulated amount
     * @param ecart     the gap (simulated − real)
     */
    public record NatureComparison(String nature, BigDecimal real, BigDecimal simulated, BigDecimal ecart) {
    }

    /**
     * The impact of the simulation on one distributor.
     *
     * @param distributor the distributor partner code
     * @param real        the real amount
     * @param simulated   the simulated amount
     * @param ecart       the gap (simulated − real)
     */
    public record DistributorImpact(String distributor, BigDecimal real, BigDecimal simulated, BigDecimal ecart) {
    }

    /**
     * The result of a simulation.
     *
     * @param runId          the simulation run id
     * @param realTotal      the real total
     * @param simulatedTotal the simulated total
     * @param ecart          the gap (simulated − real)
     * @param linesReplayed  the number of replayed lines
     * @param linesModified  the number of modified lines
     * @param held           the number of held events (dropped rate)
     * @param byNature       the comparison by nature
     * @param topDistributors the most affected distributors
     */
    public record SimulationResult(Long runId, BigDecimal realTotal, BigDecimal simulatedTotal, BigDecimal ecart,
                                   int linesReplayed, int linesModified, int held,
                                   List<NatureComparison> byNature, List<DistributorImpact> topDistributors) {
    }

    /**
     * One past simulation of an entity (PAR-13).
     *
     * @param periodMonth      the simulated period month (ISO)
     * @param ruleCode         the simulated rule code
     * @param versionNo        the simulated version number
     * @param scopeDistributor the distributor scope, or null
     * @param realTotal        the real total
     * @param simulatedTotal   the simulated total
     * @param ecart            the gap (simulated − real)
     * @param linesReplayed    the number of replayed lines
     * @param linesModified    the number of modified lines
     * @param held             the number of held events
     * @param runAt            when the simulation ran (ISO)
     */
    public record SimulationHistoryView(String periodMonth, String ruleCode, Integer versionNo,
                                        String scopeDistributor, BigDecimal realTotal, BigDecimal simulatedTotal,
                                        BigDecimal ecart, Integer linesReplayed, Integer linesModified, Integer held,
                                        String runAt) {
    }
}
