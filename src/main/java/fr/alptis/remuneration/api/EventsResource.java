package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.integration.ManualEventService;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Interface endpoint for the manual event entry screen (ING-07): an operator enters a single
 * business event, which is controlled, journalled and traced like an imported one.
 */
@Path("/api/ui/v1/events")
public class EventsResource {

    private final ManualEventService service;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param service the manual event service
     * @param scope   the entity partition
     */
    @Inject
    public EventsResource(ManualEventService service, EntityScope scope) {
        this.service = service;
        this.scope = scope;
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
