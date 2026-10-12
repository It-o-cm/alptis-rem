package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.engine.SimulationService;
import fr.alptis.remuneration.engine.SimulationService.SimulationResult;
import fr.alptis.remuneration.engine.SimulationService.Substitution;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface endpoint for the simulation screen (PAR-13): replays a past period under a project rule
 * version with a substitution table and returns the comparison with the real result. Confined to the
 * caller's visible entities; the simulation writes in a separate space and touches nothing real.
 */
@Path("/api/ui/v1/simulation")
public class SimulationResource {

    private final SimulationService simulation;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param simulation the simulation service
     * @param scope      the entity partition
     */
    @Inject
    public SimulationResource(SimulationService simulation, EntityScope scope) {
        this.simulation = simulation;
        this.scope = scope;
    }

    /**
     * Runs a simulation of a rule version on a period.
     *
     * @param entityCode the entity code
     * @param month      the replayed period month
     * @param request    the simulation request
     * @return the simulation result
     */
    @POST
    @Path("/{entity}/{month}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR"})
    public SimulationResult simulate(@PathParam("entity") String entityCode, @PathParam("month") String month,
                                     SimulationRequest request) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        List<Substitution> table = new ArrayList<>();
        if (request.substitutions() != null) {
            for (SubstitutionInput input : request.substitutions()) {
                table.add(new Substitution(input.oldRate(), input.newRate()));
            }
        }
        return simulation.simulate(entityCode, LocalDate.parse(month), request.ruleCode(), request.versionNo(),
                table, request.scopeDistributor());
    }

    /**
     * Lists the past simulations of an entity (PAR-13).
     *
     * @param entityCode the entity code
     * @return the simulation history
     */
    @GET
    @Path("/{entity}/history")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR"})
    public List<SimulationService.SimulationHistoryView> history(@PathParam("entity") String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        return simulation.history(entityCode);
    }

    /**
     * A simulation request: the rule and version to simulate, the substitution table and the scope.
     *
     * @param ruleCode         the simulated rule code
     * @param versionNo        the simulated version number
     * @param substitutions    the substitution table
     * @param scopeDistributor the distributor scope, or null
     */
    public record SimulationRequest(String ruleCode, Integer versionNo, List<SubstitutionInput> substitutions,
                                    String scopeDistributor) {
    }

    /**
     * One substitution input: an old rate mapped to a new one (null to drop the rate).
     *
     * @param oldRate the old rate
     * @param newRate the new rate, or null to drop it
     */
    public record SubstitutionInput(BigDecimal oldRate, BigDecimal newRate) {
    }
}
