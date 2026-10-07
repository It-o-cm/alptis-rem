package fr.alptis.remuneration.security;

import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;

/**
 * Authenticates the dev/test users declared in {@link DevSecurityConfig}, standing in for the
 * group's single sign-on. Present outside production only.
 *
 * <p>On success it builds a security identity carrying the user's roles and, as the
 * {@code entities} attribute, the list of entity codes it may see (a single {@code *} meaning
 * every entity). The data-access layer reads that attribute to partition data by entity.</p>
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class DevIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    /** Name of the identity attribute holding the visible entity codes. */
    public static final String ENTITIES_ATTRIBUTE = "entities";

    /** The configured users. */
    private final DevSecurityConfig config;

    /** Resolves roles and entities from directory groups. */
    private final RoleResolver roleResolver;

    /**
     * Creates the provider.
     *
     * @param config       the configured dev/test users
     * @param roleResolver the resolver of roles and entities from directory groups
     */
    @Inject
    public DevIdentityProvider(DevSecurityConfig config, RoleResolver roleResolver) {
        this.config = config;
        this.roleResolver = roleResolver;
    }

    /**
     * Declares the request type this provider handles.
     *
     * @return the username/password request type
     */
    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    /**
     * Authenticates a username/password request against the configured users.
     *
     * @param request the authentication request
     * @param context the authentication context (unused)
     * @return a security identity on success, or a failed {@link Uni} on unknown user or bad password
     */
    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        String username = request.getUsername();
        DevSecurityConfig.DevUser user = config.users().get(username);
        if (user == null) {
            return Uni.createFrom().failure(new AuthenticationFailedException("unknown user"));
        }
        String provided = new String(request.getPassword().getPassword());
        if (!user.password().equals(provided)) {
            return Uni.createFrom().failure(new AuthenticationFailedException("bad password"));
        }
        // Role resolution reads the database, so it runs on a worker thread.
        return context.runBlocking(() -> {
            RoleResolver.Grants grants = roleResolver.resolve(user.groups());
            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(username))
                    .addRoles(new HashSet<>(grants.roles()))
                    .addAttribute(ENTITIES_ATTRIBUTE, grants.entities())
                    .build();
        });
    }
}
