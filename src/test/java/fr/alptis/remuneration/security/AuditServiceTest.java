package fr.alptis.remuneration.security;

import fr.alptis.remuneration.domain.AuditEvent;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link AuditService}: the first trace starts the chain and each next trace is chained to
 * the previous one by fingerprint.
 */
@QuarkusTest
class AuditServiceTest {

    /** Service under test. */
    @Inject
    AuditService audit;

    /**
     * Verifies that each trace is chained to the previous one by fingerprint. (The genesis branch,
     * when the trail is empty, is covered by the resource tests, which reset the trail first.)
     */
    @Test
    @TestTransaction
    void records_a_hash_chained_trail() {
        AuditEvent first = audit.record("admin-alptis", null, "UPDATE_ENTITY", "LegalEntity", 1L,
                null, "{\"minPaymentAmount\":10.00}");
        assertNotNull(first.hash);
        assertEquals(64, first.hash.length());
        assertEquals("admin-alptis", first.userId);

        AuditEvent second = audit.record("admin-alptis", null, "UPDATE_ENTITY", "LegalEntity", 1L,
                "{\"minPaymentAmount\":10.00}", "{\"minPaymentAmount\":20.00}");
        assertEquals(first.hash, second.previousHash);
        assertNotEquals(first.hash, second.hash);
        assertEquals(64, second.hash.length());
    }
}
