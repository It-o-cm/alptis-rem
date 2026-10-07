package fr.alptis.remuneration.security;

import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link EntityScope}, built with hand-made security identities.
 */
class EntityScopeTest {

    private static EntityScope scope(List<String> entities) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("user"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        return new EntityScope(new CurrentUser(identity));
    }

    /**
     * A scoped user sees only its own entity.
     */
    @Test
    void scoped_user_sees_only_its_entity() {
        EntityScope scope = scope(List.of("UNEP"));
        assertFalse(scope.unrestricted());
        assertTrue(scope.canSee("UNEP"));
        assertFalse(scope.canSee("ALPTIS_ASSURANCES"));
        assertTrue(scope.visibleCodes().contains("UNEP"));
    }

    /**
     * A group-level user sees every entity.
     */
    @Test
    void group_level_user_sees_everything() {
        EntityScope scope = scope(List.of("*"));
        assertTrue(scope.unrestricted());
        assertTrue(scope.canSee("ALPTIS_ASSURANCES"));
        assertTrue(scope.canSee("UNEP"));
    }
}
