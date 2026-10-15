package fr.alptis.remuneration.security;

import fr.alptis.remuneration.api.TestDatabaseReset;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.RoleMapping;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SecurityBootstrap} seeded the entities and role mappings at startup, and
 * that seeding again is idempotent. The reset commits (it toggles H2 referential integrity), so the
 * reads run in their own transactions and the baseline is left clean for the following tests.
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
    void seed_is_idempotent() {
        reset.reset();

        long mappings = QuarkusTransaction.requiringNew().call(() -> {
            assertEquals(4, LegalEntity.count());
            long count = RoleMapping.count();
            assertTrue(count >= 17);
            return count;
        });

        bootstrap.seed();

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(4, LegalEntity.count());
            assertEquals(mappings, RoleMapping.count());
            assertEquals(4, SecurityBootstrap.entityCodes().size());
        });
    }
}
