package fr.alptis.remuneration.api;

import fr.alptis.remuneration.integration.FlowCatalogService;
import fr.alptis.remuneration.integration.FlowCatalogService.FlowDirection;
import fr.alptis.remuneration.integration.FlowCatalogService.FlowView;
import fr.alptis.remuneration.integration.FlowCatalogService.FormatsReference;
import fr.alptis.remuneration.reporting.ExportService;
import fr.alptis.remuneration.reporting.ExportService.ExportObject;
import fr.alptis.remuneration.reporting.ExportService.ExportView;
import fr.alptis.remuneration.domain.ExportBatch;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the flow screens (section 9): the flow catalogue (ING-10), the export
 * journal of an entity with the on-demand and Data exports it can produce (REP-04, section 9.11/9.12),
 * and the exchange-format reference (section 9.9). The entity partition confines the journal and the
 * exports to the caller's visible entities.
 */
@Path("/api/ui/v1/flows")
public class FlowsResource {

    private final FlowCatalogService catalog;
    private final ExportService exports;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param catalog     the flow catalogue
     * @param exports     the export service
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public FlowsResource(FlowCatalogService catalog, ExportService exports, CurrentUser currentUser,
                         EntityScope scope) {
        this.catalog = catalog;
        this.exports = exports;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Returns the flow catalogue, optionally filtered by direction (ING-10).
     *
     * @param direction the direction (INBOUND or OUTBOUND), all by default
     * @return the catalogue rows
     */
    @GET
    @Path("/catalog")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<FlowView> catalog(@QueryParam("direction") String direction) {
        return catalog.catalog(direction == null ? null : FlowDirection.valueOf(direction));
    }

    /**
     * Returns the exchange-format reference (section 9.9).
     *
     * @return the formats reference
     */
    @GET
    @Path("/formats")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public FormatsReference formats() {
        return catalog.formats();
    }

    /**
     * Returns the export journal of an entity (REP-04).
     *
     * @param entityCode the entity code
     * @return the journal rows
     */
    @GET
    @Path("/{entity}/exports")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<ExportView> exports(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return exports.list(entityCode);
    }

    /**
     * Produces an export of an object for an entity, on demand or as the Data export, journalled.
     *
     * @param entityCode the entity code
     * @param object     the object (COMMISSION_LINES or ACCOUNTS)
     * @param period     the period month for an on-demand list, or null for the Data export
     * @return the produced journal row
     */
    @POST
    @Path("/{entity}/exports")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "FINANCE_CONTROLLER"})
    public ExportView produce(@PathParam("entity") String entityCode, @QueryParam("object") String object,
                              @QueryParam("period") String period) {
        requireScope(entityCode);
        ExportObject target = ExportObject.valueOf(object);
        ExportBatch batch = period == null
                ? exports.dataExport(entityCode, target, currentUser.username())
                : exports.listExport(entityCode, LocalDate.parse(period), target, currentUser.username());
        return new ExportView(batch.getId(), batch.flowCode, batch.fileName, batch.fileHash, batch.generatedBy,
                batch.linesWritten, batch.totalAmount, batch.status);
    }

    /**
     * Downloads the content of a journalled export, recomputed identically.
     *
     * @param id the export batch id
     * @return the CSV content
     */
    @GET
    @Path("/exports/{id}/content")
    @Produces("text/csv")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public String content(@PathParam("id") Long id) {
        ExportBatch batch = ExportBatch.findById(id);
        if (batch == null || !scope.canSee(batch.entity.code)) {
            throw new ResourceNotFoundException("Export inconnu : " + id);
        }
        return exports.content(id);
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
}
