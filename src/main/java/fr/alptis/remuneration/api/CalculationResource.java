package fr.alptis.remuneration.api;

import fr.alptis.remuneration.cycle.CalculationQueryService;
import fr.alptis.remuneration.cycle.CalculationQueryService.ExecutionsView;
import fr.alptis.remuneration.engine.CalculationEngine;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
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

/**
 * Interface endpoints for the calculation-executions screen (CAL-01, CAL-12): launch a real
 * calculation of a period, launch a partial recalculation scoped to a distributor, a product or a
 * rule, and read the runs with their counters and the units left in failure. Partitioned by entity.
 */
@Path("/api/ui/v1/calculations")
public class CalculationResource {

    private final CalculationEngine engine;
    private final CalculationQueryService query;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param engine the calculation engine
     * @param query  the executions read model
     * @param scope  the entity partition
     */
    @Inject
    public CalculationResource(CalculationEngine engine, CalculationQueryService query, EntityScope scope) {
        this.engine = engine;
        this.query = query;
        this.scope = scope;
    }

    /**
     * Returns the runs of an entity's period and the units left in failure.
     *
     * @param entityCode the entity code
     * @param month      the period month (ISO date of the first day)
     * @return the executions view
     */
    @GET
    @Path("/{entityCode}/{month}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public ExecutionsView executions(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.executions(entityCode, LocalDate.parse(month));
    }

    /**
     * Launches a full real calculation of a period (CAL-01).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the refreshed executions view
     */
    @POST
    @Path("/{entityCode}/{month}/run")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public ExecutionsView run(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        LocalDate periodMonth = LocalDate.parse(month);
        engine.runReal(entityCode, periodMonth);
        return query.executions(entityCode, periodMonth);
    }

    /**
     * Launches a partial recalculation scoped to a distributor, a product or a rule (CAL-12).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param type       the scope kind ({@code DISTRIBUTOR}, {@code PRODUCT} or {@code RULE})
     * @param value      the scoped code
     * @return the refreshed executions view
     */
    @POST
    @Path("/{entityCode}/{month}/recalc")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public ExecutionsView recalc(@PathParam("entityCode") String entityCode, @PathParam("month") String month,
                                 @QueryParam("type") String type, @QueryParam("value") String value) {
        requireScope(entityCode);
        LocalDate periodMonth = LocalDate.parse(month);
        engine.runPartial(entityCode, periodMonth, type, value);
        return query.executions(entityCode, periodMonth);
    }

    /**
     * Checks the entity is within the caller's partition.
     *
     * @param entityCode the entity code
     */
    private void requireScope(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
    }
}
