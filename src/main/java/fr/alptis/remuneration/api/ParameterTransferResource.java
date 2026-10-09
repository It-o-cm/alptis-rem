package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.rules.PlanTransferService;
import fr.alptis.remuneration.rules.PlanTransferService.PlanExport;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface endpoints for the parameter export/import screen (PAR-15): a plan with its rules and
 * versions is exported as an open JSON structure and imported back under a new code (every version in
 * draft), for the promotion between environments and the reversibility. Confined to the caller's
 * visible entities.
 */
@Path("/api/ui/v1/parameters")
public class ParameterTransferResource {

    private final PlanTransferService transfer;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param transfer the plan transfer service
     * @param scope    the entity partition
     */
    @Inject
    public ParameterTransferResource(PlanTransferService transfer, EntityScope scope) {
        this.transfer = transfer;
        this.scope = scope;
    }

    /**
     * Exports a plan, its rules and versions (PAR-15).
     *
     * @param entityCode the entity code
     * @param planCode   the plan code
     * @return the plan export
     */
    @GET
    @Path("/{entity}/plans/{planCode}/export")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "AUDITOR"})
    public PlanExport export(@PathParam("entity") String entityCode, @PathParam("planCode") String planCode) {
        requireScope(entityCode);
        return transfer.export(entityCode, planCode);
    }

    /**
     * Imports a plan under a new code (PAR-15).
     *
     * @param entityCode the entity code
     * @param newCode    the code of the plan to create
     * @param export     the plan export
     * @return the created plan code
     */
    @POST
    @Path("/{entity}/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN"})
    public ImportedPlan importPlan(@PathParam("entity") String entityCode,
                                   @QueryParam("newCode") String newCode, PlanExport export) {
        requireScope(entityCode);
        return new ImportedPlan(transfer.importPlan(entityCode, newCode, export));
    }

    /**
     * Fails with a not-found when the entity is out of the caller's scope.
     *
     * @param entityCode the entity code
     */
    private void requireScope(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
    }

    /**
     * The result of an import: the created plan code.
     *
     * @param planCode the created plan code
     */
    public record ImportedPlan(String planCode) {
    }
}
