package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.ExportBatch;
import fr.alptis.remuneration.domain.ExportBatchStatus;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.RunStatus;
import fr.alptis.remuneration.domain.RunType;
import fr.alptis.remuneration.reporting.SupervisionService.Health;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests of the supervision (OUT-07, NFR-16): with an import, an export and a calculation present, the
 * health reports their timestamps and a healthy status. Runs against UNEP in rolled-back
 * transactions.
 */
@QuarkusTest
class SupervisionServiceTest {

    @Inject
    SupervisionService service;
    @Inject
    DateTimeProvider clock;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /**
     * The health reports the last import, export and calculation timestamps and a healthy status.
     */
    @Test
    @TestTransaction
    void reports_the_treatment_metrics() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = LocalDate.of(2026, 9, 1);
        period.state = PeriodState.CONTROLLED;
        period.persist();
        ImportBatch importBatch = new ImportBatch();
        importBatch.entity = unep();
        importBatch.flowCode = "events";
        importBatch.sourceSystem = "SYS";
        importBatch.fileName = "f.csv";
        importBatch.fileHash = "h";
        importBatch.receivedAt = clock.now();
        importBatch.status = ImportBatchStatus.LOADED;
        importBatch.persist();
        ExportBatch exportBatch = new ExportBatch();
        exportBatch.entity = unep();
        exportBatch.flowCode = "OUT-05";
        exportBatch.fileName = "e.csv";
        exportBatch.fileHash = "h";
        exportBatch.generatedAt = clock.now();
        exportBatch.status = ExportBatchStatus.GENERATED;
        exportBatch.persist();
        CalculationRun run = new CalculationRun();
        run.period = period;
        run.runType = RunType.ACTUAL;
        run.status = RunStatus.COMPLETED;
        run.finishedAt = clock.now();
        run.persist();

        Health health = service.health();
        assertEquals("UP", health.status());
        assertNotNull(health.lastImportAt());
        assertNotNull(health.lastExportAt());
        assertNotNull(health.lastCalcAt());
    }
}
