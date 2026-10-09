package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.AccountMapping;
import fr.alptis.remuneration.domain.AccountRole;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AccountingScheme;
import fr.alptis.remuneration.domain.AccountingVariant;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read model for the accounting screens: the accounting schema and account mapping of an entity
 * (FIN-02) and the search of a period's entries (FIN-01, FIN-05, FIN-07). The balance view of an
 * account is read elsewhere; this service exposes already-written schema and entries.
 */
@ApplicationScoped
public class AccountingQueryService {

    /**
     * Returns the accounting schema and the account mapping of an entity.
     *
     * @param entityCode the entity code
     * @return the schema view
     */
    public SchemeView scheme(String entityCode) {
        LegalEntity entity = entity(entityCode);
        List<SchemeRowView> schema = AccountingScheme.<AccountingScheme>list("entity = ?1 order by id", entity)
                .stream()
                .map(s -> new SchemeRowView(s.accountingEvent, s.nature, s.variant, s.debitRole, s.creditRole))
                .toList();
        List<MappingRowView> mapping = AccountMapping.<AccountMapping>list("entity = ?1 order by id", entity)
                .stream()
                .map(m -> new MappingRowView(m.accountRole, m.accountNumber, m.journal))
                .toList();
        return new SchemeView(schema, mapping);
    }

    /**
     * Returns the accounting entries of a period (search).
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the entries, ordered as written
     */
    public List<EntryView> entries(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = entity(entityCode);
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue");
        }
        return AccountingEntry.<AccountingEntry>list("period = ?1 order by id", period).stream()
                .map(e -> new EntryView(e.getId(), e.accountingEvent, e.journal, e.entryDate, e.debitAccount,
                        e.creditAccount, e.amount, e.accountingMonth,
                        e.commissionLine == null ? null : e.commissionLine.getId(),
                        e.paymentOrder == null ? null : e.paymentOrder.endToEndId))
                .toList();
    }

    /**
     * Looks up an entity by code.
     *
     * @param code the entity code
     * @return the entity
     */
    private LegalEntity entity(String code) {
        LegalEntity entity = LegalEntity.find("code", code).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + code);
        }
        return entity;
    }

    /**
     * One schema row: the event, the nature, the variant and the debit and credit roles.
     *
     * @param event     the accounting event
     * @param nature    the line nature, or null for any
     * @param variant   the variant
     * @param debitRole the debit role
     * @param creditRole the credit role
     */
    public record SchemeRowView(AccountingEvent event, LineNature nature, AccountingVariant variant,
                                AccountRole debitRole, AccountRole creditRole) {
    }

    /**
     * One mapping row: a role, its account number and its journal.
     *
     * @param role          the account role
     * @param accountNumber the account number
     * @param journal       the journal
     */
    public record MappingRowView(AccountRole role, String accountNumber, String journal) {
    }

    /**
     * The schema and mapping of an entity.
     *
     * @param schema  the schema rows
     * @param mapping the mapping rows
     */
    public record SchemeView(List<SchemeRowView> schema, List<MappingRowView> mapping) {
    }

    /**
     * One accounting entry.
     *
     * @param id              the entry id
     * @param accountingEvent the accounting event
     * @param journal         the journal
     * @param entryDate       the entry date
     * @param debitAccount    the debit account
     * @param creditAccount   the credit account
     * @param amount          the amount
     * @param accountingMonth the accounting month
     * @param lineId          the commission line id, or null
     * @param endToEndId      the payment order reference, or null
     */
    public record EntryView(Long id, AccountingEvent accountingEvent, String journal, LocalDate entryDate,
                            String debitAccount, String creditAccount, BigDecimal amount,
                            LocalDate accountingMonth, Long lineId, String endToEndId) {
    }
}
