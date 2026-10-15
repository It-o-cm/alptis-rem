package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.accounting.CutoffService.CutoffView;
import fr.alptis.remuneration.accounting.CutoffService.PilotageReport;
import fr.alptis.remuneration.accounting.CutoffService.PilotageRow;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CutoffExtract;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.ForecastBasis;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the cut-off service (section 10.4, FIN-03/FIN-09): the pilotage state read by
 * each maille with the worked figures of the mock-up, and the monthly extraction with its idempotent
 * re-production, the stamping of its entries and its CSV. Runs against UNEP in rolled-back
 * transactions.
 */
@QuarkusTest
class CutoffServiceTest {

    @Inject
    CutoffService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);
    private long seq;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period for a month. */
    private static Period period(LocalDate month) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        return period;
    }

    /** Creates a coverage carrying a product with a forecast basis. */
    private static Coverage coverageWithProduct(String code, String productLabel) {
        Product product = new Product();
        product.entity = unep();
        product.code = "PRD-" + code;
        product.label = productLabel;
        product.forecastBasis = ForecastBasis.ISSUED_PREMIUMS;
        product.persist();
        ProductOption option = new ProductOption();
        option.product = product;
        option.persist();
        Coverage coverage = new Coverage();
        coverage.coverageCode = code;
        coverage.option = option;
        coverage.persist();
        return coverage;
    }

    /** Creates and persists a line of a nature, status, amount, coverage and account. */
    private CommissionLine line(Period period, LineNature nature, LineStatus status, String amount,
                                Coverage coverage, DistributorAccount account) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = nature;
        line.status = status;
        line.amount = new BigDecimal(amount);
        line.coverage = coverage;
        line.account = account;
        line.persist();
        return line;
    }

    /** Creates a distributor account. */
    private DistributorAccount account() {
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = PayeeType.DISTRIBUTOR;
        account.payeeId = ++seq;
        account.persist();
        return account;
    }

    /** Returns the row of a report whose label matches. */
    private static PilotageRow rowOf(PilotageReport report, String label) {
        return report.rows().stream().filter(r -> r.label().equals(label)).findFirst().orElseThrow();
    }

    /** Asserts the six figures of a row. */
    private static void assertRow(PilotageRow row, String forecast, String earned, String adjust, String paid,
                                  String balance, String remaining) {
        assertEquals(0, new BigDecimal(forecast).compareTo(row.forecast()), "forecast");
        assertEquals(0, new BigDecimal(earned).compareTo(row.earned()), "earned");
        assertEquals(0, new BigDecimal(adjust).compareTo(row.adjustments()), "adjustments");
        assertEquals(0, new BigDecimal(paid).compareTo(row.paid()), "paid");
        assertEquals(0, new BigDecimal(balance).compareTo(row.balance()), "balance");
        assertEquals(0, new BigDecimal(remaining).compareTo(row.remaining()), "remaining");
    }

    /**
     * The pilotage state by coverage and by entity reproduces the worked figures: a coverage with a
     * forecast of 180, six collections accrued (90) of which five paid (75), and a second coverage
     * carrying a reprise and a paid escompte advance.
     */
    @Test
    @TestTransaction
    void pilotage_by_coverage_and_entity() {
        Period p = period(M);
        Coverage c1 = coverageWithProduct("C1", "Santé Select");
        DistributorAccount a = account();
        line(p, LineNature.LINEAR, LineStatus.FORECAST, "180.00", c1, a);
        for (int i = 0; i < 5; i++) {
            line(p, LineNature.LINEAR, LineStatus.PAID, "15.00", c1, a);
        }
        line(p, LineNature.LINEAR, LineStatus.EARNED, "15.00", c1, a);
        Coverage c2 = coverageWithProduct("C2", "Prévoyance");
        line(p, LineNature.CLAWBACK, LineStatus.EARNED, "-210.00", c2, null);
        line(p, LineNature.ADVANCE, LineStatus.PAID, "480.00", c2, null);

        PilotageReport byCoverage = service.pilotage("UNEP", LocalDate.of(2026, 9, 30), CutoffView.COVERAGE);
        assertRow(rowOf(byCoverage, "C1"), "180.00", "90.00", "0.00", "75.00", "15.00", "90.00");
        assertRow(rowOf(byCoverage, "C2"), "0.00", "0.00", "-210.00", "480.00", "-690.00", "0.00");

        PilotageReport byEntity = service.pilotage("UNEP", LocalDate.of(2026, 9, 30), CutoffView.ENTITY);
        assertEquals(1, byEntity.rows().size());
        assertRow(byEntity.total(), "180.00", "90.00", "-210.00", "555.00", "-675.00", "90.00");

        // A coverage-less line falls under the dash group in the coverage maille.
        line(p, LineNature.MANUAL, LineStatus.EARNED, "5.00", null, null);
        PilotageReport byCoverage2 = service.pilotage("UNEP", LocalDate.of(2026, 9, 30), CutoffView.COVERAGE);
        assertRow(rowOf(byCoverage2, "—"), "0.00", "0.00", "5.00", "0.00", "5.00", "0.00");
    }

    /**
     * The product and distributor mailles label a line by its product and its account, and fall back
     * to a dash when the coverage, the option, the product or the account is missing.
     */
    @Test
    @TestTransaction
    void product_and_distributor_labels_handle_missing_links() {
        Period p = period(LocalDate.of(2026, 10, 1));
        Coverage withProduct = coverageWithProduct("CX", "PER individuel");
        ProductOption noProductOption = new ProductOption();
        noProductOption.persist();
        Coverage optionNoProduct = new Coverage();
        optionNoProduct.coverageCode = "CY";
        optionNoProduct.option = noProductOption;
        optionNoProduct.persist();
        Coverage noOption = new Coverage();
        noOption.coverageCode = "CZ";
        noOption.persist();
        line(p, LineNature.LINEAR, LineStatus.EARNED, "10.00", withProduct, account());
        line(p, LineNature.LINEAR, LineStatus.EARNED, "20.00", optionNoProduct, null);
        line(p, LineNature.LINEAR, LineStatus.EARNED, "30.00", noOption, null);
        line(p, LineNature.MANUAL, LineStatus.EARNED, "5.00", null, null);

        PilotageReport byProduct = service.pilotage("UNEP", LocalDate.of(2026, 10, 31), CutoffView.PRODUCT);
        assertRow(rowOf(byProduct, "PER individuel"), "0.00", "10.00", "0.00", "0.00", "10.00", "-10.00");
        // The option-without-product, coverage-without-option and coverage-less lines all fall to a dash.
        assertRow(rowOf(byProduct, "—"), "0.00", "50.00", "5.00", "0.00", "55.00", "-50.00");

        PilotageReport byDistributor = service.pilotage("UNEP", LocalDate.of(2026, 10, 31),
                CutoffView.DISTRIBUTOR);
        assertRow(rowOf(byDistributor, "DISTRIBUTOR #" + seq), "0.00", "10.00", "0.00", "0.00", "10.00", "-10.00");
        assertTrue(byDistributor.rows().stream().anyMatch(r -> r.label().equals("—")));
    }

    /**
     * The extraction is produced once: it stamps the period's entries, names and fingerprints the CSV,
     * and a second call returns the same extract. The CSV carries the entry columns.
     */
    @Test
    @TestTransaction
    void extraction_is_produced_once_and_stamps_entries() {
        Period p = period(LocalDate.of(2026, 11, 1));
        DistributorAccount a = account();
        Statement statement = new Statement();
        statement.entity = unep();
        statement.period = p;
        statement.statementNumber = "ST-UNEP-X";
        statement.persist();
        CommissionLine l1 = line(p, LineNature.LINEAR, LineStatus.PAYABLE, "15.00", null, a);
        l1.statement = statement;
        l1.premiumFiscalYear = (short) 2026;
        entry(p, AccountingEvent.EARNING, l1, null);
        PaymentBatch batch = batch(p);
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.amount = new BigDecimal("15.00");
        order.status = PaymentOrderStatus.ISSUED;
        order.endToEndId = null;
        order.persist();
        entry(p, AccountingEvent.PAYMENT, null, order);
        CommissionLine l2 = line(p, LineNature.LINEAR, LineStatus.EARNED, "15.00", null, null);
        entry(p, AccountingEvent.PAYABLE_RECOGNITION, l2, null);
        // An order without a batch leaves the batch-reference column empty.
        PaymentOrder orphan = new PaymentOrder();
        orphan.amount = new BigDecimal("15.00");
        orphan.status = PaymentOrderStatus.ISSUED;
        orphan.persist();
        entry(p, AccountingEvent.PAYMENT_REJECTION, null, orphan);

        CutoffExtract extract = service.extract("UNEP", LocalDate.of(2026, 11, 1));
        assertEquals("UNEP_ECR_202611.csv", extract.fileRef);
        assertEquals(4, AccountingEntry.count("cutoffExtract = ?1", extract));
        CutoffExtract again = service.extract("UNEP", LocalDate.of(2026, 11, 1));
        assertEquals(extract.getId(), again.getId());

        String csv = service.extractCsv("UNEP", LocalDate.of(2026, 11, 1));
        assertTrue(csv.startsWith("entity_code;fiscal_year;accounting_month;"));
        assertTrue(csv.contains("EARNING;"));
        assertTrue(csv.contains("ST-UNEP-X"));
        assertTrue(csv.contains(batch.batchReference));
    }

    /**
     * An extraction CSV requested before the extract exists is not found, and an unknown entity or
     * period is reported as not found.
     */
    @Test
    @TestTransaction
    void unknown_paths_are_not_found() {
        period(LocalDate.of(2026, 12, 1));
        assertThrows(ResourceNotFoundException.class,
                () -> service.extractCsv("UNEP", LocalDate.of(2026, 12, 1)));
        assertThrows(ResourceNotFoundException.class,
                () -> service.pilotage("GHOST", LocalDate.of(2026, 12, 31), CutoffView.ENTITY));
        assertThrows(ResourceNotFoundException.class,
                () -> service.extract("UNEP", LocalDate.of(2030, 1, 1)));
    }

    /** Creates a draft cycle batch for a period. */
    private static PaymentBatch batch(Period period) {
        PaymentBatch batch = new PaymentBatch();
        batch.entity = unep();
        batch.period = period;
        batch.batchType = BatchType.CYCLE;
        batch.batchReference = "CYCLE-UNEP-" + period.periodMonth;
        batch.status = BatchStatus.ISSUED;
        batch.totalAmount = BigDecimal.ZERO;
        batch.persist();
        return batch;
    }

    /** Creates a raw accounting entry for a period, line and order. */
    private static void entry(Period period, AccountingEvent event, CommissionLine line, PaymentOrder order) {
        AccountingEntry entry = new AccountingEntry();
        entry.entity = unep();
        entry.period = period;
        entry.accountingEvent = event;
        entry.journal = "COM";
        entry.entryDate = period.periodMonth.withDayOfMonth(period.periodMonth.lengthOfMonth());
        entry.debitAccount = "428600";
        entry.creditAccount = "467200";
        entry.amount = new BigDecimal("15.00");
        entry.fiscalYear = (short) period.periodMonth.getYear();
        entry.accountingMonth = period.periodMonth;
        entry.commissionLine = line;
        entry.paymentOrder = order;
        if (line != null) {
            entry.premiumFiscalYear = line.premiumFiscalYear;
        }
        entry.persist();
    }
}
