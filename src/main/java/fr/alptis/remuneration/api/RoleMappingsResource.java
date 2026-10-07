package fr.alptis.remuneration.api;

import fr.alptis.remuneration.security.RoleMappingAdminService;
import fr.alptis.remuneration.security.RoleMappingAdminService.RoleMappingView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Interface endpoints for the directory-group to role correspondence screen (section 3). Reading
 * is open to the technical administrator and the auditor; changes are reserved to the technical
 * administrator (ADM-01).
 */
@Path("/api/ui/v1/role-mappings")
public class RoleMappingsResource {

    /** The role-mapping administration service. */
    private final RoleMappingAdminService service;

    /**
     * Creates the resource.
     *
     * @param service the role-mapping administration service
     */
    @Inject
    public RoleMappingsResource(RoleMappingAdminService service) {
        this.service = service;
    }

    /**
     * Lists every role mapping.
     *
     * @return the role mappings
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"IT_ADMIN", "AUDITOR"})
    public List<RoleMappingView> list() {
        return service.list();
    }

    /**
     * Creates a role mapping.
     *
     * @param request the mapping to create
     * @return a 201 response with the created mapping
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("IT_ADMIN")
    public Response create(RoleMappingRequest request) {
        RoleMappingView created = service.create(request.directoryGroup(), request.role(), request.entityCode());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Deletes a role mapping by its natural key.
     *
     * @param directoryGroup the directory group
     * @param role           the role name
     * @param entityCode     the entity code, or absent for the every-entity mapping
     * @return a 204 response
     */
    @DELETE
    @RolesAllowed("IT_ADMIN")
    public Response delete(@QueryParam("group") String directoryGroup,
                           @QueryParam("role") String role,
                           @QueryParam("entity") String entityCode) {
        service.delete(directoryGroup, role, entityCode);
        return Response.noContent().build();
    }

    /**
     * Request body to create a role mapping.
     *
     * @param directoryGroup the directory group
     * @param role           the role name
     * @param entityCode     the entity code, or {@code null} for every entity
     */
    public record RoleMappingRequest(String directoryGroup, String role, String entityCode) {
    }
}
