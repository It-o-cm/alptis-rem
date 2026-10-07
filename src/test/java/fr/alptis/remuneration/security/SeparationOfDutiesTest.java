package fr.alptis.remuneration.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SeparationOfDuties}, covering each leg of the guard, including the
 * reference acceptance case CR-71.
 */
class SeparationOfDutiesTest {

    /** The guard under test. */
    private final SeparationOfDuties guard = new SeparationOfDuties();

    /**
     * CR-71: an administrator who created a rule version cannot approve it himself.
     */
    @Test
    void cr71_admin_cannot_approve_own_rule_version() {
        SeparationOfDutiesException thrown = assertThrows(SeparationOfDutiesException.class,
                () -> guard.ensureDifferentApprover("admin-alptis", "admin-alptis"));
        assertTrue(thrown.getMessage().contains("Séparation des tâches"));
    }

    /**
     * A second administrator may approve the version (different approver leg).
     */
    @Test
    void allows_a_different_approver() {
        assertDoesNotThrow(() -> guard.ensureDifferentApprover("admin-alptis", "admin2-alptis"));
    }

    /**
     * A null creator (no recorded author) does not block approval (null-creator leg).
     */
    @Test
    void allows_a_null_creator() {
        assertDoesNotThrow(() -> guard.ensureDifferentApprover(null, "admin-alptis"));
    }
}
