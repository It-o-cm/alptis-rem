package fr.alptis.remuneration.security;

import fr.alptis.remuneration.api.TestDatabaseReset;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RoleResolver} against the role mappings seeded at startup. The reset commits (it
 * toggles H2 referential integrity), so each resolution runs in its own transaction rather than a
 * rolled-back one, and the baseline is left clean for the following tests.
 */
@QuarkusTest
class RoleResolverTest {

    /** Resolver under test. */
    @Inject
    RoleResolver resolver;

    /** Restores the baseline mappings before resolving. */
    @Inject
    TestDatabaseReset reset;

    /**
     * Verifies that an entity-scoped group yields its role and entity.
     */
    @Test
    void resolves_an_entity_scoped_group() {
        reset.reset();
        RoleResolver.Grants grants = QuarkusTransaction.requiringNew()
                .call(() -> resolver.resolve(List.of("grp-op-alptis")));
        assertTrue(grants.roles().contains("OPERATOR"));
        assertEquals(List.of("ALPTIS_ASSURANCES"), grants.entities());
    }

    /**
     * Verifies that a group-level mapping (null entity) yields every entity.
     */
    @Test
    void resolves_a_group_level_mapping_as_all_entities() {
        reset.reset();
        RoleResolver.Grants grants = QuarkusTransaction.requiringNew()
                .call(() -> resolver.resolve(List.of("grp-it")));
        assertTrue(grants.roles().contains("IT_ADMIN"));
        assertTrue(grants.entities().contains("*"));
    }

    /**
     * Verifies that no groups yield no roles and no entities.
     */
    @Test
    void resolves_nothing_for_no_groups() {
        reset.reset();
        RoleResolver.Grants grants = QuarkusTransaction.requiringNew()
                .call(() -> resolver.resolve(List.of()));
        assertTrue(grants.roles().isEmpty());
        assertTrue(grants.entities().isEmpty());
    }
}
