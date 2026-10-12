package fr.alptis.remuneration.api;

import fr.alptis.remuneration.cycle.ValidationService;
import fr.alptis.remuneration.cycle.ValidationService.ValidationSummary;
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
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;

/**
 * Interface endpoints for the business-validation screen (WKF-02, section 7.2): read a period's
 * totals, its gap with the previous month and whether a second validation level is required, and give
 * a validation level. Partitioned by entity.
 */
@Path("/api/ui/v1/validation")
public class ValidationResource {

    private final ValidationService validation;
    private final CurrentUser currentUser;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param validation  the business-validation service
     * @param currentUser the current user
     * @param scope       the entity partition
     */
    @Inject
    public ValidationResource(ValidationService validation, CurrentUser currentUser, EntityScope scope) {
        this.validation = validation;
        this.currentUser = currentUser;
        this.scope = scope;
    }

    /**
     * Returns a period's validation summary (WKF-02).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the validation summary
     */
    @GET
    @Path("/{entityCode}/{month}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public ValidationSummary summary(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return validation.summary(entityCode, LocalDate.parse(month));
    }

    /**
     * Gives a validation level on a period (WKF-02).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the refreshed validation summary
     */
    @POST
    @Path("/{entityCode}/{month}/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_VALIDATOR"})
    public ValidationSummary validate(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return validation.validate(entityCode, LocalDate.parse(month), currentUser.username(),
                currentUser.appRoles());
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
