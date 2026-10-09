package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.AdjustmentStatus;
import fr.alptis.remuneration.domain.AdjustmentType;
import fr.alptis.remuneration.domain.Claim;
import fr.alptis.remuneration.domain.ClaimLine;
import fr.alptis.remuneration.domain.ClaimStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.EntityParameter;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adjustment and claim service (section 8.5, REG-01 to REG-06): records an adjustment with its
 * mandatory reason, submits it to a second user's approval (the business validator beyond a
 * parameterised threshold), applies the approved adjustment as a {@code MANUAL} or {@code TRANSFER}
 * line, optionally auto-reverses it on the next period, imports adjustments in bulk, and tracks a
 * distributor's claim against its contested lines until it is closed. An adjustment corrects an
 * amount, never an existing line nor the source data.
 */
@ApplicationScoped
public class AdjustmentService {

    /** The entity parameter carrying the second-level approval threshold. */
    private static final String THRESHOLD_KEY = "ADJUSTMENT_APPROVAL_THRESHOLD";

    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public AdjustmentService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Records a submitted adjustment (REG-01, REG-02), requiring a reason.
     *
     * @param entity        the entity
     * @param period        the period
     * @param type          the adjustment type
     * @param amount        the signed amount
     * @param account       the account corrected or credited
     * @param reason        the mandatory reason
     * @param attachmentRef an optional attachment reference
     * @param autoReverse   whether the adjustment is auto-reversed on the next period
     * @param correctedLine the line corrected, possibly null
     * @param counterpart   the counterpart account of a transfer, possibly null
     * @param submitter     the submitting user
     * @return the submitted adjustment
     */
    @Transactional
    public Adjustment create(LegalEntity entity, Period period, AdjustmentType type, BigDecimal amount,
                             DistributorAccount account, String reason, String attachmentRef, boolean autoReverse,
                             CommissionLine correctedLine, DistributorAccount counterpart, String submitter) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Un motif est exigé pour un ajustement");
        }
        Adjustment adjustment = new Adjustment();
        adjustment.entity = entity;
        adjustment.period = period;
        adjustment.adjustmentType = type;
        adjustment.amount = amount;
        adjustment.account = account;
        adjustment.correctedLine = correctedLine;
        adjustment.counterpartAccount = counterpart;
        adjustment.reason = reason;
        adjustment.attachmentRef = attachmentRef;
        adjustment.autoReverse = autoReverse;
        adjustment.status = AdjustmentStatus.SUBMITTED;
        adjustment.setCreatedBy(submitter);
        adjustment.setCreatedAt(clock.now());
        adjustment.persist();
        return adjustment;
    }

    /**
     * Approves and applies an adjustment (REG-03, REG-05): a second user, the business validator when
     * the amount exceeds the entity threshold, then a {@code MANUAL} or {@code TRANSFER} line, and the
     * auto-reversal on the next period when requested.
     *
     * @param adjustmentId the adjustment id
     * @param approver     the approving user (must differ from the submitter)
     * @param roles        the approver's roles
     * @return the applied adjustment
     */
    @Transactional
    public Adjustment approve(Long adjustmentId, String approver, java.util.Set<Role> roles) {
        Adjustment adjustment = Adjustment.findById(adjustmentId);
        if (adjustment == null) {
            throw new ResourceNotFoundException("Ajustement inconnu : " + adjustmentId);
        }
        if (approver == null || approver.equals(adjustment.getCreatedBy())) {
            throw new ValidationException("Un second utilisateur doit approuver l'ajustement");
        }
        BigDecimal threshold = threshold(adjustment.entity);
        if (threshold != null && adjustment.amount.abs().compareTo(threshold) > 0
                && !roles.contains(Role.BUSINESS_VALIDATOR)) {
            throw new ValidationException("Au-delà du seuil, l'approbation revient au valideur métier");
        }
        if (threshold == null && !roles.contains(Role.OPERATOR) && !roles.contains(Role.BUSINESS_ADMIN)
                && !roles.contains(Role.BUSINESS_VALIDATOR)) {
            throw new ValidationException("Rôle non autorisé à approuver un ajustement");
        }
        adjustment.status = AdjustmentStatus.APPROVED;
        adjustment.approvedBy = approver;
        adjustment.approvedAt = clock.now();
        apply(adjustment);
        if (Boolean.TRUE.equals(adjustment.autoReverse)) {
            reverseNextPeriod(adjustment);
        }
        return adjustment;
    }

    /**
     * Applies an approved adjustment as a line: a transfer writes two {@code TRANSFER} lines between
     * the account and its counterpart, any other adjustment a single {@code MANUAL} line.
     *
     * @param adjustment the approved adjustment
     */
    private void apply(Adjustment adjustment) {
        if (adjustment.adjustmentType == AdjustmentType.ACCOUNT_TRANSFER) {
            writeLine(adjustment, adjustment.account, adjustment.amount.negate(), LineNature.TRANSFER);
            adjustment.resultingLine = writeLine(adjustment, adjustment.counterpartAccount, adjustment.amount,
                    LineNature.TRANSFER);
        } else {
            adjustment.resultingLine = writeLine(adjustment, adjustment.account, adjustment.amount, LineNature.MANUAL);
        }
        adjustment.status = AdjustmentStatus.APPLIED;
    }

    /**
     * Creates the auto-reversal of an adjustment on the entity's next period (contrepassation).
     *
     * @param adjustment the auto-reversed adjustment
     */
    private void reverseNextPeriod(Adjustment adjustment) {
        Period next = nextPeriod(adjustment.entity, adjustment.period);
        Adjustment reversal = new Adjustment();
        reversal.entity = adjustment.entity;
        reversal.period = next;
        reversal.adjustmentType = AdjustmentType.MANUAL_CLAWBACK;
        reversal.amount = adjustment.amount.negate();
        reversal.account = adjustment.account;
        reversal.reason = "Contrepassation de l'ajustement " + adjustment.getId();
        reversal.autoReverse = Boolean.FALSE;
        reversal.status = AdjustmentStatus.APPROVED;
        reversal.approvedBy = adjustment.approvedBy;
        reversal.approvedAt = clock.now();
        reversal.setCreatedBy(adjustment.getCreatedBy());
        reversal.setCreatedAt(clock.now());
        reversal.persist();
        reversal.resultingLine = writeLine(reversal, reversal.account, reversal.amount, LineNature.MANUAL);
        reversal.status = AdjustmentStatus.APPLIED;
    }

    /**
     * Returns the entity's period following a period, creating it open when absent.
     *
     * @param entity the entity
     * @param period the period
     * @return the next period
     */
    private Period nextPeriod(LegalEntity entity, Period period) {
        Period next = Period.find("entity = ?1 and periodMonth = ?2", entity,
                period.periodMonth.plusMonths(1)).firstResult();
        if (next == null) {
            next = new Period();
            next.entity = entity;
            next.periodMonth = period.periodMonth.plusMonths(1);
            next.state = PeriodState.OPEN;
            next.setCreatedAt(clock.now());
            next.persist();
        }
        return next;
    }

    /**
     * Writes an adjustment line on an account.
     *
     * @param adjustment the adjustment
     * @param account    the account
     * @param amount     the signed amount
     * @param nature     the line nature
     * @return the written line
     */
    private CommissionLine writeLine(Adjustment adjustment, DistributorAccount account, BigDecimal amount,
                                     LineNature nature) {
        CommissionLine line = new CommissionLine();
        line.entity = adjustment.entity;
        line.period = adjustment.period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = amount;
        line.attachmentMonth = adjustment.period.periodMonth;
        line.explanation = "{\"family\":\"" + nature.name() + "\",\"adjustment\":" + adjustment.getId()
                + ",\"reason\":\"" + adjustment.reason + "\"}";
        line.setCreatedAt(clock.now());
        line.persist();
        return line;
    }

    /**
     * Imports several submitted adjustments at once (REG-04).
     *
     * @param adjustments the adjustments to create
     * @return the number created
     */
    @Transactional
    public int importBulk(List<Adjustment> adjustments) {
        int count = 0;
        for (Adjustment adjustment : adjustments) {
            create(adjustment.entity, adjustment.period, adjustment.adjustmentType, adjustment.amount,
                    adjustment.account, adjustment.reason, adjustment.attachmentRef,
                    Boolean.TRUE.equals(adjustment.autoReverse), adjustment.correctedLine,
                    adjustment.counterpartAccount, adjustment.getCreatedBy());
            count++;
        }
        return count;
    }

    /**
     * Records a distributor's claim and links it to its contested lines (REG-06).
     *
     * @param entity    the entity
     * @param distributor the claiming distributor
     * @param subject   the claim subject
     * @param contested the contested lines
     * @return the opened claim
     */
    @Transactional
    public Claim recordClaim(LegalEntity entity, Distributor distributor, String subject,
                             List<CommissionLine> contested) {
        Claim claim = new Claim();
        claim.entity = entity;
        claim.distributor = distributor;
        claim.subject = subject;
        claim.status = ClaimStatus.OPEN;
        claim.receivedAt = clock.now();
        claim.setCreatedAt(clock.now());
        claim.persist();
        for (CommissionLine line : contested) {
            ClaimLine claimLine = new ClaimLine();
            claimLine.claim = claim;
            claimLine.commissionLine = line;
            claimLine.persist();
        }
        return claim;
    }

    /**
     * Closes a claim with its resolution (REG-06).
     *
     * @param claimId    the claim id
     * @param resolution the resolution text
     * @return the closed claim
     */
    @Transactional
    public Claim resolveClaim(Long claimId, String resolution) {
        Claim claim = Claim.findById(claimId);
        if (claim == null) {
            throw new ResourceNotFoundException("Réclamation inconnue : " + claimId);
        }
        claim.status = ClaimStatus.CLOSED;
        claim.resolution = resolution;
        claim.closedAt = clock.now();
        return claim;
    }

    /**
     * Reads the entity's second-level approval threshold, or null when none is set.
     *
     * @param entity the entity
     * @return the threshold, or null
     */
    private static BigDecimal threshold(LegalEntity entity) {
        EntityParameter parameter = EntityParameter.find("entity = ?1 and paramKey = ?2", entity, THRESHOLD_KEY)
                .firstResult();
        return parameter == null ? null : new BigDecimal(parameter.paramValue);
    }
}
