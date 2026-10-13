package fr.alptis.remuneration.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ConditionFailureMode;
import fr.alptis.remuneration.domain.ConditionOperator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleCondition;
import fr.alptis.remuneration.domain.RuleBasis;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.domain.VersionStatus;
import fr.alptis.remuneration.engine.FamilyRegistry;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import fr.alptis.remuneration.security.SeparationOfDuties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures plans, rules, versions and their approval cycle through screens, without development
 * (PAR-01 to PAR-08, PAR-12, PAR-14). A version carries all that is computed; a change never
 * overwrites a version, it adds one (PAR-03). A version follows DRAFT, APPROVED, ACTIVE, RETIRED
 * (PAR-04); approval is performed by a second administrator (separation of duties) and refused while
 * a conflict exists (PAR-05, section 6.11). Parameters are validated against the family schema
 * (PAR-02). Everything is partitioned by entity and traced in the audit trail.
 */
@ApplicationScoped
public class RuleConfigService {

    /** Upper plausibility bound for a rate: a higher rate raises a PAR-14 alert. */
    private static final BigDecimal RATE_PLAUSIBILITY_CEILING = new BigDecimal("0.60");

    private final EntityScope scope;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final DateTimeProvider clock;
    private final FamilyRegistry registry;
    private final ConflictDetector conflictDetector;
    private final SeparationOfDuties separationOfDuties;
    private final ObjectMapper mapper;

    /**
     * Creates the service.
     *
     * @param scope              the entity partition
     * @param currentUser        the current user (author and approver)
     * @param audit              the audit trail
     * @param clock              the application date/time provider
     * @param registry           the family registry (schema validation)
     * @param conflictDetector   the conflict detector
     * @param separationOfDuties the separation-of-duties guard
     * @param mapper             the Jackson mapper
     */
    @Inject
    public RuleConfigService(EntityScope scope, CurrentUser currentUser, AuditService audit, DateTimeProvider clock,
                             FamilyRegistry registry, ConflictDetector conflictDetector,
                             SeparationOfDuties separationOfDuties, ObjectMapper mapper) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.registry = registry;
        this.conflictDetector = conflictDetector;
        this.separationOfDuties = separationOfDuties;
        this.mapper = mapper;
    }

    /**
     * Lists the plans of the user's entities, most recent vintage first.
     *
     * @return the plan views
     */
    @Transactional
    public List<PlanView> listPlans() {
        List<CommissionPlan> plans = scope.unrestricted()
                ? CommissionPlan.list("order by vintage desc, code")
                : CommissionPlan.list("entity.code in ?1 order by vintage desc, code", scope.visibleCodes());
        return plans.stream().map(RuleConfigService::toPlanView).toList();
    }

    /**
     * Creates a plan in an entity.
     *
     * @param entityCode the entity code
     * @param code       the plan code
     * @param label      the plan label
     * @param direction  the plan direction
     * @param vintage    the plan vintage (year)
     * @return the created plan view
     * @throws ConflictException if a plan with that code already exists in the entity
     */
    @Transactional
    public PlanView createPlan(String entityCode, String code, String label, Direction direction, Short vintage) {
        LegalEntity entity = requireEntity(entityCode);
        if (CommissionPlan.count("entity = ?1 and code = ?2", entity, code) > 0) {
            throw new ConflictException("Plan déjà existant : " + code);
        }
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = code;
        plan.label = label;
        plan.direction = direction;
        plan.vintage = vintage;
        stamp(plan);
        plan.persist();
        audit.record(currentUser.username(), entity, "CREATE_PLAN", "CommissionPlan", plan.getId(), null,
                "{\"code\":\"" + code + "\"}");
        return toPlanView(plan);
    }

    /**
     * Lists the rules of a plan.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @return the rule views
     */
    @Transactional
    public List<RuleView> listRules(String entityCode, String planCode) {
        CommissionPlan plan = requirePlan(entityCode, planCode);
        return Rule.<Rule>list("plan = ?1 order by code", plan).stream().map(RuleConfigService::toRuleView).toList();
    }

    /**
     * Creates a rule in a plan.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param code       the rule code
     * @param label      the rule label
     * @param family     the calculation family
     * @return the created rule view
     * @throws ConflictException if a rule with that code already exists in the plan
     */
    @Transactional
    public RuleView createRule(String entityCode, String planCode, String code, String label, RuleFamily family) {
        CommissionPlan plan = requirePlan(entityCode, planCode);
        if (Rule.count("plan = ?1 and code = ?2", plan, code) > 0) {
            throw new ConflictException("Règle déjà existante : " + code);
        }
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = code;
        rule.label = label;
        rule.family = family;
        stamp(rule);
        rule.persist();
        audit.record(currentUser.username(), plan.entity, "CREATE_RULE", "Rule", rule.getId(), null,
                "{\"code\":\"" + code + "\"}");
        return toRuleView(rule);
    }

    /**
     * Lists the versions of a rule, most recent first, with their detected conflicts.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @return the version views
     */
    @Transactional
    public List<VersionView> listVersions(String entityCode, String planCode, String ruleCode) {
        Rule rule = requireRule(entityCode, planCode, ruleCode);
        return RuleVersion.<RuleVersion>list("rule = ?1 order by versionNo desc", rule).stream()
                .map(v -> toVersionView(v, conflictDetector.conflicts(v)))
                .toList();
    }

    /**
     * Adds a new draft version to a rule, validating its parameters against the family schema.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param input      the version fields
     * @return the created version view
     */
    @Transactional
    public VersionView addVersion(String entityCode, String planCode, String ruleCode, VersionInput input) {
        Rule rule = requireRule(entityCode, planCode, ruleCode);
        registry.validate(rule.family, input.parameters());
        int nextVersion = 1 + (int) RuleVersion.count("rule = ?1", rule);
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = nextVersion;
        apply(version, input);
        version.status = VersionStatus.DRAFT;
        stamp(version);
        version.setCreatedBy(currentUser.username());
        version.persist();
        if (input.conditions() != null) {
            for (ConditionInput condition : input.conditions()) {
                RuleCondition row = new RuleCondition();
                row.ruleVersion = version;
                row.indicatorType = condition.indicatorType() == null ? null
                        : QualityIndicatorType.valueOf(condition.indicatorType());
                row.counterCode = condition.counterCode();
                row.operator = condition.operator() == null ? null
                        : ConditionOperator.valueOf(condition.operator());
                row.threshold = condition.threshold();
                row.measurementPeriod = condition.measurementPeriod();
                row.onFailure = condition.onFailure() == null ? null
                        : ConditionFailureMode.valueOf(condition.onFailure());
                row.persist();
            }
        }
        audit.record(currentUser.username(), rule.plan.entity, "CREATE_RULE_VERSION", "RuleVersion",
                version.getId(), null, "{\"rule\":\"" + ruleCode + "\",\"versionNo\":" + nextVersion + "}");
        return toVersionView(version, List.of());
    }

    /**
     * Approves a draft version: a second administrator, no conflict (PAR-04, PAR-05).
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param versionNo  the version number
     * @return the approved version view
     * @throws ValidationException if the version is not a draft
     * @throws ConflictException   if a conflicting version exists
     */
    @Transactional
    public VersionView approveVersion(String entityCode, String planCode, String ruleCode, int versionNo) {
        RuleVersion version = requireVersion(entityCode, planCode, ruleCode, versionNo);
        if (version.status != VersionStatus.DRAFT) {
            throw new ValidationException("Seule une version en projet peut être approuvée.");
        }
        separationOfDuties.ensureDifferentApprover(version.getCreatedBy(), currentUser.username());
        List<RuleVersion> conflicts = conflictDetector.conflicts(version);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Approbation refusée : conflit avec " + conflicts.size()
                    + " version(s) de même priorité et groupe d'exclusion sur un périmètre et une période communs.");
        }
        version.status = VersionStatus.APPROVED;
        version.approvedBy = currentUser.username();
        version.approvedAt = clock.now();
        version.setUpdatedAt(clock.now());
        audit.record(currentUser.username(), version.rule.plan.entity, "APPROVE_RULE_VERSION", "RuleVersion",
                version.getId(), "\"DRAFT\"", "\"APPROVED\"");
        return toVersionView(version, List.of());
    }

    /**
     * Generates the amendment (avenant) founding a rule version from its parameters and records it as
     * sent for electronic signature (PAR-11): an {@link Agreement} of the matching amendment type is
     * created, unsigned (awaiting signature), carrying the generated document reference. The electronic
     * signature itself is out of the dev profile (section 8 of the brief); the amendment waits in the
     * {@code NOT_SIGNED} state until its signature is recorded.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param versionNo  the rule version number
     * @return the generated amendment view
     */
    @Transactional
    public AmendmentView generateAmendment(String entityCode, String planCode, String ruleCode, int versionNo) {
        RuleVersion version = requireVersion(entityCode, planCode, ruleCode, versionNo);
        LegalEntity entity = version.rule.plan.entity;
        AgreementType type = amendmentTypeOf(version.outputNature);
        String reference = entity.code + "_AVENANT_" + ruleCode + "_v" + versionNo;
        Agreement agreement = new Agreement();
        agreement.entity = entity;
        agreement.agreementType = type;
        agreement.reference = reference;
        agreement.documentRef = reference + ".pdf";
        agreement.validFrom = version.validFrom;
        agreement.validTo = version.validTo;
        agreement.signatureStatus = SignatureStatus.NOT_SIGNED;
        agreement.setCreatedAt(clock.now());
        agreement.persist();
        audit.record(currentUser.username(), entity, "GENERATE_AMENDMENT", "Agreement", agreement.getId(), null,
                "{\"reference\":\"" + reference + "\",\"type\":\"" + type.name() + "\"}");
        return new AmendmentView(reference, type.name(), agreement.signatureStatus.name(), agreement.documentRef);
    }

    /**
     * Maps a rule's produced nature to the amendment type it founds: a surcommission distributeur or
     * prévoyance amendment, else a partnership agreement.
     *
     * @param nature the rule version's output nature
     * @return the amendment type
     */
    private static AgreementType amendmentTypeOf(LineNature nature) {
        return switch (nature) {
            case RCD -> AgreementType.RCD_AMENDMENT;
            case RCP -> AgreementType.RCP_AMENDMENT;
            default -> AgreementType.PARTNERSHIP;
        };
    }

    /**
     * The generated amendment (PAR-11).
     *
     * @param reference       the amendment reference
     * @param type            the amendment type
     * @param signatureStatus the signature status (awaiting signature)
     * @param documentRef     the generated document reference
     */
    public record AmendmentView(String reference, String type, String signatureStatus, String documentRef) {
    }

    /**
     * Duplicates a plan for the annual renewal (PAR-12): the new plan copies every rule and version
     * as a draft, shifted to the new vintage year.
     *
     * @param entityCode the entity code
     * @param sourceCode the source plan code
     * @param newCode    the new plan code
     * @param newVintage the new vintage (year)
     * @return the created plan view
     */
    @Transactional
    public PlanView duplicatePlan(String entityCode, String sourceCode, String newCode, Short newVintage) {
        CommissionPlan source = requirePlan(entityCode, sourceCode);
        if (CommissionPlan.count("entity = ?1 and code = ?2", source.entity, newCode) > 0) {
            throw new ConflictException("Plan déjà existant : " + newCode);
        }
        int yearShift = newVintage - source.vintage;
        CommissionPlan copy = new CommissionPlan();
        copy.entity = source.entity;
        copy.code = newCode;
        copy.label = source.label;
        copy.direction = source.direction;
        copy.vintage = newVintage;
        stamp(copy);
        copy.persist();
        for (Rule rule : Rule.<Rule>list("plan", source)) {
            Rule ruleCopy = new Rule();
            ruleCopy.plan = copy;
            ruleCopy.code = rule.code;
            ruleCopy.label = rule.label;
            ruleCopy.family = rule.family;
            stamp(ruleCopy);
            ruleCopy.persist();
            for (RuleVersion version : RuleVersion.<RuleVersion>list("rule", rule)) {
                RuleVersion versionCopy = new RuleVersion();
                versionCopy.rule = ruleCopy;
                versionCopy.versionNo = version.versionNo;
                versionCopy.validFrom = shift(version.validFrom, yearShift);
                versionCopy.validTo = shift(version.validTo, yearShift);
                versionCopy.status = VersionStatus.DRAFT;
                versionCopy.versionAnchor = version.versionAnchor;
                versionCopy.scope = version.scope;
                versionCopy.triggerEventTypes = version.triggerEventTypes == null ? null
                        : new ArrayList<>(version.triggerEventTypes);
                versionCopy.basis = version.basis;
                versionCopy.outputNature = version.outputNature;
                versionCopy.parameters = version.parameters;
                versionCopy.clawbackPolicy = version.clawbackPolicy;
                versionCopy.paymentSchedule = version.paymentSchedule;
                versionCopy.priority = version.priority;
                versionCopy.exclusionGroup = version.exclusionGroup;
                versionCopy.agreement = version.agreement;
                stamp(versionCopy);
                versionCopy.setCreatedBy(currentUser.username());
                versionCopy.persist();
            }
        }
        audit.record(currentUser.username(), source.entity, "DUPLICATE_PLAN", "CommissionPlan", copy.getId(),
                "{\"source\":\"" + sourceCode + "\"}", "{\"code\":\"" + newCode + "\"}");
        return toPlanView(copy);
    }

    /**
     * Returns the plausibility alerts (PAR-14) for a parameter payload: any rate above the ceiling.
     *
     * @param parametersJson the parameters JSON, possibly null
     * @return the French alert messages (empty when every rate is plausible)
     */
    public List<String> plausibilityAlerts(String parametersJson) {
        List<String> alerts = new ArrayList<>();
        if (parametersJson == null || parametersJson.isBlank()) {
            return alerts;
        }
        JsonNode root;
        try {
            root = mapper.readTree(parametersJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return alerts;
        }
        collectRateAlerts(root, alerts);
        return alerts;
    }

    /**
     * Walks a JSON node and adds an alert for every numeric value, under a rate-like key, that
     * exceeds the plausibility ceiling.
     *
     * @param node   the current node
     * @param alerts the accumulator
     */
    private static void collectRateAlerts(JsonNode node, List<String> alerts) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                JsonNode value = e.getValue();
                if (value.isNumber() && isRateKey(e.getKey())
                        && value.decimalValue().compareTo(RATE_PLAUSIBILITY_CEILING) > 0) {
                    alerts.add("Taux peu vraisemblable (" + value.decimalValue() + ") pour « " + e.getKey() + " ».");
                } else {
                    collectRateAlerts(value, alerts);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRateAlerts(child, alerts));
        }
    }

    /**
     * Tells whether a key denotes a rate (and is therefore bounded by the plausibility ceiling).
     *
     * @param key the property name
     * @return {@code true} for a rate-like key
     */
    private static boolean isRateKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("rate") || lower.equals("acquisition") || lower.equals("servicing");
    }

    /**
     * Applies the input fields to a version (shared by create).
     *
     * @param version the version to fill
     * @param input   the input fields
     */
    private static void apply(RuleVersion version, VersionInput input) {
        version.validFrom = input.validFrom();
        version.validTo = input.validTo();
        version.versionAnchor = input.versionAnchor();
        version.scope = input.scope();
        version.triggerEventTypes = input.triggerEventTypes();
        version.basis = input.basis();
        version.outputNature = input.outputNature();
        version.parameters = input.parameters();
        version.clawbackPolicy = input.clawbackPolicy();
        version.paymentSchedule = input.paymentSchedule();
        version.priority = input.priority();
        version.exclusionGroup = input.exclusionGroup();
    }

    /**
     * Shifts a date by a number of years, keeping null null.
     *
     * @param date  the date, possibly null
     * @param years the number of years to add
     * @return the shifted date, or null
     */
    private static LocalDate shift(LocalDate date, int years) {
        return date == null ? null : date.plusYears(years);
    }

    /**
     * Stamps the creation timestamp from the clock.
     *
     * @param entity the entity to stamp
     */
    private void stamp(fr.alptis.remuneration.domain.BaseEntity entity) {
        entity.setCreatedAt(clock.now());
    }

    /**
     * Resolves an entity in the user's scope.
     *
     * @param entityCode the entity code
     * @return the entity
     * @throws ResourceNotFoundException if unknown or out of scope
     */
    private LegalEntity requireEntity(String entityCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null || !scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        return entity;
    }

    /**
     * Resolves a plan in the user's scope.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @return the plan
     * @throws ResourceNotFoundException if unknown or out of scope
     */
    private CommissionPlan requirePlan(String entityCode, String planCode) {
        LegalEntity entity = requireEntity(entityCode);
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", entity, planCode).firstResult();
        if (plan == null) {
            throw new ResourceNotFoundException("Plan introuvable : " + planCode);
        }
        return plan;
    }

    /**
     * Resolves a rule in the user's scope.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @return the rule
     * @throws ResourceNotFoundException if unknown or out of scope
     */
    private Rule requireRule(String entityCode, String planCode, String ruleCode) {
        CommissionPlan plan = requirePlan(entityCode, planCode);
        Rule rule = Rule.find("plan = ?1 and code = ?2", plan, ruleCode).firstResult();
        if (rule == null) {
            throw new ResourceNotFoundException("Règle introuvable : " + ruleCode);
        }
        return rule;
    }

    /**
     * Resolves a version in the user's scope.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param versionNo  the version number
     * @return the version
     * @throws ResourceNotFoundException if unknown or out of scope
     */
    private RuleVersion requireVersion(String entityCode, String planCode, String ruleCode, int versionNo) {
        Rule rule = requireRule(entityCode, planCode, ruleCode);
        RuleVersion version = RuleVersion.find("rule = ?1 and versionNo = ?2", rule, versionNo).firstResult();
        if (version == null) {
            throw new ResourceNotFoundException("Version introuvable : " + ruleCode + " v" + versionNo);
        }
        return version;
    }

    /**
     * Builds the view of a plan.
     *
     * @param plan the plan
     * @return its view
     */
    private static PlanView toPlanView(CommissionPlan plan) {
        return new PlanView(plan.entity.code, plan.code, plan.label,
                plan.direction == null ? null : plan.direction.name(), plan.vintage);
    }

    /**
     * Builds the view of a rule.
     *
     * @param rule the rule
     * @return its view
     */
    private static RuleView toRuleView(Rule rule) {
        return new RuleView(rule.code, rule.label, rule.family == null ? null : rule.family.name());
    }

    /**
     * Builds the view of a version, with the codes of its detected conflicts.
     *
     * @param version   the version
     * @param conflicts the conflicting versions
     * @return its view
     */
    private static VersionView toVersionView(RuleVersion version, List<RuleVersion> conflicts) {
        List<String> conflictCodes = conflicts.stream()
                .map(c -> c.rule.code + " v" + c.versionNo).toList();
        List<ConditionView> conditions = RuleCondition.<RuleCondition>list("ruleVersion", version).stream()
                .map(c -> new ConditionView(
                        c.indicatorType == null ? null : c.indicatorType.name(),
                        c.counterCode,
                        c.operator == null ? null : c.operator.name(),
                        c.threshold,
                        c.onFailure == null ? null : c.onFailure.name()))
                .toList();
        return new VersionView(version.versionNo,
                version.validFrom == null ? null : version.validFrom.toString(),
                version.validTo == null ? null : version.validTo.toString(),
                version.status == null ? null : version.status.name(),
                version.priority, version.exclusionGroup, version.parameters,
                version.clawbackPolicy, version.paymentSchedule, conditions,
                version.approvedBy, conflictCodes);
    }

    /**
     * Fields of a rule version supplied by the configuration form.
     *
     * @param validFrom         the validity start
     * @param validTo           the validity end
     * @param versionAnchor     the historisation anchor
     * @param scope             the perimeter (JSON)
     * @param triggerEventTypes the triggering event types
     * @param basis             the basis
     * @param outputNature      the produced line nature
     * @param parameters        the family parameters (JSON)
     * @param clawbackPolicy    the clawback policy (JSON)
     * @param paymentSchedule   the payment schedule (JSON)
     * @param priority          the priority
     * @param exclusionGroup    the exclusion group
     */
    public record VersionInput(LocalDate validFrom, LocalDate validTo, VersionAnchor versionAnchor, String scope,
                               List<String> triggerEventTypes, RuleBasis basis, LineNature outputNature,
                               String parameters, String clawbackPolicy, String paymentSchedule, Integer priority,
                               String exclusionGroup, List<ConditionInput> conditions) {
    }

    /**
     * Exchange view of a plan.
     *
     * @param entityCode the entity code
     * @param code       the plan code
     * @param label      the plan label
     * @param direction  the direction
     * @param vintage    the vintage
     */
    public record PlanView(String entityCode, String code, String label, String direction, Short vintage) {
    }

    /**
     * Exchange view of a rule.
     *
     * @param code   the rule code
     * @param label  the rule label
     * @param family the calculation family
     */
    public record RuleView(String code, String label, String family) {
    }

    /**
     * Exchange view of a rule version.
     *
     * @param versionNo      the version number
     * @param validFrom      the validity start
     * @param validTo        the validity end
     * @param status         the status
     * @param priority       the priority
     * @param exclusionGroup the exclusion group
     * @param parameters     the family parameters (JSON)
     * @param approvedBy     the approver, if approved
     * @param conflicts      the codes of detected conflicting versions
     */
    public record VersionView(Integer versionNo, String validFrom, String validTo, String status, Integer priority,
                              String exclusionGroup, String parameters, String clawbackPolicy, String paymentSchedule,
                              List<ConditionView> conditions, String approvedBy, List<String> conflicts) {
    }

    /**
     * One payment condition of a version (PAR-07): an indicator or counter compared to a threshold,
     * and what a failure does.
     *
     * @param indicatorType the quality-indicator type, if any
     * @param counterCode   the counter code, if any
     * @param operator      the comparison operator
     * @param threshold     the threshold
     * @param onFailure     what a failure does (FORFEIT or DEFER)
     */
    public record ConditionView(String indicatorType, String counterCode, String operator, BigDecimal threshold,
                                String onFailure) {
    }

    /**
     * A payment condition supplied by the configuration form (PAR-07).
     *
     * @param indicatorType     the quality-indicator type name, or null
     * @param counterCode       the counter code, or null
     * @param operator          the comparison operator name
     * @param threshold         the threshold
     * @param measurementPeriod the measurement period, or null
     * @param onFailure         what a failure does (FORFEIT or DEFER)
     */
    public record ConditionInput(String indicatorType, String counterCode, String operator, BigDecimal threshold,
                                 String measurementPeriod, String onFailure) {
    }
}
