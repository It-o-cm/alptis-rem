package fr.alptis.remuneration.api;

import fr.alptis.remuneration.integration.MappingAdminService;
import fr.alptis.remuneration.integration.MappingAdminService.MappingTestResult;
import fr.alptis.remuneration.integration.MappingAdminService.MappingView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Interface endpoints for the file-mapping screen (ING-02): list the mappings and their versions,
 * add a version, and test a definition on a sample file showing the pivot result (INT-13).
 */
@Path("/api/ui/v1/mappings")
public class MappingsResource {

    private final MappingAdminService service;

    /**
     * Creates the resource.
     *
     * @param service the mapping administration service
     */
    @Inject
    public MappingsResource(MappingAdminService service) {
        this.service = service;
    }

    /**
     * Lists the file mappings and their versions.
     *
     * @return the mapping views
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "AUDITOR"})
    public List<MappingView> list() {
        return service.list();
    }

    /**
     * Adds a new mapping version.
     *
     * @param request the mapping to add
     * @return a 201 response with the created mapping
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public Response add(MappingRequest request) {
        MappingView created = service.addVersion(request.entityCode(), request.flowCode(),
                request.sourceSystem(), request.definition());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Tests a mapping definition against a sample file.
     *
     * @param request the test request
     * @return the pivot preview
     */
    @POST
    @Path("/test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR"})
    public MappingTestResult test(MappingTestRequest request) {
        return service.test(request.definition(), request.sample());
    }

    /**
     * Request body to add a mapping version.
     *
     * @param entityCode   the entity code
     * @param flowCode     the flow code
     * @param sourceSystem the source system
     * @param definition   the mapping definition (JSON)
     */
    public record MappingRequest(String entityCode, String flowCode, String sourceSystem, String definition) {
    }

    /**
     * Request body to test a mapping.
     *
     * @param definition the mapping definition (JSON)
     * @param sample     the sample file content
     */
    public record MappingTestRequest(String definition, String sample) {
    }
}
