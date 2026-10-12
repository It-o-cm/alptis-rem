package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AuditEvent;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.ReplacementOrder;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Read side of the distributor referentials for the search and hierarchy screens (REF-02, REF-03,
 * REP-03). A distributor is shared across entities; it is visible to a user when it has a status in
 * one of the user's entities, and its production codes are partitioned by entity.
 */
@ApplicationScoped
public class ReferentialQueryService {

    private final EntityScope scope;

    private final AccountService accounts;

    /**
     * Creates the service.
     *
     * @param scope    the entity partition
     * @param accounts the account service used to read a distributor's balances for the fiche
     */
    @Inject
    public ReferentialQueryService(EntityScope scope, AccountService accounts) {
        this.scope = scope;
        this.accounts = accounts;
    }

    /**
     * Searches distributors by partner code or legal name, within the user's entities.
     *
     * @param query the case-insensitive search term, possibly empty
     * @param page  the zero-based page index
     * @param size  the page size
     * @return a page of distributor views
     */
    @Transactional
    public PageResult<DistributorView> searchDistributors(String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        String like = "%" + (query == null ? "" : query.toLowerCase()) + "%";
        PanacheQuery<Distributor> q;
        if (scope.unrestricted()) {
            q = Distributor.find("lower(partnerCode) like ?1 or lower(legalName) like ?1 order by partnerCode", like);
        } else {
            q = Distributor.find("(lower(partnerCode) like ?1 or lower(legalName) like ?1) and id in "
                    + "(select de.distributor.id from DistributorEntity de where de.entity.code in ?2) order by partnerCode",
                    like, scope.visibleCodes());
        }
        long total = q.count();
        List<DistributorView> items = q.page(Page.of(safePage, safeSize)).<Distributor>list().stream()
                .map(this::toView).toList();
        return new PageResult<>(items, safePage, safeSize, total);
    }

    /**
     * Returns one distributor by its partner code, with its per-entity statuses and production codes.
     *
     * @param partnerCode the partner code
     * @return the distributor detail view
     * @throws ResourceNotFoundException if the distributor is unknown or out of scope
     */
    @Transactional
    public DistributorDetail distributor(String partnerCode) {
        Distributor distributor = Distributor.find("partnerCode", partnerCode).firstResult();
        if (distributor == null || !visible(distributor)) {
            throw new ResourceNotFoundException("Distributeur introuvable : " + partnerCode);
        }
        List<StatusView> statuses = DistributorEntity.<DistributorEntity>list("distributor", distributor).stream()
                .filter(de -> scope.canSee(de.entity.code))
                .map(de -> new StatusView(de.entity.code, de.status.name(), string(de.validFrom), string(de.validTo)))
                .toList();
        List<ProductionCodeView> codes = ProductionCode.<ProductionCode>list("distributor", distributor).stream()
                .filter(pc -> scope.canSee(pc.entity.code))
                .map(pc -> new ProductionCodeView(pc.entity.code, pc.code, pc.label,
                        pc.status == null ? null : pc.status.name()))
                .toList();
        List<AgreementView> agreements = Agreement.<Agreement>list("distributor", distributor).stream()
                .filter(a -> scope.canSee(a.entity.code))
                .map(a -> new AgreementView(a.entity.code, a.agreementType.name(), a.reference,
                        string(a.validFrom), string(a.validTo),
                        a.signatureStatus == null ? null : a.signatureStatus.name()))
                .toList();
        List<EnrollmentView> enrollments = Enrollment.<Enrollment>list("distributor", distributor).stream()
                .filter(e -> scope.canSee(e.rule.plan.entity.code))
                .map(e -> new EnrollmentView(e.rule.plan.entity.code, e.rule.code, e.rule.label,
                        e.status == null ? null : e.status.name(), string(e.validFrom), string(e.validTo)))
                .toList();
        List<QualityIndicatorView> indicators = QualityIndicator.<QualityIndicator>list(
                        "distributor = ?1 order by periodTo desc", distributor).stream()
                .filter(q -> scope.canSee(q.entity.code))
                .map(q -> new QualityIndicatorView(q.entity.code, distributor.partnerCode,
                        q.indicatorType == null ? null : q.indicatorType.name(),
                        q.range == null ? null : q.range.code, string(q.periodTo),
                        q.value == null ? null : q.value.toPlainString()))
                .toList();
        List<AccountSummaryView> accountSummaries = DistributorEntity.<DistributorEntity>list("distributor", distributor)
                .stream()
                .filter(de -> scope.canSee(de.entity.code))
                .map(de -> de.entity)
                .distinct()
                .map(entity -> accountSummary(entity, distributor))
                .toList();
        List<HistoryEntryView> history = hierarchyHistory(distributor);
        return new DistributorDetail(distributor.partnerCode, distributor.legalName,
                distributor.group == null ? null : distributor.group.code,
                distributor.group == null ? null : distributor.group.label,
                distributor.distributorType == null ? null : distributor.distributorType.name(),
                statuses, codes, agreements, enrollments, indicators, accountSummaries, history);
    }

    /**
     * Reads the hierarchy history of a distributor (REF-03): the audit-journal entries recording a
     * change of its grouping or a reassignment of one of its production codes, most recent first,
     * within the caller's entity partition.
     *
     * @param distributor the distributor
     * @return the history entries
     */
    private List<HistoryEntryView> hierarchyHistory(Distributor distributor) {
        List<Long> codeIds = ProductionCode.<ProductionCode>list("distributor", distributor).stream()
                .map(ProductionCode::getId)
                .toList();
        List<AuditEvent> events = AuditEvent.<AuditEvent>list(
                "(objectType = ?1 and objectId = ?2) or (objectType = ?3 and objectId in ?4) order by occurredAt desc",
                "Distributor", distributor.getId(), "ProductionCode", codeIds.isEmpty() ? List.of(-1L) : codeIds);
        return events.stream()
                .filter(e -> e.entity == null || scope.canSee(e.entity.code))
                .map(e -> new HistoryEntryView(e.occurredAt.toString(), e.userId, e.action,
                        e.entity == null ? null : e.entity.code, e.beforeValue, e.afterValue))
                .toList();
    }

    /**
     * Builds the account summary of a distributor in one entity: its running balance history and the
     * current balance (the last point of the series, or zero when empty).
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @return the account summary view
     */
    private AccountSummaryView accountSummary(LegalEntity entity, Distributor distributor) {
        List<AccountService.MonthBalance> series = accounts.balanceSeries(entity, distributor);
        List<MonthBalanceView> history = series.stream()
                .map(mb -> new MonthBalanceView(string(mb.month()), mb.balance().toPlainString()))
                .toList();
        String balance = series.isEmpty()
                ? java.math.BigDecimal.ZERO.toPlainString()
                : series.get(series.size() - 1).balance().toPlainString();
        return new AccountSummaryView(entity.code, balance, history);
    }

    /**
     * Tells whether a distributor is visible to the user (has a status in one of its entities).
     *
     * @param distributor the distributor
     * @return {@code true} if visible
     */
    private boolean visible(Distributor distributor) {
        if (scope.unrestricted()) {
            return true;
        }
        return DistributorEntity.count("distributor = ?1 and entity.code in ?2", distributor, scope.visibleCodes()) > 0;
    }

    /**
     * Builds the summary view of a distributor.
     *
     * @param distributor the distributor
     * @return its view
     */
    private DistributorView toView(Distributor distributor) {
        return new DistributorView(distributor.partnerCode, distributor.legalName,
                distributor.distributorType == null ? null : distributor.distributorType.name(),
                distributor.group == null ? null : distributor.group.code);
    }

    /**
     * Searches contracts by number or policyholder name within the user's entities, and returns
     * each with its coverages (section 5.2).
     *
     * @param query the case-insensitive search term, possibly empty
     * @param page  the zero-based page index
     * @param size  the page size
     * @return a page of contract views
     */
    @Transactional
    public PageResult<ContractView> searchContracts(String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        String like = "%" + (query == null ? "" : query.toLowerCase()) + "%";
        PanacheQuery<Contract> q = scope.unrestricted()
                ? Contract.find("lower(contractNumber) like ?1 order by contractNumber", like)
                : Contract.find("lower(contractNumber) like ?1 and entity.code in ?2 order by contractNumber",
                        like, scope.visibleCodes());
        long total = q.count();
        List<ContractView> items = q.page(Page.of(safePage, safeSize)).<Contract>list().stream()
                .map(ReferentialQueryService::toContractView).toList();
        return new PageResult<>(items, safePage, safeSize, total);
    }

    /**
     * Builds the view of a contract with its coverages.
     *
     * @param contract the contract
     * @return its view
     */
    private static ContractView toContractView(Contract contract) {
        List<CoverageView> coverages = Coverage.<Coverage>list("contract", contract).stream()
                .map(c -> new CoverageView(c.coverageCode, c.status == null ? null : c.status.name(),
                        c.annualPremium == null ? null : c.annualPremium.toPlainString()))
                .toList();
        return new ContractView(contract.entity.code, contract.contractNumber, contract.sourceSystem,
                contract.status == null ? null : contract.status.name(), coverages);
    }

    /**
     * Lists the products of the user's entities (REF-01).
     *
     * @return the product views
     */
    @Transactional
    public List<ProductView> products() {
        List<Product> products = scope.unrestricted()
                ? Product.listAll()
                : Product.list("entity.code in ?1", scope.visibleCodes());
        return products.stream()
                .map(p -> new ProductView(p.entity.code, p.code, p.label,
                        p.range == null ? null : p.range.code,
                        p.insurer == null ? null : p.insurer.code,
                        Boolean.TRUE.equals(p.commissionable)))
                .toList();
    }

    /**
     * Lists the quality indicators of the user's entities (REF-07), most recent period first.
     *
     * @return the quality-indicator views
     */
    @Transactional
    public List<QualityIndicatorView> qualityIndicators() {
        List<QualityIndicator> indicators = scope.unrestricted()
                ? QualityIndicator.list("order by periodTo desc")
                : QualityIndicator.list("entity.code in ?1 order by periodTo desc", scope.visibleCodes());
        return indicators.stream()
                .map(q -> new QualityIndicatorView(q.entity.code,
                        q.distributor == null ? null : q.distributor.partnerCode,
                        q.indicatorType == null ? null : q.indicatorType.name(),
                        q.range == null ? null : q.range.code,
                        string(q.periodTo),
                        q.value == null ? null : q.value.toPlainString()))
                .toList();
    }

    /**
     * Lists the bank accounts of the user's entities (REF-05).
     *
     * @return the bank-account views
     */
    @Transactional
    public List<BankAccountView> bankAccounts() {
        List<BankAccount> accounts = scope.unrestricted()
                ? BankAccount.list("order by validFrom desc")
                : BankAccount.list("entity.code in ?1 order by validFrom desc", scope.visibleCodes());
        return accounts.stream()
                .map(b -> new BankAccountView(b.entity.code,
                        b.holderType == null ? null : b.holderType.name(),
                        b.holderName, maskedIban(b.iban), b.bic, string(b.validFrom), string(b.validTo)))
                .toList();
    }

    /**
     * Lists the portfolio transfers of the user's entities (REF-09), most recent effect first.
     *
     * @return the transfer views
     */
    @Transactional
    public List<TransferView> portfolioTransfers() {
        List<PortfolioTransfer> transfers = scope.unrestricted()
                ? PortfolioTransfer.list("order by effectDate desc")
                : PortfolioTransfer.list("entity.code in ?1 order by effectDate desc", scope.visibleCodes());
        return transfers.stream()
                .map(t -> new TransferView(t.entity.code,
                        t.fromDistributor == null ? null : t.fromDistributor.partnerCode,
                        t.toDistributor == null ? null : t.toDistributor.partnerCode,
                        string(t.effectDate),
                        t.advanceDebtHandling == null ? null : t.advanceDebtHandling.name(),
                        t.status == null ? null : t.status.name()))
                .toList();
    }

    /**
     * Lists the replacement orders of the user's entities (REF-09), most recent effect first.
     *
     * @return the replacement-order views
     */
    @Transactional
    public List<ReplacementOrderView> replacementOrders() {
        String where = scope.unrestricted()
                ? "order by effectDate desc"
                : "contract.entity.code in ?1 order by effectDate desc";
        List<ReplacementOrder> orders = scope.unrestricted()
                ? ReplacementOrder.list(where)
                : ReplacementOrder.list(where, scope.visibleCodes());
        return orders.stream()
                .map(o -> new ReplacementOrderView(
                        o.contract == null ? null : o.contract.contractNumber,
                        o.fromProductionCode == null ? null : o.fromProductionCode.code,
                        o.toProductionCode == null ? null : o.toProductionCode.code,
                        string(o.receivedDate), string(o.effectDate)))
                .toList();
    }

    /**
     * Lists the payment rules of the user's entities (REF-06), with their beneficiary shares.
     *
     * @return the payment-rule views
     */
    @Transactional
    public List<PayeeRuleView> payeeRules() {
        List<PayeeRule> rules = scope.unrestricted()
                ? PayeeRule.list("order by validFrom desc")
                : PayeeRule.list("entity.code in ?1 order by validFrom desc", scope.visibleCodes());
        return rules.stream()
                .map(r -> new PayeeRuleView(r.entity.code,
                        r.productionCode == null ? null : r.productionCode.code,
                        r.distributor == null ? null : r.distributor.partnerCode,
                        r.range == null ? null : r.range.code,
                        r.paymentFrequency == null ? null : r.paymentFrequency.name(),
                        string(r.validFrom), string(r.validTo),
                        PayeeRuleShare.<PayeeRuleShare>list("payeeRule = ?1", r).stream()
                                .map(s -> new ShareView(s.payeeType == null ? null : s.payeeType.name(),
                                        payeeCode(s.payeeType, s.payeeId),
                                        s.share == null ? null : s.share.toPlainString()))
                                .toList()))
                .toList();
    }

    /**
     * Lists the external-code correspondences of the user's entities (REF-04).
     *
     * @return the external-code views
     */
    @Transactional
    public List<ExternalCodeView> externalCodes() {
        List<ExternalCode> codes = ExternalCode.list("order by sourceSystem, externalCode");
        return codes.stream()
                .filter(c -> scope.unrestricted() || internalEntityCode(c) == null
                        || scope.visibleCodes().contains(internalEntityCode(c)))
                .map(c -> new ExternalCodeView(c.objectType, payeeCode(parseType(c.objectType), c.objectId),
                        c.sourceSystem, c.externalCode))
                .toList();
    }

    /**
     * Lists the distributor groupings with their head and member count (CPT-02, REF-03).
     *
     * @return the grouping views
     */
    @Transactional
    public List<GroupView> groups() {
        List<GroupView> views = new java.util.ArrayList<>();
        for (DistributorGroup group : DistributorGroup.<DistributorGroup>list("order by code")) {
            views.add(groupView(group));
        }
        return views;
    }

    /**
     * Returns a grouping with its members and each member's production codes (CPT-02, REF-03).
     *
     * @param code the grouping code
     * @return the grouping detail
     * @throws ResourceNotFoundException when the grouping is unknown
     */
    @Transactional
    public GroupDetail groupDetail(String code) {
        DistributorGroup group = DistributorGroup.<DistributorGroup>find("code", code).firstResult();
        if (group == null) {
            throw new ResourceNotFoundException("Groupement inconnu : " + code);
        }
        List<MemberView> members = new java.util.ArrayList<>();
        for (Distributor member : Distributor.<Distributor>list("group = ?1 order by partnerCode", group)) {
            List<String> codes = ProductionCode.<ProductionCode>list("distributor = ?1 order by code", member)
                    .stream().map(pc -> pc.code).toList();
            members.add(new MemberView(member.partnerCode, member.legalName, codes));
        }
        return new GroupDetail(groupView(group), members);
    }

    /**
     * Maps a grouping to its view.
     *
     * @param group the grouping
     * @return the view
     */
    private static GroupView groupView(DistributorGroup group) {
        return new GroupView(group.code, group.label,
                group.headDistributor == null ? null : group.headDistributor.partnerCode,
                group.headDistributor == null ? null : group.headDistributor.legalName,
                Distributor.count("group = ?1", group));
    }

    /**
     * Resolves a payee reference back to its business code for display.
     *
     * @param type the payee type
     * @param id   the technical identifier
     * @return the business code, or null if unknown
     */
    private static String payeeCode(PayeeType type, Long id) {
        if (type == null || id == null) {
            return null;
        }
        return switch (type) {
            case PRODUCTION_CODE -> {
                ProductionCode pc = ProductionCode.findById(id);
                yield pc == null ? null : pc.code;
            }
            case DISTRIBUTOR -> {
                Distributor d = Distributor.findById(id);
                yield d == null ? null : d.partnerCode;
            }
            case GROUP -> {
                DistributorGroup g = DistributorGroup.findById(id);
                yield g == null ? null : g.code;
            }
        };
    }

    /**
     * Finds the entity code owning an external-coded object, used to apply the entity partition.
     * A distributor or group is shared, so it is visible to every entity; only a production code is
     * partitioned.
     *
     * @param code the external code
     * @return the owning entity code, or null when the object is shared
     */
    private static String internalEntityCode(ExternalCode code) {
        if (!PayeeType.PRODUCTION_CODE.name().equals(code.objectType)) {
            return null;
        }
        ProductionCode pc = ProductionCode.findById(code.objectId);
        return pc == null || pc.entity == null ? null : pc.entity.code;
    }

    /**
     * Parses a payee type stored as a string, returning null when unrecognised.
     *
     * @param raw the raw value
     * @return the payee type, or null
     */
    private static PayeeType parseType(String raw) {
        try {
            return raw == null ? null : PayeeType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Masks an IBAN, keeping only its country prefix and last four characters.
     *
     * @param iban the IBAN
     * @return the masked IBAN
     */
    private static String maskedIban(String iban) {
        if (iban == null || iban.length() < 8) {
            return iban;
        }
        return iban.substring(0, 4) + "…" + iban.substring(iban.length() - 4);
    }

    /**
     * Formats a date as an ISO string, or {@code null} when the date is null.
     *
     * @param date the date, possibly null
     * @return the ISO string, or {@code null}
     */
    private static String string(java.time.LocalDate date) {
        return date == null ? null : date.toString();
    }

    /**
     * View of a product (REF-01).
     *
     * @param entityCode     the entity code
     * @param code           the product code
     * @param label          the product label
     * @param rangeCode      the range code, if any
     * @param insurerCode    the insurer code, if any
     * @param commissionable whether the product gives rise to commission
     */
    public record ProductView(String entityCode, String code, String label, String rangeCode, String insurerCode,
                              boolean commissionable) {
    }

    /**
     * View of a distributor grouping (CPT-02, REF-03).
     *
     * @param code         the grouping code
     * @param label        the grouping label
     * @param headCode     the head distributor's partner code, possibly null
     * @param headName     the head distributor's name, possibly null
     * @param memberCount  the number of members
     */
    public record GroupView(String code, String label, String headCode, String headName, long memberCount) {
    }

    /**
     * Detail of a grouping with its members (CPT-02).
     *
     * @param group   the grouping view
     * @param members the member distributors
     */
    public record GroupDetail(GroupView group, List<MemberView> members) {
    }

    /**
     * One member of a grouping.
     *
     * @param partnerCode     the member's partner code
     * @param legalName       the member's legal name
     * @param productionCodes the member's production codes
     */
    public record MemberView(String partnerCode, String legalName, List<String> productionCodes) {
    }

    /**
     * View of a quality indicator (REF-07).
     *
     * @param entityCode    the entity code
     * @param partnerCode   the distributor partner code
     * @param indicatorType the indicator type
     * @param rangeCode     the measured range, if any
     * @param periodTo      the end of the measurement period
     * @param value         the value, as a decimal fraction
     */
    public record QualityIndicatorView(String entityCode, String partnerCode, String indicatorType, String rangeCode,
                                       String periodTo, String value) {
    }

    /**
     * View of a portfolio transfer (REF-09).
     *
     * @param entityCode          the entity code
     * @param fromPartnerCode     the transferring distributor
     * @param toPartnerCode       the receiving distributor
     * @param effectDate          the effect date
     * @param advanceDebtHandling how the discount receivable is handled
     * @param status              the transfer status
     */
    public record TransferView(String entityCode, String fromPartnerCode, String toPartnerCode, String effectDate,
                               String advanceDebtHandling, String status) {
    }

    /**
     * View of a replacement order (REF-09).
     *
     * @param contractNumber     the contract number
     * @param fromProductionCode the former production code
     * @param toProductionCode   the new production code
     * @param receivedDate       the reception date
     * @param effectDate         the effect date
     */
    public record ReplacementOrderView(String contractNumber, String fromProductionCode, String toProductionCode,
                                       String receivedDate, String effectDate) {
    }

    /**
     * View of a payment rule (REF-06) with its beneficiary shares.
     *
     * @param entityCode     the entity code
     * @param productionCode the targeted production code, if any
     * @param partnerCode    the targeted distributor, if any
     * @param rangeCode      the targeted range, if any
     * @param frequency      the payment frequency
     * @param validFrom      the validity start, if any
     * @param validTo        the validity end, if any
     * @param shares         the beneficiary shares (summing to 1)
     */
    public record PayeeRuleView(String entityCode, String productionCode, String partnerCode, String rangeCode,
                                String frequency, String validFrom, String validTo, List<ShareView> shares) {
    }

    /**
     * View of one beneficiary share of a payment rule.
     *
     * @param payeeType the beneficiary type
     * @param payeeCode the beneficiary business code
     * @param share     the share as a decimal fraction
     */
    public record ShareView(String payeeType, String payeeCode, String share) {
    }

    /**
     * View of an external-code correspondence (REF-04).
     *
     * @param objectType   the internal object type
     * @param internalCode the internal object business code
     * @param sourceSystem the source system
     * @param externalCode the identifier in that source system
     */
    public record ExternalCodeView(String objectType, String internalCode, String sourceSystem, String externalCode) {
    }

    /**
     * View of a bank account (REF-05), with a masked IBAN.
     *
     * @param entityCode the entity code
     * @param holderType the holder type
     * @param holderName the holder name
     * @param iban       the masked IBAN
     * @param bic        the BIC
     * @param validFrom  the validity start
     * @param validTo    the validity end, if any
     */
    public record BankAccountView(String entityCode, String holderType, String holderName, String iban, String bic,
                                  String validFrom, String validTo) {
    }

    /**
     * View of a contract and its coverages.
     *
     * @param entityCode     the entity code
     * @param contractNumber the contract number
     * @param sourceSystem   the source system
     * @param status         the contract status
     * @param coverages      the coverages
     */
    public record ContractView(String entityCode, String contractNumber, String sourceSystem, String status,
                               List<CoverageView> coverages) {
    }

    /**
     * View of a coverage.
     *
     * @param coverageCode  the coverage code
     * @param status        the coverage status
     * @param annualPremium the annualised premium
     */
    public record CoverageView(String coverageCode, String status, String annualPremium) {
    }

    /**
     * Summary view of a distributor.
     *
     * @param partnerCode     the partner code
     * @param legalName       the legal name
     * @param distributorType the distributor type
     * @param groupCode       the group code, if any
     */
    public record DistributorView(String partnerCode, String legalName, String distributorType, String groupCode) {
    }

    /**
     * Consolidated fiche of a distributor (REF-10, CPT-01, CPT-06): identity and hierarchy, per-entity
     * statuses, production codes, reserved-rule agreements and enrollments, quality indicators and the
     * account balances with their month-by-month history.
     *
     * @param partnerCode     the partner code
     * @param legalName       the legal name
     * @param groupCode       the group code, if any
     * @param groupLabel      the group label, if any
     * @param distributorType the distributor type, if any
     * @param statuses        the per-entity statuses
     * @param productionCodes the production codes
     * @param agreements      the reserved-rule and discount agreements
     * @param enrollments     the reserved-rule enrollments
     * @param indicators      the quality indicators, most recent first
     * @param accounts        the per-entity account balances with their history
     * @param history         the hierarchy history (groupings and production-code reassignments)
     */
    public record DistributorDetail(String partnerCode, String legalName, String groupCode, String groupLabel,
                                    String distributorType, List<StatusView> statuses,
                                    List<ProductionCodeView> productionCodes, List<AgreementView> agreements,
                                    List<EnrollmentView> enrollments, List<QualityIndicatorView> indicators,
                                    List<AccountSummaryView> accounts, List<HistoryEntryView> history) {
    }

    /**
     * One hierarchy-history entry of a distributor (REF-03, ADM-04): a dated, attributed change of its
     * grouping or a production-code reassignment, with the before and after values.
     *
     * @param occurredAt the moment of the change (ISO)
     * @param userId     the author
     * @param action     the action code
     * @param entityCode the entity code, possibly null
     * @param before     the value before, possibly null
     * @param after      the value after, possibly null
     */
    public record HistoryEntryView(String occurredAt, String userId, String action, String entityCode,
                                   String before, String after) {
    }

    /**
     * A reserved-rule or discount agreement founding a distributor's rights (section 5.4).
     *
     * @param entityCode      the entity code
     * @param agreementType   the agreement type
     * @param reference       the agreement reference
     * @param validFrom       the validity start
     * @param validTo         the validity end, if any
     * @param signatureStatus the signature status, if any
     */
    public record AgreementView(String entityCode, String agreementType, String reference,
                                String validFrom, String validTo, String signatureStatus) {
    }

    /**
     * A distributor's enrollment to a reserved rule (section 5.4).
     *
     * @param entityCode the entity code of the rule
     * @param ruleCode   the rule code
     * @param ruleLabel  the rule label
     * @param status     the enrollment status, if any
     * @param validFrom  the validity start
     * @param validTo    the validity end, if any
     */
    public record EnrollmentView(String entityCode, String ruleCode, String ruleLabel, String status,
                                 String validFrom, String validTo) {
    }

    /**
     * A distributor's account balance in one entity, with its month-by-month history (CPT-06).
     *
     * @param entityCode the entity code
     * @param balance    the current running balance
     * @param history    the running balance per month, ascending
     */
    public record AccountSummaryView(String entityCode, String balance, List<MonthBalanceView> history) {
    }

    /**
     * One month and the running account balance at the end of it.
     *
     * @param month   the month
     * @param balance the running balance
     */
    public record MonthBalanceView(String month, String balance) {
    }

    /**
     * Per-entity status of a distributor.
     *
     * @param entityCode the entity code
     * @param status     the status
     * @param validFrom  the validity start
     * @param validTo    the validity end, if any
     */
    public record StatusView(String entityCode, String status, String validFrom, String validTo) {
    }

    /**
     * A production code of a distributor.
     *
     * @param entityCode the entity code
     * @param code       the production code
     * @param label      the label
     * @param status     the status
     */
    public record ProductionCodeView(String entityCode, String code, String label, String status) {
    }
}
