package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.PeriodTransition;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the period cycle (section 7.1, step 9): the forward path and its side effects, the role
 * control, the calculation and control guards, and the Finance-refusal rollback. Runs against UNEP,
 * inside rolled-back transactions.
 */
@QuarkusTest
class PeriodServiceTest {

    @Inject
    PeriodService service;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period of the entity in a given state. */
    private static Period period(LocalDate month, PeriodState state) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = state;
        period.persist();
        return period;
    }

    private static final LocalDate M = LocalDate.of(2026, 3, 1);

    /**
     * The forward path takes the period from open to closed, posting an acquired line to its account
     * and proposing its payment on the way.
     */
    @Test
    @TestTransaction
    void forward_path_to_closed() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Period p = period(M, PeriodState.OPEN);
        Distributor d = new Distributor();
        d.partnerCode = "D1";
        d.legalName = "D1";
        d.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-1";
        pc.distributor = d;
        pc.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = p;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal("100");
        line.originProductionCode = pc;
        line.persist();

        service.transition("UNEP", M, PeriodState.LOADED, java.util.Set.of(Role.OPERATOR), "u", null);
        service.transition("UNEP", M, PeriodState.CALCULATED, java.util.Set.of(Role.BUSINESS_ADMIN), "u", null);
        service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.OPERATOR), "u", null);
        service.transition("UNEP", M, PeriodState.BUSINESS_VALIDATED, java.util.Set.of(Role.BUSINESS_VALIDATOR), "u", null);
        service.transition("UNEP", M, PeriodState.PAYMENT_PREPARED, java.util.Set.of(Role.OPERATOR), "u", null);
        service.transition("UNEP", M, PeriodState.FINANCE_VALIDATED, java.util.Set.of(Role.FINANCE_CONTROLLER), "u", null);
        service.transition("UNEP", M, PeriodState.PAID, java.util.Set.of(Role.FINANCE_CONTROLLER), "u", null);
        Period closed = service.transition("UNEP", M, PeriodState.CLOSED, java.util.Set.of(Role.FINANCE_CONTROLLER), "u", null);

        assertEquals(PeriodState.CLOSED, closed.state);
        assertEquals(8, PeriodTransition.count("period", p));
        assertEquals(1, PaymentOrder.count());
    }

    /**
     * The recalculation rollbacks step a controlled or calculated period back without side effects.
     */
    @Test
    @TestTransaction
    void recalculation_rollbacks() {
        period(M, PeriodState.CALCULATED);
        assertEquals(PeriodState.LOADED,
                service.transition("UNEP", M, PeriodState.LOADED, java.util.Set.of(Role.OPERATOR), "u", null).state);
        service.transition("UNEP", M, PeriodState.CALCULATED, java.util.Set.of(Role.BUSINESS_ADMIN), "u", null);
        service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.OPERATOR), "u", null);
        assertEquals(PeriodState.OPEN,
                service.transition("UNEP", M, PeriodState.OPEN, java.util.Set.of(Role.OPERATOR), "u", null).state);
    }

    /**
     * A transition by the wrong role, or one that does not exist, is refused.
     */
    @Test
    @TestTransaction
    void wrong_role_and_unknown_transition() {
        period(M, PeriodState.OPEN);
        assertThrows(ValidationException.class,
                () -> service.transition("UNEP", M, PeriodState.LOADED, java.util.Set.of(Role.BUSINESS_ADMIN), "u", null));
        assertThrows(ValidationException.class,
                () -> service.transition("UNEP", M, PeriodState.CALCULATED, java.util.Set.of(Role.BUSINESS_ADMIN), "u", null));
    }

    /**
     * A period is calculable only when the previous one is at least business-validated.
     */
    @Test
    @TestTransaction
    void calculation_waits_for_previous_period() {
        period(LocalDate.of(2026, 2, 1), PeriodState.CONTROLLED);
        period(M, PeriodState.LOADED);
        assertThrows(ValidationException.class,
                () -> service.transition("UNEP", M, PeriodState.CALCULATED, java.util.Set.of(Role.BUSINESS_ADMIN), "u", null));
        Period previous = Period.find("entity = ?1 and periodMonth = ?2", unep(), LocalDate.of(2026, 2, 1))
                .firstResult();
        previous.state = PeriodState.BUSINESS_VALIDATED;
        assertEquals(PeriodState.CALCULATED,
                service.transition("UNEP", M, PeriodState.CALCULATED, java.util.Set.of(Role.BUSINESS_ADMIN), "u", null).state);
    }

    /**
     * A period cannot be controlled while a blocking anomaly stays open; once decided, it can.
     */
    @Test
    @TestTransaction
    void control_waits_for_blocking_anomaly() {
        Period p = period(M, PeriodState.CALCULATED);
        ControlDefinition def = new ControlDefinition();
        def.entity = unep();
        def.code = "C1";
        def.label = "c";
        def.family = ControlFamily.CALCULATION;
        def.severity = ControlSeverity.BLOCKING;
        def.active = Boolean.TRUE;
        def.persist();
        ControlResult result = new ControlResult();
        result.period = p;
        result.control = def;
        result.severity = ControlSeverity.BLOCKING;
        result.decision = ControlDecision.OPEN;
        result.persist();

        assertThrows(ValidationException.class,
                () -> service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.OPERATOR), "u", null));
        result.decision = ControlDecision.ACCEPTED;
        assertEquals(PeriodState.CONTROLLED,
                service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.OPERATOR), "u", null).state);
    }

    /**
     * A Finance refusal needs a reason, frees the payable lines and drops the draft payment batch.
     */
    @Test
    @TestTransaction
    void finance_refusal_rollback() {
        unep().minPaymentAmount = BigDecimal.ZERO;
        Period p = period(M, PeriodState.CONTROLLED);
        Distributor d = new Distributor();
        d.partnerCode = "D1";
        d.legalName = "D1";
        d.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-1";
        pc.distributor = d;
        pc.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = p;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal("100");
        line.originProductionCode = pc;
        line.persist();
        service.transition("UNEP", M, PeriodState.BUSINESS_VALIDATED, java.util.Set.of(Role.BUSINESS_VALIDATOR), "u", null);
        service.transition("UNEP", M, PeriodState.PAYMENT_PREPARED, java.util.Set.of(Role.OPERATOR), "u", null);
        assertEquals(1, PaymentBatch.count());
        assertEquals(LineStatus.PAYABLE, CommissionLine.<CommissionLine>findById(line.getId()).status);

        assertThrows(ValidationException.class,
                () -> service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.FINANCE_CONTROLLER), "u", null));
        assertThrows(ValidationException.class,
                () -> service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.FINANCE_CONTROLLER), "u", " "));
        service.transition("UNEP", M, PeriodState.CONTROLLED, java.util.Set.of(Role.FINANCE_CONTROLLER), "u", "refus Finance");

        assertEquals(0, PaymentBatch.count());
        assertEquals(0, PaymentOrder.count());
        assertEquals(LineStatus.EARNED,
                CommissionLine.<CommissionLine>findById(line.getId()).status);
    }

    /**
     * An unknown entity or period is reported as not found.
     */
    @Test
    @TestTransaction
    void unknown_entity_or_period() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.transition("NOPE", M, PeriodState.LOADED, java.util.Set.of(Role.OPERATOR), "u", null));
        assertThrows(ResourceNotFoundException.class,
                () -> service.transition("UNEP", M, PeriodState.LOADED, java.util.Set.of(Role.OPERATOR), "u", null));
    }
}
