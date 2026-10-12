package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.AdvanceDebtHandling;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ContractStatus;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DistributorType;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.EnrollmentStatus;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentFrequency;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.ReplacementOrder;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.TransferStatus;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ReferentialQueryService} with hand-made entity scopes and a seeded dataset, covering
 * the restricted and unrestricted partitions and the optional-field branches of every view
 * (REF-01/02/03/04/05/06/07/09). Runs inside a rolled-back transaction so the shared instance is
 * left untouched.
 */
@QuarkusTest
class ReferentialQueryServiceTest {

    /** The real account service, used by the consolidated fiche for a distributor's balances. */
    @jakarta.inject.Inject
    fr.alptis.remuneration.accounts.AccountService accounts;

    /** Builds an entity scope for the given visible codes ({@code *} for unrestricted). */
    private static EntityScope scope(List<String> entities) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("user"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        return new EntityScope(new CurrentUser(identity));
    }

    /** Restricted service, seeing only Alptis. */
    private ReferentialQueryService restricted() {
        return new ReferentialQueryService(scope(List.of("ALPTIS_ASSURANCES")), accounts);
    }

    /** Unrestricted service, seeing every entity. */
    private ReferentialQueryService unrestricted() {
        return new ReferentialQueryService(scope(List.of("*")), accounts);
    }

    /** Returns the seeded Alptis entity. */
    private static LegalEntity alptis() {
        return LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
    }

    /**
     * Seeds a distributor with a group, a status in Alptis and two production codes (one with a
     * label and status, one bare).
     *
     * @return the created distributor
     */
    private Distributor seedDistributor() {
        DistributorGroup group = new DistributorGroup();
        group.code = "G1";
        group.label = "Groupe";
        group.persist();
        Distributor d = new Distributor();
        d.partnerCode = "P1";
        d.legalName = "Cabinet Un";
        d.distributorType = DistributorType.INDEPENDENT;
        d.group = group;
        d.persist();
        DistributorEntity link = new DistributorEntity();
        link.distributor = d;
        link.entity = alptis();
        link.validFrom = LocalDate.of(2026, 1, 1);
        link.status = PartnerStatus.ACTIVE;
        link.persist();
        ProductionCode pc1 = new ProductionCode();
        pc1.entity = alptis();
        pc1.code = "61723";
        pc1.distributor = d;
        pc1.label = "Lyon";
        pc1.status = PartnerStatus.ACTIVE;
        pc1.persist();
        ProductionCode pc2 = new ProductionCode();
        pc2.entity = alptis();
        pc2.code = "66117";
        pc2.distributor = d;
        pc2.persist();
        // A status and a production code in UNEP, invisible to an Alptis-restricted user.
        DistributorEntity unepLink = new DistributorEntity();
        unepLink.distributor = d;
        unepLink.entity = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        unepLink.validFrom = LocalDate.of(2026, 1, 1);
        unepLink.status = PartnerStatus.ACTIVE;
        unepLink.persist();
        ProductionCode pc3 = new ProductionCode();
        pc3.entity = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        pc3.code = "U-99";
        pc3.distributor = d;
        pc3.persist();
        return d;
    }

    /**
     * Searches distributors in both partitions and reads a distributor detail; covers the null
     * query, non-positive size and negative page branches.
     */
    @Test
    @TestTransaction
    void searches_distributors_and_reads_detail() {
        seedDistributor();
        // A bare distributor (no type, no group) matched by the search covers the null branches of
        // the summary view.
        Distributor bare = new Distributor();
        bare.partnerCode = "P0";
        bare.legalName = "Cabinet Zero";
        bare.persist();
        DistributorEntity link = new DistributorEntity();
        link.distributor = bare;
        link.entity = alptis();
        link.validFrom = LocalDate.of(2026, 1, 1);
        link.status = PartnerStatus.ACTIVE;
        link.persist();

        assertEquals(2, restricted().searchDistributors("p", 0, 20).total());
        assertEquals(2, unrestricted().searchDistributors(null, -1, 0).items().size());

        var detail = restricted().distributor("P1");
        assertEquals("G1", detail.groupCode());
        // The UNEP status and production code are filtered out for an Alptis-restricted user.
        assertEquals(1, detail.statuses().size());
        assertEquals(2, detail.productionCodes().size());
        // Unrestricted sees the UNEP status and code too.
        assertEquals(2, unrestricted().distributor("P1").statuses().size());
        assertEquals(3, unrestricted().distributor("P1").productionCodes().size());
    }

    /**
     * Reads the consolidated fiche: agreements, enrollments, quality indicators and the per-entity
     * account balances with their history, each partitioned by entity and with the optional-field
     * branches covered (set and unset signature, status, indicator fields; empty and non-empty
     * balance series; a bare group with no label).
     */
    @Test
    @TestTransaction
    void reads_the_consolidated_fiche() {
        Distributor d = seedDistributor();
        LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();

        // An Alptis agreement with a reference and a signature; a UNEP one with neither.
        Agreement signed = new Agreement();
        signed.entity = alptis();
        signed.distributor = d;
        signed.agreementType = AgreementType.PARTNERSHIP;
        signed.reference = "ACC-1";
        signed.validFrom = LocalDate.of(2026, 1, 1);
        signed.signatureStatus = SignatureStatus.NOT_SIGNED;
        signed.persist();
        Agreement bare = new Agreement();
        bare.entity = unep;
        bare.distributor = d;
        bare.agreementType = AgreementType.RCD_AMENDMENT;
        bare.persist();

        // An Alptis plan with a rule, enrolled with an active status; a UNEP plan rule with no status.
        Enrollment active = enroll(alptis(), "RCD-RULE", d, EnrollmentStatus.ACTIVE);
        Enrollment statusless = enroll(unep, "U-RULE", d, null);
        assertNull(statusless.status);

        // Two Alptis indicators (one full, one all-null) and one UNEP indicator.
        ProductRange range = new ProductRange();
        range.code = "SANTE";
        range.label = "Santé";
        range.persist();
        indicator(alptis(), d, QualityIndicatorType.COMPLAINT_RATE, range, new BigDecimal("0.10"));
        indicator(alptis(), d, null, null, null);
        indicator(unep, d, QualityIndicatorType.UNPAID_RATE, null, new BigDecimal("0.20"));

        // An Alptis account with one entry: a non-empty balance series. UNEP has none (empty → zero).
        DistributorAccount account = new DistributorAccount();
        account.entity = alptis();
        account.payeeType = PayeeType.DISTRIBUTOR;
        account.payeeId = d.getId();
        account.persist();
        AccountEntry entry = new AccountEntry();
        entry.account = account;
        entry.entryDate = LocalDate.of(2026, 3, 1);
        entry.entryType = AccountEntryType.PAYMENT;
        entry.amount = new BigDecimal("12.34");
        entry.persist();

        // Restricted to Alptis: one of each, the account series non-empty.
        var restricted = restricted().distributor("P1");
        assertEquals("G1", restricted.groupCode());
        assertEquals("Groupe", restricted.groupLabel());
        assertEquals(1, restricted.agreements().size());
        assertEquals("ACC-1", restricted.agreements().get(0).reference());
        assertEquals("NOT_SIGNED", restricted.agreements().get(0).signatureStatus());
        assertEquals(1, restricted.enrollments().size());
        assertEquals("ACTIVE", restricted.enrollments().get(0).status());
        assertEquals(2, restricted.indicators().size());
        assertEquals(1, restricted.accounts().size());
        assertEquals("12.34", restricted.accounts().get(0).balance());
        assertEquals(1, restricted.accounts().get(0).history().size());

        // Unrestricted: the UNEP rows appear too, and UNEP's empty series yields a zero balance.
        var all = unrestricted().distributor("P1");
        assertEquals(2, all.agreements().size());
        var unepAgreement = all.agreements().stream()
                .filter(a -> a.entityCode().equals("UNEP")).findFirst().orElseThrow();
        assertNull(unepAgreement.reference());
        assertNull(unepAgreement.signatureStatus());
        assertEquals(2, all.enrollments().size());
        assertEquals(3, all.indicators().size());
        assertTrue(all.indicators().stream().anyMatch(i -> i.indicatorType() == null));
        assertEquals(2, all.accounts().size());
        var unepAccount = all.accounts().stream().filter(a -> a.entityCode().equals("UNEP")).findFirst().orElseThrow();
        assertEquals("0", unepAccount.balance());
        assertTrue(unepAccount.history().isEmpty());
    }

    /**
     * Seeds an enrollment of a distributor to a freshly-created rule in an entity's plan.
     *
     * @param entity      the entity whose plan carries the rule
     * @param ruleCode    the rule code
     * @param distributor the enrolled distributor
     * @param status      the enrollment status, possibly null
     * @return the persisted enrollment
     */
    private static Enrollment enroll(LegalEntity entity, String ruleCode, Distributor distributor,
                                     EnrollmentStatus status) {
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = ruleCode + "-PLAN";
        plan.label = "Plan";
        plan.direction = Direction.INBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = ruleCode;
        rule.label = "Règle " + ruleCode;
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        Enrollment enrollment = new Enrollment();
        enrollment.rule = rule;
        enrollment.distributor = distributor;
        enrollment.status = status;
        enrollment.validFrom = LocalDate.of(2026, 1, 1);
        enrollment.persist();
        return enrollment;
    }

    /**
     * Seeds a quality indicator for a distributor in an entity.
     *
     * @param entity        the entity
     * @param distributor   the distributor
     * @param indicatorType the indicator type, possibly null
     * @param range         the product range, possibly null
     * @param value         the value, possibly null
     */
    private static void indicator(LegalEntity entity, Distributor distributor, QualityIndicatorType indicatorType,
                                  ProductRange range, BigDecimal value) {
        QualityIndicator indicator = new QualityIndicator();
        indicator.entity = entity;
        indicator.distributor = distributor;
        indicator.indicatorType = indicatorType;
        indicator.range = range;
        indicator.periodTo = LocalDate.of(2026, 6, 30);
        indicator.value = value;
        indicator.persist();
    }

    /**
     * A distributor with no status in the user's entities, and an unknown code, are both invisible.
     */
    @Test
    @TestTransaction
    void hides_out_of_scope_and_unknown_distributors() {
        Distributor d = new Distributor();
        d.partnerCode = "PX";
        d.legalName = "Hors périmètre";
        d.persist();
        DistributorEntity link = new DistributorEntity();
        link.distributor = d;
        link.entity = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        link.validFrom = LocalDate.of(2026, 1, 1);
        link.status = PartnerStatus.ACTIVE;
        link.persist();

        ReferentialQueryService restricted = restricted();
        assertThrows(ResourceNotFoundException.class, () -> restricted.distributor("PX"));
        assertThrows(ResourceNotFoundException.class, () -> restricted.distributor("UNKNOWN"));
        // Unrestricted sees it.
        assertEquals("PX", unrestricted().distributor("PX").partnerCode());
    }

    /**
     * Searches contracts and reads their coverages in both partitions, covering the coverage-status
     * and premium null branches.
     */
    @Test
    @TestTransaction
    void searches_contracts_with_coverages() {
        Contract contract = new Contract();
        contract.entity = alptis();
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "AD-1";
        contract.status = ContractStatus.IN_FORCE;
        contract.persist();
        Coverage full = new Coverage();
        full.contract = contract;
        full.coverageCode = "C1";
        full.status = ContractStatus.IN_FORCE;
        full.annualPremium = new BigDecimal("120.00");
        full.persist();
        Coverage bare = new Coverage();
        bare.contract = contract;
        bare.coverageCode = "C2";
        bare.persist();

        assertEquals(1, restricted().searchContracts("ad", 0, 20).total());
        var page = unrestricted().searchContracts(null, -1, 0);
        assertEquals(1, page.items().size());
        assertEquals(2, page.items().get(0).coverages().size());
    }

    /**
     * Reads products with and without a range and insurer, in both partitions.
     */
    @Test
    @TestTransaction
    void lists_products() {
        Insurer insurer = new Insurer();
        insurer.code = "INS1";
        insurer.name = "Assureur";
        insurer.persist();
        ProductRange range = new ProductRange();
        range.code = "SANTE";
        range.label = "Santé";
        range.persist();
        Product full = new Product();
        full.entity = alptis();
        full.code = "PR1";
        full.label = "Produit";
        full.range = range;
        full.insurer = insurer;
        full.commissionable = true;
        full.persist();
        Product bare = new Product();
        bare.entity = alptis();
        bare.code = "PR2";
        bare.label = "Produit nu";
        bare.persist();

        assertEquals(2, restricted().products().size());
        assertEquals(2, unrestricted().products().size());
    }

    /**
     * Reads quality indicators with and without distributor, range and value.
     */
    @Test
    @TestTransaction
    void lists_quality_indicators() {
        Distributor d = seedDistributor();
        ProductRange range = new ProductRange();
        range.code = "SANTE";
        range.label = "Santé";
        range.persist();
        QualityIndicator full = new QualityIndicator();
        full.entity = alptis();
        full.distributor = d;
        full.indicatorType = QualityIndicatorType.COMPLAINT_RATE;
        full.range = range;
        full.periodTo = LocalDate.of(2026, 6, 30);
        full.value = new BigDecimal("0.0100");
        full.persist();
        QualityIndicator bare = new QualityIndicator();
        bare.entity = alptis();
        bare.periodTo = LocalDate.of(2026, 3, 31);
        bare.persist();

        assertEquals(2, restricted().qualityIndicators().size());
        assertEquals(2, unrestricted().qualityIndicators().size());
    }

    /**
     * Reads bank accounts with a long, a short and a null IBAN, covering the masking branches.
     */
    @Test
    @TestTransaction
    void lists_bank_accounts_with_masking() {
        BankAccount longIban = new BankAccount();
        longIban.entity = alptis();
        longIban.holderType = PayeeType.DISTRIBUTOR;
        longIban.holderName = "Titulaire";
        longIban.iban = "FR7612345678901234567";
        longIban.bic = "BANKFRPP";
        longIban.validFrom = LocalDate.of(2026, 1, 1);
        longIban.validTo = LocalDate.of(2026, 12, 31);
        longIban.persist();
        BankAccount shortIban = new BankAccount();
        shortIban.entity = alptis();
        shortIban.iban = "FR76";
        shortIban.validFrom = LocalDate.of(2026, 1, 1);
        shortIban.persist();
        BankAccount nullIban = new BankAccount();
        nullIban.entity = alptis();
        nullIban.validFrom = LocalDate.of(2026, 1, 1);
        nullIban.persist();

        var accounts = restricted().bankAccounts();
        assertEquals(3, accounts.size());
        assertTrue(accounts.stream().anyMatch(a -> a.iban() != null && a.iban().contains("…")));
        assertEquals(3, unrestricted().bankAccounts().size());
    }

    /**
     * Reads portfolio transfers and replacement orders with full and bare rows, in both partitions.
     */
    @Test
    @TestTransaction
    void lists_transfers_and_replacement_orders() {
        Distributor d = seedDistributor();
        PortfolioTransfer full = new PortfolioTransfer();
        full.entity = alptis();
        full.fromDistributor = d;
        full.toDistributor = d;
        full.effectDate = LocalDate.of(2026, 7, 1);
        full.advanceDebtHandling = AdvanceDebtHandling.TAKEN_OVER;
        full.status = TransferStatus.PENDING;
        full.persist();
        PortfolioTransfer bare = new PortfolioTransfer();
        bare.entity = alptis();
        bare.effectDate = LocalDate.of(2026, 8, 1);
        bare.persist();

        Contract contract = new Contract();
        contract.entity = alptis();
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "AD-1";
        contract.persist();
        ProductionCode from = ProductionCode.find("code", "61723").firstResult();
        ReplacementOrder full2 = new ReplacementOrder();
        full2.contract = contract;
        full2.fromProductionCode = from;
        full2.toProductionCode = from;
        full2.receivedDate = LocalDate.of(2026, 6, 1);
        full2.effectDate = LocalDate.of(2026, 7, 1);
        full2.persist();
        ReplacementOrder bare2 = new ReplacementOrder();
        bare2.effectDate = LocalDate.of(2026, 8, 1);
        bare2.persist();

        assertEquals(2, restricted().portfolioTransfers().size());
        assertEquals(2, unrestricted().portfolioTransfers().size());
        // The bare order has no contract, so the entity-filtered query excludes it.
        assertEquals(1, restricted().replacementOrders().size());
        assertEquals(2, unrestricted().replacementOrders().size());
    }

    /**
     * Reads payment rules (with every perimeter and bare) and their shares, in both partitions.
     */
    @Test
    @TestTransaction
    void lists_payee_rules() {
        Distributor d = seedDistributor();
        ProductRange range = new ProductRange();
        range.code = "SANTE";
        range.label = "Santé";
        range.persist();
        ProductionCode pc = ProductionCode.find("code", "61723").firstResult();
        DistributorGroup group = DistributorGroup.find("code", "G1").firstResult();

        PayeeRule full = new PayeeRule();
        full.entity = alptis();
        full.productionCode = pc;
        full.distributor = d;
        full.range = range;
        full.paymentFrequency = PaymentFrequency.MONTHLY;
        full.validFrom = LocalDate.of(2026, 1, 1);
        full.validTo = LocalDate.of(2026, 12, 31);
        full.persist();
        share(full, PayeeType.DISTRIBUTOR, d.getId(), "0.9");
        share(full, PayeeType.GROUP, group.getId(), "0.1");

        PayeeRule bare = new PayeeRule();
        bare.entity = alptis();
        bare.persist();

        var rules = restricted().payeeRules();
        assertEquals(2, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.shares().size() == 2));
        assertEquals(2, unrestricted().payeeRules().size());
    }

    /**
     * Reads external codes for a production code (partitioned), a distributor and a group (shared),
     * plus one whose object type is unknown, in both partitions.
     */
    @Test
    @TestTransaction
    void lists_external_codes() {
        Distributor d = seedDistributor();
        ProductionCode pc = ProductionCode.find("code", "61723").firstResult();
        DistributorGroup group = DistributorGroup.find("code", "G1").firstResult();

        externalCode(PayeeType.PRODUCTION_CODE.name(), pc.getId(), "C-PC");
        externalCode(PayeeType.DISTRIBUTOR.name(), d.getId(), "C-D");
        externalCode(PayeeType.GROUP.name(), group.getId(), "C-G");
        externalCode("MYSTERY", 999L, "C-X");

        assertEquals(4, restricted().externalCodes().size());
        assertEquals(4, unrestricted().externalCodes().size());
    }

    /**
     * An external code on a production code of another entity is hidden from a restricted user.
     */
    @Test
    @TestTransaction
    void hides_external_codes_of_other_entities() {
        ProductionCode other = new ProductionCode();
        other.entity = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        other.code = "U-1";
        other.persist();
        externalCode(PayeeType.PRODUCTION_CODE.name(), other.getId(), "C-U");

        assertEquals(0, restricted().externalCodes().size());
        assertEquals(1, unrestricted().externalCodes().size());
    }

    /**
     * An external code whose production code no longer exists is treated as shared (visible).
     */
    @Test
    @TestTransaction
    void external_code_of_missing_production_code_is_visible() {
        externalCode(PayeeType.PRODUCTION_CODE.name(), 424242L, "C-ORPHAN");
        assertEquals(1, restricted().externalCodes().size());
    }

    /**
     * Covers the defensive null branches of the views: a contract with no status, payment-rule
     * shares with a null type, id and amount, external codes whose payee is unknown or whose type is
     * null, and a production-code external code whose production code has no entity.
     */
    @Test
    @TestTransaction
    void covers_defensive_null_branches() {
        Contract contract = new Contract();
        contract.entity = alptis();
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "NS-1";
        contract.persist();

        PayeeRule rule = new PayeeRule();
        rule.entity = alptis();
        rule.persist();
        share(rule, PayeeType.DISTRIBUTOR, null, "0.5");
        PayeeRuleShare noType = new PayeeRuleShare();
        noType.payeeRule = rule;
        noType.payeeType = null;
        noType.payeeId = 5L;
        noType.share = null;
        noType.persist();

        externalCode(PayeeType.DISTRIBUTOR.name(), 999_999L, "C-BADD");
        externalCode(PayeeType.GROUP.name(), 999_999L, "C-BADG");
        externalCode(null, 5L, "C-NOTYPE");
        ProductionCode orphan = new ProductionCode();
        orphan.entity = null;
        orphan.code = "NO-ENTITY";
        orphan.persist();
        externalCode(PayeeType.PRODUCTION_CODE.name(), orphan.getId(), "C-NOENT");

        assertNull(restricted().searchContracts("ns", 0, 20).items().get(0).status());
        assertEquals(1, restricted().payeeRules().size());
        assertEquals(2, restricted().payeeRules().get(0).shares().size());
        assertEquals(4, restricted().externalCodes().size());
    }

    /** Persists a payment-rule share. */
    private void share(PayeeRule rule, PayeeType type, Long payeeId, String value) {
        PayeeRuleShare s = new PayeeRuleShare();
        s.payeeRule = rule;
        s.payeeType = type;
        s.payeeId = payeeId;
        s.share = new BigDecimal(value);
        s.persist();
    }

    /** Persists an external code. */
    private void externalCode(String objectType, Long objectId, String external) {
        ExternalCode c = new ExternalCode();
        c.objectType = objectType;
        c.objectId = objectId;
        c.sourceSystem = "CLEVA";
        c.externalCode = external;
        c.persist();
    }

    /**
     * Lists the groupings (a head-less one gives a null head) and one grouping's members with their
     * production codes; an unknown grouping is not found (CPT-02, REF-03).
     */
    @Test
    @TestTransaction
    void lists_groupings_and_members() {
        seedDistributor();
        ReferentialQueryService service = unrestricted();
        ReferentialQueryService.GroupView g1 = service.groups().stream()
                .filter(g -> g.code().equals("G1")).findFirst().orElseThrow();
        assertNull(g1.headCode());
        assertEquals(1, g1.memberCount());

        ReferentialQueryService.GroupDetail detail = service.groupDetail("G1");
        assertEquals(1, detail.members().size());
        assertTrue(detail.members().get(0).productionCodes().contains("61723"));
        assertThrows(ResourceNotFoundException.class, () -> service.groupDetail("NOPE"));
    }
}
