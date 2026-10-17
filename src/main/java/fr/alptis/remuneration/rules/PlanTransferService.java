package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleBasis;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Scale;
import fr.alptis.remuneration.domain.ScaleMeasure;
import fr.alptis.remuneration.domain.ScaleMode;
import fr.alptis.remuneration.domain.ScaleOutput;
import fr.alptis.remuneration.domain.ScaleTier;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.domain.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Export and import of the rule configuration (PAR-15): a plan with its rules and versions is
 * serialised to an open JSON structure for promotion between environments and reversibility, and
 * imported back under a new plan code with every version recreated in {@code DRAFT}. The import never
 * overwrites an existing plan.
 */
@ApplicationScoped
public class PlanTransferService {

    /**
     * Exports a plan, its rules and their versions.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @return the plan export
     */
    public PlanExport export(String entityCode, String planCode) {
        LegalEntity entity = entity(entityCode);
        CommissionPlan plan = plan(entity, planCode);
        List<RuleExport> rules = Rule.<Rule>list("plan = ?1 order by code", plan).stream()
                .map(this::exportRule)
                .toList();
        return new PlanExport(plan.code, plan.label, plan.direction.name(), plan.vintage, rules);
    }

    /**
     * Exports one rule and its versions.
     *
     * @param rule the rule
     * @return the rule export
     */
    private RuleExport exportRule(Rule rule) {
        List<VersionExport> versions = RuleVersion.<RuleVersion>list("rule = ?1 order by versionNo", rule).stream()
                .map(v -> new VersionExport(v.versionNo, v.validFrom, v.validTo,
                        v.versionAnchor == null ? null : v.versionAnchor.name(), v.scope,
                        v.basis == null ? null : v.basis.name(),
                        v.outputNature == null ? null : v.outputNature.name(), v.parameters, v.clawbackPolicy,
                        v.paymentSchedule, v.priority, v.exclusionGroup, exportScale(v)))
                .toList();
        return new RuleExport(rule.code, rule.label, rule.family.name(), versions);
    }

    /**
     * Exports a version's tiered scale, if it has one (PAR-13): a scale would otherwise be lost on the
     * export/import round-trip.
     *
     * @param version the rule version
     * @return the scale export, or null when the version carries no scale
     */
    private ScaleExport exportScale(RuleVersion version) {
        Scale scale = Scale.find("ruleVersion", version).firstResult();
        if (scale == null) {
            return null;
        }
        List<TierExport> tiers = ScaleTier.<ScaleTier>list("scale = ?1 order by lowerBound", scale).stream()
                .map(t -> new TierExport(t.lowerBound, t.upperBound, t.rate, t.amount, t.fromFirstUnit))
                .toList();
        return new ScaleExport(scale.mode.name(), scale.measure.name(), scale.output.name(), tiers);
    }

    /**
     * Imports a plan under a new code, recreating its rules and versions in {@code DRAFT}.
     *
     * @param entityCode  the target entity code
     * @param newPlanCode the code of the plan to create
     * @param export      the plan export
     * @return the created plan code
     */
    @Transactional
    public String importPlan(String entityCode, String newPlanCode, PlanExport export) {
        LegalEntity entity = entity(entityCode);
        if (CommissionPlan.count("entity = ?1 and code = ?2", entity, newPlanCode) > 0) {
            throw new ValidationException("Un plan porte déjà le code : " + newPlanCode);
        }
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = newPlanCode;
        plan.label = export.label();
        plan.direction = Direction.valueOf(export.direction());
        plan.vintage = export.vintage();
        plan.persist();
        for (RuleExport ruleExport : export.rules()) {
            Rule rule = new Rule();
            rule.plan = plan;
            rule.code = ruleExport.code();
            rule.label = ruleExport.label();
            rule.family = RuleFamily.valueOf(ruleExport.family());
            rule.persist();
            for (VersionExport versionExport : ruleExport.versions()) {
                RuleVersion version = new RuleVersion();
                version.rule = rule;
                version.versionNo = versionExport.versionNo();
                version.status = VersionStatus.DRAFT;
                version.validFrom = versionExport.validFrom();
                version.validTo = versionExport.validTo();
                version.versionAnchor = versionExport.versionAnchor() == null
                        ? null : VersionAnchor.valueOf(versionExport.versionAnchor());
                version.scope = versionExport.scope();
                version.basis = versionExport.basis() == null ? null : RuleBasis.valueOf(versionExport.basis());
                version.outputNature = versionExport.outputNature() == null
                        ? null : LineNature.valueOf(versionExport.outputNature());
                version.parameters = versionExport.parameters();
                version.clawbackPolicy = versionExport.clawbackPolicy();
                version.paymentSchedule = versionExport.paymentSchedule();
                version.priority = versionExport.priority();
                version.exclusionGroup = versionExport.exclusionGroup();
                version.persist();
                importScale(version, versionExport.scale());
            }
        }
        return newPlanCode;
    }

    /**
     * Recreates a version's tiered scale from its export, if any (PAR-13).
     *
     * @param version the recreated version
     * @param export  the scale export, or null
     */
    private void importScale(RuleVersion version, ScaleExport export) {
        if (export == null) {
            return;
        }
        Scale scale = new Scale();
        scale.ruleVersion = version;
        scale.mode = ScaleMode.valueOf(export.mode());
        scale.measure = ScaleMeasure.valueOf(export.measure());
        scale.output = ScaleOutput.valueOf(export.output());
        scale.persist();
        for (TierExport tierExport : export.tiers()) {
            ScaleTier tier = new ScaleTier();
            tier.scale = scale;
            tier.lowerBound = tierExport.lowerBound();
            tier.upperBound = tierExport.upperBound();
            tier.rate = tierExport.rate();
            tier.amount = tierExport.amount();
            tier.fromFirstUnit = tierExport.fromFirstUnit();
            tier.persist();
        }
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
     * Looks up a plan by entity and code.
     *
     * @param entity   the entity
     * @param planCode the plan code
     * @return the plan
     */
    private CommissionPlan plan(LegalEntity entity, String planCode) {
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", entity, planCode).firstResult();
        if (plan == null) {
            throw new ResourceNotFoundException("Plan inconnu : " + planCode);
        }
        return plan;
    }

    /**
     * A plan export: the plan and its rules.
     *
     * @param code      the plan code
     * @param label     the plan label
     * @param direction the plan direction
     * @param vintage   the plan vintage
     * @param rules     the rules
     */
    public record PlanExport(String code, String label, String direction, Short vintage, List<RuleExport> rules) {
    }

    /**
     * A rule export: the rule and its versions.
     *
     * @param code     the rule code
     * @param label    the rule label
     * @param family   the rule family
     * @param versions the versions
     */
    public record RuleExport(String code, String label, String family, List<VersionExport> versions) {
    }

    /**
     * A version export: every parameter needed to recreate a version.
     *
     * @param versionNo       the version number
     * @param validFrom       the start of validity
     * @param validTo         the end of validity
     * @param versionAnchor   the version anchor
     * @param scope           the scope JSON
     * @param basis           the rule basis
     * @param outputNature    the output nature
     * @param parameters      the parameters JSON
     * @param clawbackPolicy  the clawback policy JSON
     * @param paymentSchedule the payment schedule JSON
     * @param priority        the priority
     * @param exclusionGroup  the exclusion group
     * @param scale           the tiered scale, or null when the version has none
     */
    public record VersionExport(Integer versionNo, LocalDate validFrom, LocalDate validTo, String versionAnchor,
                                String scope, String basis, String outputNature, String parameters,
                                String clawbackPolicy, String paymentSchedule, Integer priority,
                                String exclusionGroup, ScaleExport scale) {
    }

    /**
     * A tiered scale export: its mode, measure, output and tiers (PAR-13).
     *
     * @param mode    the scale mode (MARGINAL, WHOLE)
     * @param measure the scale measure
     * @param output  the scale output
     * @param tiers   the tiers
     */
    public record ScaleExport(String mode, String measure, String output, List<TierExport> tiers) {
    }

    /**
     * A scale-tier export.
     *
     * @param lowerBound    the inclusive lower bound
     * @param upperBound    the upper bound, or null for an open tier
     * @param rate          the tier rate, or null
     * @param amount        the tier flat amount, or null
     * @param fromFirstUnit whether the rate applies from the first unit once the tier is reached
     */
    public record TierExport(java.math.BigDecimal lowerBound, java.math.BigDecimal upperBound,
                             java.math.BigDecimal rate, java.math.BigDecimal amount, Boolean fromFirstUnit) {
    }
}
