package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.integration.ImportService;
import fr.alptis.remuneration.security.EntityScope;
import fr.alptis.remuneration.accounts.AccountService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing service of the public read API (section 9.8, OUT-04): a distributor's accounts, commission
 * lines, statements and escompte agreements, the state of the periods and the deposit and result of
 * an import. Every distributor resource is confined to the caller's visible entities; an explicit
 * {@code entity} must be one of them, otherwise the resource is reported as not found (cross-entity
 * isolation, CR-70).
 */
@ApplicationScoped
public class PublicApiService {

    /** The entity-visibility scope of the caller. */
    private final EntityScope scope;

    /** The distributor-account service, for balances. */
    private final AccountService accounts;

    /** The import service, for the deposit endpoint. */
    private final ImportService imports;

    /**
     * Creates the service.
     *
     * @param scope    the entity-visibility scope
     * @param accounts the distributor-account service
     * @param imports  the import service
     */
    @Inject
    public PublicApiService(EntityScope scope, AccountService accounts, ImportService imports) {
        this.scope = scope;
        this.accounts = accounts;
        this.imports = imports;
    }

    /**
     * Resolves the entity codes in scope: the single given entity when visible, otherwise every
     * visible entity.
     *
     * @param entity the requested entity code, or null
     * @return the entity codes to read
     */
    private List<String> codes(String entity) {
        if (entity != null) {
            if (!scope.canSee(entity)) {
                throw new ResourceNotFoundException("Entité hors périmètre : " + entity);
            }
            return List.of(entity);
        }
        if (scope.unrestricted()) {
            return LegalEntity.<LegalEntity>listAll().stream().map(e -> e.code).toList();
        }
        return scope.visibleCodes();
    }

    /**
     * Resolves a distributor by its partner code.
     *
     * @param code the partner code
     * @return the distributor
     */
    private Distributor distributor(String code) {
        Distributor distributor = Distributor.find("partnerCode", code).firstResult();
        if (distributor == null) {
            throw new ResourceNotFoundException("Distributeur inconnu : " + code);
        }
        return distributor;
    }

    /**
     * Returns the accounts and payment status of a distributor across the entities in scope.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @return the account views
     */
    public List<AccountDto> accounts(String code, String entity) {
        Distributor distributor = distributor(code);
        List<String> codes = codes(entity);
        List<AccountDto> result = new ArrayList<>();
        for (DistributorAccount account : DistributorAccount.<DistributorAccount>list(
                "payeeType = ?1 and payeeId = ?2 and entity.code in ?3", PayeeType.DISTRIBUTOR,
                distributor.getId(), codes)) {
            result.add(new AccountDto(account.entity.code, accounts.balance(account),
                    Boolean.TRUE.equals(account.blocked)));
        }
        return result;
    }

    /**
     * Returns the commission lines of a distributor, optionally filtered by period, nature and status.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @param month  the period month, or null
     * @param nature the nature, or null
     * @param status the status, or null
     * @return the line views
     */
    public List<LineDto> commissionLines(String code, String entity, LocalDate month, LineNature nature,
                                         LineStatus status) {
        Distributor distributor = distributor(code);
        List<String> codes = codes(entity);
        StringBuilder query = new StringBuilder("originDistributor = ?1 and entity.code in ?2");
        List<Object> params = new ArrayList<>(List.of(distributor, codes));
        if (month != null) {
            params.add(month);
            query.append(" and period.periodMonth = ?").append(params.size());
        }
        if (nature != null) {
            params.add(nature);
            query.append(" and nature = ?").append(params.size());
        }
        if (status != null) {
            params.add(status);
            query.append(" and status = ?").append(params.size());
        }
        List<LineDto> result = new ArrayList<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list(query.toString(), params.toArray())) {
            result.add(new LineDto(line.entity.code, line.nature, line.status, line.amount,
                    line.period == null ? null : line.period.periodMonth));
        }
        return result;
    }

    /**
     * Returns the statements of a distributor across the entities in scope.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @return the statement views
     */
    public List<StatementDto> statements(String code, String entity) {
        Distributor distributor = distributor(code);
        List<String> codes = codes(entity);
        List<StatementDto> result = new ArrayList<>();
        for (Statement statement : Statement.<Statement>list(
                "account.payeeType = ?1 and account.payeeId = ?2 and entity.code in ?3", PayeeType.DISTRIBUTOR,
                distributor.getId(), codes)) {
            result.add(new StatementDto(statement.entity.code, statement.statementNumber, statement.versionNo,
                    statement.netAmount, statement.status.name(), statement.pdfRef));
        }
        return result;
    }

    /**
     * Returns the escompte agreements and receivables of a distributor across the entities in scope.
     *
     * @param code   the distributor partner code
     * @param entity the entity filter, or null
     * @return the agreement views
     */
    public List<AgreementDto> advanceAgreements(String code, String entity) {
        Distributor distributor = distributor(code);
        List<String> codes = codes(entity);
        List<AgreementDto> result = new ArrayList<>();
        for (AdvanceAgreement agreement : AdvanceAgreement.<AdvanceAgreement>list(
                "distributor = ?1 and entity.code in ?2", distributor, codes)) {
            result.add(new AgreementDto(agreement.entity.code, agreement.validFrom, agreement.validTo,
                    agreement.maxContracts, agreement.status.name()));
        }
        return result;
    }

    /**
     * Returns the state of the periods of the entities in scope.
     *
     * @param entity the entity filter, or null
     * @return the period views
     */
    public List<PeriodDto> periods(String entity) {
        List<String> codes = codes(entity);
        List<PeriodDto> result = new ArrayList<>();
        for (Period period : Period.<Period>list("entity.code in ?1 order by entity.code, periodMonth", codes)) {
            result.add(new PeriodDto(period.entity.code, period.periodMonth, period.state.name()));
        }
        return result;
    }

    /**
     * Deposits a pivot file or event batch under a flow for an entity, and returns the import result.
     *
     * @param flow         the flow code
     * @param entity       the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the file content
     * @return the import result
     */
    @Transactional
    public ImportResultDto deposit(String flow, String entity, String sourceSystem, String fileName,
                                   String content) {
        if (!scope.canSee(entity)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entity);
        }
        ImportBatch batch = imports.importFile(entity, flow, sourceSystem, fileName, content);
        return result(batch);
    }

    /**
     * Returns the result of an import by its id.
     *
     * @param id the import batch id
     * @return the import result
     */
    public ImportResultDto importResult(Long id) {
        ImportBatch batch = ImportBatch.findById(id);
        if (batch == null || !scope.canSee(batch.entity.code)) {
            throw new ResourceNotFoundException("Import inconnu : " + id);
        }
        return result(batch);
    }

    /**
     * Maps an import batch to its result view.
     *
     * @param batch the import batch
     * @return the result view
     */
    private ImportResultDto result(ImportBatch batch) {
        return new ImportResultDto(batch.getId(), batch.flowCode, batch.status.name(), batch.linesRead,
                batch.linesLoaded, batch.linesRejected, batch.linesOnHold);
    }

    /**
     * A distributor account balance and payment status.
     *
     * @param entity  the entity code
     * @param balance the account balance
     * @param blocked whether the account is blocked
     */
    public record AccountDto(String entity, BigDecimal balance, boolean blocked) {
    }

    /**
     * A commission line.
     *
     * @param entity the entity code
     * @param nature the nature
     * @param status the status
     * @param amount the amount
     * @param month  the period month
     */
    public record LineDto(String entity, LineNature nature, LineStatus status, BigDecimal amount,
                          LocalDate month) {
    }

    /**
     * A statement summary with its PDF reference.
     *
     * @param entity          the entity code
     * @param statementNumber the statement number
     * @param versionNo       the version
     * @param netAmount       the net amount
     * @param status          the status
     * @param pdfRef          the archived PDF reference, or null
     */
    public record StatementDto(String entity, String statementNumber, Integer versionNo, BigDecimal netAmount,
                               String status, String pdfRef) {
    }

    /**
     * An escompte agreement.
     *
     * @param entity       the entity code
     * @param validFrom    the start of validity
     * @param validTo      the end of validity
     * @param maxContracts the maximum number of contracts
     * @param status       the status
     */
    public record AgreementDto(String entity, LocalDate validFrom, LocalDate validTo, Integer maxContracts,
                               String status) {
    }

    /**
     * The state of a period.
     *
     * @param entity the entity code
     * @param month  the period month
     * @param state  the period state
     */
    public record PeriodDto(String entity, LocalDate month, String state) {
    }

    /**
     * The result of an import: its counters and status.
     *
     * @param id       the import batch id
     * @param flowCode the flow code
     * @param status   the status
     * @param read     the lines read
     * @param loaded   the lines loaded
     * @param rejected the lines rejected
     * @param onHold   the lines on hold
     */
    public record ImportResultDto(Long id, String flowCode, String status, Integer read, Integer loaded,
                                  Integer rejected, Integer onHold) {
    }
}
