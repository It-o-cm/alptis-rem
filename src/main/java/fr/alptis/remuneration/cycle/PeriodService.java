package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.accounting.AccountingService;
import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentExclusion;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.PeriodTransition;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.StatementStatus;
import fr.alptis.remuneration.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Period cycle service (section 7.1, WKF-01): drives a period through its states with role control,
 * the forward path from {@code OPEN} to {@code CLOSED} and the authorised rollbacks (recalculation
 * from {@code CALCULATED}/{@code CONTROLLED}, and the Finance refusal from {@code PAYMENT_PREPARED}
 * back to {@code CONTROLLED}, which frees the payable lines and drops the draft batches). A period is
 * only calculable once the previous one is at least business-validated, and only controllable once no
 * blocking anomaly stays open.
 */
@ApplicationScoped
public class PeriodService {

    /** The allowed transitions of the cycle, with the role that performs each. */
    private static final List<Transition> TRANSITIONS = List.of(
            new Transition(PeriodState.OPEN, PeriodState.LOADED, Role.OPERATOR, false),
            new Transition(PeriodState.LOADED, PeriodState.CALCULATED, Role.BUSINESS_ADMIN, false),
            new Transition(PeriodState.CALCULATED, PeriodState.CONTROLLED, Role.OPERATOR, false),
            new Transition(PeriodState.CONTROLLED, PeriodState.BUSINESS_VALIDATED, Role.BUSINESS_VALIDATOR, false),
            new Transition(PeriodState.BUSINESS_VALIDATED, PeriodState.PAYMENT_PREPARED, null, false),
            new Transition(PeriodState.PAYMENT_PREPARED, PeriodState.FINANCE_VALIDATED, Role.FINANCE_CONTROLLER, false),
            new Transition(PeriodState.FINANCE_VALIDATED, PeriodState.PAID, Role.FINANCE_CONTROLLER, false),
            new Transition(PeriodState.PAID, PeriodState.CLOSED, Role.FINANCE_CONTROLLER, false),
            new Transition(PeriodState.CALCULATED, PeriodState.LOADED, Role.OPERATOR, false),
            new Transition(PeriodState.CALCULATED, PeriodState.OPEN, Role.OPERATOR, false),
            new Transition(PeriodState.CONTROLLED, PeriodState.LOADED, Role.OPERATOR, false),
            new Transition(PeriodState.CONTROLLED, PeriodState.OPEN, Role.OPERATOR, false),
            new Transition(PeriodState.PAYMENT_PREPARED, PeriodState.CONTROLLED, Role.FINANCE_CONTROLLER, true));

    private final AccountService accounts;
    private final AccountingService accounting;
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param accounts   the distributor-account service
     * @param accounting the accounting-entry service
     * @param clock      the application date/time provider
     */
    @Inject
    public PeriodService(AccountService accounts, AccountingService accounting, DateTimeProvider clock) {
        this.accounts = accounts;
        this.accounting = accounting;
        this.clock = clock;
    }

    /**
     * Moves a period to a new state (section 7.1), checking the role, the guards and recording the
     * transition.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @param toState     the target state
     * @param roles       the acting user's roles
     * @param userId      the acting user
     * @param reason      the reason, required for a Finance refusal
     * @return the period in its new state
     */
    @Transactional
    public Period transition(String entityCode, LocalDate periodMonth, PeriodState toState, java.util.Set<Role> roles,
                             String userId, String reason) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + periodMonth);
        }
        Transition transition = TRANSITIONS.stream()
                .filter(t -> t.from == period.state && t.to == toState).findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Transition interdite : " + period.state + " vers " + toState));
        if (transition.role != null && !roles.contains(transition.role)) {
            throw new ValidationException("Rôle non autorisé pour " + period.state + " vers " + toState);
        }
        if (transition.requiresReason && (reason == null || reason.isBlank())) {
            throw new ValidationException("Un motif est exigé pour ce retour arrière");
        }
        guard(entity, period, toState);
        applyEffects(entity, period, toState);
        PeriodState from = period.state;
        period.state = toState;
        record(period, from, toState, userId, reason);
        return period;
    }

    /**
     * Checks the guards of a target state: a period is calculated only if the previous one is at least
     * business-validated, and controlled only if no blocking anomaly stays open.
     *
     * @param entity  the entity
     * @param period  the period
     * @param toState the target state
     */
    private void guard(LegalEntity entity, Period period, PeriodState toState) {
        if (toState == PeriodState.CALCULATED) {
            Period previous = Period.find("entity = ?1 and periodMonth = ?2", entity,
                    period.periodMonth.minusMonths(1)).firstResult();
            if (previous != null && previous.state.ordinal() < PeriodState.BUSINESS_VALIDATED.ordinal()) {
                throw new ValidationException("La période précédente n'est pas encore validée par le métier");
            }
        }
        if (toState == PeriodState.CONTROLLED
                && ControlResult.count("period = ?1 and severity = ?2 and decision = ?3",
                period, ControlSeverity.BLOCKING, ControlDecision.OPEN) > 0) {
            throw new ValidationException("Une anomalie bloquante reste ouverte");
        }
    }

    /**
     * Applies the side effects of a transition: posting the acquired lines to the accounts at business
     * validation, preparing the payment at payment preparation, and freeing the payable lines and
     * dropping the draft batches on a Finance refusal.
     *
     * @param entity  the entity
     * @param period  the period
     * @param toState the target state
     */
    private void applyEffects(LegalEntity entity, Period period, PeriodState toState) {
        if (toState == PeriodState.LOADED && period.dataCutoffDate == null) {
            // Freezing the data fixes the cutoff — the last day of the period's month by default (section
            // 7.2). Events received after it belong to the next period (CYC-04).
            period.dataCutoffDate = period.periodMonth.plusMonths(1).minusDays(1);
        }
        if (toState == PeriodState.BUSINESS_VALIDATED) {
            accounts.postToAccounts(entity, period);
            accounting.generateForValidation(entity, period);
        } else if (toState == PeriodState.PAYMENT_PREPARED) {
            accounts.proposePayments(entity, period);
        } else if (period.state == PeriodState.PAYMENT_PREPARED && toState == PeriodState.CONTROLLED) {
            reopen(period);
        }
    }

    /**
     * Reverses a payment preparation on a Finance refusal (section 7.1): the payable lines become
     * earned again and the draft batches, their orders, exclusions and statements are removed.
     *
     * @param period the period
     */
    private void reopen(Period period) {
        for (PaymentBatch batch : PaymentBatch.<PaymentBatch>list("period = ?1 and status = ?2",
                period, BatchStatus.DRAFT)) {
            PaymentOrder.<PaymentOrder>list("batch = ?1", batch).forEach(PaymentOrder::delete);
            PaymentExclusion.<PaymentExclusion>list("batch = ?1", batch).forEach(PaymentExclusion::delete);
            batch.delete();
        }
        // Drop each payable line's link to its draft bordereau and return it to earned before the
        // statements are removed, so no line is left referencing a deleted statement at flush time.
        for (CommissionLine line : CommissionLine.<CommissionLine>list("period = ?1 and status = ?2",
                period, LineStatus.PAYABLE)) {
            line.status = LineStatus.EARNED;
            line.statement = null;
        }
        fr.alptis.remuneration.domain.Statement.<fr.alptis.remuneration.domain.Statement>list(
                "period = ?1 and status = ?2", period, StatementStatus.DRAFT)
                .forEach(fr.alptis.remuneration.domain.Statement::delete);
        accounting.discardForPeriod(period);
    }

    /**
     * Records a period transition in the audit trail.
     *
     * @param period the period
     * @param from   the previous state
     * @param to     the new state
     * @param userId the acting user
     * @param reason the reason, possibly null
     */
    private void record(Period period, PeriodState from, PeriodState to, String userId, String reason) {
        PeriodTransition transition = new PeriodTransition();
        transition.period = period;
        transition.fromState = from;
        transition.toState = to;
        transition.occurredAt = clock.now();
        transition.userId = userId;
        transition.reason = reason;
        transition.setCreatedAt(clock.now());
        transition.persist();
    }

    /** One allowed cycle transition. */
    private record Transition(PeriodState from, PeriodState to, Role role, boolean requiresReason) {
    }
}
