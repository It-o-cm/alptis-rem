package fr.alptis.remuneration.api;

import fr.alptis.remuneration.documents.DocumentTemplateService;
import fr.alptis.remuneration.documents.StatementService;
import fr.alptis.remuneration.documents.StatementService.StatementContent;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.payment.PaymentQueryService;
import fr.alptis.remuneration.payment.PaymentQueryService.StatementView;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Interface endpoints for the bordereaux (section 9.5, BRD): the statements of a period, the rendered
 * content of one, its publication (archived with a hash), its spreadsheet export and its
 * rectificative version.
 */
@Path("/api/ui/v1/documents")
public class DocumentsResource {

    private final PaymentQueryService query;
    private final StatementService statements;
    private final DocumentTemplateService templates;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param query      the payment read model
     * @param statements the statement service
     * @param templates  the document-template service
     * @param scope      the entity partition
     */
    @Inject
    public DocumentsResource(PaymentQueryService query, StatementService statements,
                             DocumentTemplateService templates, EntityScope scope) {
        this.query = query;
        this.statements = statements;
        this.templates = templates;
        this.scope = scope;
    }

    /**
     * Lists the current document templates of an entity (BRD-05).
     *
     * @param entityCode the entity code
     * @return the template views
     */
    @GET
    @Path("/templates/{entityCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"BUSINESS_ADMIN", "OPERATOR", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<DocumentTemplateService.TemplateView> templates(@PathParam("entityCode") String entityCode) {
        return templates.list(entityCode);
    }

    /**
     * Saves a new version of a document template (BRD-05); reserved to the business administrator.
     *
     * @param entityCode the entity code
     * @param request    the template edit
     * @return the saved template view
     */
    @POST
    @Path("/templates/{entityCode}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("BUSINESS_ADMIN")
    public DocumentTemplateService.TemplateView saveTemplate(@PathParam("entityCode") String entityCode,
                                                             TemplateRequest request) {
        return templates.save(entityCode, request.documentType(), request.content());
    }

    /**
     * Request body of a document-template edit.
     *
     * @param documentType the template type name
     * @param content      the edited content
     */
    public record TemplateRequest(String documentType, String content) {
    }

    /**
     * Lists the statements of an entity's period.
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @return the statement summaries
     */
    @GET
    @Path("/{entityCode}/{month}/statements")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<StatementView> list(@PathParam("entityCode") String entityCode, @PathParam("month") String month) {
        requireScope(entityCode);
        return query.statements(entityCode, LocalDate.parse(month));
    }

    /**
     * Returns the informative statement of a group member paid by its grouping (BRD-11).
     *
     * @param entityCode the entity code
     * @param month      the period month
     * @param code       the member's production code
     * @return the informative statement content
     */
    @GET
    @Path("/{entityCode}/{month}/member/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public StatementContent memberStatement(@PathParam("entityCode") String entityCode,
                                            @PathParam("month") String month, @PathParam("code") String code) {
        requireScope(entityCode);
        return statements.memberStatement(entityCode, LocalDate.parse(month), code);
    }

    /**
     * Returns the rendered content of a statement.
     *
     * @param statementId the statement id
     * @return the statement content
     */
    @GET
    @Path("/statements/{statementId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public StatementContent content(@PathParam("statementId") Long statementId) {
        requireStatementScope(statementId);
        return statements.render(statementId);
    }

    /**
     * Returns the spreadsheet (CSV) form of a statement.
     *
     * @param statementId the statement id
     * @return the CSV content
     */
    @GET
    @Path("/statements/{statementId}/spreadsheet")
    @Produces("text/csv")
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public String spreadsheet(@PathParam("statementId") Long statementId) {
        requireStatementScope(statementId);
        return statements.spreadsheet(statementId);
    }

    /**
     * Publishes a statement.
     *
     * @param statementId the statement id
     * @return the published statement summary
     */
    @POST
    @Path("/statements/{statementId}/publish")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public StatementView publish(@PathParam("statementId") Long statementId) {
        requireStatementScope(statementId);
        Statement statement = statements.publish(statementId);
        return new StatementView(statement.getId(), statement.statementNumber, statement.versionNo,
                statement.netAmount, statement.status);
    }

    /**
     * Issues a rectificative version of a statement.
     *
     * @param statementId the statement id
     * @return the rectificative statement summary
     */
    @POST
    @Path("/statements/{statementId}/rectify")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public StatementView rectify(@PathParam("statementId") Long statementId) {
        requireStatementScope(statementId);
        Statement statement = statements.rectify(statementId);
        return new StatementView(statement.getId(), statement.statementNumber, statement.versionNo,
                statement.netAmount, statement.status);
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

    /**
     * Checks the statement's entity is within the caller's partition.
     *
     * @param statementId the statement id
     */
    private void requireStatementScope(Long statementId) {
        Statement statement = Statement.findById(statementId);
        if (statement == null) {
            throw new ResourceNotFoundException("Bordereau inconnu : " + statementId);
        }
        requireScope(statement.entity.code);
    }
}
