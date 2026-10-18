package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.StatementGrouping;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.StatementLevel;
import fr.alptis.remuneration.domain.StatementStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the statement (bordereau) rendering and publication (section 9.5, BRD, step 10): the
 * rubric grouping, the archived hash, the spreadsheet form and the rectificative version. Runs
 * against UNEP, inside rolled-back transactions.
 */
@QuarkusTest
class StatementServiceTest {

    @Inject
    StatementService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates the period and a distributor account with its statement and a line of every rubric. */
    private Statement seed() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.BUSINESS_VALIDATED;
        period.persist();
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = PayeeType.DISTRIBUTOR;
        account.payeeId = 1L;
        account.persist();
        Product product = new Product();
        product.entity = unep();
        product.code = "SANTE_ST";
        product.label = "s";
        product.persist();
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "AD-1";
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "AD-1-C1";
        coverage.effectDate = M;
        coverage.persist();
        line(period, account, coverage, LineNature.LINEAR, "30.00");
        CommissionLine clawback = new CommissionLine();
        clawback.entity = unep();
        clawback.period = period;
        clawback.account = account;
        clawback.direction = Direction.OUTBOUND;
        clawback.status = LineStatus.PAYABLE;
        clawback.nature = LineNature.CLAWBACK;
        clawback.amount = new BigDecimal("-10.00");
        clawback.reason = "Résiliation au 200e jour";
        clawback.persist();
        line(period, account, null, LineNature.ADVANCE, "96.00");
        line(period, account, null, LineNature.ADVANCE_ADJUSTMENT, "5.00");
        line(period, account, null, LineNature.RCD, "20.00");
        line(period, account, null, LineNature.MANUAL, "9.00");
        Statement statement = new Statement();
        statement.entity = unep();
        statement.period = period;
        statement.account = account;
        statement.batchType = BatchType.CYCLE;
        statement.statementNumber = "ST-UNEP-2026-09-1";
        statement.versionNo = 1;
        statement.level = StatementLevel.DISTRIBUTOR;
        statement.carriedBalance = new BigDecimal("-50.00");
        statement.periodTotal = new BigDecimal("150.00");
        statement.netAmount = new BigDecimal("100.00");
        statement.status = StatementStatus.DRAFT;
        statement.persist();
        return statement;
    }

    private long seq = 100;

    /** A product option whose product carries the given code and optional insurer. */
    private ProductOption optionOf(String productCode, String insurerCode) {
        Product p = new Product();
        p.entity = unep();
        p.code = productCode;
        p.label = productCode;
        if (insurerCode != null) {
            Insurer ins = new Insurer();
            ins.code = insurerCode;
            ins.persist();
            p.insurer = ins;
        }
        p.persist();
        ProductOption option = new ProductOption();
        option.product = p;
        option.code = "OPT-" + (seq++);
        option.persist();
        return option;
    }

    /** A coverage carrying the given option (its contract has no own product here). */
    private Coverage coverageWith(ProductOption option) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "C-" + (seq++);
        contract.persist();
        Coverage c = new Coverage();
        c.contract = contract;
        c.coverageCode = contract.contractNumber + "-C1";
        c.effectDate = M;
        c.option = option;
        c.persist();
        return c;
    }

    /** A distributor carrying a bordereau declension preference. */
    private Distributor distributorWith(StatementGrouping grouping) {
        Distributor d = new Distributor();
        d.partnerCode = "D-" + (seq++);
        d.legalName = "D";
        d.statementGrouping = grouping;
        d.persist();
        return d;
    }

    /** A persisted statement on an account for a period. */
    private Statement statementOn(DistributorAccount account, Period period) {
        Statement s = new Statement();
        s.entity = unep();
        s.period = period;
        s.account = account;
        s.batchType = BatchType.CYCLE;
        s.statementNumber = "ST-" + (seq++);
        s.versionNo = 1;
        s.level = StatementLevel.DISTRIBUTOR;
        s.carriedBalance = BigDecimal.ZERO;
        s.periodTotal = BigDecimal.ZERO;
        s.netAmount = BigDecimal.ZERO;
        s.status = StatementStatus.DRAFT;
        s.persist();
        return s;
    }

    /** A persisted period for the default month. */
    private Period aPeriod() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.BUSINESS_VALIDATED;
        period.persist();
        return period;
    }

    /** A DISTRIBUTOR-type account pointing at a payee id. */
    private DistributorAccount distributorAccount(PayeeType type, long payeeId) {
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = type;
        account.payeeId = payeeId;
        account.persist();
        return account;
    }

    /**
     * BRD-04: a statement declined by insurer tags each line with its insurer code when the line carries
     * a product with an insurer, and leaves it blank otherwise (no insurer, no product, no coverage).
     */
    @Test
    @TestTransaction
    void renders_declension_by_insurer() {
        Period period = aPeriod();
        Distributor d = distributorWith(StatementGrouping.BY_INSURER);
        DistributorAccount account = distributorAccount(PayeeType.DISTRIBUTOR, d.getId());
        line(period, account, coverageWith(optionOf("P1", "AXA")), LineNature.LINEAR, "30.00");
        line(period, account, coverageWith(optionOf("P2", null)), LineNature.LINEAR, "20.00");
        line(period, account, null, LineNature.ADVANCE, "10.00");
        line(period, account, coverageWith(null), LineNature.MANUAL, "5.00");
        Statement statement = statementOn(account, period);

        var content = service.render(statement.getId());
        assertEquals("BY_INSURER", content.grouping());
        assertEquals("AXA", content.rows().get(0).group());
        assertEquals(null, content.rows().get(1).group());
        assertEquals(null, content.rows().get(2).group());
        assertEquals(null, content.rows().get(3).group());
        // The CSV carries the declension column.
        assertTrue(service.spreadsheet(statement.getId()).contains(";declinaison\n"));
    }

    /**
     * BRD-04: a statement declined by product tags each line with its product code; a production-code
     * account resolves its distributor's preference.
     */
    @Test
    @TestTransaction
    void renders_declension_by_product_through_production_code() {
        Period period = aPeriod();
        Distributor d = distributorWith(StatementGrouping.BY_PRODUCT);
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC-BRD04";
        pc.distributor = d;
        pc.persist();
        DistributorAccount account = distributorAccount(PayeeType.PRODUCTION_CODE, pc.getId());
        line(period, account, coverageWith(optionOf("PROD-X", null)), LineNature.LINEAR, "40.00");
        Statement statement = statementOn(account, period);

        var content = service.render(statement.getId());
        assertEquals("BY_PRODUCT", content.grouping());
        assertEquals("PROD-X", content.rows().get(0).group());
    }

    /**
     * BRD-04: with no distributor, an unknown production code, a group account or a distributor without
     * preference, the statement is not declined (no group on its lines).
     */
    @Test
    @TestTransaction
    void renders_without_declension_on_resolution_edges() {
        Period period = aPeriod();
        // A production-code account whose production code does not exist.
        DistributorAccount ghost = distributorAccount(PayeeType.PRODUCTION_CODE, 999999L);
        line(period, ghost, coverageWith(optionOf("PX", "AXA")), LineNature.LINEAR, "10.00");
        assertEquals("NONE", service.render(statementOn(ghost, period).getId()).grouping());

        // A group account (neither production code nor distributor).
        DistributorAccount groupAcct = distributorAccount(PayeeType.GROUP, 7L);
        line(period, groupAcct, null, LineNature.LINEAR, "10.00");
        assertEquals("NONE", service.render(statementOn(groupAcct, period).getId()).grouping());

        // A distributor without a declension preference.
        Distributor plain = distributorWith(null);
        DistributorAccount acct = distributorAccount(PayeeType.DISTRIBUTOR, plain.getId());
        line(period, acct, null, LineNature.LINEAR, "10.00");
        assertEquals("NONE", service.render(statementOn(acct, period).getId()).grouping());
    }

    /** Posts a line of a nature on an account. */
    private static void line(Period period, DistributorAccount account, Coverage coverage, LineNature nature,
                             String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.coverage = coverage;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.PAYABLE;
        line.nature = nature;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /**
     * Rendering groups the lines into the section 9.5 rubrics with the footer totals.
     */
    @Test
    @TestTransaction
    void renders_rubrics() {
        Statement statement = seed();
        StatementService.StatementContent content = service.render(statement.getId());

        assertEquals(6, content.rows().size());
        assertTrue(content.rows().stream().anyMatch(r -> r.section().equals("Reprises")));
        assertTrue(content.rows().stream().anyMatch(r -> r.section().equals("Escomptes versés")));
        assertTrue(content.rows().stream().anyMatch(r -> r.section().equals("Régularisations d'escompte")));
        assertTrue(content.rows().stream().anyMatch(r -> r.section().equals("Rémunérations complémentaires")));
        assertTrue(content.rows().stream().anyMatch(r -> r.section().equals("Ajustements")));
        assertTrue(content.rows().stream().anyMatch(r -> r.section().equals("Commissions") && r.label().equals("AD-1-C1")));
        assertEquals(0, content.net().compareTo(new BigDecimal("100.00")));
    }

    /** A PAYABLE line of a cycle or savings product, attached to a statement (PAI-04 fixtures). */
    private void lotLine(Period period, DistributorAccount account, boolean savings, String amount,
                         Statement statement) {
        Product p = new Product();
        p.entity = unep();
        p.code = (savings ? "PER-" : "SANTE-") + (seq++);
        p.label = p.code;
        if (savings) {
            ProductRange range = new ProductRange();
            range.code = "R-" + (seq++);
            range.label = "Épargne retraite";
            range.retirementSavings = Boolean.TRUE;
            range.persist();
            p.range = range;
        }
        p.persist();
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "LC-" + (seq++);
        contract.product = p;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contract.contractNumber + "-C1";
        coverage.effectDate = M;
        coverage.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.contract = contract;
        line.coverage = coverage;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.PAYABLE;
        line.nature = LineNature.LINEAR;
        line.amount = amount == null ? null : new BigDecimal(amount);
        line.statement = statement;
        line.persist();
    }

    /** A persisted statement of a given lot type on an account for a period. */
    private Statement statementOfType(DistributorAccount account, Period period, BatchType batchType) {
        Statement s = statementOn(account, period);
        s.batchType = batchType;
        return s;
    }

    /**
     * PAI-04: a savings lot with no positive net of its own is imputed on the cycle bordereau as a single
     * imputation line; the savings lines are not re-listed as rubric rows and the net stays the cycle net.
     */
    @Test
    @TestTransaction
    void renders_one_imputation_line_when_the_other_lot_has_no_statement() {
        Period period = aPeriod();
        DistributorAccount account = distributorAccount(PayeeType.PRODUCTION_CODE, 70L);
        Statement cycle = statementOfType(account, period, BatchType.CYCLE);
        cycle.periodTotal = new BigDecimal("200.00");
        cycle.carriedBalance = BigDecimal.ZERO;
        cycle.netAmount = new BigDecimal("150.00");
        lotLine(period, account, false, "200.00", cycle);
        lotLine(period, account, true, "-50.00", cycle);
        lotLine(period, account, true, null, cycle);

        StatementService.StatementContent content = service.render(cycle.getId());

        assertEquals(1, content.rows().stream().filter(r -> r.section().equals("Imputation")).count());
        StatementService.Row imputation = content.rows().stream()
                .filter(r -> r.section().equals("Imputation")).findFirst().orElseThrow();
        assertEquals("Lot épargne retraite", imputation.label());
        assertEquals(0, imputation.amount().compareTo(new BigDecimal("-50.00")));
        assertEquals(1, content.rows().stream().filter(r -> !r.section().equals("Imputation")).count());
        assertEquals(0, content.net().compareTo(new BigDecimal("150.00")));
    }

    /**
     * When both lots have their own statement, each bordereau shows only its own lines and carries no
     * imputation line (the other lot renders on its own statement).
     */
    @Test
    @TestTransaction
    void renders_each_lot_on_its_own_statement_without_imputation() {
        Period period = aPeriod();
        DistributorAccount account = distributorAccount(PayeeType.PRODUCTION_CODE, 71L);
        Statement cycle = statementOfType(account, period, BatchType.CYCLE);
        Statement savings = statementOfType(account, period, BatchType.SAVINGS_CYCLE);
        lotLine(period, account, false, "200.00", cycle);
        lotLine(period, account, true, "50.00", savings);

        StatementService.StatementContent cycleContent = service.render(cycle.getId());
        assertEquals(0, cycleContent.rows().stream().filter(r -> r.section().equals("Imputation")).count());
        assertEquals(1, cycleContent.rows().size());

        StatementService.StatementContent savingsContent = service.render(savings.getId());
        assertEquals(0, savingsContent.rows().stream().filter(r -> r.section().equals("Imputation")).count());
        assertEquals(1, savingsContent.rows().size());
    }

    /**
     * PAI-04 (symmetric): a cycle lot with no positive net of its own is imputed on the savings bordereau
     * as one imputation line labelled by the cycle lot.
     */
    @Test
    @TestTransaction
    void renders_imputation_of_the_cycle_lot_on_the_savings_bordereau() {
        Period period = aPeriod();
        DistributorAccount account = distributorAccount(PayeeType.PRODUCTION_CODE, 72L);
        Statement savings = statementOfType(account, period, BatchType.SAVINGS_CYCLE);
        savings.netAmount = new BigDecimal("30.00");
        lotLine(period, account, true, "50.00", savings);
        lotLine(period, account, false, "-20.00", savings);

        StatementService.StatementContent content = service.render(savings.getId());

        StatementService.Row imputation = content.rows().stream()
                .filter(r -> r.section().equals("Imputation")).findFirst().orElseThrow();
        assertEquals("Lot de cycle", imputation.label());
        assertEquals(0, imputation.amount().compareTo(new BigDecimal("-20.00")));
    }

    /**
     * A line whose contract carries a non-retirement-savings range, and one whose contract carries no
     * product at all, are both cycle lines (not savings): on a cycle bordereau they render as native
     * rubrics and never as an imputation line.
     */
    @Test
    @TestTransaction
    void a_line_with_a_non_savings_range_or_no_product_is_a_cycle_line() {
        Period period = aPeriod();
        DistributorAccount account = distributorAccount(PayeeType.PRODUCTION_CODE, 73L);
        Statement cycle = statementOfType(account, period, BatchType.CYCLE);
        // A product carrying a non-retirement range.
        Product p = new Product();
        p.entity = unep();
        p.code = "SANTE-NR";
        p.label = "Santé";
        ProductRange range = new ProductRange();
        range.code = "R-NR";
        range.label = "Santé";
        range.retirementSavings = Boolean.FALSE;
        range.persist();
        p.range = range;
        p.persist();
        contractLineOn(period, account, p, "NR-1", "40.00", cycle);
        // A contract with no product at all.
        contractLineOn(period, account, null, "NR-2", "10.00", cycle);

        StatementService.StatementContent content = service.render(cycle.getId());

        assertEquals(0, content.rows().stream().filter(r -> r.section().equals("Imputation")).count());
        assertEquals(2, content.rows().size());
    }

    /** A PAYABLE line carrying a contract (optionally with a product) attached to a statement. */
    private void contractLineOn(Period period, DistributorAccount account, Product product, String contractNumber,
                                String amount, Statement statement) {
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.account = account;
        line.contract = contract;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.PAYABLE;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.statement = statement;
        line.persist();
    }

    /**
     * BRD-02/BRD-03: a grouping statement names its grouping and carries the GROUP level; an unknown
     * grouping falls back to its id; a statement with no level yields a null level.
     */
    @Test
    @TestTransaction
    void renders_beneficiary_label_and_level_for_a_grouping_and_a_null_level() {
        Period period = aPeriod();
        fr.alptis.remuneration.domain.DistributorGroup g = new fr.alptis.remuneration.domain.DistributorGroup();
        g.code = "GX";
        g.label = "Groupement X";
        g.persist();
        Statement groupStmt = statementOfType(distributorAccount(PayeeType.GROUP, g.getId()), period, BatchType.CYCLE);
        groupStmt.level = StatementLevel.GROUP;
        StatementService.StatementContent group = service.render(groupStmt.getId());
        assertEquals("GROUP", group.level());
        assertEquals("Groupement GX", group.beneficiary());

        // An account whose grouping no longer exists falls back to the raw id.
        StatementService.StatementContent ghost = service.render(
                statementOfType(distributorAccount(PayeeType.GROUP, 999999L), period, BatchType.CYCLE).getId());
        assertEquals("Groupement 999999", ghost.beneficiary());

        // A statement with no level yields a null level in the content.
        Statement noLevel = statementOfType(distributorAccount(PayeeType.PRODUCTION_CODE, 888L), period,
                BatchType.CYCLE);
        noLevel.level = null;
        assertNull(service.render(noLevel.getId()).level());
    }

    /**
     * BRD-04: the consolidated multi-entity bordereau of a distributor gives one subtotal per entity
     * (its cycle net there) and the grand total; it aggregates the distributor's production-code and
     * distributor-level accounts, ignoring other distributors, groupings and account-less statements; an
     * unknown distributor is reported as not found.
     */
    @Test
    @TestTransaction
    void consolidated_gives_a_subtotal_per_entity() {
        LocalDate month = LocalDate.of(2027, 3, 1);
        LegalEntity unep = unep();
        LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
        Period pUnep = periodFor(unep, month);
        Period pAlptis = periodFor(alptis, month);
        Distributor d = new Distributor();
        d.partnerCode = "D-CONSO";
        d.legalName = "Consolidé";
        d.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep;
        pc.code = "PC-CONSO";
        pc.distributor = d;
        pc.persist();
        // UNEP: a production-code account of the distributor (100). ALPTIS: a distributor-level account (60).
        DistributorAccount pcAccount = accountOf(unep, PayeeType.PRODUCTION_CODE, pc.getId());
        cycleStatement(unep, pUnep, pcAccount, "100.00");
        cycleStatement(alptis, pAlptis, accountOf(alptis, PayeeType.DISTRIBUTOR, d.getId()), "60.00");
        // Noise, all excluded by distributorOf: a grouping account, another distributor's code, an unknown
        // code and an account-less statement.
        fr.alptis.remuneration.domain.DistributorGroup g = new fr.alptis.remuneration.domain.DistributorGroup();
        g.code = "GZ";
        g.label = "GZ";
        g.persist();
        cycleStatement(unep, pUnep, accountOf(unep, PayeeType.GROUP, g.getId()), "5.00");
        Distributor other = new Distributor();
        other.partnerCode = "D-OTHER";
        other.legalName = "Autre";
        other.persist();
        ProductionCode pcOther = new ProductionCode();
        pcOther.entity = unep;
        pcOther.code = "PC-OTHER";
        pcOther.distributor = other;
        pcOther.persist();
        cycleStatement(unep, pUnep, accountOf(unep, PayeeType.PRODUCTION_CODE, pcOther.getId()), "7.00");
        cycleStatement(unep, pUnep, accountOf(unep, PayeeType.PRODUCTION_CODE, 999999L), "9.00");
        cycleStatement(unep, pUnep, null, "3.00");
        // A distributor-owned statement with no net contributes zero (covers the null-net branch).
        cycleStatement(unep, pUnep, pcAccount, null);

        StatementService.StatementContent content = service.consolidated("D-CONSO", month, null);

        assertEquals(2, content.rows().size());
        assertEquals(0, content.net().compareTo(new BigDecimal("160.00")));
        assertTrue(content.rows().stream().allMatch(r -> r.section().equals("Sous-total entité")));
        assertEquals(0, content.rows().stream().filter(r -> r.label().equals(unep.name)).findFirst().orElseThrow()
                .amount().compareTo(new BigDecimal("100.00")));
        assertEquals(0, content.rows().stream().filter(r -> r.label().equals(alptis.name)).findFirst().orElseThrow()
                .amount().compareTo(new BigDecimal("60.00")));
        assertEquals("Distributeur Consolidé", content.beneficiary());

        // Restricting to a single entity keeps only its subtotal.
        assertEquals(0, service.consolidated("D-CONSO", month, java.util.List.of("UNEP")).net()
                .compareTo(new BigDecimal("100.00")));
        assertThrows(ResourceNotFoundException.class, () -> service.consolidated("NOPE", month, null));
    }

    /** A persisted period for an entity and month. */
    private Period periodFor(LegalEntity ent, LocalDate month) {
        Period period = new Period();
        period.entity = ent;
        period.periodMonth = month;
        period.state = PeriodState.BUSINESS_VALIDATED;
        period.persist();
        return period;
    }

    /** A persisted account of a given payee type for an entity. */
    private DistributorAccount accountOf(LegalEntity ent, PayeeType type, long payeeId) {
        DistributorAccount account = new DistributorAccount();
        account.entity = ent;
        account.payeeType = type;
        account.payeeId = payeeId;
        account.persist();
        return account;
    }

    /** A persisted cycle statement for an entity, period and (possibly null) account, with a net. */
    private Statement cycleStatement(LegalEntity ent, Period period, DistributorAccount account, String net) {
        Statement s = new Statement();
        s.entity = ent;
        s.period = period;
        s.account = account;
        s.batchType = BatchType.CYCLE;
        s.statementNumber = "ST-C-" + (seq++);
        s.versionNo = 1;
        s.level = StatementLevel.PRODUCTION_CODE;
        s.carriedBalance = BigDecimal.ZERO;
        s.periodTotal = net == null ? null : new BigDecimal(net);
        s.netAmount = net == null ? null : new BigDecimal(net);
        s.status = StatementStatus.DRAFT;
        s.persist();
        return s;
    }

    /**
     * Publishing archives the statement with a hash and marks it published; the spreadsheet carries
     * the lines and the footer.
     */
    @Test
    @TestTransaction
    void publishes_and_exports_spreadsheet() {
        Statement statement = seed();

        Statement published = service.publish(statement.getId());
        assertEquals(StatementStatus.PUBLISHED, published.status);
        assertEquals(64, published.pdfHash.length());
        assertTrue(published.pdfRef.endsWith("_v1.pdf"));
        assertTrue(published.sheetRef.endsWith("_v1.xlsx"));
        assertNotNull(published.publishedAt);
        // BRD-04: with no executed cycle lot the publication date defaults to today, and the GED metadata
        // is deposited under a reference.
        assertNotNull(published.publicationDate);
        assertTrue(published.gedReference.startsWith("GED-"));

        String csv = service.spreadsheet(statement.getId());
        assertTrue(csv.contains("rubrique;libelle"));
        assertTrue(csv.contains("Net payé;;;;100.00"));

        // The real documents are produced: a PDF and an xlsx workbook (BRD-01).
        byte[] pdf = service.pdf(statement.getId());
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
        byte[] workbook = service.workbook(statement.getId());
        assertEquals('P', workbook[0]);
        assertEquals('K', workbook[1]);
    }

    /**
     * BRD-04: a statement whose period's cycle lot was executed is published dated the day after that
     * execution.
     */
    @Test
    @TestTransaction
    void publishes_on_the_day_after_the_cycle_execution() {
        Statement statement = seed();
        fr.alptis.remuneration.domain.PaymentBatch cycle = new fr.alptis.remuneration.domain.PaymentBatch();
        cycle.entity = unep();
        cycle.period = statement.period;
        cycle.batchType = BatchType.CYCLE;
        cycle.batchReference = "CYCLE-PUB";
        cycle.status = fr.alptis.remuneration.domain.BatchStatus.ISSUED;
        cycle.totalAmount = BigDecimal.ZERO;
        cycle.executionDate = LocalDate.of(2026, 9, 15);
        cycle.persist();

        Statement published = service.publish(statement.getId());
        assertEquals(LocalDate.of(2026, 9, 16), published.publicationDate);
    }

    /**
     * A rectificative version refers to the first and carries the next version number.
     */
    @Test
    @TestTransaction
    void rectifies_into_a_new_version() {
        Statement statement = seed();
        service.publish(statement.getId());

        Statement rectified = service.rectify(statement.getId());
        assertEquals(2, rectified.versionNo);
        assertEquals(statement.getId(), rectified.replacesStatement.getId());
        assertEquals(StatementStatus.DRAFT, rectified.status);
    }

    /**
     * An unknown statement is reported as not found by every operation.
     */
    @Test
    @TestTransaction
    void unknown_statement() {
        assertThrows(ResourceNotFoundException.class, () -> service.render(-1L));
        assertThrows(ResourceNotFoundException.class, () -> service.publish(-1L));
        assertThrows(ResourceNotFoundException.class, () -> service.rectify(-1L));
    }

    /** Creates a period at month M in a state. */
    private static Period memberPeriod() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        return period;
    }

    /** Adds a line carrying a member production code as its origin, with a possibly-null amount. */
    private static void memberLine(Period period, fr.alptis.remuneration.domain.ProductionCode pc,
                                   LineNature nature, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.originProductionCode = pc;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = amount == null ? null : new BigDecimal(amount);
        line.persist();
    }

    /**
     * BRD-11: the informative statement of a group member lists the lines carrying its production code
     * as origin, summing their amounts (a null amount counting as zero).
     */
    @Test
    @TestTransaction
    void member_statement_lists_the_members_share() {
        Period period = memberPeriod();
        fr.alptis.remuneration.domain.ProductionCode pc = new fr.alptis.remuneration.domain.ProductionCode();
        pc.entity = unep();
        pc.code = "PC-M";
        pc.persist();
        memberLine(period, pc, LineNature.LINEAR, "30.00");
        memberLine(period, pc, LineNature.CLAWBACK, null);

        StatementService.StatementContent content = service.memberStatement("UNEP", M, "PC-M");
        assertEquals(2, content.rows().size());
        assertEquals(0, content.net().compareTo(new BigDecimal("30.00")));
        assertTrue(content.beneficiary().contains("PC-M"));
    }

    /** The member statement guards the unknown entity, period and production code. */
    @Test
    @TestTransaction
    void member_statement_guards() {
        assertThrows(ResourceNotFoundException.class, () -> service.memberStatement("NOPE", M, "PC-M"));
        assertThrows(ResourceNotFoundException.class, () -> service.memberStatement("UNEP", M, "PC-M"));
        memberPeriod();
        assertThrows(ResourceNotFoundException.class, () -> service.memberStatement("UNEP", M, "NO-CODE"));
    }
}
