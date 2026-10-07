package fr.alptis.remuneration.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * The entity partition of the current request, applied in the data-access layer (structural
 * choice 1 of section 12.2): a user sees only the entities it is attached to, a group-level user
 * ({@code *}) sees them all. Services use it to filter queries and to guard single-object access.
 */
@RequestScoped
public class EntityScope {

    /** The current request's user. */
    private final CurrentUser currentUser;

    /**
     * Creates the request-scoped entity partition.
     *
     * @param currentUser the current request's user
     */
    @Inject
    public EntityScope(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Tells whether the user may see every entity.
     *
     * @return {@code true} if unrestricted
     */
    public boolean unrestricted() {
        return currentUser.seesAllEntities();
    }

    /**
     * Returns the entity codes the user may see (meaningful only when not unrestricted).
     *
     * @return the visible entity codes
     */
    public List<String> visibleCodes() {
        return currentUser.entities();
    }

    /**
     * Tells whether the user may see the given entity.
     *
     * @param entityCode the entity code to test
     * @return {@code true} if the user may see it
     */
    public boolean canSee(String entityCode) {
        return unrestricted() || visibleCodes().contains(entityCode);
    }
}
