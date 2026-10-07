package fr.alptis.remuneration.security;

import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CurrentUser}, built with hand-made security identities.
 */
class CurrentUserTest {

    /**
     * Verifies name, roles and the entity list for a single-entity user.
     */
    @Test
    void exposes_name_roles_and_entities_of_a_scoped_user() {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("operator-alptis"))
                .addRole("OPERATOR")
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, List.of("ALPTIS_ASSURANCES"))
                .build();
        CurrentUser user = new CurrentUser(identity);

        assertEquals("operator-alptis", user.username());
        assertTrue(user.roles().contains("OPERATOR"));
        assertTrue(user.hasRole("OPERATOR"));
        assertFalse(user.hasRole("BUSINESS_ADMIN"));
        assertEquals(List.of("ALPTIS_ASSURANCES"), user.entities());
        assertFalse(user.seesAllEntities());
    }

    /**
     * Verifies that a group-level user ({@code *}) sees every entity.
     */
    @Test
    void recognises_a_group_level_user() {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("auditor"))
                .addRole("AUDITOR")
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, List.of("*"))
                .build();
        CurrentUser user = new CurrentUser(identity);

        assertTrue(user.seesAllEntities());
    }

    /**
     * Verifies that a missing entities attribute yields an empty list.
     */
    @Test
    void defaults_to_no_entities_when_attribute_is_absent() {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("nobody"))
                .build();
        CurrentUser user = new CurrentUser(identity);

        assertTrue(user.entities().isEmpty());
        assertFalse(user.seesAllEntities());
    }
}
