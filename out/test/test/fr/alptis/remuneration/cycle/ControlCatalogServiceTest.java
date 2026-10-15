package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the control-catalogue seeding (CTL-02, step 9): the delivered controls are created once
 * for an entity and a null entity is ignored.
 */
@QuarkusTest
class ControlCatalogServiceTest {

    @Inject
    ControlCatalogService service;

    /**
     * Seeding a fresh entity creates the delivered controls once; a second pass and a null entity add
     * nothing.
     */
    @Test
    @TestTransaction
    void seeds_once() {
        LegalEntity entity = new LegalEntity();
        entity.code = "CTL_TEST";
        entity.name = "Contrôles";
        entity.persist();

        assertEquals(33, service.seed(entity));
        assertEquals(33, ControlDefinition.count("entity = ?1", entity));
        assertEquals(0, service.seed(entity));
        assertEquals(0, service.seed(null));
    }
}
