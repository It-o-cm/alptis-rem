package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.DocumentTemplate;
import fr.alptis.remuneration.domain.DocumentType;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DocumentTemplateService}: listing the current template per type, versioning on save,
 * the unknown-type guard and the entity partition (BRD-05). Runs against UNEP in rolled-back
 * transactions.
 */
@QuarkusTest
class DocumentTemplateServiceTest {

    @Inject
    AuditService audit;
    @Inject
    DateTimeProvider clock;

    /** Builds a service for the given visible entities. */
    private DocumentTemplateService service(List<String> entities) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("admin-unep"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        CurrentUser user = new CurrentUser(identity);
        return new DocumentTemplateService(new EntityScope(user), user, audit, clock);
    }

    /** The UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /**
     * A first save creates version 1; a second save of the same type creates version 2, which the list
     * returns as the current template.
     */
    @Test
    @TestTransaction
    void saves_versions_and_lists_the_current_one() {
        DocumentTemplateService svc = service(List.of("UNEP"));
        var v1 = svc.save("UNEP", "STATEMENT", "{\"logo\":\"A\"}");
        assertEquals(1, v1.versionNo());
        var v2 = svc.save("UNEP", "STATEMENT", "{\"logo\":\"B\"}");
        assertEquals(2, v2.versionNo());
        svc.save("UNEP", "TAX_CERTIFICATE", "{\"mentions\":\"X\"}");

        // A template with no validity date (nullable column) is listed with a null validFrom.
        DocumentTemplate dateless = new DocumentTemplate();
        dateless.entity = unep();
        dateless.documentType = DocumentType.MEMBER_STATEMENT;
        dateless.versionNo = 1;
        dateless.content = "{}";
        dateless.persist();

        List<DocumentTemplateService.TemplateView> list = svc.list("UNEP");
        assertEquals(3, list.size());
        var statement = list.stream().filter(t -> "STATEMENT".equals(t.documentType())).findFirst().orElseThrow();
        assertEquals(2, statement.versionNo());
        assertEquals("{\"logo\":\"B\"}", statement.content());
        assertNotNull(statement.validFrom());
        var member = list.stream().filter(t -> "MEMBER_STATEMENT".equals(t.documentType())).findFirst().orElseThrow();
        assertNull(member.validFrom());
        // The versions are kept (append-only): four rows across the three types.
        assertEquals(4, DocumentTemplate.count("entity = ?1", unep()));
    }

    /**
     * An unknown type is rejected, and an out-of-scope entity is not found on both read and write.
     */
    @Test
    @TestTransaction
    void guards_type_and_scope() {
        DocumentTemplateService svc = service(List.of("UNEP"));
        assertThrows(ValidationException.class, () -> svc.save("UNEP", "NOPE", "{}"));
        assertThrows(ResourceNotFoundException.class, () -> svc.list("ALPTIS_ASSURANCES"));
        assertThrows(ResourceNotFoundException.class,
                () -> svc.save("ALPTIS_ASSURANCES", "STATEMENT", "{}"));
        // An unknown entity within scope is also not found.
        assertThrows(ResourceNotFoundException.class, () -> service(List.of("*")).list("GHOST"));
    }
}
