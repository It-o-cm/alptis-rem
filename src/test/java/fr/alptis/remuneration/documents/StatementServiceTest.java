package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
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
        assertNotNull(published.pdfHash);
        assertTrue(published.pdfRef.endsWith("_v1.pdf"));

        String csv = service.spreadsheet(statement.getId());
        assertTrue(csv.contains("rubrique;libelle"));
        assertTrue(csv.contains("Net payé;;;;100.00"));
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
}
