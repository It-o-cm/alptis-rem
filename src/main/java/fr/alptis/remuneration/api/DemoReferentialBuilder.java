package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementProduct;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DistributorType;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.EnrollmentStatus;
import fr.alptis.remuneration.domain.EntityParameter;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.ImportMapping;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.InsurerRole;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ManagementMode;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentFrequency;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductTarget;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Scale;
import fr.alptis.remuneration.domain.ScaleMeasure;
import fr.alptis.remuneration.domain.ScaleMode;
import fr.alptis.remuneration.domain.ScaleOutput;
import fr.alptis.remuneration.domain.ScaleTier;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.StatementGrouping;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.domain.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the static referential of the demonstration dataset: ranges, insurers, products,
 * distributors and their groups, production codes, bank details, the nine-family demonstration rules,
 * agreements and enrollments, discount (escompte) conventions, external codes, payee rules, file
 * mappings, quality indicators and entity parameters — for both Alptis Assurances and UNEP.
 *
 * <p>Referential objects are created directly (the simplest route allowed by the dataset brief); only
 * what the application computes is produced by the real services, in later blocks. The family→product
 * mapping is deliberately conflict-free: each premium-driven base rule sits on its own product and
 * event type so the engine never has to arbitrate two base rules on the same grain.</p>
 */
@ApplicationScoped
public class DemoReferentialBuilder {

    /** The author stamped on every referential object of this block. */
    private static final String AUTHOR = "demo-referential";

    /** The first day the demonstration referential is valid. */
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the builder.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public DemoReferentialBuilder(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Builds the whole referential into the shared world.
     *
     * @param world the shared demo world to populate
     */
    void build(DemoWorld world) {
        loadEntities(world);
        Map<String, ProductRange> ranges = ranges();
        List<Insurer> insurers = insurers();
        products(world, ranges, insurers);
        distributors(world);
        groups(world);
        productionCodes(world);
        bankAccounts(world);
        demonstrationRules(world);
        agreementsAndEnrollments(world);
        advanceAgreements(world);
        externalCodes(world);
        payeeRules(world);
        mappings(world);
        qualityIndicators(world);
        entityParameters(world);
    }

    /**
     * Loads the two demonstration entities (seeded by the security bootstrap) into the world and sets
     * their payment parameters.
     *
     * @param world the shared demo world
     */
    private void loadEntities(DemoWorld world) {
        for (String code : List.of(DemoCodes.ALPTIS, DemoCodes.UNEP)) {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", code).firstResult();
            entity.minPaymentAmount = new BigDecimal("1.00");
            entity.advanceSettlementDays = 90;
            entity.advanceSettlementTolerance = new BigDecimal("5.00");
            entity.debtorIban = "FR7630004000031234567890143";
            entity.debtorBic = "BNPAFRPP";
            entity.persist();
            world.entities.put(code, entity);
        }
    }

    /**
     * Creates the product ranges (global, shared by both entities).
     *
     * @return the created ranges
     */
    private Map<String, ProductRange> ranges() {
        String[][] rows = {
                {"SANTE", "Santé", "INDIVIDUAL"},
                {"PREVOYANCE", "Prévoyance", "PROFESSIONAL"},
                {"EPARGNE", "Épargne retraite", "INDIVIDUAL"},
                {"EMPRUNTEUR", "Emprunteur", "INDIVIDUAL"},
                {"COLLECTIVE", "Collective", "COMPANY"},
        };
        Map<String, ProductRange> ranges = new java.util.LinkedHashMap<>();
        for (String[] row : rows) {
            ProductRange range = new ProductRange();
            range.code = row[0];
            range.label = row[1];
            range.target = ProductTarget.valueOf(row[2]);
            stamp(range);
            range.persist();
            ranges.put(range.code, range);
        }
        return ranges;
    }

    /**
     * Creates the insurers (global).
     *
     * @return the created insurers
     */
    private List<Insurer> insurers() {
        String[][] rows = {
                {"ALPTIS_RISK", "Alptis Assurances — porteur de risque", "RISK_CARRIER"},
                {"UNEP_RISK", "UNEP — porteur de risque", "RISK_CARRIER"},
                {"DELEG_MGR", "Gestionnaire délégué", "DELEGATED_MANAGER"},
        };
        List<Insurer> insurers = new ArrayList<>();
        for (String[] row : rows) {
            Insurer insurer = new Insurer();
            insurer.code = row[0];
            insurer.name = row[1];
            insurer.role = InsurerRole.valueOf(row[2]);
            stamp(insurer);
            insurer.persist();
            insurers.add(insurer);
        }
        return insurers;
    }

    /**
     * Creates the product catalogue of both entities.
     *
     * @param world    the shared demo world
     * @param ranges   the product ranges
     * @param insurers the insurers
     */
    private void products(DemoWorld world, Map<String, ProductRange> ranges, List<Insurer> insurers) {
        String[][] rows = {
                {DemoCodes.P_LINEAR, "Santé Démo (linéaire)", "SANTE"},
                {DemoCodes.P_UPFRONT, "Précompte Démo", "SANTE"},
                {DemoCodes.P_PREVOYANCE, "Prévoyance Pro+", "PREVOYANCE"},
                {DemoCodes.P_DECES, "Décès Plus", "PREVOYANCE"},
                {DemoCodes.P_COLLECTIVE, "Santé Collective", "COLLECTIVE"},
                {DemoCodes.P_PER, "Plan Épargne Retraite", "EPARGNE"},
                {DemoCodes.P_EMPRUNTEUR, "Assurance Emprunteur", "EMPRUNTEUR"},
                {DemoCodes.P_FORFAIT, "Forfait de service", "SANTE"},
                {DemoCodes.P_EXTERNE, "Courtage externe", "COLLECTIVE"},
        };
        for (LegalEntity entity : world.entities.values()) {
            Insurer insurer = entity.code.equals(DemoCodes.ALPTIS) ? insurers.get(0) : insurers.get(1);
            for (String[] row : rows) {
                Product product = new Product();
                product.entity = entity;
                product.code = row[0];
                product.label = row[1];
                product.range = ranges.get(row[2]);
                product.insurer = insurer;
                product.managementMode = ManagementMode.DIRECT;
                product.referenceLagMonths = (short) 0;
                product.commissionable = Boolean.TRUE;
                product.marketedFrom = LocalDate.of(2020, 1, 1);
                stamp(product);
                product.persist();
                world.put(product);
            }
        }
    }

    /**
     * Creates the distributor roster: the mock-up cabinet plus twenty-five others covering every
     * distributor type, both ORIAS statuses and (through their entity links and production codes)
     * every partner status.
     *
     * @param world the shared demo world
     */
    private void distributors(DemoWorld world) {
        Distributor cabinet = distributor(DemoCodes.CABINET, "Cabinet Martin", "SARL",
                DistributorType.INDEPENDENT, OriasStatus.REGISTERED);
        world.put(cabinet);
        link(cabinet, world.entity(DemoCodes.ALPTIS), PartnerStatus.ACTIVE);

        String[] names = {
                "Cabinet Dubois", "Assurances Petit", "Courtage Moreau", "Groupe Laurent",
                "Conseil Simon", "Cabinet Michel", "Patrimoine Garcia", "Agence Roux",
                "Cabinet Fournier", "Prévoyance Girard", "Courtage Bonnet", "Cabinet André",
                "Assurances Mercier", "Groupe Blanc", "Conseil Guerin", "Cabinet Muller",
                "Patrimoine Faure", "Agence Rousseau", "Cabinet Vincent", "Courtage Chevalier",
                "Prévoyance Robin", "Cabinet Masson", "Assurances Henry", "Groupe Legrand",
                "Conseil Garnier",
        };
        DistributorType[] types = DistributorType.values();
        PartnerStatus[] statuses = PartnerStatus.values();
        for (int i = 0; i < names.length; i++) {
            String partner = String.format("P%05d", 20001 + i);
            // Two distributors are struck off ORIAS (one of them therefore unpayable despite a balance).
            OriasStatus orias = (i == 4 || i == 17) ? OriasStatus.STRUCK_OFF : OriasStatus.REGISTERED;
            Distributor distributor = distributor(partner, names[i], legalForm(i), types[i % types.length], orias);
            world.put(distributor);
            // The first four distributors are active (they head groups, are enrolled and carry scenes);
            // the rest cycle through all four partner statuses so the whole enumeration is represented.
            PartnerStatus status = i < 4 ? PartnerStatus.ACTIVE : statuses[i % statuses.length];
            link(distributor, world.entity(DemoCodes.ALPTIS), status);
            // The first three distributors work with both entities.
            if (i < 3) {
                link(distributor, world.entity(DemoCodes.UNEP), PartnerStatus.ACTIVE);
            }
            // The rest of the UNEP roster: every fourth distributor is UNEP-only material too.
            if (i >= 3 && i % 2 == 0) {
                link(distributor, world.entity(DemoCodes.UNEP), status);
            }
        }
    }

    /**
     * Builds one distributor with a plausible identity.
     *
     * @param partner the partner code
     * @param name    the legal name
     * @param form    the legal form
     * @param type    the distributor type
     * @param orias   the ORIAS status
     * @return the persisted distributor
     */
    private Distributor distributor(String partner, String name, String form, DistributorType type,
                                    OriasStatus orias) {
        Distributor distributor = new Distributor();
        distributor.partnerCode = partner;
        distributor.legalName = name;
        distributor.legalForm = form;
        distributor.siren = "4" + partner.substring(1) + "321".substring(0, 3);
        distributor.addressLine1 = "12 rue du Commerce";
        distributor.postalCode = "69003";
        distributor.city = "Lyon";
        distributor.oriasNumber = "0" + partner.substring(1) + "77";
        distributor.oriasStatus = orias;
        distributor.distributorType = type;
        distributor.commissionable = Boolean.TRUE;
        distributor.advanceExcluded = Boolean.FALSE;
        distributor.statementGrouping = StatementGrouping.NONE;
        stamp(distributor);
        distributor.persist();
        return distributor;
    }

    /**
     * Returns a plausible legal form for the given index.
     *
     * @param i the roster index
     * @return a legal form label
     */
    private static String legalForm(int i) {
        String[] forms = {"SARL", "SAS", "EURL", "SA"};
        return forms[i % forms.length];
    }

    /**
     * Links a distributor to an entity with a partner status in force since {@link #FROM}.
     *
     * @param distributor the distributor
     * @param entity      the entity
     * @param status      the partner status within that entity
     */
    private void link(Distributor distributor, LegalEntity entity, PartnerStatus status) {
        DistributorEntity link = new DistributorEntity();
        link.distributor = distributor;
        link.entity = entity;
        link.status = status;
        link.validFrom = FROM;
        stamp(link);
        link.persist();
    }

    /**
     * Creates the two distributor groups and their three-level hierarchy (group → head distributor →
     * production code), attaching a handful of members to each group.
     *
     * @param world the shared demo world
     */
    private void groups(DemoWorld world) {
        DistributorGroup north = group("GRP-NORD", "Groupement Nord", world.distributor("P20001"));
        DistributorGroup south = group("GRP-SUD", "Groupement Sud", world.distributor("P20004"));
        attach(world.distributor("P20001"), north);
        attach(world.distributor("P20002"), north);
        attach(world.distributor("P20003"), north);
        attach(world.distributor("P20004"), south);
        attach(world.distributor("P20005"), south);
    }

    /**
     * Creates one distributor group headed by the given distributor.
     *
     * @param code label code
     * @param label the group label
     * @param head  the head distributor
     * @return the persisted group
     */
    private DistributorGroup group(String code, String label, Distributor head) {
        DistributorGroup group = new DistributorGroup();
        group.code = code;
        group.label = label;
        group.headDistributor = head;
        stamp(group);
        group.persist();
        return group;
    }

    /**
     * Attaches a distributor to a group.
     *
     * @param distributor the distributor
     * @param group       the group it belongs to
     */
    private void attach(Distributor distributor, DistributorGroup group) {
        distributor.group = group;
        distributor.persist();
    }

    /**
     * Creates the production codes: one per distributor-entity link, plus extra codes for the first
     * few distributors, with partner statuses covering the whole enumeration.
     *
     * @param world the shared demo world
     */
    private void productionCodes(DemoWorld world) {
        PartnerStatus[] statuses = PartnerStatus.values();
        int i = 0;
        for (Distributor distributor : world.distributors.values()) {
            for (DistributorEntity link : DistributorEntity.<DistributorEntity>list("distributor", distributor)) {
                productionCode(world, link.entity, distributor, "PC-" + distributor.partnerCode, link.status);
                i++;
            }
            // The first five distributors own a second production code with a cycling status.
            if (i <= 10 && i % 2 == 0) {
                productionCode(world, world.entity(DemoCodes.ALPTIS), distributor,
                        "PC-" + distributor.partnerCode + "-2", statuses[i % statuses.length]);
            }
        }
    }

    /**
     * Creates one production code and indexes it.
     *
     * @param world       the shared demo world
     * @param entity      the entity
     * @param distributor the owning distributor
     * @param code        the production-code value
     * @param status      the partner status
     * @return the persisted production code
     */
    private ProductionCode productionCode(DemoWorld world, LegalEntity entity, Distributor distributor,
                                          String code, PartnerStatus status) {
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.distributor = distributor;
        pc.code = code;
        pc.label = distributor.legalName;
        pc.status = status;
        stamp(pc);
        pc.persist();
        return world.put(pc);
    }

    /**
     * Creates bank details for most production codes. Two production codes are deliberately left
     * without bank details so the payment block can show the no-bank-details exclusion.
     *
     * @param world the shared demo world
     */
    private void bankAccounts(DemoWorld world) {
        int i = 0;
        for (ProductionCode pc : world.productionCodes.values()) {
            i++;
            // Leave two production codes without bank details (every arbitrary 7th and 13th).
            if (i == 7 || i == 13) {
                continue;
            }
            BankAccount bank = new BankAccount();
            bank.entity = pc.entity;
            bank.holderType = PayeeType.PRODUCTION_CODE;
            bank.holderId = pc.getId();
            bank.iban = "FR76300010079412345678901" + String.format("%02d", i % 100);
            bank.bic = "AGRIFRPP";
            bank.holderName = pc.label;
            bank.validFrom = FROM;
            stamp(bank);
            bank.persist();
        }
    }

    /**
     * Creates the demonstration plan and its ten nine-family rules for each entity, on demo-only
     * products kept off the Guide barème so the engine never has to arbitrate two base rules on the
     * same grain. UNEP's linear rule carries retired, active and draft versions, the draft standing for
     * a version awaiting four-eyes approval; Alptis carries a single active linear rule.
     *
     * @param world the shared demo world
     */
    private void demonstrationRules(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            CommissionPlan plan = new CommissionPlan();
            plan.entity = entity;
            plan.code = DemoCodes.DEMO_PLAN;
            plan.label = "Barème de démonstration 2026";
            plan.direction = Direction.OUTBOUND;
            plan.vintage = (short) 2026;
            stamp(plan);
            plan.persist();
            world.put(plan);

            if (entity.code.equals(DemoCodes.UNEP)) {
                linearRuleWithVersions(plan);
            } else {
                simpleRule(plan, "LINEAR", RuleFamily.LINEAR, LineNature.LINEAR, DemoCodes.P_LINEAR,
                        List.of("PREMIUM_COLLECTED"), "{\"allowedRates\":[0.10,0.15]}", null, "BASE",
                        VersionStatus.ACTIVE);
            }
            simpleRule(plan, "UPFRONT", RuleFamily.UPFRONT, LineNature.ACQUISITION,
                    DemoCodes.P_UPFRONT, List.of("PREMIUM_COLLECTED", "PREMIUM_CANCELLED"),
                    "{\"allowedRates\":[{\"acquisition\":0.2,\"servicing\":0.1}],\"firstYearDays\":365}",
                    upfrontClawback(), "BASE", VersionStatus.ACTIVE);
            simpleRule(plan, "FIXED", RuleFamily.FIXED, LineNature.FEE, DemoCodes.P_FORFAIT,
                    List.of("PREMIUM_COLLECTED"), "{\"amount\":300}", null, "BASE", VersionStatus.ACTIVE);
            simpleRule(plan, "EXTERNAL", RuleFamily.EXTERNAL, LineNature.EXTERNAL, DemoCodes.P_EXTERNE,
                    List.of("PREMIUM_COLLECTED"), "{}", null, "BASE", VersionStatus.ACTIVE);
            simpleRule(plan, "SHARE", RuleFamily.SHARE, LineNature.RETROCESSION, DemoCodes.P_COLLECTIVE,
                    List.of("INSURER_COMMISSION"), "{\"sourceNature\":\"INSURER\",\"rate\":0.6}", null,
                    "BASE", VersionStatus.ACTIVE);
            simpleRule(plan, "OUTSTANDING", RuleFamily.OUTSTANDING, LineNature.OUTSTANDING, DemoCodes.P_PER,
                    List.of("OUTSTANDING_BALANCE"), "{\"annualRate\":0.003}", null, "BASE", VersionStatus.ACTIVE);
            simpleRule(plan, "SAVINGS", RuleFamily.ADVANCE, LineNature.ADVANCE, DemoCodes.P_PER,
                    List.of("SCHEDULED_PAYMENT"),
                    "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.08,"
                            + "\"offsetting\":false,\"annualReadjustment\":false}",
                    null, "SAVINGS", VersionStatus.ACTIVE);
            tieredRule(plan, "RCD", LineNature.RCD, DemoCodes.P_COLLECTIVE,
                    "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}", "RCD",
                    ScaleMode.MARGINAL, new double[][]{{20000, 45000, 0.03}, {45000, 75000, 0.04},
                            {75000, 100000, 0.05}});
            tieredRule(plan, "COMPLEMENT", LineNature.COMPLEMENT, DemoCodes.P_PER,
                    "{\"counterCode\":\"PER\",\"calculationFrequency\":\"ANNUAL\"}", "COMPLEMENT",
                    ScaleMode.WHOLE, new double[][]{{0, 10000, 0.10}, {10000, 20000, 0.15}, {20000, 50000, 0.20}});
            simpleRule(plan, "PORTFOLIO", RuleFamily.PORTFOLIO, LineNature.RCP, DemoCodes.P_COLLECTIVE,
                    List.of(), "{\"rate\":0.04}", null, "RCP", VersionStatus.ACTIVE);
        }
    }

    /**
     * Creates a linear rule carrying three versions — retired, active and draft — the draft standing
     * for a version awaiting four-eyes approval.
     *
     * @param plan the plan
     */
    private void linearRuleWithVersions(CommissionPlan plan) {
        Rule rule = rule(plan, "LINEAR", RuleFamily.LINEAR);
        version(rule, 1, VersionStatus.RETIRED, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                scope(plan.entity.code, DemoCodes.P_LINEAR, "PREMIUM_COLLECTED"),
                List.of("PREMIUM_COLLECTED"), LineNature.LINEAR, "{\"allowedRates\":[0.08]}", null, "BASE", true);
        version(rule, 2, VersionStatus.ACTIVE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                scope(plan.entity.code, DemoCodes.P_LINEAR, "PREMIUM_COLLECTED"),
                List.of("PREMIUM_COLLECTED"), LineNature.LINEAR, "{\"allowedRates\":[0.10,0.15]}", null, "BASE", true);
        version(rule, 3, VersionStatus.DRAFT, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31),
                scope(plan.entity.code, DemoCodes.P_LINEAR, "PREMIUM_COLLECTED"),
                List.of("PREMIUM_COLLECTED"), LineNature.LINEAR, "{\"allowedRates\":[0.12]}", null, "BASE", false);
    }

    /**
     * Creates a rule carrying a single version.
     *
     * @param plan           the plan
     * @param suffix         the rule-code suffix
     * @param family         the family
     * @param nature         the output line nature
     * @param product        the targeted product code
     * @param triggers       the trigger event types
     * @param parameters     the parameters JSON
     * @param clawback       the clawback policy JSON, or null
     * @param exclusionGroup the exclusion group
     * @param status         the version status
     */
    private void simpleRule(CommissionPlan plan, String suffix, RuleFamily family, LineNature nature,
                            String product, List<String> triggers, String parameters, String clawback,
                            String exclusionGroup, VersionStatus status) {
        Rule rule = rule(plan, suffix, family);
        String eventType = triggers.isEmpty() ? null : triggers.get(0);
        version(rule, 1, status, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                scope(plan.entity.code, product, eventType), triggers, nature, parameters, clawback,
                exclusionGroup, true);
    }

    /**
     * Creates a tiered rule and attaches a scale with the given tiers.
     *
     * @param plan           the plan
     * @param suffix         the rule-code suffix
     * @param nature         the output line nature (RCD or COMPLEMENT)
     * @param product        the targeted product code
     * @param parameters     the parameters JSON
     * @param exclusionGroup the exclusion group
     * @param mode           the scale mode
     * @param tiers          the tiers as {lowerBound, upperBound, rate}
     */
    private void tieredRule(CommissionPlan plan, String suffix, LineNature nature, String product,
                            String parameters, String exclusionGroup, ScaleMode mode, double[][] tiers) {
        Rule rule = rule(plan, suffix, RuleFamily.TIERED);
        RuleVersion v = version(rule, 1, VersionStatus.ACTIVE, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), scope(plan.entity.code, product, null), List.of(), nature,
                parameters, null, exclusionGroup, true);
        Scale scale = new Scale();
        scale.ruleVersion = v;
        scale.mode = mode;
        scale.measure = ScaleMeasure.AMOUNT;
        scale.output = ScaleOutput.RATE;
        stamp(scale);
        scale.persist();
        for (double[] t : tiers) {
            ScaleTier tier = new ScaleTier();
            tier.scale = scale;
            tier.lowerBound = BigDecimal.valueOf(t[0]);
            tier.upperBound = BigDecimal.valueOf(t[1]);
            tier.rate = BigDecimal.valueOf(t[2]);
            tier.fromFirstUnit = mode == ScaleMode.WHOLE;
            stamp(tier);
            tier.persist();
        }
    }

    /**
     * Creates a rule shell.
     *
     * @param plan   the plan
     * @param suffix the rule-code suffix
     * @param family the family
     * @return the persisted rule
     */
    private Rule rule(CommissionPlan plan, String suffix, RuleFamily family) {
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = DemoCodes.DEMO_PLAN + "_" + suffix;
        rule.label = label(family);
        rule.family = family;
        stamp(rule);
        rule.persist();
        return rule;
    }

    /**
     * Returns a French label for a family.
     *
     * @param family the family
     * @return a human label
     */
    private static String label(RuleFamily family) {
        return switch (family) {
            case LINEAR -> "Commission linéaire";
            case UPFRONT -> "Précompte acquisition et gestion";
            case ADVANCE -> "Escompte épargne retraite";
            case TIERED -> "Barème à tranches";
            case PORTFOLIO -> "Rémunération sur portefeuille";
            case SHARE -> "Rétrocession de commission";
            case OUTSTANDING -> "Commission sur encours";
            case FIXED -> "Forfait de service";
            case EXTERNAL -> "Commission externe";
        };
    }

    /**
     * Creates and persists a rule version.
     *
     * @param rule           the owning rule
     * @param no             the version number
     * @param status         the version status
     * @param from           the validity start
     * @param to             the validity end
     * @param scope          the scope JSON
     * @param triggers       the trigger event types
     * @param nature         the output nature
     * @param parameters     the parameters JSON
     * @param clawback       the clawback policy JSON, or null
     * @param exclusionGroup the exclusion group
     * @param approved       whether the version carries an approval stamp
     * @return the persisted version
     */
    private RuleVersion version(Rule rule, int no, VersionStatus status, LocalDate from, LocalDate to,
                                String scope, List<String> triggers, LineNature nature, String parameters,
                                String clawback, String exclusionGroup, boolean approved) {
        RuleVersion v = new RuleVersion();
        v.rule = rule;
        v.versionNo = no;
        v.status = status;
        v.validFrom = from;
        v.validTo = to;
        v.versionAnchor = VersionAnchor.EVENT_DATE;
        v.scope = scope;
        v.triggerEventTypes = new ArrayList<>(triggers);
        v.outputNature = nature;
        v.parameters = parameters;
        v.clawbackPolicy = clawback;
        v.priority = 10;
        v.exclusionGroup = exclusionGroup;
        if (approved) {
            v.approvedBy = "validator-demo";
            v.approvedAt = clock.now();
        }
        stamp(v);
        v.persist();
        return v;
    }

    /**
     * Builds a scope JSON string.
     *
     * @param entityCode the entity code
     * @param product    the product code
     * @param eventType  the event type, or null to omit
     * @return the scope JSON
     */
    private static String scope(String entityCode, String product, String eventType) {
        String base = "{\"entity\":[\"" + entityCode + "\"],\"product\":[\"" + product + "\"]";
        return eventType == null ? base + "}" : base + ",\"eventType\":[\"" + eventType + "\"]}";
    }

    /**
     * Returns the shared upfront clawback policy (100% the first year, 50% the second).
     *
     * @return the clawback policy JSON
     */
    private static String upfrontClawback() {
        return "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],"
                + "\"triggers\":[\"TERMINATION\",\"WITHDRAWAL\"],"
                + "\"steps\":[{\"maxDays\":365,\"rate\":1.0},{\"maxDays\":730,\"rate\":0.5}]}";
    }

    /**
     * Creates the agreements and enrollments: a signed RCD amendment and a signed RCP amendment with
     * active enrollments (so the development and portfolio runs fire), plus a partnership agreement and
     * an unsigned amendment whose enrollment stays pending signature.
     *
     * @param world the shared demo world
     */
    private void agreementsAndEnrollments(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            Distributor enrolled = world.distributor("P20001");
            Agreement rcd = agreement(entity, AgreementType.RCD_AMENDMENT, enrolled, "ACC-RCD-" + entity.code, true);
            enroll(ruleOf(world, entity, "RCD"), enrolled, rcd, EnrollmentStatus.ACTIVE);
            Agreement rcp = agreement(entity, AgreementType.RCP_AMENDMENT, enrolled, "ACC-RCP-" + entity.code, true);
            enroll(ruleOf(world, entity, "PORTFOLIO"), enrolled, rcp, EnrollmentStatus.ACTIVE);
            agreement(entity, AgreementType.PARTNERSHIP, enrolled, "ACC-PART-" + entity.code, true);
            Agreement pending = agreement(entity, AgreementType.RCD_AMENDMENT, world.distributor("P20002"),
                    "ACC-PEND-" + entity.code, false);
            enroll(ruleOf(world, entity, "RCD"), world.distributor("P20002"), pending,
                    EnrollmentStatus.PENDING_SIGNATURE);
        }
    }

    /**
     * Returns a rule of the demo plan by family suffix.
     *
     * @param world  the shared demo world
     * @param entity the entity
     * @param suffix the rule-code suffix
     * @return the matching rule
     */
    private static Rule ruleOf(DemoWorld world, LegalEntity entity, String suffix) {
        return Rule.<Rule>find("plan = ?1 and code = ?2", world.plan(entity.code, DemoCodes.DEMO_PLAN),
                DemoCodes.DEMO_PLAN + "_" + suffix).firstResult();
    }

    /**
     * Creates one agreement.
     *
     * @param entity      the entity
     * @param type        the agreement type
     * @param distributor the distributor party
     * @param reference   the agreement reference
     * @param signed      whether the agreement is signed
     * @return the persisted agreement
     */
    private Agreement agreement(LegalEntity entity, AgreementType type, Distributor distributor,
                                String reference, boolean signed) {
        Agreement agreement = new Agreement();
        agreement.entity = entity;
        agreement.agreementType = type;
        agreement.distributor = distributor;
        agreement.reference = reference;
        agreement.validFrom = LocalDate.of(2026, 1, 1);
        agreement.validTo = LocalDate.of(2026, 12, 31);
        agreement.signatureStatus = signed ? SignatureStatus.SIGNED : SignatureStatus.NOT_SIGNED;
        if (signed) {
            agreement.signedAt = clock.now();
        }
        stamp(agreement);
        agreement.persist();
        return agreement;
    }

    /**
     * Creates one enrollment.
     *
     * @param rule        the enrolled rule
     * @param distributor the enrolled distributor
     * @param agreement   the backing agreement
     * @param status      the enrollment status
     */
    private void enroll(Rule rule, Distributor distributor, Agreement agreement, EnrollmentStatus status) {
        Enrollment enrollment = new Enrollment();
        enrollment.rule = rule;
        enrollment.distributor = distributor;
        enrollment.agreement = agreement;
        enrollment.validFrom = LocalDate.of(2026, 1, 1);
        enrollment.status = status;
        stamp(enrollment);
        enrollment.persist();
    }

    /**
     * Creates three discount (escompte) conventions on Alptis in different states — a valid one near
     * its contract cap, a suspended one and a closed one — the valid one covering the upfront product
     * so the engine can open an advance against it.
     *
     * @param world the shared demo world
     */
    private void advanceAgreements(DemoWorld world) {
        LegalEntity entity = world.entity(DemoCodes.ALPTIS);
        // The valid convention sits on P20002 (which carries the upfront scene), keeping the mock-up
        // cabinet's September bordereau a clean 110,00 €.
        AdvanceAgreement valid = advanceAgreement(entity, world.distributor("P20002"),
                AdvanceAgreementStatus.VALID, 2);
        AdvanceAgreementProduct link = new AdvanceAgreementProduct();
        link.advanceAgreement = valid;
        link.product = world.product(DemoCodes.ALPTIS, DemoCodes.P_UPFRONT);
        stamp(link);
        link.persist();
        advanceAgreement(entity, world.distributor("P20003"), AdvanceAgreementStatus.SUSPENDED, 50);
        advanceAgreement(entity, world.distributor("P20005"), AdvanceAgreementStatus.CLOSED, 50);
    }

    /**
     * Creates one discount convention.
     *
     * @param entity       the entity
     * @param distributor  the distributor party
     * @param status       the convention status
     * @param maxContracts the contract cap
     * @return the persisted convention
     */
    private AdvanceAgreement advanceAgreement(LegalEntity entity, Distributor distributor,
                                              AdvanceAgreementStatus status, int maxContracts) {
        AdvanceAgreement agreement = new AdvanceAgreement();
        agreement.entity = entity;
        agreement.distributor = distributor;
        agreement.validFrom = LocalDate.of(2026, 1, 1);
        agreement.validTo = LocalDate.of(2026, 12, 31);
        agreement.anticipationDays = 0;
        agreement.maxContracts = maxContracts;
        agreement.unpaidRankThreshold = (short) 2;
        agreement.status = status;
        stamp(agreement);
        agreement.persist();
        return agreement;
    }

    /**
     * Creates external codes mapping legacy identifiers onto a few distributors and products.
     *
     * @param world the shared demo world
     */
    private void externalCodes(DemoWorld world) {
        int i = 0;
        for (Distributor distributor : List.of(world.distributor(DemoCodes.CABINET),
                world.distributor("P20001"), world.distributor("P20002"))) {
            externalCode("DISTRIBUTOR", distributor.getId(), "LEGACY", "EXT-D-" + (++i));
        }
        externalCode("PRODUCT", world.product(DemoCodes.ALPTIS, DemoCodes.P_LINEAR).getId(),
                "LEGACY", "EXT-P-SANTE");
    }

    /**
     * Creates one external-code mapping.
     *
     * @param objectType the mapped object type
     * @param objectId   the mapped object id
     * @param source     the source system
     * @param code       the external code
     */
    private void externalCode(String objectType, Long objectId, String source, String code) {
        ExternalCode external = new ExternalCode();
        external.objectType = objectType;
        external.objectId = objectId;
        external.sourceSystem = source;
        external.externalCode = code;
        stamp(external);
        external.persist();
    }

    /**
     * Creates a multi-beneficiary payee rule on Alptis, splitting a production code's commission
     * between the production code itself and its group.
     *
     * @param world the shared demo world
     */
    private void payeeRules(DemoWorld world) {
        ProductionCode pc = world.productionCode(DemoCodes.ALPTIS, "PC-P20001");
        DistributorGroup group = pc.distributor.group;
        PayeeRule payeeRule = new PayeeRule();
        payeeRule.entity = world.entity(DemoCodes.ALPTIS);
        payeeRule.productionCode = pc;
        payeeRule.distributor = pc.distributor;
        payeeRule.paymentFrequency = PaymentFrequency.MONTHLY;
        payeeRule.validFrom = LocalDate.of(2026, 1, 1);
        stamp(payeeRule);
        payeeRule.persist();
        payeeShare(payeeRule, PayeeType.PRODUCTION_CODE, pc.getId(), new BigDecimal("0.900000"));
        payeeShare(payeeRule, PayeeType.GROUP, group.getId(), new BigDecimal("0.100000"));
    }

    /**
     * Creates one payee-rule share.
     *
     * @param rule      the owning payee rule
     * @param payeeType the payee type
     * @param payeeId   the payee id
     * @param share     the share fraction
     */
    private void payeeShare(PayeeRule rule, PayeeType payeeType, Long payeeId, BigDecimal share) {
        PayeeRuleShare s = new PayeeRuleShare();
        s.payeeRule = rule;
        s.payeeType = payeeType;
        s.payeeId = payeeId;
        s.share = share;
        stamp(s);
        s.persist();
    }

    /**
     * Creates an events file mapping per entity so the mappings screen is populated.
     *
     * @param world the shared demo world
     */
    private void mappings(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            ImportMapping mapping = new ImportMapping();
            mapping.entity = entity;
            mapping.flowCode = "EVENTS";
            mapping.sourceSystem = "LEGACY";
            mapping.versionNo = 1;
            mapping.definition = "{\"columns\":{\"contract\":\"NUM_CONTRAT\",\"amount\":\"MONTANT\","
                    + "\"eventType\":\"TYPE\",\"eventDate\":\"DATE_EFFET\"}}";
            mapping.validFrom = LocalDate.of(2026, 1, 1);
            stamp(mapping);
            mapping.persist();
        }
    }

    /**
     * Creates quality indicators for the enrolled distributor so the indicators screen and the
     * portfolio conditions have data.
     *
     * @param world the shared demo world
     */
    private void qualityIndicators(DemoWorld world) {
        QualityIndicatorType[] types = {QualityIndicatorType.LOSS_RATIO,
                QualityIndicatorType.PORTFOLIO_LAPSE_RATE, QualityIndicatorType.UNPAID_RATE};
        BigDecimal[] values = {new BigDecimal("0.55"), new BigDecimal("0.04"), new BigDecimal("0.01")};
        for (LegalEntity entity : world.entities.values()) {
            for (int i = 0; i < types.length; i++) {
                QualityIndicator indicator = new QualityIndicator();
                indicator.entity = entity;
                indicator.distributor = world.distributor("P20001");
                indicator.indicatorType = types[i];
                indicator.periodFrom = LocalDate.of(2026, 1, 1);
                indicator.periodTo = LocalDate.of(2026, 12, 31);
                indicator.value = values[i];
                indicator.source = "demo";
                stamp(indicator);
                indicator.persist();
            }
        }
    }

    /**
     * Creates entity parameters for both entities so the administration screen shows configured values.
     *
     * @param world the shared demo world
     */
    private void entityParameters(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            entityParameter(entity, "statement.footer", "Service rémunération — " + entity.code);
            entityParameter(entity, "payment.min.amount", "1.00");
        }
    }

    /**
     * Creates one entity parameter.
     *
     * @param entity the entity
     * @param key    the parameter key
     * @param value  the parameter value
     */
    private void entityParameter(LegalEntity entity, String key, String value) {
        EntityParameter parameter = new EntityParameter();
        parameter.entity = entity;
        parameter.paramKey = key;
        parameter.paramValue = value;
        parameter.validFrom = LocalDate.of(2026, 1, 1);
        stamp(parameter);
        parameter.persist();
    }

    /**
     * Stamps the creation audit columns on a freshly built entity.
     *
     * @param entity the entity to stamp
     */
    private void stamp(fr.alptis.remuneration.domain.BaseEntity entity) {
        entity.setCreatedAt(clock.now());
        entity.setCreatedBy(AUTHOR);
    }
}
