package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.ExportBatch;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;

/**
 * Treatment and flow supervision (section 3, OUT-07, NFR-16): a health point and the metrics of the
 * processing — pending and held events, open periods, and the timestamps of the last import, export
 * and calculation. It carries no amount (the technical administrator has no right on amounts).
 */
@ApplicationScoped
public class SupervisionService {

    /**
     * Returns the current health and metrics of the treatments and flows.
     *
     * @return the health view
     */
    public Health health() {
        long pending = BusinessEvent.count("processingStatus = ?1", ProcessingStatus.PENDING);
        long held = BusinessEvent.count("processingStatus = ?1", ProcessingStatus.ON_HOLD);
        long openPeriods = Period.count("state <> ?1", PeriodState.CLOSED);
        ImportBatch lastImport = ImportBatch.<ImportBatch>find("order by receivedAt desc").firstResult();
        ExportBatch lastExport = ExportBatch.<ExportBatch>find("order by generatedAt desc").firstResult();
        CalculationRun lastCalc = CalculationRun.<CalculationRun>find("order by finishedAt desc").firstResult();
        return new Health("UP", pending, held, openPeriods,
                lastImport == null ? null : lastImport.receivedAt,
                lastExport == null ? null : lastExport.generatedAt,
                lastCalc == null ? null : lastCalc.finishedAt);
    }

    /**
     * The health and metrics of the treatments and flows.
     *
     * @param status       the overall status
     * @param pendingEvents the number of pending events
     * @param heldEvents   the number of held events
     * @param openPeriods  the number of non-closed periods
     * @param lastImportAt the last import timestamp, or null
     * @param lastExportAt the last export timestamp, or null
     * @param lastCalcAt   the last finished calculation timestamp, or null
     */
    public record Health(String status, long pendingEvents, long heldEvents, long openPeriods,
                         OffsetDateTime lastImportAt, OffsetDateTime lastExportAt, OffsetDateTime lastCalcAt) {
    }
}
