package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportMapping;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Manages file mappings (ING-02): lists them, adds a new version (a mapping is never overwritten,
 * each change is a new version), and tests a definition against a sample file, showing the pivot
 * result (INT-13). Scoped by entity; changes are audited.
 */
@ApplicationScoped
public class MappingAdminService {

    private final EntityScope scope;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final MappingEngine engine;

    /**
     * Creates the service.
     *
     * @param scope       the entity partition
     * @param currentUser the current user
     * @param audit       the audit trail
     * @param engine      the mapping engine
     */
    @Inject
    public MappingAdminService(EntityScope scope, CurrentUser currentUser, AuditService audit, MappingEngine engine) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
        this.engine = engine;
    }

    /**
     * Lists the mappings visible to the user, latest version first.
     *
     * @return the mapping views
     */
    @Transactional
    public List<MappingView> list() {
        List<ImportMapping> mappings = scope.unrestricted()
                ? ImportMapping.listAll(Sort.by("flowCode").and("versionNo", Sort.Direction.Descending))
                : ImportMapping.list("entity.code in ?1 order by flowCode, versionNo desc", scope.visibleCodes());
        return mappings.stream().map(MappingAdminService::toView).toList();
    }

    /**
     * Adds a new version of a mapping for an entity, flow and source system.
     *
     * @param entityCode   the entity code
     * @param flowCode     the flow code
     * @param sourceSystem the source system
     * @param definition   the mapping definition (JSON)
     * @return the created mapping view
     * @throws ResourceNotFoundException if the entity is out of scope
     */
    @Transactional
    public MappingView addVersion(String entityCode, String flowCode, String sourceSystem, String definition) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null || !scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        int nextVersion = 1 + (int) ImportMapping.count("entity = ?1 and flowCode = ?2 and sourceSystem = ?3",
                entity, flowCode, sourceSystem);
        ImportMapping mapping = new ImportMapping();
        mapping.entity = entity;
        mapping.flowCode = flowCode;
        mapping.sourceSystem = sourceSystem;
        mapping.versionNo = nextVersion;
        mapping.definition = definition;
        mapping.persist();
        audit.record(currentUser.username(), entity, "ADD_MAPPING_VERSION", "ImportMapping",
                mapping.getId(), null, "{\"flowCode\":\"" + flowCode + "\",\"versionNo\":" + nextVersion + "}");
        return toView(mapping);
    }

    /**
     * Tests a mapping definition against a sample file, returning the pivot preview (INT-13).
     *
     * @param definition the mapping definition (JSON)
     * @param sample     the sample file content
     * @return the pivot preview rows and the filtered count
     */
    public MappingTestResult test(String definition, String sample) {
        MappingEngine.Result result = engine.apply(definition, CsvFile.parse(sample));
        List<Map<String, String>> flat = result.groups().stream().flatMap(List::stream).toList();
        return new MappingTestResult(flat, result.groups().size(), result.filtered());
    }

    /**
     * Builds the view of a mapping.
     *
     * @param mapping the mapping
     * @return its view
     */
    private static MappingView toView(ImportMapping mapping) {
        return new MappingView(mapping.getId(), mapping.entity.code, mapping.flowCode, mapping.sourceSystem,
                mapping.versionNo, mapping.definition);
    }

    /**
     * Exchange view of a file mapping.
     *
     * @param id           the mapping id
     * @param entityCode   the entity code
     * @param flowCode     the flow code
     * @param sourceSystem the source system
     * @param versionNo    the version number
     * @param definition   the mapping definition (JSON)
     */
    public record MappingView(long id, String entityCode, String flowCode, String sourceSystem, Integer versionNo,
                              String definition) {
    }

    /**
     * Result of testing a mapping: the produced pivot rows, the number of kept source lines and the
     * number of filtered lines.
     *
     * @param rows     the pivot preview rows
     * @param kept     the number of kept source lines (conforming)
     * @param filtered the number of filtered lines
     */
    public record MappingTestResult(List<Map<String, String>> rows, int kept, int filtered) {
    }
}
