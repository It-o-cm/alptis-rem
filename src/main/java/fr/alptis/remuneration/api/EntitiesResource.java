package fr.alptis.remuneration.api;

import fr.alptis.remuneration.integration.EntityAdminService;
import fr.alptis.remuneration.integration.EntityAdminService.EntityUpdate;
import fr.alptis.remuneration.integration.EntityAdminService.EntityView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Interface endpoints for the per-entity parameters screen (REF-01, ADM-05). Reads are open to
 * the business, finance and audit roles and partitioned by entity; updates are reserved to the
 * business administrator and go through the audit trail.
 */
@Path("/api/ui/v1/entities")
public class EntitiesResource {

    /** The entity administration service. */
    private final EntityAdminService service;

    /**
     * Creates the resource.
     *
     * @param service the entity administration service
     */
    @Inject
    public EntitiesResource(EntityAdminService service) {
        this.service = service;
    }

    /**
     * Lists the entities the current user may see.
     *
     * @return the visible entities
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<EntityView> list() {
        return service.list();
    }

    /**
     * Returns one entity by its code.
     *
     * @param code the entity code
     * @return the entity view
     */
    @GET
    @Path("/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public EntityView get(@PathParam("code") String code) {
        return service.get(code);
    }

    /**
     * Updates one entity's parameters.
     *
     * @param code    the entity code
     * @param version the optimistic-lock version the caller last saw
     * @param update  the new parameter values
     * @return the updated entity view
     */
    @PUT
    @Path("/{code}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public EntityView update(@PathParam("code") String code,
                             @HeaderParam("X-Version") long version,
                             EntityUpdate update) {
        return service.update(code, update, version);
    }
}
