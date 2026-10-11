package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.PageResult;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.integration.EventQueryService;
import fr.alptis.remuneration.integration.EventQueryService.EventDetail;
import fr.alptis.remuneration.integration.EventQueryService.EventView;
import fr.alptis.remuneration.integration.EventQueryService.ReasonCount;
import fr.alptis.remuneration.integration.ManualEventService;
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
import java.util.Map;

/**
 * Interface endpoint for the manual event entry screen (ING-07): an operator enters a single
 * business event, which is controlled, journalled and traced like an imported one.
 */
@Path("/api/ui/v1/events")
public class EventsResource {

    private final ManualEventService service;
    private final EventQueryService query;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param service the manual event service
     * @param query   the event read model
     * @param scope   the entity partition
     */
    @Inject
    public EventsResource(ManualEventService service, EventQueryService query, EntityScope scope) {
        this.service = service;
        this.query = query;
        this.scope = scope;
    }

    /**
     * Searches events by key or contract, status and type (ING-06, section 5.3).
     *
     * @param q      a term matched against the source key and contract number
     * @param status a processing status to filter on
     * @param type   an event type to filter on
     * @param page   the zero-based page index
     * @param size   the page size
     * @return a page of event views
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public PageResult<EventView> search(@QueryParam("q") String q, @QueryParam("status") String status,
                                        @QueryParam("type") String type,
                                        @QueryParam("page") @DefaultValue("0") int page,
                                        @QueryParam("size") @DefaultValue("20") int size) {
        return query.search(q, status, type, page, size);
    }

    /**
     * Counts the held events by reason (P8, ING-06).
     *
     * @return the reason counts
     */
    @GET
    @Path("/held-by-reason")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<ReasonCount> heldByReason() {
        return query.heldByReason();
    }

    /**
     * Returns the detail of one event: its status, hold reason and produced lines (ING-06, 5.3).
     *
     * @param id the event id
     * @return the event detail
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public EventDetail detail(@PathParam("id") long id) {
        return query.detail(id);
    }

    /**
     * Records one manually-entered event.
     *
     * @param request the manual event request
     * @return a 201 response with the resulting line counters
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public Response create(ManualEventRequest request) {
        if (!scope.canSee(request.entityCode())) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + request.entityCode());
        }
        ImportBatch batch = service.create(request.entityCode(), request.sourceSystem(), request.fields());
        ManualEventResponse body = new ManualEventResponse(batch.linesLoaded, batch.linesRejected, batch.linesOnHold);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Records one manually-entered complementary datum (a coverage) (ING-07).
     *
     * @param request the manual complementary-data request
     * @return a 201 response with the resulting line counters
     */
    @POST
    @Path("/complementary-data")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public Response createComplementaryData(ManualEventRequest request) {
        if (!scope.canSee(request.entityCode())) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + request.entityCode());
        }
        ImportBatch batch = service.createComplementaryData(request.entityCode(), request.sourceSystem(),
                request.fields());
        ManualEventResponse body = new ManualEventResponse(batch.linesLoaded, batch.linesRejected, batch.linesOnHold);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Request body carrying the manual event.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system to attribute the event to
     * @param fields       the pivot event fields
     */
    public record ManualEventRequest(String entityCode, String sourceSystem, Map<String, String> fields) {
    }

    /**
     * Response giving the outcome of the manual entry.
     *
     * @param loaded   1 if the event was integrated
     * @param rejected 1 if the event was rejected
     * @param onHold   1 if the event was put on hold
     */
    public record ManualEventResponse(Integer loaded, Integer rejected, Integer onHold) {
    }
}
