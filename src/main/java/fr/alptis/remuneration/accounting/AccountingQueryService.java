package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.AccountMapping;
import fr.alptis.remuneration.domain.AccountRole;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AccountingScheme;
import fr.alptis.remuneration.domain.AccountingVariant;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                .map(AccountingQueryService::toEntryView)
                .toList();
    }

    /**
     * Searches an entity's accounting entries across every month (FIN-01, FIN-05), optionally filtered
     * by an account (debit or credit) and a fiscal year.
     *
     * @param entityCode  the entity code
     * @param account     an account number to match on debit or credit, or null/blank for any
     * @param fiscalYear  a fiscal year to match, or null for any
     * @return the matching entries, most recent accounting month first
     */
    public List<EntryView> searchEntries(String entityCode, String account, Short fiscalYear) {
        LegalEntity entity = entity(entityCode);
        String wanted = account == null || account.isBlank() ? null : account;
        return AccountingEntry.<AccountingEntry>list("entity = ?1 order by accountingMonth desc, id", entity).stream()
                .filter(e -> wanted == null || wanted.equals(e.debitAccount) || wanted.equals(e.creditAccount))
                .filter(e -> fiscalYear == null || fiscalYear.equals(e.fiscalYear))
                .map(AccountingQueryService::toEntryView)
                .toList();
    }

    /**
     * Returns the audit chain of an accounting entry (FIN-07): the source flux (event), the bordereau,
     * the commission line with its basis, rate and rule version, the payment order, and the entry.
     *
     * @param entityCode the entity code
     * @param entryId    the accounting-entry id
     * @return the chain view
     * @throws ResourceNotFoundException if the entry is unknown or out of the entity
     */
    public ChainView entryChain(String entityCode, Long entryId) {
        LegalEntity entity = entity(entityCode);
        AccountingEntry entry = AccountingEntry.findById(entryId);
        if (entry == null || entry.entity == null || !entry.entity.code.equals(entity.code)) {
            throw new ResourceNotFoundException("Écriture introuvable : " + entryId);
        }
        var line = entry.commissionLine;
        return new ChainView(entry.getId(),
                line == null || line.event == null ? null : line.event.eventType.name(),
                line == null || line.event == null ? null : line.event.sourceKey,
                line == null || line.statement == null ? null : line.statement.statementNumber,
                line == null ? null : line.getId(),
                line == null || line.nature == null ? null : line.nature.name(),
                line == null ? null : line.basisAmount,
                line == null ? null : line.rate,
                line == null ? null : line.amount,
                line == null || line.ruleVersion == null ? null
                        : line.ruleVersion.rule.code + " v" + line.ruleVersion.versionNo,
                // The payment order carried by the entry, or the one that paid its line, so a commission
                // entry still traces to its virement (REP-02, full chain from one entry).
                paymentReference(entry, line),
                entry.debitAccount, entry.creditAccount, entry.amount);
    }

    /**
     * The end-to-end reference of the payment order attached to an entry, or failing that the one that
     * paid the entry's commission line, so a commission entry still traces to its payment (REP-02).
     *
     * @param entry the accounting entry
     * @param line  the entry's commission line, possibly null
     * @return the payment reference, or null
     */
    private static String paymentReference(AccountingEntry entry, CommissionLine line) {
        if (entry.paymentOrder != null) {
            return entry.paymentOrder.endToEndId;
        }
        if (line != null && line.paymentOrder != null) {
            return line.paymentOrder.endToEndId;
        }
        return null;
    }

    /**
     * Maps an accounting entry to its view.
     *
     * @param e the entry
     * @return the view
     */
    private static EntryView toEntryView(AccountingEntry e) {
        return new EntryView(e.getId(), e.accountingEvent, e.journal, e.entryDate, e.debitAccount,
                e.creditAccount, e.amount, e.accountingMonth,
                e.commissionLine == null ? null : e.commissionLine.getId(),
                e.paymentOrder == null ? null : e.paymentOrder.endToEndId);
    }

    /**
     * Reconciles an entity's acquired and paid commissions, by beneficiary and at the global level
     * (FIN-06): acquired covers every counted line (earned, payable, paid); paid covers the paid
     * lines; the gap is what remains to pay.
     *
     * @param entityCode the entity code
     * @return the reconciliation view
     */
    public Reconciliation reconciliation(String entityCode) {
        LegalEntity entity = entity(entityCode);
        Map<String, BigDecimal[]> byBeneficiary = new LinkedHashMap<>();
        BigDecimal acquiredTotal = BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and status in ?2", entity,
                List.of(LineStatus.EARNED, LineStatus.PAYABLE, LineStatus.PAID))) {
            String beneficiary = line.originDistributor == null ? "—" : line.originDistributor.partnerCode;
            BigDecimal[] totals = byBeneficiary.computeIfAbsent(beneficiary,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(line.amount);
            acquiredTotal = acquiredTotal.add(line.amount);
            if (line.status == LineStatus.PAID) {
                totals[1] = totals[1].add(line.amount);
                paidTotal = paidTotal.add(line.amount);
            }
        }
        List<BeneficiaryReconciliation> rows = byBeneficiary.entrySet().stream()
                .map(e -> new BeneficiaryReconciliation(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0].subtract(e.getValue()[1])))
                .toList();
        return new Reconciliation(acquiredTotal, paidTotal, acquiredTotal.subtract(paidTotal), rows);
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

    /**
     * The FIN-07 audit chain of an accounting entry: flux, bordereau, commission line (basis, rate,
     * rule), payment order and the entry itself.
     *
     * @param entryId       the accounting-entry id
     * @param eventType     the source event type, or null
     * @param eventKey      the source event key, or null
     * @param statement     the bordereau number, or null
     * @param lineId        the commission-line id, or null
     * @param lineNature    the commission-line nature, or null
     * @param basis         the basis amount, or null
     * @param rate          the rate, or null
     * @param lineAmount    the commission-line amount, or null
     * @param ruleVersion   the rule code and version, or null
     * @param paymentOrder  the payment-order reference, or null
     * @param debitAccount  the entry's debit account
     * @param creditAccount the entry's credit account
     * @param amount        the entry's amount
     */
    public record ChainView(Long entryId, String eventType, String eventKey, String statement, Long lineId,
                            String lineNature, BigDecimal basis, BigDecimal rate, BigDecimal lineAmount,
                            String ruleVersion, String paymentOrder, String debitAccount, String creditAccount,
                            BigDecimal amount) {
    }

    /**
     * The reconciliation of acquired and paid commissions (FIN-06): the global totals and the gap,
     * with a row per beneficiary.
     *
     * @param acquired      the total acquired
     * @param paid          the total paid
     * @param gap           the acquired-minus-paid gap (still to pay)
     * @param beneficiaries the per-beneficiary breakdown
     */
    public record Reconciliation(BigDecimal acquired, BigDecimal paid, BigDecimal gap,
                                 List<BeneficiaryReconciliation> beneficiaries) {
    }

    /**
     * One beneficiary's acquired-versus-paid reconciliation (FIN-06).
     *
     * @param distributor the beneficiary partner code
     * @param acquired    the acquired total
     * @param paid        the paid total
     * @param gap         the gap still to pay
     */
    public record BeneficiaryReconciliation(String distributor, BigDecimal acquired, BigDecimal paid,
                                            BigDecimal gap) {
    }
}
