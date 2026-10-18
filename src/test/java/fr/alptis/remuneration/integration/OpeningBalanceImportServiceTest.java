package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ProductionCounter;
import fr.alptis.remuneration.domain.Rule;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests of the opening-balance history reprise (ING-11): a pivot file opens the migrated accounts at
 * their carried balance, for a production code, a distributor and a group; an unknown beneficiary is
 * held and a malformed line rejected. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class OpeningBalanceImportServiceTest {

    @Inject
    ImportService imports;
    @Inject
    AccountService accounts;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /**
     * The file opens three accounts at their balances, holds the unknown beneficiary and rejects the
     * malformed, missing and bad-type lines.
     */
    @Test
    @TestTransaction
    void opens_accounts_and_reports_the_rejections() {
        Distributor distributor = new Distributor();
        distributor.partnerCode = "D1";
        distributor.legalName = "Cabinet";
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC1";
        pc.distributor = distributor;
        pc.persist();
        DistributorGroup group = new DistributorGroup();
        group.code = "G1";
        group.label = "Groupement";
        group.persist();

        String csv = "payee_type;payee_code;opening_balance\n"
                + "PRODUCTION_CODE;PC1;-350.00\n"
                + "DISTRIBUTOR;D1;100.00\n"
                + "GROUP;G1;50.00\n"
                + "DISTRIBUTOR;UNKNOWN;10.00\n"
                + "PRODUCTION_CODE;PC1;abc\n"
                + ";X;10.00\n"
                + "PRODUCTION_CODE;;10.00\n"
                + "PRODUCTION_CODE;PC1;\n"
                + "BADTYPE;X;10.00\n";

        ImportBatch batch = imports.importFile("UNEP", OpeningBalanceImportService.OPENING_BALANCES, "SYS",
                "opening.csv", csv);

        assertEquals(3, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(5, batch.linesRejected);
        assertEquals(3, AccountEntry.count("entryType = ?1", AccountEntryType.OPENING_BALANCE));

        DistributorAccount account = accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, pc.getId());
        assertEquals(0, new BigDecimal("-350.00").compareTo(accounts.balance(account)));
    }

    /**
     * The opening discount-positions file opens an {@code ACTIVE} position at its versed amount, backed
     * by a settled {@code ADVANCE} line; an unknown guarantee is held and the missing or malformed lines
     * are rejected.
     */
    @Test
    @TestTransaction
    void opens_discount_positions_and_reports_the_rejections() {
        Product product = new Product();
        product.entity = unep();
        product.code = "PER";
        product.label = "PER";
        product.persist();
        Contract contract = new Contract();
        contract.entity = unep();
        contract.sourceSystem = "SYS";
        contract.contractNumber = "C1";
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "COV1";
        coverage.effectDate = LocalDate.of(2026, 1, 1);
        coverage.persist();

        String csv = "coverage_code;advanced;total_due;settlement_due_date\n"
                + "COV1;120.00;120.00;2026-12-31\n"
                + "UNKNOWNCOV;120.00;120.00;2026-12-31\n"
                + ";120.00;120.00;2026-12-31\n"
                + "COV1;;120.00;2026-12-31\n"
                + "COV1;120.00;;2026-12-31\n"
                + "COV1;120.00;120.00;\n"
                + "COV1;abc;120.00;2026-12-31\n"
                + "COV1;120.00;xyz;2026-12-31\n"
                + "COV1;120.00;120.00;notadate\n";

        ImportBatch batch = imports.importFile("UNEP", OpeningBalanceImportService.OPENING_ADVANCES, "SYS",
                "advances.csv", csv);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(7, batch.linesRejected);

        AdvancePosition position = AdvancePosition.<AdvancePosition>find("coverage = ?1", coverage).firstResult();
        assertNotNull(position);
        assertEquals(AdvanceStatus.ACTIVE, position.status);
        assertEquals(0, new BigDecimal("120.00").compareTo(position.advancedAcquisition));
        assertEquals(0, new BigDecimal("120.00").compareTo(position.totalAdvanceDue));
        assertEquals(LocalDate.of(2026, 12, 31), position.settlementDueDate);
        CommissionLine line = CommissionLine.<CommissionLine>find("advancePosition = ?1", position).firstResult();
        assertNotNull(line);
        assertEquals(LineNature.ADVANCE, line.nature);
        assertEquals(0, new BigDecimal("120.00").compareTo(line.amount));
    }

    /**
     * The opening annual-counters file carries a rule's cumulative basis and entitlement for a payee at
     * a month; an unknown rule or payee is held and the missing or malformed lines are rejected.
     */
    @Test
    @TestTransaction
    void loads_annual_counters_and_reports_the_rejections() {
        Distributor distributor = new Distributor();
        distributor.partnerCode = "D1";
        distributor.legalName = "Cabinet";
        distributor.persist();
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "PLAN1";
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R1";
        rule.label = "RCD";
        rule.persist();

        String csv = "rule_code;payee_type;payee_code;year;month;cumulative_basis;cumulative_entitlement\n"
                + "R1;DISTRIBUTOR;D1;2026;5;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;UNKNOWN;2026;5;10000.00;400.00\n"
                + "UNKNOWN;DISTRIBUTOR;D1;2026;5;10000.00;400.00\n"
                + ";DISTRIBUTOR;D1;2026;5;10000.00;400.00\n"
                + "R1;;D1;2026;5;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;;2026;5;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;D1;;5;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;D1;2026;;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;D1;2026;5;;400.00\n"
                + "R1;DISTRIBUTOR;D1;2026;5;10000.00;\n"
                + "R1;BADTYPE;D1;2026;5;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;D1;abcd;5;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;D1;2026;xx;10000.00;400.00\n"
                + "R1;DISTRIBUTOR;D1;2026;5;bad;400.00\n"
                + "R1;DISTRIBUTOR;D1;2026;5;10000.00;bad\n";

        ImportBatch batch = imports.importFile("UNEP", OpeningBalanceImportService.OPENING_COUNTERS, "SYS",
                "counters.csv", csv);

        assertEquals(1, batch.linesLoaded);
        assertEquals(2, batch.linesOnHold);
        assertEquals(12, batch.linesRejected);

        ProductionCounter counter = ProductionCounter.<ProductionCounter>find(
                "rule = ?1 and payeeId = ?2", rule, distributor.getId()).firstResult();
        assertNotNull(counter);
        assertEquals((short) 2026, (short) counter.counterYear);
        assertEquals((short) 5, (short) counter.counterMonth);
        assertEquals(0, new BigDecimal("400.00").compareTo(counter.cumulativeEntitlement));
        assertEquals(0, new BigDecimal("10000.00").compareTo(counter.cumulativeBasis));
    }
}
