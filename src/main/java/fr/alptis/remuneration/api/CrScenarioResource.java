package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.StaffCategory;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dev-only loader of a reference recette case's input data for the end-to-end suite (TESTS-E2E.md
 * section 3, outside production). It seeds exactly the product, rule version, coverage, remuneration
 * choice, distributor, production code and events of a case — never the resulting commission lines —
 * so the e2e test can launch the real engine from the calculations screen and read the computed lines
 * and their explanation where a user would. The expected amounts stay in the spec (section 13) and in
 * the test. A single case is planted per call; the case switch is extended as cases are replayed.
 */
@Path("/api/test/cr-scenario")
@UnlessBuildProfile("prod")
public class CrScenarioResource {

    /**
     * Plants a recette case's data for an entity and month.
     *
     * @param crCode     the recette case code (e.g. CR-01)
     * @param entityCode the entity code
     * @param month      the period month (ISO date) the calculation will run
     * @return 204 once seeded, 400 for an unknown case
     */
    @POST
    @Transactional
    public Response seed(@QueryParam("cr") String crCode, @QueryParam("entity") String entityCode,
                         @QueryParam("month") String month) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        LocalDate periodMonth = LocalDate.parse(month);
        period(entity, periodMonth);
        switch (crCode) {
            case "CR-01" -> seedCr01(entity, periodMonth);
            case "CR-02" -> seedCr02(entity, periodMonth);
            case "CR-03" -> seedCr03(entity, periodMonth);
            case "CR-04" -> seedCr04(entity, periodMonth);
            case "CR-05" -> seedCr05(entity, periodMonth);
            case "CR-06" -> seedCr06(entity, periodMonth);
            case "CR-07" -> seedCr07(entity, periodMonth);
            case "CR-08" -> seedCr08(entity, periodMonth);
            case "CR-09" -> seedCr09(entity, periodMonth);
            case "CR-10" -> seedCr10(entity, periodMonth);
            case "CR-11" -> seedCr11(entity, periodMonth);
            case "CR-12" -> seedCr12(entity, periodMonth);
            default -> {
                return Response.status(Response.Status.BAD_REQUEST).entity("Cas inconnu : " + crCode).build();
            }
        }
        return Response.noContent().build();
    }

    /**
     * The premium perimeter of the reference product for an entity: the product and a collected premium.
     *
     * @param entity the entity
     * @return the scope JSON
     */
    private String premiumScope(LegalEntity entity) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"]}";
    }

    /** Parameters of the reference 30/10 upfront rule (first year 365 days). */
    private static final String UPFRONT_PARAMS =
            "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}";

    /**
     * CR-01 (linéaire) : Santé Select, choix linéaire 15 %, un encaissement de 100 € hors taxes ; le
     * moteur doit produire une seule ligne {@code LINEAR} de 15,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr01(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR01", product, periodMonth, "P-CR01", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CR01", "R-CR01-LIN", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        premium(entity, coverage, "CR01-E1", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CR-02 (précompté, année 1) : apport 30 %, suivi 10 %, prime annuelle de 1 000 € encaissée la
     * première année ; le moteur doit produire une ligne {@code ACQUISITION} de 300,00 € et une ligne
     * {@code SERVICING} de 100,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr02(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR02", "P-CR02");
        premium(entity, coverage, "CR02-E1", periodMonth.plusDays(4), "1000.00");
    }

    /**
     * CR-03 (précompté, année 2) : même garantie une année plus tard, prime de 1 000 € encaissée la
     * deuxième année ; le moteur ne doit produire qu'une ligne {@code SERVICING} de 100,00 €, sans apport.
     * La garantie prend effet un an avant le mois de calcul, de sorte que la prime tombe en 2e année.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr03(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = periodMonth.minusYears(1);
        Coverage coverage = upfrontCoverage(entity, effect, "CR03", "P-CR03");
        premium(entity, coverage, "CR03-E1", periodMonth.plusDays(4), "1000.00");
    }

    /**
     * CR-04 (reprise en 1re année) : six encaissements mensuels de 100 € (précompté 30/10) puis
     * résiliation au 180e jour ; acquis apport 180,00 € et suivi 60,00 €, reprise −180,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr04(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR04", "P-CR04");
        for (int i = 0; i < 6; i++) {
            premium(entity, coverage, "CR04-E" + i, periodMonth.plusMonths(i), "100.00");
        }
        event(entity, coverage, "CR04-TERM", EventType.TERMINATION, periodMonth.plusDays(180), null);
    }

    /**
     * CR-05 (reprise en 2e année) : prime de 1 000 € puis résiliation au 540e jour ; reprise −150,00 €
     * (50 % de l'apport de 300 €).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr05(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR05", "P-CR05");
        premium(entity, coverage, "CR05-E1", periodMonth.plusDays(4), "1000.00");
        event(entity, coverage, "CR05-TERM", EventType.TERMINATION, periodMonth.plusDays(540), null);
    }

    /**
     * CR-06 (pas de reprise au-delà de deux ans) : prime de 1 000 € puis résiliation au 731e jour ;
     * aucune reprise.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr06(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR06", "P-CR06");
        premium(entity, coverage, "CR06-E1", periodMonth.plusDays(4), "1000.00");
        event(entity, coverage, "CR06-TERM", EventType.TERMINATION, periodMonth.plusDays(731), null);
    }

    /**
     * CR-07 (sans suite) : deux encaissements de 100 € puis classement sans suite ; reprise de 100 % de
     * toutes les lignes acquises, soit −80,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr07(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR07", "P-CR07");
        premium(entity, coverage, "CR07-E1", periodMonth, "100.00");
        premium(entity, coverage, "CR07-E2", periodMonth.plusMonths(1), "100.00");
        event(entity, coverage, "CR07-NP", EventType.NOT_PURSUED, periodMonth.plusDays(90), null);
    }

    /**
     * CR-08 (décès) : prime de 1 000 € puis décès de l'adhérent au 5e mois ; aucune reprise (le décès
     * n'est pas un événement de rupture).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr08(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR08", "P-CR08");
        premium(entity, coverage, "CR08-E1", periodMonth.plusDays(4), "1000.00");
        event(entity, coverage, "CR08-DEATH", EventType.DEATH, periodMonth.plusDays(150), null);
    }

    /**
     * CR-09 (annulation d'encaissement) : l'encaissement de CR-01 est annulé ; une ligne `LINEAR` de
     * 15,00 € et sa ligne inverse de −15,00 €, même règle, même bénéficiaire.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr09(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR09", product, periodMonth, "P-CR09", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CR09", "R-CR09", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        BusinessEvent origin = premium(entity, coverage, "CR09-E1", periodMonth.plusDays(4), "100.00");
        BusinessEvent cancellation = event(entity, coverage, "CR09-CANCEL", EventType.PREMIUM_CANCELLED,
                periodMonth.plusDays(9), null);
        cancellation.reversesEvent = origin;
    }

    /**
     * CR-10 (changement de taux) : barème à 15 % jusqu'au 28 février, 12 % à partir du 1er mars (choix et
     * règle versionnés) ; l'encaissement de janvier reste à 15 % (15,00 €), celui de mars passe à 12 %
     * (12,00 €), aucune ligne recalculée.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr10(LegalEntity entity, LocalDate periodMonth) {
        LocalDate boundary = periodMonth.plusMonths(2);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR10", product, periodMonth, "P-CR10", null);
        linearChoice(coverage, "0.15", periodMonth, boundary.minusDays(1));
        linearChoice(coverage, "0.12", boundary, null);
        version(entity, "PLAN-CR10-V1", "R-CR10-V1", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, boundary.minusDays(1), null);
        version(entity, "PLAN-CR10-V2", "R-CR10-V2", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.12]}", boundary, null, null);
        premium(entity, coverage, "CR10-JAN", periodMonth.plusDays(14), "100.00");
        premium(entity, coverage, "CR10-MAR", boundary.plusDays(14), "100.00");
    }

    /**
     * CR-11 (taux hors grille) : garantie au taux linéaire de 12 % alors que la grille n'admet que 15 et
     * 20 % ; l'événement est mis en attente avec le motif « taux hors grille », aucune ligne produite.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr11(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR11", product, periodMonth, "P-CR11", null);
        linearChoice(coverage, "0.12", periodMonth, null);
        version(entity, "PLAN-CR11", "R-CR11", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15,0.20]}", periodMonth, null, null);
        premium(entity, coverage, "CR11-E1", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CR-12 (collèges) : santé collective à 7 % pour les cadres et 5 % pour les non-cadres, 1 000 €
     * encaissés sur chaque collège ; 70,00 € et 50,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr12(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage executive = coverage(entity, "CR12-EXE", product, periodMonth, "P-CR12-EXE",
                StaffCategory.EXECUTIVE);
        Coverage nonExecutive = coverage(entity, "CR12-NON", product, periodMonth, "P-CR12-NON",
                StaffCategory.NON_EXECUTIVE);
        linearChoice(executive, "0.07", periodMonth, null);
        linearChoice(nonExecutive, "0.05", periodMonth, null);
        version(entity, "PLAN-CR12-EXE", "R-CR12-EXE", RuleFamily.LINEAR, LineNature.LINEAR,
                collegeScope(entity, "EXECUTIVE"), "{\"allowedRates\":[0.07]}", periodMonth, null, null);
        version(entity, "PLAN-CR12-NON", "R-CR12-NON", RuleFamily.LINEAR, LineNature.LINEAR,
                collegeScope(entity, "NON_EXECUTIVE"), "{\"allowedRates\":[0.05]}", periodMonth, null, null);
        premium(entity, executive, "CR12-EXE-E", periodMonth.plusDays(4), "1000.00");
        premium(entity, nonExecutive, "CR12-NON-E", periodMonth.plusDays(4), "1000.00");
    }

    /**
     * The premium perimeter restricted to a college, for the collective-college case.
     *
     * @param entity  the entity
     * @param college the staff category code
     * @return the scope JSON
     */
    private String collegeScope(LegalEntity entity, String college) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"],\"college\":[\"" + college + "\"]}";
    }

    /**
     * Finds the entity's period for a month, or creates it in a calculable state.
     *
     * @param entity      the entity
     * @param periodMonth the month
     * @return the period
     */
    private Period period(LegalEntity entity, LocalDate periodMonth) {
        Period existing = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (existing != null) {
            return existing;
        }
        Period period = new Period();
        period.entity = entity;
        period.periodMonth = periodMonth;
        period.state = PeriodState.LOADED;
        period.persist();
        return period;
    }

    /**
     * Creates a product for the entity.
     *
     * @param entity the entity
     * @param code   the product code
     * @return the product
     */
    private Product product(LegalEntity entity, String code) {
        Product product = new Product();
        product.entity = entity;
        product.code = code;
        product.label = code;
        product.persist();
        return product;
    }

    /**
     * Creates a coverage on a new contract, with its main production-code assignment and a new
     * distributor.
     *
     * @param entity         the entity
     * @param contractNumber the contract number
     * @param product        the product
     * @param effectDate     the coverage effect date
     * @param partnerCode    the distributor partner code
     * @param college        the staff category (college), or null
     * @return the coverage
     */
    private Coverage coverage(LegalEntity entity, String contractNumber, Product product, LocalDate effectDate,
                              String partnerCode, StaffCategory college) {
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contractNumber + "-C1";
        coverage.effectDate = effectDate;
        coverage.staffCategory = college;
        coverage.persist();
        Distributor distributor = new Distributor();
        distributor.partnerCode = partnerCode;
        distributor.legalName = partnerCode;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = "PC-" + contractNumber;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = effectDate;
        assignment.persist();
        return coverage;
    }

    /**
     * Creates a linear remuneration choice for a coverage.
     *
     * @param coverage the coverage
     * @param rate     the linear rate, as a fraction
     * @param from     the start of validity
     * @param to       the end of validity, or null
     */
    private void linearChoice(Coverage coverage, String rate, LocalDate from, LocalDate to) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = new BigDecimal(rate);
        choice.validFrom = from;
        choice.validTo = to;
        choice.persist();
    }

    /**
     * Creates an upfront remuneration choice for a coverage.
     *
     * @param coverage    the coverage
     * @param acquisition the acquisition rate, as a fraction
     * @param servicing   the servicing rate, as a fraction
     * @param from        the start of validity
     */
    private void upfrontChoice(Coverage coverage, String acquisition, String servicing, LocalDate from) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal(acquisition);
        choice.servicingRate = new BigDecimal(servicing);
        choice.validFrom = from;
        choice.persist();
    }

    /**
     * Creates an active rule version for the entity, creating its plan and rule as needed.
     *
     * @param entity     the entity
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param family     the calculation family
     * @param nature     the output nature
     * @param scope      the scope JSON
     * @param parameters the parameters JSON
     * @param from       the start of validity
     * @param to         the end of validity, or null
     * @param clawback   the clawback policy JSON, or null when the rule has none
     */
    private void version(LegalEntity entity, String planCode, String ruleCode, RuleFamily family, LineNature nature,
                         String scope, String parameters, LocalDate from, LocalDate to, String clawback) {
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = planCode;
        plan.label = planCode;
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = ruleCode;
        rule.label = ruleCode;
        rule.family = family;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = VersionStatus.ACTIVE;
        version.scope = scope;
        version.parameters = parameters;
        version.exclusionGroup = "BASE";
        version.priority = 10;
        version.outputNature = nature;
        version.validFrom = from;
        version.validTo = to;
        version.clawbackPolicy = clawback;
        version.persist();
    }

    /** The reference upfront clawback policy: 100% the first year, 50% the second, on acquisition. */
    private static final String CLAWBACK =
            "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],\"triggers\":[\"TERMINATION\",\"WITHDRAWAL\"],"
            + "\"steps\":[{\"maxDays\":365,\"rate\":1.0},{\"maxDays\":730,\"rate\":0.5}]}";

    /**
     * Creates an upfront garantie with its 30/10 choice and an active upfront rule carrying the reference
     * clawback policy, and returns the coverage.
     *
     * @param entity         the entity
     * @param effect         the coverage effect date (and rule start)
     * @param contractNumber the contract number (also used to key the plan/rule)
     * @param partnerCode    the distributor partner code
     * @return the coverage
     */
    private Coverage upfrontCoverage(LegalEntity entity, LocalDate effect, String contractNumber, String partnerCode) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, contractNumber, product, effect, partnerCode, null);
        upfrontChoice(coverage, "0.30", "0.10", effect);
        version(entity, "PLAN-" + contractNumber, "R-" + contractNumber, RuleFamily.UPFRONT, LineNature.ACQUISITION,
                premiumScope(entity), UPFRONT_PARAMS, effect, null, CLAWBACK);
        return coverage;
    }

    /**
     * Injects a pending premium event on a coverage.
     *
     * @param entity   the entity
     * @param coverage the coverage
     * @param key      the source key
     * @param date     the event date
     * @param basis    the basis amount
     */
    private BusinessEvent premium(LegalEntity entity, Coverage coverage, String key, LocalDate date, String basis) {
        return event(entity, coverage, key, EventType.PREMIUM_COLLECTED, date, basis);
    }

    /**
     * Injects a pending event on a coverage.
     *
     * @param entity   the entity
     * @param coverage the coverage
     * @param key      the source key
     * @param type     the event type
     * @param date     the event date
     * @param basis    the basis amount, or null for a non-financial event (termination, death, …)
     */
    private BusinessEvent event(LegalEntity entity, Coverage coverage, String key, EventType type, LocalDate date,
                                String basis) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = "SYS";
        event.sourceKey = key;
        event.eventType = type;
        event.contract = coverage.contract;
        event.coverage = coverage;
        event.eventDate = date;
        event.receivedDate = date;
        event.basisAmount = basis == null ? null : new BigDecimal(basis);
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();
        return event;
    }
}
