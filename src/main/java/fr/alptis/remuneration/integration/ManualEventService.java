package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportBatch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * Creates a single business event or a single complementary datum (a coverage) entered by hand at
 * the screen (ING-07). Each goes through the same controls and journal as an imported one, so a
 * manual entry is traced and idempotent by its source key, and the next calculation picks it up
 * (INT-11).
 */
@ApplicationScoped
public class ManualEventService {

    /** The events importer, reused to control and journal the manual event. */
    private final EventImportService events;

    /** The coverages importer, reused to control and journal a manual complementary datum. */
    private final CoverageImportService coverages;

    /**
     * Creates the service.
     *
     * @param events    the events importer
     * @param coverages the coverages importer
     */
    @Inject
    public ManualEventService(EventImportService events, CoverageImportService coverages) {
        this.events = events;
        this.coverages = coverages;
    }

    /**
     * Records one manually-entered event.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system to attribute the event to
     * @param fields       the pivot event fields (source_key, event_type, contract_number, ...)
     * @return the journalled batch (one line)
     */
    public ImportBatch create(String entityCode, String sourceSystem, Map<String, String> fields) {
        String sourceKey = fields.getOrDefault("source_key", "");
        List<List<Map<String, String>>> groups = List.of(List.of(fields));
        return events.importGroups(entityCode, sourceSystem, "manual-" + sourceKey, "manual:" + sourceKey, groups, 0);
    }

    /**
     * Records one manually-entered complementary datum: a coverage (garantie) in the pivot format,
     * journalled and controlled like an imported one (ING-07).
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system to attribute the datum to
     * @param fields       the pivot coverage fields (contract_number, coverage_code, product_code, ...)
     * @return the journalled batch (one line)
     */
    public ImportBatch createComplementaryData(String entityCode, String sourceSystem, Map<String, String> fields) {
        String key = fields.getOrDefault("coverage_code", fields.getOrDefault("contract_number", ""));
        return coverages.importCoverageRows(entityCode, sourceSystem, "manual-coverage-" + key,
                "manual-coverage:" + key, List.of(fields));
    }
}
