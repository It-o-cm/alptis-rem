package fr.alptis.remuneration.api;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
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
 * Interface endpoints for the distributor-account screens (CPT-02 to CPT-07, section 8.8): a
 * distributor's accounts with their balances, the compensation between them, the manual block of an
 * account, and the list of debtor accounts.
 */
@Path("/api/ui/v1/accounts")
public class AccountsResource {

    private final AccountService accounts;
    private final EntityScope scope;

    /**
     * Creates the resource.
     *
     * @param accounts the distributor-account service
     * @param scope    the entity partition
     */
    @Inject
    public AccountsResource(AccountService accounts, EntityScope scope) {
        this.accounts = accounts;
        this.scope = scope;
    }

    /**
     * Returns a distributor's accounts in an entity with their balances (CPT-02, CPT-06).
     *
     * @param entityCode  the entity code
     * @param partnerCode the distributor partner code
     * @return the distributor account view
     */
    @GET
    @Path("/{entityCode}/distributor/{partnerCode}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public DistributorView distributor(@PathParam("entityCode") String entityCode,
                                       @PathParam("partnerCode") String partnerCode) {
        LegalEntity entity = requireEntity(entityCode);
        Distributor distributor = Distributor.<Distributor>find("partnerCode", partnerCode).firstResult();
        if (distributor == null) {
            throw new ResourceNotFoundException("Distributeur inconnu : " + partnerCode);
        }
        return view(partnerCode, accounts.accountsOfDistributor(entity, distributor));
    }

    /**
     * Returns a distributor's running account balance month by month (for the balance chart).
     *
     * @param entityCode  the entity code
     * @param partnerCode the distributor partner code
     * @return the balance series
     */
    @GET
    @Path("/{entityCode}/distributor/{partnerCode}/balance-series")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<AccountService.MonthBalance> balanceSeries(@PathParam("entityCode") String entityCode,
                                                           @PathParam("partnerCode") String partnerCode) {
        LegalEntity entity = requireEntity(entityCode);
        Distributor distributor = Distributor.<Distributor>find("partnerCode", partnerCode).firstResult();
        if (distributor == null) {
            throw new ResourceNotFoundException("Distributeur inconnu : " + partnerCode);
        }
        return accounts.balanceSeries(entity, distributor);
    }

    /**
     * Lists the entity's debtor accounts (CPT-07).
     *
     * @param entityCode the entity code
     * @return the debtor account views
     */
    @GET
    @Path("/{entityCode}/debtors")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<AccountView> debtors(@PathParam("entityCode") String entityCode) {
        LegalEntity entity = requireEntity(entityCode);
        List<AccountView> views = new ArrayList<>();
        for (DistributorAccount account : accounts.debtors(entity)) {
            views.add(accountView(account));
        }
        return views;
    }

    /**
     * Compensates a distributor's accounts in an entity's period (CPT-04).
     *
     * @param entityCode  the entity code
     * @param month       the period month
     * @param partnerCode the distributor partner code
     * @return the number of transfer lines written
     */
    @POST
    @Path("/{entityCode}/{month}/distributor/{partnerCode}/compensate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public CompensationResult compensate(@PathParam("entityCode") String entityCode,
                                         @PathParam("month") String month,
                                         @PathParam("partnerCode") String partnerCode) {
        LegalEntity entity = requireEntity(entityCode);
        Distributor distributor = Distributor.<Distributor>find("partnerCode", partnerCode).firstResult();
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, LocalDate.parse(month))
                .firstResult();
        if (distributor == null || period == null) {
            throw new ResourceNotFoundException("Distributeur ou période inconnu");
        }
        return new CompensationResult(accounts.compensate(entity, distributor, period));
    }

    /**
     * Blocks an account with a reason and an optional lifting date (CPT-05).
     *
     * @param accountId the account id
     * @param request   the block request
     * @return the blocked account view
     */
    @POST
    @Path("/{accountId}/block")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public AccountView block(@PathParam("accountId") Long accountId, BlockRequest request) {
        requireAccountScope(accountId);
        LocalDate until = request.until() == null ? null : LocalDate.parse(request.until());
        return accountView(accounts.block(accountId, request.reason(), until));
    }

    /**
     * Lifts the block on an account (CPT-05).
     *
     * @param accountId the account id
     * @return the unblocked account view
     */
    @POST
    @Path("/{accountId}/unblock")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public AccountView unblock(@PathParam("accountId") Long accountId) {
        requireAccountScope(accountId);
        return accountView(accounts.unblock(accountId));
    }

    /**
     * Lists a distributor's commission lines in an entity, with their status and block state (CPT-05,
     * CPT-06), for the line-blocking screen.
     *
     * @param entityCode  the entity code
     * @param partnerCode the distributor partner code
     * @return the commission-line views
     */
    @GET
    @Path("/{entityCode}/distributor/{partnerCode}/lines")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN", "BUSINESS_VALIDATOR", "FINANCE_CONTROLLER", "VIEWER", "AUDITOR"})
    public List<LineView> lines(@PathParam("entityCode") String entityCode,
                                @PathParam("partnerCode") String partnerCode) {
        LegalEntity entity = requireEntity(entityCode);
        Distributor distributor = Distributor.<Distributor>find("partnerCode", partnerCode).firstResult();
        if (distributor == null) {
            throw new ResourceNotFoundException("Distributeur inconnu : " + partnerCode);
        }
        return accounts.linesOfDistributor(entity, distributor).stream().map(AccountsResource::lineView).toList();
    }

    /**
     * Blocks a single commission line with a reason and an optional lifting date (CPT-05).
     *
     * @param lineId  the commission-line id
     * @param request the block request
     * @return the blocked line view
     */
    @POST
    @Path("/lines/{lineId}/block")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public LineView blockLine(@PathParam("lineId") Long lineId, BlockRequest request) {
        requireLineScope(lineId);
        LocalDate until = request.until() == null ? null : LocalDate.parse(request.until());
        return lineView(accounts.blockLine(lineId, request.reason(), until));
    }

    /**
     * Lifts the block on a commission line (CPT-05).
     *
     * @param lineId the commission-line id
     * @return the unblocked line view
     */
    @POST
    @Path("/lines/{lineId}/unblock")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"OPERATOR", "BUSINESS_ADMIN"})
    public LineView unblockLine(@PathParam("lineId") Long lineId) {
        requireLineScope(lineId);
        return lineView(accounts.unblockLine(lineId));
    }

    /**
     * Returns the entity, checking it is within the caller's partition.
     *
     * @param entityCode the entity code
     * @return the entity
     */
    private LegalEntity requireEntity(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        return entity;
    }

    /**
     * Checks the account's entity is within the caller's partition.
     *
     * @param accountId the account id
     */
    private void requireAccountScope(Long accountId) {
        DistributorAccount account = DistributorAccount.findById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Compte inconnu : " + accountId);
        }
        if (!scope.canSee(account.entity.code)) {
            throw new ResourceNotFoundException("Compte hors périmètre : " + accountId);
        }
    }

    /**
     * Checks the commission line's entity is within the caller's partition.
     *
     * @param lineId the commission-line id
     */
    private void requireLineScope(Long lineId) {
        fr.alptis.remuneration.domain.CommissionLine line =
                fr.alptis.remuneration.domain.CommissionLine.findById(lineId);
        if (line == null) {
            throw new ResourceNotFoundException("Ligne inconnue : " + lineId);
        }
        if (!scope.canSee(line.entity.code)) {
            throw new ResourceNotFoundException("Ligne hors périmètre : " + lineId);
        }
    }

    /**
     * Builds a commission-line view with its status and block state.
     *
     * @param line the commission line
     * @return the line view
     */
    private static LineView lineView(fr.alptis.remuneration.domain.CommissionLine line) {
        return new LineView(line.getId(), line.nature.name(), line.amount, line.status.name(),
                line.attachmentMonth.toString(), Boolean.TRUE.equals(line.blocked), line.blockReason,
                line.blockedUntil == null ? null : line.blockedUntil.toString());
    }

    /**
     * Builds the distributor view from its accounts.
     *
     * @param partnerCode the partner code
     * @param list        the distributor's accounts
     * @return the view
     */
    private DistributorView view(String partnerCode, List<DistributorAccount> list) {
        List<AccountView> views = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (DistributorAccount account : list) {
            AccountView accountView = accountView(account);
            views.add(accountView);
            total = total.add(accountView.balance());
        }
        return new DistributorView(partnerCode, views, total);
    }

    /**
     * Builds an account view with its current balance.
     *
     * @param account the account
     * @return the account view
     */
    private AccountView accountView(DistributorAccount account) {
        return new AccountView(account.getId(), account.payeeType, account.payeeId, accounts.balance(account),
                Boolean.TRUE.equals(account.blocked), account.blockReason,
                account.blockedUntil == null ? null : account.blockedUntil.toString());
    }

    /**
     * One account of a distributor view.
     *
     * @param id          the account id
     * @param payeeType   the payee type
     * @param payeeId     the payee id
     * @param balance     the current balance
     * @param blocked     whether the account is blocked
     * @param blockReason the block reason, possibly null
     * @param blockedUntil the lifting date (ISO), possibly null
     */
    public record AccountView(Long id, PayeeType payeeType, Long payeeId, BigDecimal balance, boolean blocked,
                              String blockReason, String blockedUntil) {
    }

    /**
     * A distributor's accounts and their total.
     *
     * @param partnerCode the partner code
     * @param accounts    the account views
     * @param total       the total balance
     */
    public record DistributorView(String partnerCode, List<AccountView> accounts, BigDecimal total) {
    }

    /**
     * One commission line of a distributor, with its block state (CPT-05, CPT-06).
     *
     * @param id              the line id
     * @param nature          the line nature
     * @param amount          the amount
     * @param status          the line status
     * @param attachmentMonth the attachment month (ISO), possibly null
     * @param blocked         whether the line is blocked
     * @param blockReason     the block reason, possibly null
     * @param blockedUntil    the lifting date (ISO), possibly null
     */
    public record LineView(Long id, String nature, BigDecimal amount, String status, String attachmentMonth,
                           boolean blocked, String blockReason, String blockedUntil) {
    }

    /**
     * Request body of an account or line block.
     *
     * @param reason the block reason
     * @param until  the lifting date (ISO), or null
     */
    public record BlockRequest(String reason, String until) {
    }

    /**
     * Result of a compensation.
     *
     * @param transfers the number of transfer lines written
     */
    public record CompensationResult(int transfers) {
    }
}
