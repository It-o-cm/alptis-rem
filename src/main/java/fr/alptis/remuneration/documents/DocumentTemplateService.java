package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.DocumentTemplate;
import fr.alptis.remuneration.domain.DocumentType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read and edit side of the document templates (BRD-05): the business administrator edits a model's
 * content (logo, mentions, columns, order); each save adds a new version, the current one being the
 * highest. Partitioned by entity; changes are historised in the audit journal (ADM-04).
 */
@ApplicationScoped
public class DocumentTemplateService {

    private final EntityScope scope;

    private final CurrentUser currentUser;

    private final AuditService audit;

    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param scope       the entity partition
     * @param currentUser the current user
     * @param audit       the audit journal
     * @param clock       the application date/time provider
     */
    @Inject
    public DocumentTemplateService(EntityScope scope, CurrentUser currentUser, AuditService audit,
                                   DateTimeProvider clock) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Lists the current document templates of an entity (BRD-05): the highest version of each type.
     *
     * @param entityCode the entity code
     * @return the current template views, by type
     * @throws ResourceNotFoundException if the entity is out of the caller's partition
     */
    @Transactional
    public List<TemplateView> list(String entityCode) {
        LegalEntity entity = requireEntity(entityCode);
        Map<DocumentType, DocumentTemplate> current = new LinkedHashMap<>();
        for (DocumentTemplate template : DocumentTemplate.<DocumentTemplate>list(
                "entity = ?1 order by documentType, versionNo", entity)) {
            current.put(template.documentType, template);
        }
        List<TemplateView> views = new ArrayList<>();
        for (DocumentTemplate template : current.values()) {
            views.add(new TemplateView(template.documentType.name(), template.versionNo, template.content,
                    template.validFrom == null ? null : template.validFrom.toString()));
        }
        return views;
    }

    /**
     * Saves a new version of a document template (BRD-05): the business administrator's edited content
     * becomes the highest version of the type.
     *
     * @param entityCode   the entity code
     * @param documentType the template type name
     * @param content      the edited content (logo, mentions, columns, order)
     * @return the saved template view
     * @throws ResourceNotFoundException if the entity is out of the caller's partition
     * @throws ValidationException       if the type is unknown
     */
    @Transactional
    public TemplateView save(String entityCode, String documentType, String content) {
        LegalEntity entity = requireEntity(entityCode);
        DocumentType type = parseType(documentType);
        DocumentTemplate latest = DocumentTemplate.<DocumentTemplate>find(
                "entity = ?1 and documentType = ?2 order by versionNo desc", entity, type).firstResult();
        int nextVersion = latest == null ? 1 : latest.versionNo + 1;
        DocumentTemplate template = new DocumentTemplate();
        template.entity = entity;
        template.documentType = type;
        template.versionNo = nextVersion;
        template.content = content;
        template.validFrom = clock.now().toLocalDate();
        template.setCreatedAt(clock.now());
        template.persist();
        audit.record(currentUser.username(), entity, "EDIT_DOCUMENT_TEMPLATE", "DocumentTemplate",
                template.getId(), latest == null ? null : "\"v" + latest.versionNo + "\"", "\"v" + nextVersion + "\"");
        return new TemplateView(type.name(), nextVersion, content, template.validFrom.toString());
    }

    /**
     * Loads an entity by code, enforcing the entity partition.
     *
     * @param entityCode the entity code
     * @return the entity
     */
    private LegalEntity requireEntity(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        return entity;
    }

    /**
     * Parses a document-type name.
     *
     * @param documentType the type name
     * @return the document type
     * @throws ValidationException if the name is unknown
     */
    private static DocumentType parseType(String documentType) {
        try {
            return DocumentType.valueOf(documentType);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Type de document inconnu : " + documentType);
        }
    }

    /**
     * A document template (BRD-05).
     *
     * @param documentType the template type
     * @param versionNo    the version number
     * @param content      the content (logo, mentions, columns, order)
     * @param validFrom    the validity start (ISO), possibly null
     */
    public record TemplateView(String documentType, Integer versionNo, String content, String validFrom) {
    }
}
