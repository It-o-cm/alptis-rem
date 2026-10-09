package fr.alptis.remuneration.api;

import fr.alptis.remuneration.accounting.CutoffService;
import fr.alptis.remuneration.cycle.PeriodService;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import fr.alptis.remuneration.engine.CalculationEngine;
import fr.alptis.remuneration.payment.PaymentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Demonstration dataset (step 15): plants the September 2026 scene of cabinet P13897 and replays it
 * from import to closing — calculation, business validation, payment and accounting — so the screens
 * show a real end-to-end month. The illustrative identifiers of the mock-up (bordereau
 * 2026-09-004512, lot 202609-L001) are rendered by the system's own numbering; the substantive figure
 * is the 110,00 € commission. Idempotent: it does nothing when the demo distributor already exists.
 */
@ApplicationScoped
public class DemoDataService {

    /** The demo entity. */
    static final String ENTITY = "ALPTIS_ASSURANCES";

    /** The demo distributor partner code (mock-up's cabinet). */
    static final String PARTNER = "P13897";

    /** The demo period month. */
    static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    private final CalculationEngine engine;
    private final PeriodService periods;
    private final PaymentService payments;
    private final CutoffService cutoff;
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param engine   the calculation engine
     * @param periods  the period cycle service
     * @param payments the payment service
     * @param cutoff   the cut-off service
     * @param clock    the application date/time provider
     */
    @Inject
    public DemoDataService(CalculationEngine engine, PeriodService periods, PaymentService payments,
                           CutoffService cutoff, DateTimeProvider clock) {
        this.engine = engine;
        this.periods = periods;
        this.payments = payments;
        this.cutoff = cutoff;
        this.clock = clock;
    }

    /**
     * Loads the demonstration scene and runs the full September parcours. Idempotent.
     */
    @Transactional
    public void load() {
        if (Distributor.count("partnerCode", PARTNER) > 0) {
            return;
        }
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
        seedScene(entity);
        runParcours();
    }

    /**
     * Plants the referential: a product, a linear 10 % rule, the distributor and its production code,
     * a contract, a coverage, a remuneration choice, a bank account and a premium event of 1 100 €.
     *
     * @param entity the demo entity
     */
    private void seedScene(LegalEntity entity) {
        Product product = new Product();
        product.entity = entity;
        product.code = "DEMO_SANTE";
        product.label = "Santé démonstration";
        product.persist();
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = "DEMO_PLAN";
        plan.label = "Plan de démonstration";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "DEMO_LIN";
        rule.label = "Linéaire 10 %";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = VersionStatus.ACTIVE;
        version.scope = "{\"entity\":[\"" + ENTITY + "\"],\"product\":[\"DEMO_SANTE\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        version.parameters = "{\"allowedRates\":[0.10]}";
        version.exclusionGroup = "BASE";
        version.priority = 10;
        version.outputNature = LineNature.LINEAR;
        version.validFrom = LocalDate.of(2026, 1, 1);
        version.validTo = LocalDate.of(2026, 12, 31);
        version.persist();

        Distributor distributor = new Distributor();
        distributor.partnerCode = PARTNER;
        distributor.legalName = "Cabinet Martin";
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = "PC-" + PARTNER;
        pc.distributor = distributor;
        pc.persist();
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "DEMO";
        contract.contractNumber = "AD-004512";
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "AD-004512-C1";
        coverage.effectDate = LocalDate.of(2026, 1, 1);
        coverage.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = LocalDate.of(2026, 1, 1);
        assignment.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = new BigDecimal("0.10");
        choice.validFrom = LocalDate.of(2026, 1, 1);
        choice.persist();
        BankAccount bank = new BankAccount();
        bank.entity = entity;
        bank.holderType = PayeeType.PRODUCTION_CODE;
        bank.holderId = pc.getId();
        bank.iban = "FR7630001007941234567890185";
        bank.bic = "AGRIFRPP";
        bank.holderName = "Cabinet Martin";
        bank.validFrom = LocalDate.of(2020, 1, 1);
        bank.persist();
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = "DEMO";
        event.sourceKey = "DEMO-ENC-004512";
        event.eventType = EventType.PREMIUM_COLLECTED;
        event.contract = contract;
        event.coverage = coverage;
        event.eventDate = LocalDate.of(2026, 9, 5);
        event.receivedDate = LocalDate.of(2026, 9, 5);
        event.basisAmount = new BigDecimal("1100.00");
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();

        Period period = new Period();
        period.entity = entity;
        period.periodMonth = MONTH;
        period.state = PeriodState.OPEN;
        period.setCreatedAt(clock.now());
        period.persist();
    }

    /**
     * Runs the September parcours from import to closing on the seeded scene.
     */
    private void runParcours() {
        Set<Role> roles = EnumSet.allOf(Role.class);
        transition(PeriodState.LOADED, roles);
        engine.runReal(ENTITY, MONTH);
        transition(PeriodState.CALCULATED, roles);
        transition(PeriodState.CONTROLLED, roles);
        transition(PeriodState.BUSINESS_VALIDATED, roles);
        // The PAYMENT_PREPARED transition proposes the payments (cycle lot and statements).
        transition(PeriodState.PAYMENT_PREPARED, roles);
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, MONTH).firstResult();
        PaymentBatch lot = PaymentBatch.<PaymentBatch>find("period = ?1 and batchType = ?2", period,
                BatchType.CYCLE).firstResult();
        payments.prepareOrderDetails(lot.getId());
        payments.validateFinance(lot.getId(), "demo");
        transition(PeriodState.FINANCE_VALIDATED, roles);
        for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch = ?1", lot)) {
            payments.recordBankResult(order.getId(), true, null);
        }
        transition(PeriodState.CLOSED, roles);
        cutoff.extract(ENTITY, MONTH);
    }

    /**
     * Drives the demo period to a state with the system roles.
     *
     * @param toState the target state
     * @param roles   the acting roles
     */
    private void transition(PeriodState toState, Set<Role> roles) {
        periods.transition(ENTITY, MONTH, toState, roles, null, "demo");
    }
}
