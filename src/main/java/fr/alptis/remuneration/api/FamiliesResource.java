package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.engine.FamilyRegistry;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Interface endpoints for the calculation-family screen (PAR-02): the list of families and, per
 * family, the JSON Schema of its parameters from which the interface builds the form.
 */
@Path("/api/ui/v1/families")
public class FamiliesResource {

    private final FamilyRegistry registry;

    /**
     * Creates the resource.
     *
     * @param registry the family registry
     */
    @Inject
    public FamiliesResource(FamilyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Lists the registered calculation families.
     *
     * @return the family codes and labels
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<FamilyView> families() {
        return registry.families().stream().map(f -> new FamilyView(f.name(), label(f))).toList();
    }

    /**
     * Returns the JSON Schema of a family's parameters.
     *
     * @param family the family code
     * @return the schema as an application/json response
     */
    @GET
    @Path("/{family}/schema")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public Response schema(@PathParam("family") RuleFamily family) {
        return Response.ok(registry.schemaFor(family)).build();
    }

    /**
     * Returns the French label of a family, as shown in the mock-up.
     *
     * @param family the family code
     * @return the French label
     */
    private static String label(RuleFamily family) {
        return switch (family) {
            case LINEAR -> "Linéaire";
            case UPFRONT -> "Précomptée";
            case ADVANCE -> "Escompte";
            case TIERED -> "Barème sur cumul";
            case PORTFOLIO -> "Portefeuille sous conditions";
            case SHARE -> "Quote-part d'une autre commission";
            case OUTSTANDING -> "Encours";
            case FIXED -> "Montant fixe";
            case EXTERNAL -> "Commission externe";
        };
    }

    /**
     * View of a calculation family.
     *
     * @param code  the family code
     * @param label the French label
     */
    public record FamilyView(String code, String label) {
    }
}
