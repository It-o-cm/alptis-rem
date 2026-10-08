package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.RuleBasis;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.domain.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the initial base barème of the 2026 Guide for Alptis Assurances (section 6.12): one commission
 * plan {@code GUIDE_2026} holding, per product and segment, a linear rule and/or an upfront rule,
 * each as an active base version (exclusion group {@code BASE}, priority 10) carrying the grid of
 * allowed rates the garanties may bear (P9). Upfront clawback is 100% the first year and 50% the
 * second for all precomputed modes, except the borrower (section 6.5, out of this table).
 *
 * <p>The amounts are transcribed by hand from the specification and never recomputed. Seeding is
 * idempotent: it does nothing once the plan exists.</p>
 */
@ApplicationScoped
public class BaremeInitialService {

    /** The plan code of the 2026 Guide base barème. */
    public static final String PLAN_CODE = "GUIDE_2026";

    /** The source commission year. */
    private static final short VINTAGE = 2026;

    /** The first day of the barème's validity. */
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    /** The last day of the barème's validity. */
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    /** First commission year length used by the precomputed modes, in days. */
    private static final int FIRST_YEAR_DAYS = 365;

    /** The clawback policy shared by every precomputed base rule (100% then 50%). */
    private static final String CLAWBACK = "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],"
            + "\"triggers\":[\"TERMINATION\",\"WITHDRAWAL\"],"
            + "\"steps\":[{\"maxDays\":365,\"rate\":1.0},{\"maxDays\":730,\"rate\":0.5}]}";

    /** The date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public BaremeInitialService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Seeds the base barème for an entity, once. Does nothing if the entity is null or the plan
     * already exists.
     *
     * @param entity the entity to seed the barème for, possibly null
     * @return the number of rules created (zero if already seeded or the entity is null)
     */
    @Transactional
    public int seed(LegalEntity entity) {
        if (entity == null || CommissionPlan.count("entity = ?1 and code = ?2", entity, PLAN_CODE) > 0) {
            return 0;
        }
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = PLAN_CODE;
        plan.label = "Guide 2026 — barème de base";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = VINTAGE;
        plan.setCreatedAt(clock.now());
        plan.setCreatedBy("bareme-initial");
        plan.persist();

        int created = 0;
        for (ProductBareme product : BAREME) {
            if (product.linearRates().length > 0) {
                createRule(plan, product.code() + "_LINEAR", product.label() + " — linéaire",
                        RuleFamily.LINEAR, LineNature.LINEAR, product.code(), linearParameters(product.linearRates()),
                        null);
                created++;
            }
            if (product.upfrontCouples().length > 0) {
                createRule(plan, product.code() + "_UPFRONT", product.label() + " — précompté",
                        RuleFamily.UPFRONT, LineNature.ACQUISITION, product.code(),
                        upfrontParameters(product.upfrontCouples()), CLAWBACK);
                created++;
            }
        }
        return created;
    }

    /**
     * Creates a base rule and its single active version.
     *
     * @param plan        the plan
     * @param ruleCode    the rule code
     * @param ruleLabel   the rule label
     * @param family      the calculation family
     * @param nature      the produced line nature
     * @param productCode the product the perimeter targets
     * @param parameters  the family parameters (JSON)
     * @param clawback    the clawback policy (JSON), or null
     */
    private void createRule(CommissionPlan plan, String ruleCode, String ruleLabel, RuleFamily family,
                            LineNature nature, String productCode, String parameters, String clawback) {
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = ruleCode;
        rule.label = ruleLabel;
        rule.family = family;
        rule.setCreatedAt(clock.now());
        rule.setCreatedBy("bareme-initial");
        rule.persist();

        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.validFrom = FROM;
        version.validTo = TO;
        version.status = VersionStatus.ACTIVE;
        version.versionAnchor = VersionAnchor.EVENT_DATE;
        version.scope = "{\"entity\":\"" + plan.entity.code + "\",\"product\":[\"" + productCode + "\"]}";
        version.triggerEventTypes = family == RuleFamily.UPFRONT
                ? new ArrayList<>(List.of("PREMIUM_COLLECTED", "PREMIUM_CANCELLED"))
                : new ArrayList<>(List.of("PREMIUM_COLLECTED"));
        version.basis = RuleBasis.COLLECTED_PREMIUM;
        version.outputNature = nature;
        version.parameters = parameters;
        version.clawbackPolicy = clawback;
        version.priority = 10;
        version.exclusionGroup = "BASE";
        version.approvedBy = "bareme-initial";
        version.approvedAt = OffsetDateTime.parse("2025-12-01T00:00:00Z");
        version.setCreatedAt(clock.now());
        version.setCreatedBy("bareme-initial");
        version.persist();
    }

    /**
     * Builds the LINEAR parameters JSON from a grid of rates.
     *
     * @param rates the allowed linear rates
     * @return the parameters JSON
     */
    private static String linearParameters(double[] rates) {
        StringBuilder sb = new StringBuilder("{\"allowedRates\":[");
        for (int i = 0; i < rates.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(trim(rates[i]));
        }
        return sb.append("]}").toString();
    }

    /**
     * Builds the UPFRONT parameters JSON from a grid of acquisition/servicing couples.
     *
     * @param couples the allowed couples, each {@code {acquisition, servicing}}
     * @return the parameters JSON
     */
    private static String upfrontParameters(double[][] couples) {
        StringBuilder sb = new StringBuilder("{\"allowedRates\":[");
        for (int i = 0; i < couples.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"acquisition\":").append(trim(couples[i][0]))
                    .append(",\"servicing\":").append(trim(couples[i][1])).append('}');
        }
        return sb.append("],\"firstYearDays\":").append(FIRST_YEAR_DAYS).append('}').toString();
    }

    /**
     * Formats a rate without a trailing zero fraction that would look like a float artefact.
     *
     * @param rate the rate
     * @return the plain decimal string
     */
    private static String trim(double rate) {
        return new java.math.BigDecimal(Double.toString(rate)).stripTrailingZeros().toPlainString();
    }

    /**
     * Builds a linear grid from {@code 0} to {@code max} by {@code step}.
     *
     * @param max  the maximum rate
     * @param step the step
     * @return the grid of rates
     */
    private static double[] grid(double max, double step) {
        int count = (int) Math.round(max / step) + 1;
        double[] rates = new double[count];
        for (int i = 0; i < count; i++) {
            rates[i] = new java.math.BigDecimal(Double.toString(step))
                    .multiply(java.math.BigDecimal.valueOf(i)).doubleValue();
        }
        return rates;
    }

    /** The 2026 Guide base barème (section 6.12), transcribed by hand. */
    private static final List<ProductBareme> BAREME = List.of(
            new ProductBareme("SANTE_PRO_PLUS", "Santé Pro+", grid(0.20, 0.05),
                    new double[][]{{0.20, 0.10}, {0.25, 0.15}}),
            new ProductBareme("SANTE_SELECT", "Santé Select", grid(0.20, 0.01),
                    new double[][]{{0.30, 0.10}, {0.20, 0.10}}),
            new ProductBareme("SANTE_EQUILIBRE", "Santé Équilibre", new double[]{0.15, 0.18},
                    new double[][]{{0.20, 0.10}, {0.30, 0.10}}),
            new ProductBareme("SANTE_PROTECT", "Santé Protect", new double[]{0.15, 0.18},
                    new double[0][]),
            new ProductBareme("LUMINEIS_SIMULTANE", "Luminéis — socle ou packs simultanés", new double[]{0.15},
                    new double[][]{{0.20, 0.10}}),
            new ProductBareme("LUMINEIS_APRES", "Luminéis — packs souscrits après coup", new double[]{0.10},
                    new double[0][]),
            new ProductBareme("SANTE_FRONTALIERS_SUISSES", "Santé Frontaliers Suisses", new double[]{0.15, 0.20},
                    new double[][]{{0.20, 0.10}}),
            new ProductBareme("SOLUTION_PRO_INDEP", "Solution Professions Indépendantes", grid(0.20, 0.05),
                    new double[][]{{0.20, 0.10}, {0.30, 0.10}}),
            new ProductBareme("PREVOYANCE_PRO_PLUS", "Prévoyance Pro+", grid(0.20, 0.05),
                    new double[][]{{0.20, 0.10}, {0.30, 0.10}}),
            new ProductBareme("FRAIS_GENERAUX_PLUS", "Frais Généraux+", grid(0.20, 0.05),
                    new double[][]{{0.20, 0.10}, {0.30, 0.10}}),
            new ProductBareme("HOMME_CLE_LT500_STD", "Homme Clé < 500 K€ — standard ou +40 standard",
                    new double[]{0.18}, new double[][]{{0.40, 0.10}}),
            new ProductBareme("HOMME_CLE_LT500_REDUIT", "Homme Clé < 500 K€ — +40 tarif réduit",
                    new double[]{0.14}, new double[][]{{0.20, 0.10}}),
            new ProductBareme("HOMME_CLE_GE500_STD", "Homme Clé ≥ 500 K€ — standard",
                    new double[]{0.18}, new double[][]{{0.40, 0.10}}),
            new ProductBareme("HOMME_CLE_GE500_REDUIT", "Homme Clé ≥ 500 K€ — tarif réduit",
                    new double[]{0.14}, new double[][]{{0.20, 0.10}}),
            new ProductBareme("GARANTIE_DEPENDANCE", "Garantie Dépendance", new double[0],
                    new double[][]{{0.10, 0.10}, {0.55, 0.05}}),
            new ProductBareme("PREVOYANCE_FRONTALIERS", "Prévoyance des Frontaliers", new double[0],
                    new double[][]{{0.40, 0.10}}),
            new ProductBareme("DECES_PLUS", "Décès Plus", new double[]{0.18},
                    new double[][]{{0.40, 0.10}}),
            new ProductBareme("PACK_HOSPI", "Pack Hospi", new double[0],
                    new double[][]{{0.40, 0.10}}));

    /**
     * One product row of the 2026 Guide barème.
     *
     * @param code           the product code (also the rule-code prefix)
     * @param label          the product label
     * @param linearRates    the allowed linear rates (empty if the product has no linear mode)
     * @param upfrontCouples the allowed acquisition/servicing couples (empty if no precomputed mode)
     */
    private record ProductBareme(String code, String label, double[] linearRates, double[][] upfrontCouples) {
    }
}
