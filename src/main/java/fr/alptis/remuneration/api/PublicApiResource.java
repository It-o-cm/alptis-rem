package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
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
 * Public read API (section 9.8, OUT-04): the distributor resources, the periods and the imports,
 * versioned in the path and confined to the caller's visible entities. In profile {@code dev} it is
 * reached by HTTP Basic (OAuth 2 is a production concern); every endpoint requires an authenticated
 * caller.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PublicApiResource {

    /** The backing service. */
    private final PublicApiService service;

    /**
     * Creates the resource.
     *
     * @param service the backing service
     */
    public PublicApiResource(PublicApiService service) {
        this.service = service;
    }

    /**
     * Returns the accounts and payment status of a distributor.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null for every visible entity
     * @return the account views
     */
    @GET
    @Path("/distributors/{code}/accounts")
    public List<PublicApiService.AccountDto> accounts(@PathParam("code") String code,
                                                      @QueryParam("entity") String entity) {
        return service.accounts(code, entity);
    }

    /**
     * Returns the commission lines of a distributor, filterable by period, nature and status.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @param period the period month (AAAA-MM-JJ), or null
     * @param nature the nature, or null
     * @param status the status, or null
     * @return the line views
     */
    @GET
    @Path("/distributors/{code}/commission-lines")
    public List<PublicApiService.LineDto> commissionLines(@PathParam("code") String code,
                                                          @QueryParam("entity") String entity,
                                                          @QueryParam("period") String period,
                                                          @QueryParam("nature") String nature,
                                                          @QueryParam("status") String status) {
        return service.commissionLines(code, entity, period == null ? null : LocalDate.parse(period),
                nature == null ? null : LineNature.valueOf(nature),
                status == null ? null : LineStatus.valueOf(status));
    }

    /**
     * Returns the statements of a distributor.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @return the statement views
     */
    @GET
    @Path("/distributors/{code}/statements")
    public List<PublicApiService.StatementDto> statements(@PathParam("code") String code,
                                                          @QueryParam("entity") String entity) {
        return service.statements(code, entity);
    }

    /**
     * Returns the escompte agreements and receivables of a distributor.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @return the agreement views
     */
    /**
     * Returns the annual tax certificates (attestations) of a distributor, defaulting to the current
     * fiscal year (BRD-10).
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @param year   the fiscal year, or null for the current year
     * @return the attestation views
     */
    @GET
    @Path("/distributors/{code}/tax-certificates")
    public List<PublicApiService.AttestationDto> taxCertificates(@PathParam("code") String code,
                                                                 @QueryParam("entity") String entity,
                                                                 @QueryParam("year") Integer year) {
        return service.taxCertificates(code, entity, year == null ? service.currentYear() : year);
    }

    /**
     * Returns the escompte agreements and receivables of a distributor.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @return the agreement views
     */
    @GET
    @Path("/distributors/{code}/advance-agreements")
    public List<PublicApiService.AgreementDto> advanceAgreements(@PathParam("code") String code,
                                                                 @QueryParam("entity") String entity) {
        return service.advanceAgreements(code, entity);
    }

    /**
     * Returns the state of the periods in scope.
     *
     * @param entity the entity filter, or null
     * @return the period views
     */
    @GET
    @Path("/periods")
    public List<PublicApiService.PeriodDto> periods(@QueryParam("entity") String entity) {
        return service.periods(entity);
    }

    /**
     * Deposits a pivot file or event batch under a flow.
     *
     * @param flow         the flow code
     * @param entity       the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the file content
     * @return the import result
     */
    @POST
    @Path("/imports/{flow}")
    @Consumes(MediaType.TEXT_PLAIN)
    public PublicApiService.ImportResultDto deposit(@PathParam("flow") String flow,
                                                    @QueryParam("entity") String entity,
                                                    @QueryParam("sourceSystem") String sourceSystem,
                                                    @QueryParam("fileName") String fileName, String content) {
        return service.deposit(flow, entity, sourceSystem, fileName, content);
    }

    /**
     * Returns the result of an import by its id.
     *
     * @param id the import batch id
     * @return the import result
     */
    @GET
    @Path("/imports/{id}")
    public PublicApiService.ImportResultDto importResult(@PathParam("id") Long id) {
        return service.importResult(id);
    }
}
