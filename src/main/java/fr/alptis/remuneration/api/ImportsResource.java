package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.integration.ImportQueryService;
import fr.alptis.remuneration.integration.ImportQueryService.BatchView;
import fr.alptis.remuneration.integration.ImportQueryService.HeldEventView;
import fr.alptis.remuneration.integration.ImportQueryService.RejectionView;
import fr.alptis.remuneration.integration.ImportService;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Interface endpoints for the imports screen (ING-01, ING-04, ING-05, ING-08): upload a file with
 * its flow and source system, read the journal and its five counters, list rejected and held
 * lines, and resolve a rejection. Reads and actions are partitioned by entity.
 */
@Path("/api/ui/v1/imports")
public class ImportsResource {

    private final ImportService importService;
    private final ImportQueryService query;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param importService the import engine facade
     * @param query         the import journal query service
     * @param scope         the entity partition
     */
    @Inject
    public ImportsResource(ImportService importService, ImportQueryService query, EntityScope scope) {
        this.importService = importService;
        this.query = query;
        this.scope = scope;
    }

    /**
     * Uploads a file for import under the given flow and source system.
     *
     * @param request the upload request
     * @return a 201 response with the resulting batch counters
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public Response upload(UploadRequest request) {
        if (!scope.canSee(request.entityCode())) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + request.entityCode());
        }
        ImportBatch batch = importService.importFile(request.entityCode(), request.flowCode(),
                request.sourceSystem(), request.fileName(), request.content());
        UploadResponse body = new UploadResponse(batch.getId(), batch.status.name(), batch.linesRead,
                batch.linesFiltered, batch.linesLoaded, batch.linesRejected, batch.linesOnHold);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Lists the import journal.
     *
     * @param page the zero-based page index
     * @param size the page size
     * @return a page of batch views
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public PageResult<BatchView> list(@QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("size") @DefaultValue("20") int size) {
        return query.list(page, size);
    }

    /**
     * Lists the source systems the upload form may choose from (ING-01), within the caller's partition.
     *
     * @return the source system codes
     */
    @GET
    @Path("/source-systems")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<String> sourceSystems() {
        return query.sourceSystems();
    }

    /**
     * Lists the rejected lines of a batch.
     *
     * @param id the batch id
     * @return the rejection views
     */
    @GET
    @Path("/{id}/rejections")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<RejectionView> rejections(@PathParam("id") long id) {
        return query.rejections(id);
    }

    /**
     * Lists the held lines of a batch.
     *
     * @param id the batch id
     * @return the held-event views
     */
    @GET
    @Path("/{id}/held")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<HeldEventView> held(@PathParam("id") long id) {
        return query.heldEvents(id);
    }

    /**
     * Corrects a rejected line: an optional corrected line is reprocessed against its batch (INT-03).
     *
     * @param id            the rejection id
     * @param correctedLine the corrected data line in the batch's column order, or null to only resolve
     * @return a 204 response
     */
    @POST
    @Path("/rejections/{id}/correct")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public Response correct(@PathParam("id") long id, @QueryParam("correctedLine") String correctedLine) {
        query.correct(id, correctedLine);
        return Response.noContent().build();
    }

    /**
     * Marks a rejected line as discarded.
     *
     * @param id the rejection id
     * @return a 204 response
     */
    @POST
    @Path("/rejections/{id}/discard")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public Response discard(@PathParam("id") long id) {
        query.discard(id);
        return Response.noContent().build();
    }

    /**
     * Upload request body.
     *
     * @param entityCode   the entity code
     * @param flowCode     the flow code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw file content
     */
    public record UploadRequest(String entityCode, String flowCode, String sourceSystem, String fileName,
                                String content) {
    }

    /**
     * Upload response with the resulting counters.
     *
     * @param batchId       the created batch id
     * @param status        the batch status
     * @param linesRead     lines read
     * @param linesFiltered lines filtered
     * @param linesLoaded   lines integrated
     * @param linesRejected lines rejected
     * @param linesOnHold   lines on hold
     */
    public record UploadResponse(long batchId, String status, Integer linesRead, Integer linesFiltered,
                                 Integer linesLoaded, Integer linesRejected, Integer linesOnHold) {
    }
}
