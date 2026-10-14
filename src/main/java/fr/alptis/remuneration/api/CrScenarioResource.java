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
        Coverage coverage = coverage(entity, "CR01", product, periodMonth, "P-CR01");
        linearChoice(coverage, "0.15", periodMonth);
        version(entity, "PLAN-CR01", "R-CR01-LIN", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth);
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
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR02", product, periodMonth, "P-CR02");
        upfrontChoice(coverage, "0.30", "0.10", periodMonth);
        version(entity, "PLAN-CR02", "R-CR02-UPF", RuleFamily.UPFRONT, LineNature.ACQUISITION, premiumScope(entity),
                UPFRONT_PARAMS, periodMonth);
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
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR03", product, effect, "P-CR03");
        upfrontChoice(coverage, "0.30", "0.10", effect);
        version(entity, "PLAN-CR03", "R-CR03-UPF", RuleFamily.UPFRONT, LineNature.ACQUISITION, premiumScope(entity),
                UPFRONT_PARAMS, effect);
        premium(entity, coverage, "CR03-E1", periodMonth.plusDays(4), "1000.00");
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
     * @return the coverage
     */
    private Coverage coverage(LegalEntity entity, String contractNumber, Product product, LocalDate effectDate,
                              String partnerCode) {
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
     */
    private void linearChoice(Coverage coverage, String rate, LocalDate from) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = new BigDecimal(rate);
        choice.validFrom = from;
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
     */
    private void version(LegalEntity entity, String planCode, String ruleCode, RuleFamily family, LineNature nature,
                         String scope, String parameters, LocalDate from) {
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
        version.persist();
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
    private void premium(LegalEntity entity, Coverage coverage, String key, LocalDate date, String basis) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = "SYS";
        event.sourceKey = key;
        event.eventType = EventType.PREMIUM_COLLECTED;
        event.contract = coverage.contract;
        event.coverage = coverage;
        event.eventDate = date;
        event.receivedDate = date;
        event.basisAmount = new BigDecimal(basis);
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();
    }
}
