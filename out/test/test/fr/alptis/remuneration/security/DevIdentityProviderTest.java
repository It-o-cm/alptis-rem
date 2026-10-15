package fr.alptis.remuneration.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DevIdentityProvider}, built with an in-memory configuration and a fake
 * role resolver so no database is required.
 */
class DevIdentityProviderTest {

    /** Record implementation of a configured user. */
    private record TestUser(String password, List<String> groups) implements DevSecurityConfig.DevUser {
    }

    private static final DevSecurityConfig CONFIG = () -> Map.of(
            "operator-alptis", new TestUser("secret", List.of("grp-op-alptis")));

    /** Role resolver returning canned grants, bypassing the database. */
    private static final RoleResolver RESOLVER = new RoleResolver() {
        @Override
        public Grants resolve(List<String> groups) {
            return new Grants(Set.of("OPERATOR"), List.of("ALPTIS_ASSURANCES"));
        }
    };

    /** Authentication context that simply runs the blocking supplier inline. */
    private static final AuthenticationRequestContext CONTEXT = new AuthenticationRequestContext() {
        @Override
        public Uni<SecurityIdentity> runBlocking(Supplier<SecurityIdentity> function) {
            return Uni.createFrom().item(function.get());
        }
    };

    private static UsernamePasswordAuthenticationRequest request(String user, String password) {
        return new UsernamePasswordAuthenticationRequest(user, new PasswordCredential(password.toCharArray()));
    }

    /**
     * Verifies that a valid user authenticates with resolved roles and entities attached.
     */
    @Test
    void authenticates_a_known_user_with_roles_and_entities() {
        DevIdentityProvider provider = new DevIdentityProvider(CONFIG, RESOLVER);
        assertEquals(UsernamePasswordAuthenticationRequest.class, provider.getRequestType());

        SecurityIdentity identity =
                provider.authenticate(request("operator-alptis", "secret"), CONTEXT).await().indefinitely();

        assertEquals("operator-alptis", identity.getPrincipal().getName());
        assertTrue(identity.hasRole("OPERATOR"));
        assertEquals(List.of("ALPTIS_ASSURANCES"),
                identity.getAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE));
    }

    /**
     * Verifies that an unknown user is rejected.
     */
    @Test
    void rejects_an_unknown_user() {
        DevIdentityProvider provider = new DevIdentityProvider(CONFIG, RESOLVER);
        assertThrows(AuthenticationFailedException.class,
                () -> provider.authenticate(request("ghost", "secret"), CONTEXT).await().indefinitely());
    }

    /**
     * Verifies that a wrong password is rejected.
     */
    @Test
    void rejects_a_wrong_password() {
        DevIdentityProvider provider = new DevIdentityProvider(CONFIG, RESOLVER);
        assertThrows(AuthenticationFailedException.class,
                () -> provider.authenticate(request("operator-alptis", "wrong"), CONTEXT).await().indefinitely());
    }
}
