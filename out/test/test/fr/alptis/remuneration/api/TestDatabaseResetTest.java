package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.RoleMapping;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link TestDatabaseReset} empties the database and restores the seeded baseline.
 * The reset commits (it toggles H2 referential integrity, which forces a commit), so the test runs
 * each step in its own transaction rather than a rolled-back one, and the reset itself leaves the
 * shared instance on its clean baseline for the following tests.
 */
@QuarkusTest
class TestDatabaseResetTest {

    /** The reset helper under test. */
    @Inject
    TestDatabaseReset reset;

    /**
     * Verifies that after a reset an extra row is gone and the baseline entities and mappings are back.
     */
    @Test
    void reset_clears_extra_rows_and_restores_the_seeded_baseline() {
        QuarkusTransaction.requiringNew().run(() -> {
            RoleMapping sentinel = new RoleMapping();
            sentinel.directoryGroup = "grp-sentinel";
            sentinel.role = Role.VIEWER;
            sentinel.persist();
        });
        assertEquals(1, QuarkusTransaction.requiringNew()
                .call(() -> RoleMapping.count("directoryGroup", "grp-sentinel")));

        reset.reset();

        QuarkusTransaction.requiringNew().run(() -> {
            assertNull(RoleMapping.find("directoryGroup", "grp-sentinel").firstResult());
            assertEquals(4, LegalEntity.count());
            assertTrue(RoleMapping.count() >= 17);
        });
    }
}
