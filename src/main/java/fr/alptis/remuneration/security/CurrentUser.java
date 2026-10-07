package fr.alptis.remuneration.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

/**
 * The authenticated user of the current request: its name, roles and the entities it may see.
 *
 * <p>The entity list drives data partitioning (section 12.2): a user sees only its entities, and a
 * group-level user (entities {@code *}) sees them all.</p>
 */
@RequestScoped
public class CurrentUser {

    /** The security identity of the current request. */
    private final SecurityIdentity identity;

    /**
     * Creates the request-scoped current user.
     *
     * @param identity the security identity of the current request
     */
    @Inject
    public CurrentUser(SecurityIdentity identity) {
        this.identity = identity;
    }

    /**
     * Returns the user name.
     *
     * @return the principal name
     */
    public String username() {
        return identity.getPrincipal().getName();
    }

    /**
     * Returns the roles granted to the user.
     *
     * @return the role names
     */
    public Set<String> roles() {
        return identity.getRoles();
    }

    /**
     * Tells whether the user holds the given role.
     *
     * @param role the role name to test
     * @return {@code true} if the user holds the role
     */
    public boolean hasRole(String role) {
        return identity.hasRole(role);
    }

    /**
     * Returns the entity codes the user may see; a single {@code *} means every entity.
     *
     * @return the visible entity codes, never null
     */
    @SuppressWarnings("unchecked")
    public List<String> entities() {
        Object attribute = identity.getAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE);
        if (attribute == null) {
            return List.of();
        }
        return (List<String>) attribute;
    }

    /**
     * Tells whether the user may see every entity (group level).
     *
     * @return {@code true} if the user is attached to every entity
     */
    public boolean seesAllEntities() {
        return entities().contains("*");
    }
}
