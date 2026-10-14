package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ConcurrentUpdateException;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.SeparationOfDutiesException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Maps business exceptions to {@code application/problem+json} responses with a French message, as
 * required for the interface endpoints ({@code /api/ui/v1}).
 */
public class UiExceptionMappers {

    /** Media type of RFC 7807 problem responses. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /**
     * Maps a missing or out-of-scope object to 404.
     *
     * @param exception the not-found exception
     * @return a 404 problem response
     */
    @ServerExceptionMapper
    public Response notFound(ResourceNotFoundException exception) {
        return problem(Response.Status.NOT_FOUND.getStatusCode(), "Ressource introuvable", exception.getMessage());
    }

    /**
     * Maps a concurrent-update conflict to 409.
     *
     * @param exception the conflict exception
     * @return a 409 problem response
     */
    @ServerExceptionMapper
    public Response concurrentUpdate(ConcurrentUpdateException exception) {
        return problem(Response.Status.CONFLICT.getStatusCode(), "Conflit de version", exception.getMessage());
    }

    /**
     * Maps an invalid submitted value to 400.
     *
     * @param exception the validation exception
     * @return a 400 problem response
     */
    @ServerExceptionMapper
    public Response validation(ValidationException exception) {
        return problem(Response.Status.BAD_REQUEST.getStatusCode(), "Saisie invalide", exception.getMessage());
    }

    /**
     * Maps a state conflict (e.g. a duplicate) to 409.
     *
     * @param exception the conflict exception
     * @return a 409 problem response
     */
    @ServerExceptionMapper
    public Response conflict(ConflictException exception) {
        return problem(Response.Status.CONFLICT.getStatusCode(), "Conflit", exception.getMessage());
    }

    /**
     * Maps a separation-of-duties refusal (e.g. approving one's own rule version, CR-71) to 403.
     *
     * @param exception the separation-of-duties exception
     * @return a 403 problem response
     */
    @ServerExceptionMapper
    public Response separationOfDuties(SeparationOfDutiesException exception) {
        return problem(Response.Status.FORBIDDEN.getStatusCode(), "Séparation des tâches", exception.getMessage());
    }

    /**
     * Builds a problem response.
     *
     * @param status the HTTP status
     * @param title  the short French title
     * @param detail the French detail message
     * @return the problem response
     */
    private static Response problem(int status, String title, String detail) {
        return Response.status(status).type(PROBLEM_JSON).entity(new Problem(title, status, detail)).build();
    }

    /**
     * RFC 7807 problem body.
     *
     * @param title  the short French title
     * @param status the HTTP status
     * @param detail the French detail message
     */
    public record Problem(String title, int status, String detail) {
    }
}
