package fr.alptis.remuneration.security;

import fr.alptis.remuneration.api.TestDatabaseReset;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.RoleMapping;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SecurityBootstrap} seeded the entities and role mappings at startup, and
 * that seeding again is idempotent.
 */
@QuarkusTest
class SecurityBootstrapTest {

    /** Bootstrap under test. */
    @Inject
    SecurityBootstrap bootstrap;

    /** Restores the baseline before asserting. */
    @Inject
    TestDatabaseReset reset;

    /**
     * Verifies the seeded entities and mappings, and that a second seed changes nothing.
     */
    @Test
    @TestTransaction
    void seed_is_idempotent() {
        reset.reset();
        assertEquals(4, LegalEntity.count());
        long mappings = RoleMapping.count();
        assertTrue(mappings >= 17);

        bootstrap.seed();

        assertEquals(4, LegalEntity.count());
        assertEquals(mappings, RoleMapping.count());
        assertEquals(4, SecurityBootstrap.entityCodes().size());
    }
}
