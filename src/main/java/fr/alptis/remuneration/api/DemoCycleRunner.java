package fr.alptis.remuneration.api;

import fr.alptis.remuneration.accounting.CutoffService;
import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.cycle.AdjustmentService;
import fr.alptis.remuneration.cycle.ControlService;
import fr.alptis.remuneration.cycle.PeriodService;
import fr.alptis.remuneration.documents.StatementService;
import fr.alptis.remuneration.domain.AdjustmentStatus;
import fr.alptis.remuneration.domain.AdjustmentType;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Claim;
import fr.alptis.remuneration.domain.ClaimLine;
import fr.alptis.remuneration.domain.ClaimStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.StatementStatus;
import fr.alptis.remuneration.engine.CalculationEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Drives the monthly cycle of the demonstration dataset through the real services: it emits each
 * month's business events, runs the calculation engine (premium path and the enrolment/annual closes),
 * runs the controls, drives the period state machine, settles the payments, publishes the bordereaux
 * and extracts the accounting. Alptis Assurances runs June to September closed (September keeping the
 * mock-up scene), October calculated with open anomalies and November open; UNEP runs September closed
 * and October stopped between business and Finance validation with a prepared, unvalidated lot.
 *
 * <p>Nothing computed is inserted: lines come from the engine, accounts and entries from the validation
 * transition, lots and bordereaux from payment, extracts from the cut-off.</p>
 */
@ApplicationScoped
public class DemoCycleRunner {

    /** The acting roles: every role, so the state-machine role guards always pass. */
    private static final Set<Role> ALL = EnumSet.allOf(Role.class);

    /** The author of the cycle's business actions. */
    private static final String USER = "demo-cycle";

    private final CalculationEngine engine;
    private final PeriodService periods;
    private final ControlService controls;
    private final AdjustmentService adjustments;
    private final AccountService accounts;
    private final fr.alptis.remuneration.payment.PaymentService payments;
    private final StatementService statements;
    private final CutoffService cutoff;
    private final DateTimeProvider clock;

    /** Running counter feeding unique event source keys. */
    private int seq;

    /**
     * Creates the runner.
     *
     * @param engine      the calculation engine
     * @param periods     the period cycle service
     * @param controls    the control service
     * @param adjustments the adjustment service
     * @param accounts    the distributor-account service
     * @param payments    the payment service
     * @param statements  the bordereau service
     * @param cutoff      the cut-off service
     * @param clock       the application date/time provider
     */
    @Inject
    public DemoCycleRunner(CalculationEngine engine, PeriodService periods, ControlService controls,
                           AdjustmentService adjustments, AccountService accounts,
                           fr.alptis.remuneration.payment.PaymentService payments, StatementService statements,
                           CutoffService cutoff, DateTimeProvider clock) {
        this.engine = engine;
        this.periods = periods;
        this.controls = controls;
        this.adjustments = adjustments;
        this.accounts = accounts;
        this.payments = payments;
        this.statements = statements;
        this.cutoff = cutoff;
        this.clock = clock;
    }

    /**
     * Runs the whole cycle for both entities.
     *
     * @param world the shared demo world
     */
    void run(DemoWorld world) {
        configureControlSeverities(world);
        runAlptis(world);
        runUnep(world);
    }

    /**
     * Sets the control-definition severities of the demo entities so the controls span the three
     * severities: a future-date anomaly blocks, a debtor balance is an information, the rest warn.
     *
     * @param world the shared demo world
     */
    private void configureControlSeverities(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            severity(entity, "DATA-FUTURE", ControlSeverity.BLOCKING);
            severity(entity, "ACCOUNT-DEBTOR", ControlSeverity.INFO);
            severity(entity, "DATA-PREMIUM", ControlSeverity.WARNING);
            severity(entity, "CALC-NO-RULE", ControlSeverity.WARNING);
            severity(entity, "CLAWBACK-EXCESS", ControlSeverity.WARNING);
        }
    }

    /**
     * Sets a control definition's severity for an entity.
     *
     * @param entity   the entity
     * @param code     the control code
     * @param severity the severity to apply
     */
    private void severity(LegalEntity entity, String code, ControlSeverity severity) {
        for (ControlDefinition control : ControlDefinition.<ControlDefinition>list("entity = ?1 and code = ?2",
                entity, code)) {
            control.severity = severity;
        }
    }

    /**
     * Runs the Alptis timeline: June–August closed, September closed with the full family showcase and
     * the mock-up scene, October calculated with open anomalies and adjustments, November open.
     *
     * @param world the shared demo world
     */
    private void runAlptis(DemoWorld world) {
        String e = DemoCodes.ALPTIS;
        ordinaryMonth(world, e, LocalDate.of(2026, 6, 5), true);
        ordinaryMonth(world, e, LocalDate.of(2026, 7, 5), false);
        ordinaryMonth(world, e, LocalDate.of(2026, 8, 5), false);
        showcaseMonth(world, e, LocalDate.of(2026, 9, 5), true);
        payments.offCycleLot(e, "P20007", LocalDate.of(2026, 9, 1));
        octoberAlptis(world);
        novemberAlptis(world);
        plantHeldEvent(e);
        plantClaim(e);
        plantSimulation(e);
    }

    /**
     * Runs the UNEP timeline: September closed with the full family showcase, October stopped between
     * business and Finance validation with a prepared, unvalidated payment lot.
     *
     * @param world the shared demo world
     */
    private void runUnep(DemoWorld world) {
        String e = DemoCodes.UNEP;
        showcaseMonth(world, e, LocalDate.of(2026, 9, 5), false);
        octoberUnep(world);
        plantHeldEvent(e);
        plantClaim(e);
        plantSimulation(e);
    }

    /**
     * Records one open claim per entity (REG-06), linked to a commission line of the entity, so the
     * claims screen shows a real claim with its contested line.
     *
     * @param entityCode the entity code
     */
    private void plantClaim(String entityCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        CommissionLine line = CommissionLine.<CommissionLine>find("entity = ?1 order by id", entity).firstResult();
        Claim claim = new Claim();
        claim.entity = entity;
        claim.distributor = Distributor.<Distributor>findAll().firstResult();
        claim.subject = "Taux appliqué inférieur au taux convenu";
        claim.status = ClaimStatus.OPEN;
        claim.receivedAt = clock.now();
        claim.setCreatedAt(clock.now());
        claim.setCreatedBy(USER);
        claim.persist();
        ClaimLine claimLine = new ClaimLine();
        claimLine.claim = claim;
        claimLine.commissionLine = line;
        claimLine.persist();
    }

    /**
     * Records one past simulation per entity (PAR-13), so the simulation screen shows a history. The
     * row is a representative outcome of replaying September under a project rate; the live simulation
     * writes the same history when run from the screen.
     *
     * @param entityCode the entity code
     */
    private void plantSimulation(String entityCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        fr.alptis.remuneration.domain.SimulationHistory history =
                new fr.alptis.remuneration.domain.SimulationHistory();
        history.entity = entity;
        history.period = period(entityCode, LocalDate.of(2026, 9, 1));
        history.ruleCode = "SANTE_SELECT_LINEAR";
        history.versionNo = 1;
        history.realTotal = new BigDecimal("1000.00");
        history.simulatedTotal = new BigDecimal("900.00");
        history.ecart = new BigDecimal("-100.00");
        history.linesReplayed = 12;
        history.linesModified = 4;
        history.held = 0;
        history.launchedBy = USER;
        history.runAt = clock.now();
        history.setCreatedAt(clock.now());
        history.persist();
    }

    /**
     * Plants one event left on hold for an unknown reference, so the held-events screen (P8, ING-06)
     * shows a real event waiting for its reference for every entity. Persisted {@code ON_HOLD}, it is
     * ignored by later calculations (which process only pending events) and stays held.
     *
     * @param entity the entity code
     */
    private void plantHeldEvent(String entity) {
        BusinessEvent event = new BusinessEvent();
        event.entity = LegalEntity.<LegalEntity>find("code", entity).firstResult();
        event.sourceSystem = "DEMO";
        event.sourceKey = "EVT-HELD-" + entity;
        event.eventType = EventType.TERMINATION;
        event.eventDate = LocalDate.of(2026, 10, 15);
        event.receivedDate = LocalDate.of(2026, 10, 20);
        event.processingStatus = ProcessingStatus.ON_HOLD;
        event.holdReason = HoldReason.UNKNOWN_REFERENCE;
        event.setCreatedAt(clock.now());
        event.setCreatedBy(USER);
        event.persist();
    }

    /**
     * Runs an ordinary month to closing: upfront and bulk linear premiums, the engine, the controls and
     * the full settlement. The first month also opens the discount advance on the upfront scene.
     *
     * @param world    the shared demo world
     * @param entity   the entity code
     * @param date     the event date within the month
     * @param rejectOne whether to reject one bank order (rejected then reissued the next month)
     */
    private void ordinaryMonth(DemoWorld world, String entity, LocalDate date, boolean rejectOne) {
        emitOrdinary(world, entity, date);
        engine.runReal(entity, firstOfMonth(date));
        controls.run(entity, firstOfMonth(date));
        closeFully(entity, firstOfMonth(date), rejectOne);
    }

    /**
     * Runs the family-showcase month to closing: the mock-up linear scene (Alptis), every family's
     * event, the premium path and the enrolment and annual closes, then the full settlement and a
     * rectified bordereau.
     *
     * @param world  the shared demo world
     * @param entity the entity code
     * @param date   the event date within the month
     * @param mockup whether to assert the mock-up cabinet scene (Alptis September)
     */
    private void showcaseMonth(DemoWorld world, String entity, LocalDate date, boolean mockup) {
        LocalDate month = firstOfMonth(date);
        emitOrdinary(world, entity, date);
        if (mockup) {
            // The mock-up cabinet's September scene: a single 1 100 € premium → a 110,00 € linear line.
            emit(entity, world.scene(entity, DemoCodes.F_LINEAR), EventType.PREMIUM_COLLECTED, date,
                    new BigDecimal("1100.00"), ProcessingStatus.PENDING);
        }
        emitFamilies(world, entity, date);
        emitReprises(world, entity, date);
        engine.runReal(entity, month);
        int year = month.getYear();
        engine.runDevelopment(entity, month, year);
        engine.runPortfolio(entity, month, year);
        engine.runAnnualClose(entity, month, year);
        controls.run(entity, month);
        closeFully(entity, month, false);
        rectifyOne(entity, month);
    }

    /**
     * Emits the events that exercise decision D12 (a reversal uses the origin rule version): an
     * annulation (a premium and its cancellation), a reprise (a termination clawing back the upfront
     * acquisition) and an impayé (an unpaid notice reaching the convention threshold).
     *
     * @param world  the shared demo world
     * @param entity the entity code
     * @param date   the event date
     */
    private void emitReprises(DemoWorld world, String entity, LocalDate date) {
        List<Coverage> bulk = bulkCoverages(entity);
        BusinessEvent origin = emit(entity, bulk.get(10), EventType.PREMIUM_COLLECTED, date.minusDays(3),
                new BigDecimal("800.00"), ProcessingStatus.PENDING);
        BusinessEvent cancellation = emit(entity, bulk.get(10), EventType.PREMIUM_CANCELLED, date, null,
                ProcessingStatus.PENDING);
        cancellation.reversesEvent = origin;
        emit(entity, world.scene(entity, DemoCodes.F_UPFRONT), EventType.TERMINATION, date, null,
                ProcessingStatus.PENDING);
        BusinessEvent unpaid = emit(entity, bulk.get(11), EventType.UNPAID_NOTICE, date, null,
                ProcessingStatus.PENDING);
        unpaid.unpaidRank = (short) 2;
    }

    /**
     * Emits an entity's ordinary monthly premiums: the upfront scene premium (opening and feeding the
     * discount advance) and a handful of bulk linear premiums.
     *
     * @param world  the shared demo world
     * @param entity the entity code
     * @param date   the event date
     */
    private void emitOrdinary(DemoWorld world, String entity, LocalDate date) {
        emit(entity, world.scene(entity, DemoCodes.F_UPFRONT), EventType.PREMIUM_COLLECTED, date,
                new BigDecimal("1000.00"), ProcessingStatus.PENDING);
        // The escompte coverage (Alptis only) collects a monthly premium so the June-opened advance is
        // apured from one month to the next: a real discount followed over several months (section 6.4).
        Coverage escompte = world.scene(entity, DemoCodes.F_ESCOMPTE);
        if (escompte != null) {
            emit(entity, escompte, EventType.PREMIUM_COLLECTED, date, new BigDecimal("300.00"),
                    ProcessingStatus.PENDING);
        }
        for (Coverage coverage : bulkCoverages(entity).stream().limit(8).toList()) {
            emit(entity, coverage, EventType.PREMIUM_COLLECTED, date, new BigDecimal("1000.00"),
                    ProcessingStatus.PENDING);
        }
    }

    /**
     * Emits every family's driving event for a showcase month: retrocession, outstanding, savings,
     * fixed and external in the premium path, and the subscription and collected-premium markers the
     * development and portfolio closes read (emitted processed so the premium path leaves them alone).
     *
     * @param world  the shared demo world
     * @param entity the entity code
     * @param date   the event date
     */
    private void emitFamilies(DemoWorld world, String entity, LocalDate date) {
        emit(entity, world.scene(entity, DemoCodes.F_SHARE), EventType.INSURER_COMMISSION, date,
                new BigDecimal("100.00"), ProcessingStatus.PENDING);
        emitOutstanding(entity, world.scene(entity, DemoCodes.F_OUTSTANDING), date);
        emit(entity, world.scene(entity, DemoCodes.F_SAVINGS), EventType.SCHEDULED_PAYMENT, date,
                new BigDecimal("300.00"), ProcessingStatus.PENDING);
        emit(entity, world.scene(entity, DemoCodes.F_FIXED), EventType.PREMIUM_COLLECTED, date,
                new BigDecimal("1.00"), ProcessingStatus.PENDING);
        emitExternal(entity, world.scene(entity, DemoCodes.F_EXTERNAL), date);
        // Development and portfolio markers: read by the closes, kept out of the premium path (processed).
        emit(entity, world.scene(entity, DemoCodes.F_RCD), EventType.SUBSCRIPTION, date,
                new BigDecimal("30000.00"), ProcessingStatus.PROCESSED);
        emit(entity, world.scene(entity, DemoCodes.F_PORTFOLIO), EventType.PREMIUM_COLLECTED, date,
                new BigDecimal("50000.00"), ProcessingStatus.PROCESSED);
    }

    /**
     * Runs the Alptis October: ordinary premiums plus the anomaly-triggering events (future date, null
     * premium, no rule), a debtor-creating adjustment and five adjustments in different statuses; the
     * controls are run and two anomalies decided, the period left calculated with a blocking anomaly.
     *
     * @param world the shared demo world
     */
    private void octoberAlptis(DemoWorld world) {
        String entity = DemoCodes.ALPTIS;
        LocalDate month = LocalDate.of(2026, 10, 1);
        emitOrdinary(world, entity, LocalDate.of(2026, 10, 2));
        List<Coverage> bulk = bulkCoverages(entity);
        // A future-dated premium (blocking), a zero premium (warning) and a no-rule event (warning).
        emit(entity, bulk.get(1), EventType.PREMIUM_COLLECTED, clock.today().plusMonths(2),
                new BigDecimal("500.00"), ProcessingStatus.PENDING);
        emit(entity, bulk.get(0), EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 10, 2),
                BigDecimal.ZERO, ProcessingStatus.PENDING);
        emit(entity, world.scene(entity, DemoCodes.F_SHARE), EventType.PREMIUM_COLLECTED,
                LocalDate.of(2026, 10, 2), new BigDecimal("400.00"), ProcessingStatus.PENDING);
        engine.runReal(entity, month);
        periods.transition(entity, month, PeriodState.LOADED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.CALCULATED, ALL, USER, null);
        debtorAdjustment(world, entity, month);
        otherAdjustments(world, entity, month);
        controls.run(entity, month);
        decideSome(month);
    }

    /**
     * Runs the UNEP October to a prepared-but-unvalidated payment lot: the engine, the controls and the
     * state machine up to payment-prepared, with the cycle lot's order details prepared but not
     * validated by Finance.
     *
     * @param world the shared demo world
     */
    private void octoberUnep(DemoWorld world) {
        String entity = DemoCodes.UNEP;
        LocalDate month = LocalDate.of(2026, 10, 1);
        emitOrdinary(world, entity, LocalDate.of(2026, 10, 2));
        // A zero premium so the UNEP controls raise an anomaly too (non-blocking).
        emit(entity, bulkCoverages(entity).get(0), EventType.PREMIUM_COLLECTED, LocalDate.of(2026, 10, 2),
                BigDecimal.ZERO, ProcessingStatus.PENDING);
        engine.runReal(entity, month);
        controls.run(entity, month);
        periods.transition(entity, month, PeriodState.LOADED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.CALCULATED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.CONTROLLED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.BUSINESS_VALIDATED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.PAYMENT_PREPARED, ALL, USER, null);
        for (PaymentBatch batch : cycleBatches(period(entity, month))) {
            payments.prepareOrderDetails(batch.getId());
        }
        // Two UNEP adjustments so the adjustments screen is not empty for UNEP users. The clawback uses
        // a distributor-level account (which carries no commission line) so it is cleanly debtor.
        DistributorAccount account = accounts.accountFor(world.entity(entity), PayeeType.DISTRIBUTOR,
                world.distributor("P20003").getId());
        // An approved clawback drives the account debtor (so UNEP's debtor screen is not empty), plus a
        // submitted correction awaiting validation.
        var clawback = adjustments.create(world.entity(entity), period(entity, month),
                AdjustmentType.MANUAL_CLAWBACK, new BigDecimal("-200.00"), account, "Reprise UNEP", null, false,
                null, null, "operator-unep");
        adjustments.approve(clawback.getId(), "validator-unep", ALL);
        adjustments.create(world.entity(entity), period(entity, month), AdjustmentType.CORRECTION,
                new BigDecimal("15.00"), account, "Correction UNEP", null, false, null, null, "operator-unep");
        // An exceptional advance repaid by instalments: an apurement schedule for the debtor (CAL-14).
        adjustments.create(world.entity(entity), period(entity, month), AdjustmentType.EXCEPTIONAL_ADVANCE,
                new BigDecimal("300.00"), account, "Avance exceptionnelle UNEP", null, false, null, null,
                "\"3 échéances de 100,00 € (300,00 €)\"", "operator-unep");
    }

    /**
     * Creates the November Alptis period open, with pending events awaiting calculation.
     *
     * @param world the shared demo world
     */
    private void novemberAlptis(DemoWorld world) {
        String entity = DemoCodes.ALPTIS;
        LocalDate month = LocalDate.of(2026, 11, 1);
        Period period = new Period();
        period.entity = world.entity(entity);
        period.periodMonth = month;
        period.state = PeriodState.OPEN;
        period.setCreatedAt(clock.now());
        period.setCreatedBy(USER);
        period.persist();
        emitOrdinary(world, entity, LocalDate.of(2026, 11, 5));
    }

    /**
     * Drives a period from open to closed and extracts its accounting, publishing its bordereaux and
     * optionally rejecting one bank order (reissued the next month through the carried balance).
     *
     * @param entity    the entity code
     * @param month     the period month
     * @param rejectOne whether to reject one bank order
     */
    private void closeFully(String entity, LocalDate month, boolean rejectOne) {
        periods.transition(entity, month, PeriodState.LOADED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.CALCULATED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.CONTROLLED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.BUSINESS_VALIDATED, ALL, USER, null);
        periods.transition(entity, month, PeriodState.PAYMENT_PREPARED, ALL, USER, null);
        Period period = period(entity, month);
        publishStatements(period);
        for (PaymentBatch batch : cycleBatches(period)) {
            payments.prepareOrderDetails(batch.getId());
        }
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("period = ?1", period)) {
            payments.validateFinance(batch.getId(), USER);
        }
        periods.transition(entity, month, PeriodState.FINANCE_VALIDATED, ALL, USER, null);
        settleOrders(period, rejectOne);
        periods.transition(entity, month, PeriodState.CLOSED, ALL, USER, null);
        cutoff.extract(entity, month);
    }

    /**
     * Records the bank result of every issued order of a period: all settle, except the first one when
     * a rejection is asked (its lines return payable and are paid in the next month's lot).
     *
     * @param period    the period
     * @param rejectOne whether to reject the first order
     */
    private void settleOrders(Period period, boolean rejectOne) {
        boolean rejected = false;
        for (PaymentOrder order : PaymentOrder.<PaymentOrder>list("batch.period = ?1 and status = ?2", period,
                PaymentOrderStatus.ISSUED)) {
            if (rejectOne && !rejected) {
                payments.recordBankResult(order.getId(), false, "Compte clôturé");
                rejected = true;
            } else {
                payments.recordBankResult(order.getId(), true, null);
            }
        }
    }

    /**
     * Publishes every draft bordereau of a period.
     *
     * @param period the period
     */
    private void publishStatements(Period period) {
        for (Statement statement : Statement.<Statement>list("period = ?1 and status = ?2", period,
                StatementStatus.DRAFT)) {
            statements.publish(statement.getId());
        }
    }

    /**
     * Rectifies the first published bordereau of a period and publishes the correction.
     *
     * @param entity the entity code
     * @param month  the period month
     */
    private void rectifyOne(String entity, LocalDate month) {
        Statement original = Statement.<Statement>find("period = ?1 and status = ?2", period(entity, month),
                StatementStatus.PUBLISHED).firstResult();
        Statement correction = statements.rectify(original.getId());
        statements.publish(correction.getId());
    }

    /**
     * Creates and approves a manual clawback that drives one account into a debtor balance, so the
     * debtor control has an object to flag.
     *
     * @param world  the shared demo world
     * @param entity the entity code
     * @param month  the period month
     */
    private void debtorAdjustment(DemoWorld world, String entity, LocalDate month) {
        DistributorAccount account = accounts.accountFor(world.entity(entity), PayeeType.PRODUCTION_CODE,
                world.productionCode(entity, "PC-P20010").getId());
        var adjustment = adjustments.create(world.entity(entity), period(entity, month),
                AdjustmentType.MANUAL_CLAWBACK, new BigDecimal("-200.00"), account, "Reprise exceptionnelle",
                null, false, null, null, "operator-demo");
        adjustments.approve(adjustment.getId(), "validator-demo", ALL);
    }

    /**
     * Creates four more adjustments in different statuses: one submitted (awaiting validation), one
     * applied, one rejected and one draft.
     *
     * @param world  the shared demo world
     * @param entity the entity code
     * @param month  the period month
     */
    private void otherAdjustments(DemoWorld world, String entity, LocalDate month) {
        LegalEntity e = world.entity(entity);
        Period period = period(entity, month);
        DistributorAccount account = accounts.accountFor(e, PayeeType.PRODUCTION_CODE,
                world.productionCode(entity, "PC-P20002").getId());
        adjustments.create(e, period, AdjustmentType.EXCEPTIONAL_COMMISSION, new BigDecimal("50.00"), account,
                "Prime commerciale", null, false, null, null, "operator-demo");
        var applied = adjustments.create(e, period, AdjustmentType.CORRECTION, new BigDecimal("30.00"), account,
                "Correction de saisie", null, false, null, null, "operator-demo");
        adjustments.approve(applied.getId(), "validator-demo", ALL);
        var refused = adjustments.create(e, period, AdjustmentType.EXCEPTIONAL_COMMISSION, new BigDecimal("500.00"),
                account, "Geste commercial refusé", null, false, null, null, "operator-demo");
        refused.status = AdjustmentStatus.REJECTED;
        var draft = adjustments.create(e, period, AdjustmentType.CORRECTION, new BigDecimal("10.00"), account,
                "Brouillon de correction", null, false, null, null, "operator-demo");
        draft.status = AdjustmentStatus.DRAFT;
        // An exceptional advance repaid by instalments: an apurement schedule for the debtor (CAL-14).
        adjustments.create(e, period, AdjustmentType.EXCEPTIONAL_ADVANCE, new BigDecimal("300.00"), account,
                "Avance exceptionnelle", null, false, null, null, "\"3 échéances de 100,00 € (300,00 €)\"",
                "operator-demo");
    }

    /**
     * Decides the first two open anomalies of a period, leaving the rest (including a blocking one) open.
     *
     * @param month the period month
     */
    private void decideSome(LocalDate month) {
        // Decide two non-blocking anomalies (October always has at least three), so the period keeps its
        // open blocking anomaly and shows both an accepted and a fixed decision.
        List<ControlResult> open = ControlResult.<ControlResult>list(
                "period.periodMonth = ?1 and decision = ?2 and severity <> ?3", month, ControlDecision.OPEN,
                ControlSeverity.BLOCKING);
        controls.decide(open.get(0).getId(), ControlDecision.ACCEPTED, "operator-demo", "Anomalie acceptée");
        controls.decide(open.get(1).getId(), ControlDecision.FIXED, "operator-demo", "Anomalie corrigée");
    }

    /**
     * Returns an entity's bulk linear coverages (contract numbers carrying the bulk marker).
     *
     * @param entity the entity code
     * @return the bulk coverages
     */
    private List<Coverage> bulkCoverages(String entity) {
        return Coverage.<Coverage>list("contract.entity.code = ?1 and contract.contractNumber like ?2", entity,
                "%-BULK-%");
    }

    /**
     * Returns the cycle and savings-cycle batches of a period.
     *
     * @param period the period
     * @return the cycle batches
     */
    private List<PaymentBatch> cycleBatches(Period period) {
        return PaymentBatch.list("period = ?1 and batchType in ?2", period,
                List.of(BatchType.CYCLE, BatchType.SAVINGS_CYCLE));
    }

    /**
     * Returns the period of an entity and month.
     *
     * @param entity the entity code
     * @param month  the period month
     * @return the period
     */
    private Period period(String entity, LocalDate month) {
        return Period.<Period>find("entity.code = ?1 and periodMonth = ?2", entity, month).firstResult();
    }

    /**
     * Emits one premium-style business event.
     *
     * @param entity   the entity code
     * @param coverage the coverage
     * @param type     the event type
     * @param date     the event date
     * @param basis    the basis amount
     * @param status   the processing status
     * @return the persisted event
     */
    private BusinessEvent emit(String entity, Coverage coverage, EventType type, LocalDate date, BigDecimal basis,
                               ProcessingStatus status) {
        BusinessEvent event = baseEvent(entity, coverage, type, date);
        event.basisAmount = basis;
        event.processingStatus = status;
        event.persist();
        return event;
    }

    /**
     * Emits an outstanding-balance event over the first quarter of the event's year.
     *
     * @param entity   the entity code
     * @param coverage the coverage
     * @param date     the event date
     */
    private void emitOutstanding(String entity, Coverage coverage, LocalDate date) {
        BusinessEvent event = baseEvent(entity, coverage, EventType.OUTSTANDING_BALANCE, date);
        event.basisAmount = new BigDecimal("20000.00");
        event.periodFrom = LocalDate.of(date.getYear(), 1, 1);
        event.periodTo = LocalDate.of(date.getYear(), 3, 31);
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();
    }

    /**
     * Emits an external-commission event carrying its precomputed basis, rate and amount.
     *
     * @param entity   the entity code
     * @param coverage the coverage
     * @param date     the event date
     */
    private void emitExternal(String entity, Coverage coverage, LocalDate date) {
        BusinessEvent event = baseEvent(entity, coverage, EventType.PREMIUM_COLLECTED, date);
        event.basisAmount = new BigDecimal("500.00");
        event.precomputedBasis = new BigDecimal("500.00");
        event.precomputedRate = new BigDecimal("0.035000");
        event.precomputedAmount = new BigDecimal("17.50");
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();
    }

    /**
     * Builds a business event with its common fields, not yet persisted.
     *
     * @param entity   the entity code
     * @param coverage the coverage
     * @param type     the event type
     * @param date     the event date
     * @return the event
     */
    private BusinessEvent baseEvent(String entity, Coverage coverage, EventType type, LocalDate date) {
        BusinessEvent event = new BusinessEvent();
        event.entity = LegalEntity.<LegalEntity>find("code", entity).firstResult();
        event.sourceSystem = "DEMO";
        event.sourceKey = "EVT-" + (++seq);
        event.eventType = type;
        event.coverage = coverage;
        event.contract = coverage.contract;
        event.eventDate = date;
        event.receivedDate = date;
        event.setCreatedAt(clock.now());
        event.setCreatedBy(USER);
        return event;
    }

    /**
     * Returns the first day of an event date's month.
     *
     * @param date the date
     * @return the first day of its month
     */
    private static LocalDate firstOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }
}
