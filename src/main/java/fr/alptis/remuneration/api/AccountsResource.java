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
                Boolean.TRUE.equals(account.blocked), account.blockReason);
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
     */
    public record AccountView(Long id, PayeeType payeeType, Long payeeId, BigDecimal balance, boolean blocked,
                              String blockReason) {
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
     * Request body of an account block.
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
