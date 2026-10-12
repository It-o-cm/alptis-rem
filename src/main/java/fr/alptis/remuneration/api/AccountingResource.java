package fr.alptis.remuneration.api;

import fr.alptis.remuneration.accounting.AccountingQueryService;
import fr.alptis.remuneration.accounting.AccountingQueryService.EntryView;
import fr.alptis.remuneration.accounting.AccountingQueryService.SchemeView;
import fr.alptis.remuneration.accounting.CutoffService;
import fr.alptis.remuneration.accounting.CutoffService.CutoffView;
import fr.alptis.remuneration.accounting.CutoffService.PilotageReport;
import fr.alptis.remuneration.domain.CutoffExtract;
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
import java.util.List;

/**
 * Interface endpoints for the accounting screens (section 10, FIN): the accounting schema and mapping
 * of an entity (FIN-02), the search of a period's entries (FIN-01/05/07), the cut-off pilotage state
 * (FIN-04/09) and the monthly extraction produced at closing (FIN-03). The entity partition confines
 * every read and action to the caller's visible entities.
 */
@Path("/api/ui/v1/accounting")
public class AccountingResource {

    private final AccountingQueryService query;
    private final CutoffService cutoff;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param query  the accounting read model
     * @param cutoff the cut-off service
     * @param scope  the entity partition
     */
    @Inject
    public AccountingResource(AccountingQueryService query, CutoffService cutoff, EntityScope scope) {
        this.query = query;
        this.cutoff = cutoff;
        this.scope = scope;
    }

    /**
     * Returns the accounting schema and mapping of an entity (FIN-02).
     *
     * @param entityCode the entity code
     * @return the schema view
     */
    @GET
    @Path("/{entity}/scheme")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public SchemeView scheme(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return query.scheme(entityCode);
    }

    /**
     * Returns the accounting entries of a period (FIN-01/05/07).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the entries
     */
    @GET
    @Path("/{entity}/{month}/entries")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<EntryView> entries(@PathParam("entity") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.entries(entityCode, LocalDate.parse(month));
    }

    /**
     * Searches an entity's accounting entries across every month, optionally by account and fiscal
     * year (FIN-01, FIN-05).
     *
     * @param entityCode the entity code
     * @param account    an account number to match, or null for any
     * @param fiscalYear a fiscal year to match, or null for any
     * @return the matching entries
     */
    @GET
    @Path("/{entity}/entries")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public List<EntryView> searchEntries(@PathParam("entity") String entityCode,
                                         @QueryParam("account") String account,
                                         @QueryParam("fiscalYear") Short fiscalYear) {
        requireScope(entityCode);
        return query.searchEntries(entityCode, account, fiscalYear);
    }

    /**
     * Returns the audit chain of an accounting entry: flux, bordereau, line, rule and entry (FIN-07).
     *
     * @param entityCode the entity code
     * @param entryId    the accounting-entry id
     * @return the chain view
     */
    @GET
    @Path("/{entity}/entries/{entryId}/chain")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public AccountingQueryService.ChainView entryChain(@PathParam("entity") String entityCode,
                                                       @PathParam("entryId") Long entryId) {
        requireScope(entityCode);
        return query.entryChain(entityCode, entryId);
    }

    /**
     * Reconciles an entity's acquired and paid commissions, by beneficiary and global (FIN-06).
     *
     * @param entityCode the entity code
     * @return the reconciliation view
     */
    @GET
    @Path("/{entity}/reconciliation")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public AccountingQueryService.Reconciliation reconciliation(@PathParam("entity") String entityCode) {
        requireScope(entityCode);
        return query.reconciliation(entityCode);
    }

    /**
     * Returns the cut-off pilotage state at a date, by a maille (FIN-04/09).
     *
     * @param entityCode the entity code
     * @param date       the as-of date
     * @param view       the maille (COVERAGE, DISTRIBUTOR, PRODUCT or ENTITY), entity by default
     * @return the pilotage report
     */
    @GET
    @Path("/{entity}/cutoff")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "AUDITOR"})
    public PilotageReport cutoff(@PathParam("entity") String entityCode, @QueryParam("date") String date,
                                 @QueryParam("view") String view) {
        requireScope(entityCode);
        CutoffView maille = view == null ? CutoffView.ENTITY : CutoffView.valueOf(view);
        return cutoff.pilotage(entityCode, LocalDate.parse(date), maille);
    }

    /**
     * Produces the monthly accounting extraction of a period at closing (FIN-03).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the extract summary
     */
    @POST
    @Path("/{entity}/{month}/extract")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "FINANCE_CONTROLLER"})
    public ExtractSummary extract(@PathParam("entity") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        CutoffExtract extract = cutoff.extract(entityCode, LocalDate.parse(month));
        return new ExtractSummary(extract.getId(), extract.fileRef, extract.cutoffDate, extract.status.name());
    }

    /**
     * Returns the CSV of a period's extraction (OUT-02).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the CSV content
     */
    @GET
    @Path("/{entity}/{month}/extract.csv")
    @Produces("text/csv")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "FINANCE_CONTROLLER", "AUDITOR"})
    public String extractCsv(@PathParam("entity") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return cutoff.extractCsv(entityCode, LocalDate.parse(month));
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

    /**
     * Summary of a produced extraction.
     *
     * @param id         the extract id
     * @param fileRef    the file reference
     * @param cutoffDate the cut-off date
     * @param status     the status
     */
    public record ExtractSummary(Long id, String fileRef, LocalDate cutoffDate, String status) {
    }
}
