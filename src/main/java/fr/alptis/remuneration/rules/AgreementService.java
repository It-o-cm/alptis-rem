package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementProduct;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.EnrollmentStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/**
 * Manages the agreements that found rules (section 5.4), the discount conventions (PAR-09) and the
 * enrollments to reserved rules (PAR-10). An enrollment becomes active only once its agreement is
 * recorded as signed; otherwise it waits for signature. Everything is partitioned by entity and
 * traced.
 */
@ApplicationScoped
public class AgreementService {

    /** Maximum validity span of a discount convention, in months (section 5.4). */
    private static final int ADVANCE_MAX_MONTHS = 12;

    private final EntityScope scope;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param scope       the entity partition
     * @param currentUser the current user
     * @param audit       the audit trail
     * @param clock       the application date/time provider
     */
    @Inject
    public AgreementService(EntityScope scope, CurrentUser currentUser, AuditService audit, DateTimeProvider clock) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Lists the agreements of the user's entities.
     *
     * @return the agreement views
     */
    @Transactional
    public List<AgreementView> listAgreements() {
        List<Agreement> agreements = scope.unrestricted()
                ? Agreement.list("order by validFrom desc")
                : Agreement.list("entity.code in ?1 order by validFrom desc", scope.visibleCodes());
        return agreements.stream().map(AgreementService::toAgreementView).toList();
    }

    /**
     * Records an agreement.
     *
     * @param entityCode  the entity code
     * @param type        the agreement type
     * @param partnerCode the distributor partner code, or null
     * @param groupCode   the group code, or null
     * @param reference   the agreement reference
     * @param validFrom   the validity start
     * @param validTo     the validity end, or null
     * @param signed      whether the agreement is recorded as signed
     * @return the created agreement view
     */
    @Transactional
    public AgreementView createAgreement(String entityCode, AgreementType type, String partnerCode, String groupCode,
                                         String reference, LocalDate validFrom, LocalDate validTo, boolean signed) {
        LegalEntity entity = requireEntity(entityCode);
        Agreement agreement = new Agreement();
        agreement.entity = entity;
        agreement.agreementType = type;
        agreement.distributor = distributor(partnerCode);
        agreement.group = group(groupCode);
        agreement.reference = reference;
        agreement.validFrom = validFrom;
        agreement.validTo = validTo;
        agreement.signatureStatus = signed ? SignatureStatus.SIGNED : SignatureStatus.NOT_SIGNED;
        agreement.signedAt = signed ? clock.now() : null;
        agreement.setCreatedAt(clock.now());
        agreement.persist();
        audit.record(currentUser.username(), entity, "CREATE_AGREEMENT", "Agreement", agreement.getId(), null,
                "{\"reference\":\"" + reference + "\"}");
        return toAgreementView(agreement);
    }

    /**
     * Lists the discount conventions of the user's entities.
     *
     * @return the discount-convention views
     */
    @Transactional
    public List<AdvanceView> listAdvanceAgreements() {
        List<AdvanceAgreement> agreements = scope.unrestricted()
                ? AdvanceAgreement.list("order by validFrom desc")
                : AdvanceAgreement.list("entity.code in ?1 order by validFrom desc", scope.visibleCodes());
        return agreements.stream().map(AgreementService::toAdvanceView).toList();
    }

    /**
     * Returns the fiche of a distributor's discount convention (PAR-09, CPT-08, §6.4): its parameters,
     * the counters cumulated over its discounted guarantees, each guarantee's advanced and offset
     * amounts, and a rupture simulation (the outstanding discount debt recovered if it were broken
     * today).
     *
     * @param entityCode  the entity code
     * @param partnerCode the distributor partner code
     * @return the convention detail
     * @throws ResourceNotFoundException if no convention is found for the distributor in scope
     */
    @Transactional
    public AdvanceDetail advanceDetail(String entityCode, String partnerCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        AdvanceAgreement agreement = AdvanceAgreement.<AdvanceAgreement>find(
                "entity.code = ?1 and distributor.partnerCode = ?2 order by validFrom desc", entityCode, partnerCode)
                .firstResult();
        if (agreement == null) {
            throw new ResourceNotFoundException("Convention d'escompte introuvable : " + partnerCode);
        }
        List<String> products = AdvanceAgreementProduct.<AdvanceAgreementProduct>list("advanceAgreement", agreement)
                .stream().map(p -> p.product == null ? "—" : p.product.code).toList();
        Cumul cumul = cumul(agreement);
        return new AdvanceDetail(toAdvanceView(agreement), agreement.unpaidRankThreshold, agreement.split, products,
                cumul.advanced(), cumul.offset(), cumul.outstanding(), cumul.outstanding(), cumul.positions());
    }

    /**
     * Produces the discount synthesis by convention for an entity (BRD-09): each convention with its
     * advanced, offset and outstanding amounts and the detail by discounted guarantee.
     *
     * @param entityCode the entity code
     * @return the per-convention synthesis
     * @throws ResourceNotFoundException if the entity is out of the caller's partition
     */
    @Transactional
    public List<ConventionSummary> discountSummary(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        List<ConventionSummary> summaries = new java.util.ArrayList<>();
        for (AdvanceAgreement agreement : AdvanceAgreement.<AdvanceAgreement>list(
                "entity.code = ?1 order by validFrom desc", entityCode)) {
            Cumul cumul = cumul(agreement);
            summaries.add(new ConventionSummary(
                    agreement.distributor == null ? null : agreement.distributor.partnerCode,
                    agreement.validFrom == null ? null : agreement.validFrom.toString(),
                    agreement.validTo == null ? null : agreement.validTo.toString(),
                    cumul.advanced(), cumul.offset(), cumul.outstanding(), cumul.positions()));
        }
        return summaries;
    }

    /**
     * Cumulates a convention's discounted positions: the advanced and offset totals, the outstanding
     * debt (advanced − offset − adjustments), and the per-guarantee detail.
     *
     * @param agreement the convention
     * @return the cumulated amounts and positions
     */
    private static Cumul cumul(AdvanceAgreement agreement) {
        List<PositionView> positions = new java.util.ArrayList<>();
        BigDecimal totalAdvanced = BigDecimal.ZERO;
        BigDecimal totalOffset = BigDecimal.ZERO;
        BigDecimal totalAdjustment = BigDecimal.ZERO;
        for (AdvancePosition position : AdvancePosition.<AdvancePosition>list("advanceAgreement", agreement)) {
            BigDecimal advanced = nz(position.advancedAcquisition).add(nz(position.advancedServicing))
                    .add(nz(position.advancedLinear));
            BigDecimal offset = nz(position.offsetAcquisition).add(nz(position.offsetServicing))
                    .add(nz(position.offsetLinear));
            totalAdvanced = totalAdvanced.add(advanced);
            totalOffset = totalOffset.add(offset);
            totalAdjustment = totalAdjustment.add(nz(position.adjustmentAmount));
            positions.add(new PositionView(position.coverage == null ? "—" : position.coverage.coverageCode,
                    position.status == null ? null : position.status.name(), advanced, offset));
        }
        return new Cumul(positions, totalAdvanced, totalOffset,
                totalAdvanced.subtract(totalOffset).subtract(totalAdjustment));
    }

    /**
     * A convention's cumulated discount amounts and per-guarantee detail.
     *
     * @param positions   the per-guarantee detail
     * @param advanced    the total advanced
     * @param offset      the total offset
     * @param outstanding the outstanding debt
     */
    private record Cumul(List<PositionView> positions, BigDecimal advanced, BigDecimal offset,
                         BigDecimal outstanding) {
    }

    /**
     * Returns the value or zero when null.
     *
     * @param value the value, possibly null
     * @return the value, or zero
     */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Records a discount convention (PAR-09), checking the twelve-month validity limit.
     *
     * @param entityCode        the entity code
     * @param partnerCode       the distributor partner code
     * @param validFrom         the validity start
     * @param validTo           the validity end
     * @param anticipationDays  the anticipation in days
     * @param maxContracts      the cap on enrollments
     * @param unpaidRankThreshold the unpaid rank triggering the clawback
     * @return the created discount-convention view
     * @throws ValidationException if the validity exceeds twelve months or the distributor is unknown
     */
    @Transactional
    public AdvanceView createAdvanceAgreement(String entityCode, String partnerCode, LocalDate validFrom,
                                              LocalDate validTo, Integer anticipationDays, Integer maxContracts,
                                              Short unpaidRankThreshold) {
        LegalEntity entity = requireEntity(entityCode);
        Distributor distributor = distributor(partnerCode);
        if (distributor == null) {
            throw new ValidationException("Distributeur inconnu : " + partnerCode);
        }
        if (validFrom != null && validTo != null && validFrom.plusMonths(ADVANCE_MAX_MONTHS).isBefore(validTo)) {
            throw new ValidationException("Une convention d'escompte ne peut dépasser douze mois.");
        }
        AdvanceAgreement agreement = new AdvanceAgreement();
        agreement.entity = entity;
        agreement.distributor = distributor;
        agreement.validFrom = validFrom;
        agreement.validTo = validTo;
        agreement.anticipationDays = anticipationDays;
        agreement.maxContracts = maxContracts;
        agreement.unpaidRankThreshold = unpaidRankThreshold;
        agreement.status = AdvanceAgreementStatus.VALID;
        agreement.setCreatedAt(clock.now());
        agreement.persist();
        audit.record(currentUser.username(), entity, "CREATE_ADVANCE_AGREEMENT", "AdvanceAgreement",
                agreement.getId(), null, "{\"partner\":\"" + partnerCode + "\"}");
        return toAdvanceView(agreement);
    }

    /**
     * Lists the enrollments of a rule.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @return the enrollment views
     */
    @Transactional
    public List<EnrollmentView> listEnrollments(String entityCode, String planCode, String ruleCode) {
        Rule rule = requireRule(entityCode, planCode, ruleCode);
        return Enrollment.<Enrollment>list("rule = ?1 order by validFrom desc", rule).stream()
                .map(AgreementService::toEnrollmentView).toList();
    }

    /**
     * Enrolls a distributor or a group to a reserved rule; active only if the agreement is signed.
     *
     * @param entityCode   the entity code
     * @param planCode     the plan code
     * @param ruleCode     the rule code
     * @param partnerCode  the distributor partner code, or null
     * @param groupCode    the group code, or null
     * @param agreementRef the agreement reference
     * @param validFrom    the enrollment start
     * @return the created enrollment view
     */
    @Transactional
    public EnrollmentView enroll(String entityCode, String planCode, String ruleCode, String partnerCode,
                                 String groupCode, String agreementRef, LocalDate validFrom) {
        Rule rule = requireRule(entityCode, planCode, ruleCode);
        return enrollOne(rule, partnerCode, groupCode, agreementRef, validFrom);
    }

    /**
     * Enrolls many distributors to a reserved rule in one action (PAR-10, mass enrollment).
     *
     * @param entityCode   the entity code
     * @param planCode     the plan code
     * @param ruleCode     the rule code
     * @param partnerCodes the distributor partner codes
     * @param agreementRef the agreement reference
     * @param validFrom    the enrollment start
     * @return the created enrollment views
     */
    @Transactional
    public List<EnrollmentView> enrollMass(String entityCode, String planCode, String ruleCode,
                                           List<String> partnerCodes, String agreementRef, LocalDate validFrom) {
        Rule rule = requireRule(entityCode, planCode, ruleCode);
        return partnerCodes.stream()
                .map(code -> enrollOne(rule, code, null, agreementRef, validFrom))
                .toList();
    }

    /**
     * Creates one enrollment and sets its status from the agreement signature.
     *
     * @param rule         the reserved rule
     * @param partnerCode  the distributor partner code, or null
     * @param groupCode    the group code, or null
     * @param agreementRef the agreement reference
     * @param validFrom    the enrollment start
     * @return the created enrollment view
     */
    private EnrollmentView enrollOne(Rule rule, String partnerCode, String groupCode, String agreementRef,
                                     LocalDate validFrom) {
        Agreement agreement = Agreement.find("entity = ?1 and reference = ?2", rule.plan.entity, agreementRef)
                .firstResult();
        if (agreement == null) {
            throw new ResourceNotFoundException("Accord introuvable : " + agreementRef);
        }
        Distributor distributor = distributor(partnerCode);
        DistributorGroup group = group(groupCode);
        if (distributor == null && group == null) {
            throw new ValidationException("Une inscription vise un distributeur ou un groupement.");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.rule = rule;
        enrollment.distributor = distributor;
        enrollment.group = group;
        enrollment.agreement = agreement;
        enrollment.validFrom = validFrom;
        enrollment.status = agreement.signatureStatus == SignatureStatus.SIGNED
                ? EnrollmentStatus.ACTIVE : EnrollmentStatus.PENDING_SIGNATURE;
        enrollment.setCreatedAt(clock.now());
        enrollment.persist();
        audit.record(currentUser.username(), rule.plan.entity, "CREATE_ENROLLMENT", "Enrollment", enrollment.getId(),
                null, "{\"rule\":\"" + rule.code + "\",\"status\":\"" + enrollment.status.name() + "\"}");
        return toEnrollmentView(enrollment);
    }

    /**
     * Resolves an entity in the user's scope.
     *
     * @param entityCode the entity code
     * @return the entity
     * @throws ResourceNotFoundException if unknown or out of scope
     */
    private LegalEntity requireEntity(String entityCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null || !scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        return entity;
    }

    /**
     * Resolves a rule in the user's scope.
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @return the rule
     * @throws ResourceNotFoundException if unknown or out of scope
     */
    private Rule requireRule(String entityCode, String planCode, String ruleCode) {
        LegalEntity entity = requireEntity(entityCode);
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", entity, planCode).firstResult();
        if (plan == null) {
            throw new ResourceNotFoundException("Plan introuvable : " + planCode);
        }
        Rule rule = Rule.find("plan = ?1 and code = ?2", plan, ruleCode).firstResult();
        if (rule == null) {
            throw new ResourceNotFoundException("Règle introuvable : " + ruleCode);
        }
        return rule;
    }

    /**
     * Finds a distributor by partner code, or null when the code is null/empty or unknown.
     *
     * @param partnerCode the partner code, possibly null
     * @return the distributor, or null
     */
    private static Distributor distributor(String partnerCode) {
        return partnerCode == null || partnerCode.isEmpty() ? null
                : Distributor.find("partnerCode", partnerCode).firstResult();
    }

    /**
     * Finds a group by code, or null when the code is null/empty or unknown.
     *
     * @param groupCode the group code, possibly null
     * @return the group, or null
     */
    private static DistributorGroup group(String groupCode) {
        return groupCode == null || groupCode.isEmpty() ? null
                : DistributorGroup.find("code", groupCode).firstResult();
    }

    /**
     * Builds the view of an agreement.
     *
     * @param agreement the agreement
     * @return its view
     */
    private static AgreementView toAgreementView(Agreement agreement) {
        return new AgreementView(agreement.entity.code,
                agreement.agreementType == null ? null : agreement.agreementType.name(),
                agreement.distributor == null ? null : agreement.distributor.partnerCode,
                agreement.group == null ? null : agreement.group.code,
                agreement.reference,
                agreement.signatureStatus == null ? null : agreement.signatureStatus.name());
    }

    /**
     * Builds the view of a discount convention.
     *
     * @param agreement the discount convention
     * @return its view
     */
    private static AdvanceView toAdvanceView(AdvanceAgreement agreement) {
        return new AdvanceView(agreement.entity.code,
                agreement.distributor == null ? null : agreement.distributor.partnerCode,
                agreement.validFrom == null ? null : agreement.validFrom.toString(),
                agreement.validTo == null ? null : agreement.validTo.toString(),
                agreement.anticipationDays, agreement.maxContracts,
                agreement.status == null ? null : agreement.status.name());
    }

    /**
     * Builds the view of an enrollment.
     *
     * @param enrollment the enrollment
     * @return its view
     */
    private static EnrollmentView toEnrollmentView(Enrollment enrollment) {
        return new EnrollmentView(
                enrollment.distributor == null ? null : enrollment.distributor.partnerCode,
                enrollment.group == null ? null : enrollment.group.code,
                enrollment.agreement == null ? null : enrollment.agreement.reference,
                enrollment.status == null ? null : enrollment.status.name());
    }

    /**
     * View of an agreement.
     *
     * @param entityCode   the entity code
     * @param type         the agreement type
     * @param partnerCode  the distributor, if any
     * @param groupCode    the group, if any
     * @param reference    the reference
     * @param signature    the signature status
     */
    public record AgreementView(String entityCode, String type, String partnerCode, String groupCode,
                                String reference, String signature) {
    }

    /**
     * View of a discount convention.
     *
     * @param entityCode       the entity code
     * @param partnerCode      the distributor
     * @param validFrom        the validity start
     * @param validTo          the validity end
     * @param anticipationDays the anticipation in days
     * @param maxContracts     the cap on enrollments
     * @param status           the status
     */
    public record AdvanceView(String entityCode, String partnerCode, String validFrom, String validTo,
                              Integer anticipationDays, Integer maxContracts, String status) {
    }

    /**
     * Fiche of a discount convention (PAR-09, CPT-08, §6.4).
     *
     * @param convention          the convention parameters
     * @param unpaidRankThreshold the unpaid rank triggering the clawback
     * @param split               the split policy, if any
     * @param products            the covered product codes
     * @param totalAdvanced       the total advanced across its guarantees
     * @param totalOffset         the total offset (apuré) across its guarantees
     * @param outstanding         the outstanding discount debt (créance)
     * @param ruptureAmount       the debt recovered if the convention were broken today
     * @param positions           the discounted guarantees with their advanced and offset amounts
     */
    public record AdvanceDetail(AdvanceView convention, Short unpaidRankThreshold, String split, List<String> products,
                                BigDecimal totalAdvanced, BigDecimal totalOffset, BigDecimal outstanding,
                                BigDecimal ruptureAmount, List<PositionView> positions) {
    }

    /**
     * One discounted guarantee of a convention.
     *
     * @param coverageCode the guarantee code
     * @param status       the advance-position status, if any
     * @param advanced     the amount advanced on the guarantee
     * @param offset       the amount offset (apuré) on the guarantee
     */
    public record PositionView(String coverageCode, String status, BigDecimal advanced, BigDecimal offset) {
    }

    /**
     * One line of the discount synthesis by convention (BRD-09).
     *
     * @param partnerCode the distributor partner code
     * @param validFrom   the validity start
     * @param validTo     the validity end
     * @param advanced    the total advanced (versé)
     * @param offset      the total offset (apuré)
     * @param outstanding the outstanding debt (créance)
     * @param positions   the detail by discounted guarantee (par adhérent)
     */
    public record ConventionSummary(String partnerCode, String validFrom, String validTo, BigDecimal advanced,
                                    BigDecimal offset, BigDecimal outstanding, List<PositionView> positions) {
    }

    /**
     * View of an enrollment.
     *
     * @param partnerCode  the distributor, if any
     * @param groupCode    the group, if any
     * @param agreementRef the agreement reference
     * @param status       the enrollment status
     */
    public record EnrollmentView(String partnerCode, String groupCode, String agreementRef, String status) {
    }
}
