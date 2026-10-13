package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.ExportBatch;
import fr.alptis.remuneration.domain.ExportBatchStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.reporting.ExportService.ExportObject;
import fr.alptis.remuneration.reporting.ExportService.ExportView;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the export service (sections 9.11, 9.12, REP-04/REP-05): the Data export and the
 * on-demand list export, both inscribed in the journal and re-downloadable identically, and the
 * account export with its balances. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class ExportServiceTest {

    @Inject
    ExportService service;

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

    /** Creates a distributor account. */
    private DistributorAccount account() {
        DistributorAccount account = new DistributorAccount();
        account.entity = unep();
        account.payeeType = PayeeType.DISTRIBUTOR;
        account.payeeId = ++seq;
        account.persist();
        return account;
    }

    /** Creates a line with optional account, basis and rate. */
    private CommissionLine line(Period period, String amount, DistributorAccount account, String basis,
                                String rate) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal(amount);
        line.account = account;
        line.basisAmount = basis == null ? null : new BigDecimal(basis);
        line.rate = rate == null ? null : new BigDecimal(rate);
        line.persist();
        return line;
    }

    /**
     * The Data export of the commission lines journals a full snapshot and is re-downloaded
     * identically; the CSV carries the header, a line with its payee and basis, and a bare line.
     */
    @Test
    @TestTransaction
    void data_export_journals_and_redownloads_identically() {
        Period p = period(M);
        line(p, "15.00", account(), "100.00", "0.15");
        line(p, "20.00", null, null, null);

        ExportBatch batch = service.dataExport("UNEP", ExportObject.COMMISSION_LINES, "fin");
        assertEquals("OUT-05:COMMISSION_LINES", batch.flowCode);
        assertTrue(batch.fileName.startsWith("UNEP_DATA_commission_lines_"));
        assertTrue(batch.fileName.endsWith(".csv"));
        assertEquals(2, batch.linesWritten);
        assertEquals(0, new BigDecimal("35.00").compareTo(batch.totalAmount));
        assertEquals(ExportBatchStatus.GENERATED, batch.status);

        String csv = service.content(batch.getId());
        assertTrue(csv.startsWith("line_id;nature;status;basis;rate;amount;payee_type;payee_id\n"));
        assertTrue(csv.contains(";100.00;0.15;15.00;DISTRIBUTOR;"));
        assertTrue(csv.contains(";;;20.00;;\n"));
        assertEquals(batch.fileHash, io.quarkus.runtime.util.HashUtil.sha256(csv));
    }

    /**
     * The on-demand export of a period carries the month in its flow and file name and restricts the
     * snapshot to the period.
     */
    @Test
    @TestTransaction
    void on_demand_export_carries_the_period() {
        Period september = period(M);
        line(september, "15.00", account(), null, null);
        Period october = period(LocalDate.of(2026, 10, 1));
        line(october, "99.00", account(), null, null);

        ExportBatch batch = service.listExport("UNEP", M, ExportObject.COMMISSION_LINES, "op");
        assertEquals("OUT-04:COMMISSION_LINES:2026-09-01", batch.flowCode);
        assertEquals("UNEP_COMMISSION_LINES_202609.csv", batch.fileName);
        assertEquals(1, batch.linesWritten);

        String csv = service.content(batch.getId());
        assertTrue(csv.contains(";15.00;"));
        assertTrue(!csv.contains(";99.00;"));
    }

    /**
     * The accounts export lists each account with its balance, and a period-less list export covers
     * every period.
     */
    @Test
    @TestTransaction
    void accounts_export_lists_balances() {
        account();
        account();

        ExportBatch batch = service.listExport("UNEP", null, ExportObject.ACCOUNTS, "fin");
        assertEquals("OUT-04:ACCOUNTS", batch.flowCode);
        assertEquals("UNEP_ACCOUNTS_ALL.csv", batch.fileName);
        assertEquals(2, batch.linesWritten);

        String csv = service.content(batch.getId());
        assertTrue(csv.startsWith("payee_type;payee_id;blocked;balance\n"));
        assertTrue(csv.contains("DISTRIBUTOR;"));
        assertTrue(csv.contains(";false;0.00\n"));
    }

    /**
     * REP-05: the Data export is incremental when a window is given — only the objects created or
     * modified on or after the window are fed, across commission lines, accounts and control results.
     */
    @Test
    @TestTransaction
    void data_export_is_incremental_since_a_window() {
        Period p = period(M);
        java.time.ZoneOffset off = java.time.OffsetDateTime.now().getOffset();
        // Commission lines: one created in the window, one before it, one modified in the window.
        stampLine(p, "100.00", LocalDate.of(2026, 9, 15), null);
        stampLine(p, "50.00", LocalDate.of(2026, 1, 10), null);
        stampLine(p, "70.00", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 9, 20));
        // An account created in the window and one before it.
        stampAccount(LocalDate.of(2026, 9, 12));
        stampAccount(LocalDate.of(2026, 1, 5));
        // A control result created in the window.
        stampControlResult(p, LocalDate.of(2026, 9, 18));
        stampControlResult(p, LocalDate.of(2026, 1, 8));

        ExportBatch lines = service.dataExport("UNEP", ExportObject.COMMISSION_LINES, LocalDate.of(2026, 9, 1), "fin");
        assertEquals("OUT-05:COMMISSION_LINES:INCR:2026-09-01", lines.flowCode);
        assertEquals(2, lines.linesWritten);
        String csv = service.content(lines.getId());
        assertTrue(csv.contains(";100.00;") && csv.contains(";70.00;") && !csv.contains(";50.00;"));

        assertEquals(1, service.dataExport("UNEP", ExportObject.ACCOUNTS, LocalDate.of(2026, 9, 1), "fin")
                .linesWritten);
        assertEquals(1, service.dataExport("UNEP", ExportObject.CONTROL_RESULTS, LocalDate.of(2026, 9, 1), "fin")
                .linesWritten);
        // The offset is read from the clock, aligning the stored timestamps.
        assertEquals(java.time.OffsetDateTime.now().getOffset(), off);
    }

    /** A commission line stamped with explicit created/updated dates. */
    private void stampLine(Period period, String amount, LocalDate createdOn, LocalDate updatedOn) {
        java.time.ZoneOffset off = java.time.OffsetDateTime.now().getOffset();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal(amount);
        line.setCreatedAt(createdOn.atStartOfDay().atOffset(off));
        if (updatedOn != null) {
            line.setUpdatedAt(updatedOn.atStartOfDay().atOffset(off));
        }
        line.persist();
    }

    /** An account stamped with an explicit creation date. */
    private void stampAccount(LocalDate createdOn) {
        DistributorAccount account = account();
        account.setCreatedAt(createdOn.atStartOfDay().atOffset(java.time.OffsetDateTime.now().getOffset()));
    }

    /** A warning control result stamped with an explicit creation date. */
    private void stampControlResult(Period period, LocalDate createdOn) {
        controlResult(period, "CALC-VAR-" + createdOn.getDayOfYear(),
                fr.alptis.remuneration.domain.ControlSeverity.WARNING, "COVERAGE", 1L, "x", "y");
        fr.alptis.remuneration.domain.ControlResult r = fr.alptis.remuneration.domain.ControlResult
                .<fr.alptis.remuneration.domain.ControlResult>find("control.code = ?1",
                        "CALC-VAR-" + createdOn.getDayOfYear()).firstResult();
        r.setCreatedAt(createdOn.atStartOfDay().atOffset(java.time.OffsetDateTime.now().getOffset()));
    }

    /**
     * CTL-06: the control-results export lists the level-2 (warning) and level-3 (blocking) results of
     * the period, excluding the level-1 (information) ones.
     */
    @Test
    @TestTransaction
    void control_results_export_lists_level_2_and_3() {
        Period p = period(M);
        controlResult(p, "CALC-VAR", fr.alptis.remuneration.domain.ControlSeverity.WARNING, "COVERAGE", 7L, "3100",
                "2000");
        controlResult(p, "PAY-BAL", fr.alptis.remuneration.domain.ControlSeverity.BLOCKING, "BATCH", 9L, "100", "90");
        controlResult(p, "CALC-TREND", fr.alptis.remuneration.domain.ControlSeverity.INFO, "ENTITY", null, "x", null);
        // A warning result whose object, observed and expected are all absent (empty cells in the CSV).
        controlResult(p, "COMPL-FLOW", fr.alptis.remuneration.domain.ControlSeverity.WARNING, null, null, null, null);

        ExportBatch batch = service.listExport("UNEP", M, ExportObject.CONTROL_RESULTS, "fin");
        assertEquals(3, batch.linesWritten);
        String csv = service.content(batch.getId());
        assertTrue(csv.startsWith("control_code;severity;object_type;object_id;observed;expected;decision\n"));
        assertTrue(csv.contains("CALC-VAR;WARNING;COVERAGE;7;3100;2000;"));
        assertTrue(csv.contains("PAY-BAL;BLOCKING;BATCH;9;100;90;"));
        assertTrue(csv.contains("COMPL-FLOW;WARNING;;;;;"));
        assertTrue(!csv.contains("CALC-TREND"));

        // The all-periods Data export (no month) also lists the level-2/3 results.
        ExportBatch all = service.dataExport("UNEP", ExportObject.CONTROL_RESULTS, "fin");
        assertEquals(3, all.linesWritten);
    }

    /** Persists a control definition and one result of the given severity in a period. */
    private void controlResult(Period period, String code, fr.alptis.remuneration.domain.ControlSeverity severity,
                               String objectType, Long objectId, String observed, String expected) {
        fr.alptis.remuneration.domain.ControlDefinition def = new fr.alptis.remuneration.domain.ControlDefinition();
        def.entity = unep();
        def.code = code;
        def.label = code;
        def.family = fr.alptis.remuneration.domain.ControlFamily.CALCULATION;
        def.severity = severity;
        def.active = Boolean.TRUE;
        def.versionNo = 1;
        def.validFrom = M;
        def.persist();
        fr.alptis.remuneration.domain.ControlResult r = new fr.alptis.remuneration.domain.ControlResult();
        r.period = period;
        r.control = def;
        r.severity = severity;
        r.objectType = objectType;
        r.objectId = objectId;
        r.observedValue = observed;
        r.expectedValue = expected;
        r.decision = fr.alptis.remuneration.domain.ControlDecision.OPEN;
        r.persist();
    }

    /**
     * The journal lists an entity's exports newest first.
     */
    @Test
    @TestTransaction
    void journal_lists_newest_first() {
        service.dataExport("UNEP", ExportObject.ACCOUNTS, "fin");
        service.dataExport("UNEP", ExportObject.COMMISSION_LINES, "fin");

        List<ExportView> journal = service.list("UNEP");
        assertTrue(journal.size() >= 2);
        assertEquals("OUT-05:COMMISSION_LINES", journal.get(0).flowCode());
    }

    /**
     * An unknown export batch and an unknown entity are reported as not found.
     */
    @Test
    @TestTransaction
    void unknown_paths_are_not_found() {
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class,
                () -> service.content(999999L));
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class,
                () -> service.dataExport("GHOST", ExportObject.ACCOUNTS, "fin"));
    }
}
