package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Volume dataset generator (section 11, CR-72): plants a self-contained linear scene — one plan with
 * an active linear rule, and a requested number of beneficiaries each with a production code, a
 * contract, a coverage, a linear choice and a number of pending premium events — so the real engine
 * commissions every event. Sized to the CR-72 target (20 000 beneficiaries, 420 000 events) for a
 * full-volume performance run; the automated case runs at a reduced scale. A dev tool; it writes only
 * referential and event rows, not real commission lines.
 */
@ApplicationScoped
public class VolumeDataGenerator {

    /** The product code of the generated scene. */
    static final String PRODUCT = "VOLUME_PRODUCT";

    /** The linear rate applied to every generated premium. */
    private static final BigDecimal RATE = new BigDecimal("0.15");

    /** The premium basis of every generated event. */
    private static final BigDecimal BASIS = new BigDecimal("100.00");

    /** The entity manager, for periodic flush on large volumes. */
    private final EntityManager em;

    /**
     * Creates the generator.
     *
     * @param em the entity manager
     */
    @Inject
    public VolumeDataGenerator(EntityManager em) {
        this.em = em;
    }

    /**
     * The counts of a generated dataset.
     *
     * @param beneficiaries the number of beneficiaries (and coverages)
     * @param events        the number of premium events
     */
    public record VolumeCounts(int beneficiaries, int events) {
    }

    /**
     * Generates a linear scene for an entity and a month: a rule plus {@code beneficiaries} coverages,
     * each carrying {@code eventsPerBeneficiary} premium events.
     *
     * @param entityCode          the entity code
     * @param periodMonth         the month the premiums fall in
     * @param beneficiaries       the number of beneficiaries to generate
     * @param eventsPerBeneficiary the number of premium events per beneficiary
     * @return the generated counts
     */
    @Transactional
    public VolumeCounts generate(String entityCode, LocalDate periodMonth, int beneficiaries,
                                 int eventsPerBeneficiary) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        Product product = product(entity);
        rule(entity, periodMonth);
        int events = 0;
        for (int i = 0; i < beneficiaries; i++) {
            Coverage coverage = coverage(entity, product, periodMonth, i);
            for (int j = 0; j < eventsPerBeneficiary; j++) {
                event(entity, coverage, periodMonth, i, j);
                events++;
            }
            if (i % 500 == 0) {
                em.flush();
                em.clear();
            }
        }
        return new VolumeCounts(beneficiaries, events);
    }

    /**
     * Finds or creates the generated product.
     *
     * @param entity the entity
     * @return the product
     */
    private Product product(LegalEntity entity) {
        Product product = Product.find("entity = ?1 and code = ?2", entity, PRODUCT).firstResult();
        if (product != null) {
            return product;
        }
        Product created = new Product();
        created.entity = entity;
        created.code = PRODUCT;
        created.label = "Produit de volume";
        created.persist();
        return created;
    }

    /**
     * Finds or creates the active linear rule covering the generated product for the month.
     *
     * @param entity      the entity
     * @param periodMonth the month
     */
    private void rule(LegalEntity entity, LocalDate periodMonth) {
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", entity, "VOLUME_PLAN")
                .firstResult();
        if (plan != null) {
            return;
        }
        CommissionPlan created = new CommissionPlan();
        created.entity = entity;
        created.code = "VOLUME_PLAN";
        created.label = "Plan de volume";
        created.direction = Direction.OUTBOUND;
        created.vintage = (short) periodMonth.getYear();
        created.persist();
        Rule rule = new Rule();
        rule.plan = created;
        rule.code = "VOLUME_LIN";
        rule.label = "Linéaire de volume";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = VersionStatus.ACTIVE;
        version.scope = "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"" + PRODUCT
                + "\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        version.parameters = "{\"allowedRates\":[0.15]}";
        version.exclusionGroup = "BASE";
        version.priority = 10;
        version.outputNature = LineNature.LINEAR;
        version.validFrom = periodMonth.withDayOfMonth(1);
        version.validTo = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());
        version.persist();
    }

    /**
     * Creates one beneficiary's coverage with its production-code assignment and linear choice.
     *
     * @param entity      the entity
     * @param product     the product
     * @param periodMonth the month
     * @param index       the beneficiary index
     * @return the coverage
     */
    private Coverage coverage(LegalEntity entity, Product product, LocalDate periodMonth, int index) {
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "VOLUME";
        contract.contractNumber = "VOL-" + index;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "VOL-" + index + "-C1";
        coverage.effectDate = periodMonth.withDayOfMonth(1);
        coverage.persist();
        Distributor distributor = new Distributor();
        distributor.partnerCode = "VOL-P" + index;
        distributor.legalName = "Cabinet volume " + index;
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = "VOL-PC-" + index;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = periodMonth.withDayOfMonth(1);
        assignment.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = RATE;
        choice.validFrom = periodMonth.withDayOfMonth(1);
        choice.persist();
        return coverage;
    }

    /**
     * Creates one pending premium event on a coverage.
     *
     * @param entity      the entity
     * @param coverage    the coverage
     * @param periodMonth the month
     * @param beneficiary the beneficiary index
     * @param sequence    the event index within the beneficiary
     */
    private void event(LegalEntity entity, Coverage coverage, LocalDate periodMonth, int beneficiary,
                       int sequence) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = "VOLUME";
        event.sourceKey = "VOL-E-" + beneficiary + "-" + sequence;
        event.eventType = EventType.PREMIUM_COLLECTED;
        event.contract = coverage.contract;
        event.coverage = coverage;
        event.eventDate = periodMonth.withDayOfMonth(5);
        event.receivedDate = periodMonth.withDayOfMonth(5);
        event.basisAmount = BASIS;
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();
    }
}
