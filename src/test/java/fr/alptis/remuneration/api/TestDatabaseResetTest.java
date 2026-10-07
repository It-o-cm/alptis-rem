package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.RoleMapping;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link TestDatabaseReset} empties the database and restores the seeded baseline.
 * Run inside a test transaction so the wipe and re-seed roll back and leave the shared instance
 * untouched.
 */
@QuarkusTest
class TestDatabaseResetTest {

    /** The reset helper under test. */
    @Inject
    TestDatabaseReset reset;

    /**
     * Verifies that after a reset the baseline entities and mappings are present.
     */
    @Test
    @TestTransaction
    void reset_clears_extra_rows_and_restores_the_seeded_baseline() {
        RoleMapping sentinel = new RoleMapping();
        sentinel.directoryGroup = "grp-sentinel";
        sentinel.role = Role.VIEWER;
        sentinel.persist();
        assertTrue(RoleMapping.count("directoryGroup", "grp-sentinel") == 1);

        reset.reset();

        assertNull(RoleMapping.find("directoryGroup", "grp-sentinel").firstResult());
        assertEquals(4, LegalEntity.count());
        assertTrue(RoleMapping.count() >= 17);
    }
}
