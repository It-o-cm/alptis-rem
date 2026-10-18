package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.AccountMapping;
import fr.alptis.remuneration.domain.AccountRole;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AccountingScheme;
import fr.alptis.remuneration.domain.AccountingVariant;
import fr.alptis.remuneration.domain.LegalEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Seeds the accounting schema (section 10.2) and the account mapping (requirement FIN-02) of an
 * entity. The schema links each accounting event and variant to a debit and a credit account role;
 * the mapping resolves each role to an account number and a journal. In profile {@code dev} the
 * account numbers are placeholders awaiting the chart of accounts (hypothesis H-22); the structure
 * is the one the DAF validates.
 */
@ApplicationScoped
public class AccountingSetupService {

    /** The single commission journal used for every entity in profile {@code dev}. */
    static final String JOURNAL = "COM";

    /** One row of the accounting schema: an event, a variant and its debit and credit roles. */
    private record SchemeRow(AccountingEvent event, AccountingVariant variant, AccountRole debit,
                             AccountRole credit) {
    }

    /** The accounting schema of section 10.2, event by event and variant by variant. */
    private static final List<SchemeRow> SCHEME = List.of(
            new SchemeRow(AccountingEvent.FORECAST_RECOGNITION, AccountingVariant.STANDARD,
                    AccountRole.FORECAST_EXPENSE, AccountRole.PROVISION),
            new SchemeRow(AccountingEvent.FORECAST_ADJUSTMENT, AccountingVariant.STANDARD,
                    AccountRole.FORECAST_EXPENSE, AccountRole.PROVISION),
            new SchemeRow(AccountingEvent.FORECAST_ADJUSTMENT, AccountingVariant.NEGATIVE,
                    AccountRole.PROVISION, AccountRole.FORECAST_EXPENSE),
            new SchemeRow(AccountingEvent.EARNING, AccountingVariant.STANDARD,
                    AccountRole.PROVISION, AccountRole.EARNED_COMMISSIONS),
            new SchemeRow(AccountingEvent.EARNING, AccountingVariant.NO_FORECAST,
                    AccountRole.COMMISSION_EXPENSE, AccountRole.EARNED_COMMISSIONS),
            new SchemeRow(AccountingEvent.PAYABLE_RECOGNITION, AccountingVariant.STANDARD,
                    AccountRole.EARNED_COMMISSIONS, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.PAYMENT, AccountingVariant.STANDARD,
                    AccountRole.DISTRIBUTOR_PAYABLE, AccountRole.BANK),
            new SchemeRow(AccountingEvent.PAYMENT_REJECTION, AccountingVariant.STANDARD,
                    AccountRole.BANK, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.ADVANCE_GRANTED, AccountingVariant.STANDARD,
                    AccountRole.DISTRIBUTOR_ADVANCES, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.ADVANCE_GRANTED, AccountingVariant.SAVINGS,
                    AccountRole.COMMISSION_EXPENSE, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.ADVANCE_OFFSETTING, AccountingVariant.STANDARD,
                    AccountRole.EARNED_COMMISSIONS, AccountRole.DISTRIBUTOR_ADVANCES),
            new SchemeRow(AccountingEvent.ADVANCE_SETTLEMENT, AccountingVariant.STANDARD,
                    AccountRole.DISTRIBUTOR_ADVANCES, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.ADVANCE_SETTLEMENT, AccountingVariant.NEGATIVE,
                    AccountRole.DISTRIBUTOR_PAYABLE, AccountRole.DISTRIBUTOR_ADVANCES),
            new SchemeRow(AccountingEvent.ADVANCE_WRITE_OFF, AccountingVariant.STANDARD,
                    AccountRole.COMMISSION_EXPENSE, AccountRole.DISTRIBUTOR_ADVANCES),
            new SchemeRow(AccountingEvent.CLAWBACK, AccountingVariant.STANDARD,
                    AccountRole.DISTRIBUTOR_PAYABLE, AccountRole.CLAWBACK_INCOME),
            new SchemeRow(AccountingEvent.SUPPLEMENT, AccountingVariant.STANDARD,
                    AccountRole.COMMISSION_EXPENSE, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.SUPPLEMENT, AccountingVariant.NEGATIVE,
                    AccountRole.DISTRIBUTOR_PAYABLE, AccountRole.COMMISSION_EXPENSE),
            new SchemeRow(AccountingEvent.ACCOUNT_TRANSFER, AccountingVariant.STANDARD,
                    AccountRole.DISTRIBUTOR_PAYABLE, AccountRole.DISTRIBUTOR_PAYABLE),
            new SchemeRow(AccountingEvent.INSURER_RECEIVABLE, AccountingVariant.STANDARD,
                    AccountRole.INSURER_RECEIVABLE, AccountRole.COMMISSION_INCOME));

    /** One row of the account mapping: a role and its placeholder account number. */
    private record MappingRow(AccountRole role, String accountNumber) {
    }

    /** The placeholder chart of accounts (hypothesis H-22), one account number per role. */
    private static final List<MappingRow> MAPPING = List.of(
            new MappingRow(AccountRole.FORECAST_EXPENSE, "609100"),
            new MappingRow(AccountRole.PROVISION, "428600"),
            new MappingRow(AccountRole.EARNED_COMMISSIONS, "467200"),
            new MappingRow(AccountRole.DISTRIBUTOR_PAYABLE, "401100"),
            new MappingRow(AccountRole.BANK, "512000"),
            new MappingRow(AccountRole.DISTRIBUTOR_ADVANCES, "409100"),
            new MappingRow(AccountRole.COMMISSION_EXPENSE, "622000"),
            new MappingRow(AccountRole.CLAWBACK_INCOME, "709100"),
            new MappingRow(AccountRole.INSURER_RECEIVABLE, "467100"),
            new MappingRow(AccountRole.COMMISSION_INCOME, "706000"));

    /**
     * Seeds the schema and the mapping of an entity, skipping rows already present. Idempotent so it
     * can run at startup and after a test reset.
     *
     * @param entity the entity to seed
     */
    @Transactional
    public void seed(LegalEntity entity) {
        for (MappingRow row : MAPPING) {
            if (AccountMapping.count("entity = ?1 and accountRole = ?2", entity, row.role()) == 0) {
                AccountMapping mapping = new AccountMapping();
                mapping.entity = entity;
                mapping.accountRole = row.role();
                mapping.accountNumber = row.accountNumber();
                mapping.journal = JOURNAL;
                mapping.persist();
            }
        }
        for (SchemeRow row : SCHEME) {
            if (AccountingScheme.count("entity = ?1 and accountingEvent = ?2 and variant = ?3",
                    entity, row.event(), row.variant()) == 0) {
                AccountingScheme scheme = new AccountingScheme();
                scheme.entity = entity;
                scheme.accountingEvent = row.event();
                scheme.variant = row.variant();
                scheme.debitRole = row.debit();
                scheme.creditRole = row.credit();
                scheme.persist();
            }
        }
    }

    /**
     * Updates the account number mapped to a role for an entity (FIN-02): the change applies to the
     * accounting entries generated from then on, so a later period's extraction carries the new account
     * while already-extracted entries keep theirs.
     *
     * @param entity        the entity
     * @param role          the account role
     * @param accountNumber the new account number
     * @return the updated mapping
     * @throws ResourceNotFoundException when the role is not mapped for the entity
     */
    @Transactional
    public AccountMapping updateAccount(LegalEntity entity, AccountRole role, String accountNumber) {
        AccountMapping mapping = AccountMapping.<AccountMapping>find("entity = ?1 and accountRole = ?2",
                entity, role).firstResult();
        if (mapping == null) {
            throw new ResourceNotFoundException("Rôle de compte non paramétré : " + role);
        }
        mapping.accountNumber = accountNumber;
        return mapping;
    }
}
