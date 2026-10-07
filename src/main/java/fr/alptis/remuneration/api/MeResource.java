package fr.alptis.remuneration.api;

import fr.alptis.remuneration.security.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Exposes the authenticated user to the interface so it can show the role and entity in the page
 * header and hide what the user is not allowed to do (section 3).
 */
@Path("/api/ui/v1/me")
public class MeResource {

    /** The current request's user. */
    private final CurrentUser currentUser;

    /**
     * Creates the resource.
     *
     * @param currentUser the current request's user
     */
    @Inject
    public MeResource(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Returns the authenticated user's name, roles and visible entities.
     *
     * @return the current user description
     */
    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public MeResponse me() {
        List<String> roles = currentUser.roles().stream().sorted().toList();
        return new MeResponse(currentUser.username(), roles, currentUser.entities());
    }

    /**
     * Description of the authenticated user.
     *
     * @param username the user name
     * @param roles    the user's roles, sorted
     * @param entities the entity codes the user may see ({@code *} meaning every entity)
     */
    public record MeResponse(String username, List<String> roles, List<String> entities) {
    }
}
