package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportBatch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Imports a free (non-pivot) events file through a parameterised mapping (ING-02, section 9.4):
 * the mapping turns the source lines into pivot event groups, which are then journalled and
 * controlled by the events importer. Idempotency is computed on the original file, and the lines
 * dropped by the mapping are counted as filtered (ING-05).
 */
@ApplicationScoped
public class MappingImportService {

    private final MappingEngine mappingEngine;
    private final EventImportService events;

    /**
     * Creates the service.
     *
     * @param mappingEngine the mapping transformer
     * @param events        the events importer
     */
    @Inject
    public MappingImportService(MappingEngine mappingEngine, EventImportService events) {
        this.mappingEngine = mappingEngine;
        this.events = events;
    }

    /**
     * Imports a mapped events file.
     *
     * @param entityCode     the entity code
     * @param sourceSystem   the source system
     * @param fileName       the file name
     * @param content        the raw source content
     * @param definitionJson the mapping definition (JSON)
     * @return the journalled batch
     */
    public ImportBatch importMappedEvents(String entityCode, String sourceSystem, String fileName, String content,
                                          String definitionJson) {
        MappingEngine.Result result = mappingEngine.apply(definitionJson, CsvFile.parse(content));
        return events.importGroups(entityCode, sourceSystem, fileName, fileName + "\n" + content,
                result.groups(), result.filtered());
    }
}
