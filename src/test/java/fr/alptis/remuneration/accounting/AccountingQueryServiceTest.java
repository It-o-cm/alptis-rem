package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.accounting.AccountingQueryService.EntryView;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.AccountingScheme;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the accounting read model and the idempotent schema seeding. Runs against UNEP in
 * rolled-back transactions, on the schema seeded at startup.
 */
@QuarkusTest
class AccountingQueryServiceTest {

    @Inject
    AccountingQueryService query;
    @Inject
    AccountingSetupService setup;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /**
     * Re-seeding an already-seeded entity adds nothing (idempotent): the schema and mapping counts
     * stay unchanged.
     */
    @Test
    void seeding_is_idempotent() {
        long before = AccountingScheme.count("entity = ?1", unep());
        setup.seed(unep());
        assertEquals(before, AccountingScheme.count("entity = ?1", unep()));
    }

    /**
     * The schema exposes the events and the entries carry the line id or the order reference of their
     * source.
     */
    @Test
    @TestTransaction
    void scheme_and_entries_views() {
        assertTrue(query.scheme("UNEP").schema().stream()
                .anyMatch(s -> s.event() == AccountingEvent.EARNING));

        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal("15.00");
        line.persist();
        entry(period, AccountingEvent.EARNING, line, null);
        PaymentBatch batch = new PaymentBatch();
        batch.entity = unep();
        batch.period = period;
        batch.batchType = BatchType.CYCLE;
        batch.batchReference = "CYCLE-UNEP";
        batch.status = BatchStatus.ISSUED;
        batch.totalAmount = BigDecimal.ZERO;
        batch.persist();
        PaymentOrder order = new PaymentOrder();
        order.batch = batch;
        order.amount = new BigDecimal("15.00");
        order.status = PaymentOrderStatus.ISSUED;
        order.endToEndId = "E2E-1";
        order.persist();
        entry(period, AccountingEvent.PAYMENT, null, order);

        List<EntryView> entries = query.entries("UNEP", M);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.lineId() != null));
        assertTrue(entries.stream().anyMatch(e -> "E2E-1".equals(e.endToEndId())));
    }

    /**
     * The cross-month search filters by account (debit or credit) and fiscal year (FIN-05), and the
     * audit chain links an entry to its flux, bordereau, line and rule (FIN-07); a bare entry yields a
     * chain with null line fields, and an unknown or out-of-entity entry is not found.
     */
    @Test
    @TestTransaction
    void searches_entries_and_reads_the_chain() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();

        fr.alptis.remuneration.domain.BusinessEvent event = new fr.alptis.remuneration.domain.BusinessEvent();
        event.entity = unep();
        event.eventType = fr.alptis.remuneration.domain.EventType.PREMIUM_COLLECTED;
        event.sourceKey = "EVT-1";
        event.persist();
        fr.alptis.remuneration.domain.Statement statement = new fr.alptis.remuneration.domain.Statement();
        statement.entity = unep();
        statement.period = period;
        statement.statementNumber = "ST-CHAIN";
        statement.persist();
        fr.alptis.remuneration.domain.CommissionPlan plan = new fr.alptis.remuneration.domain.CommissionPlan();
        plan.entity = unep();
        plan.code = "CH-PLAN";
        plan.label = "Plan";
        plan.direction = Direction.INBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        fr.alptis.remuneration.domain.Rule rule = new fr.alptis.remuneration.domain.Rule();
        rule.plan = plan;
        rule.code = "CH-RULE";
        rule.label = "Règle";
        rule.family = fr.alptis.remuneration.domain.RuleFamily.LINEAR;
        rule.persist();
        fr.alptis.remuneration.domain.RuleVersion version = new fr.alptis.remuneration.domain.RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = fr.alptis.remuneration.domain.VersionStatus.ACTIVE;
        version.persist();

        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal("15.00");
        line.basisAmount = new BigDecimal("100.00");
        line.rate = new BigDecimal("0.15");
        line.event = event;
        line.statement = statement;
        line.ruleVersion = version;
        line.persist();
        entry(period, AccountingEvent.EARNING, line, null);

        PaymentOrder order = new PaymentOrder();
        order.amount = new BigDecimal("15.00");
        order.status = PaymentOrderStatus.ISSUED;
        order.endToEndId = "E2E-CHAIN";
        order.persist();
        entry(period, AccountingEvent.PAYMENT, null, order);

        // Cross-month search by account (both entries share the debit account), then no match.
        assertEquals(2, query.searchEntries("UNEP", "428600", null).size());
        assertEquals(2, query.searchEntries("UNEP", "467200", null).size());
        assertEquals(0, query.searchEntries("UNEP", "999999", null).size());
        // By fiscal year, blank account, and a non-matching year.
        assertEquals(2, query.searchEntries("UNEP", "", (short) 2026).size());
        assertEquals(0, query.searchEntries("UNEP", null, (short) 2099).size());

        AccountingEntry lineEntry = AccountingEntry.<AccountingEntry>find("commissionLine", line).firstResult();
        var chain = query.entryChain("UNEP", lineEntry.getId());
        assertEquals("PREMIUM_COLLECTED", chain.eventType());
        assertEquals("EVT-1", chain.eventKey());
        assertEquals("ST-CHAIN", chain.statement());
        assertEquals("LINEAR", chain.lineNature());
        assertEquals("CH-RULE v1", chain.ruleVersion());
        assertEquals(0, chain.basis().compareTo(new BigDecimal("100.00")));
        // The line carries no payment order yet, so the commission entry's chain has no virement.
        assertNull(chain.paymentOrder());

        AccountingEntry orderEntry = AccountingEntry.<AccountingEntry>find("paymentOrder", order).firstResult();
        var bareChain = query.entryChain("UNEP", orderEntry.getId());
        assertNull(bareChain.eventType());
        assertNull(bareChain.statement());
        assertNull(bareChain.lineNature());
        assertNull(bareChain.ruleVersion());
        assertEquals("E2E-CHAIN", bareChain.paymentOrder());

        // A line with no event, bordereau, rule version nor nature: the line is linked but its
        // attributes are null (covers the middle legs of the chain's conditions).
        CommissionLine sparse = new CommissionLine();
        sparse.entity = unep();
        sparse.period = period;
        sparse.direction = Direction.OUTBOUND;
        sparse.status = LineStatus.EARNED;
        sparse.amount = new BigDecimal("1.00");
        sparse.persist();
        entry(period, AccountingEvent.EARNING, sparse, null);
        AccountingEntry sparseEntry = AccountingEntry.<AccountingEntry>find("commissionLine", sparse).firstResult();
        var sparseChain = query.entryChain("UNEP", sparseEntry.getId());
        assertEquals(sparse.getId(), sparseChain.lineId());
        assertNull(sparseChain.eventType());
        assertNull(sparseChain.statement());
        assertNull(sparseChain.lineNature());
        assertNull(sparseChain.ruleVersion());

        // A paid commission line: its entry carries no payment order directly, but the chain traces to
        // the virement that settled the line (REP-02 full chain from one commission entry).
        PaymentOrder lineOrder = new PaymentOrder();
        lineOrder.amount = new BigDecimal("15.00");
        lineOrder.status = PaymentOrderStatus.ISSUED;
        lineOrder.endToEndId = "E2E-LINE";
        lineOrder.persist();
        CommissionLine paid = new CommissionLine();
        paid.entity = unep();
        paid.period = period;
        paid.direction = Direction.OUTBOUND;
        paid.status = LineStatus.PAID;
        paid.amount = new BigDecimal("15.00");
        paid.paymentOrder = lineOrder;
        paid.persist();
        entry(period, AccountingEvent.EARNING, paid, null);
        AccountingEntry paidEntry = AccountingEntry.<AccountingEntry>find("commissionLine", paid).firstResult();
        assertEquals("E2E-LINE", query.entryChain("UNEP", paidEntry.getId()).paymentOrder());

        // An entry with neither a commission line nor a payment order: the chain has no virement.
        entry(period, AccountingEvent.EARNING, null, null);
        AccountingEntry bare = AccountingEntry.<AccountingEntry>find(
                "commissionLine is null and paymentOrder is null and entity = ?1", unep()).firstResult();
        assertNull(query.entryChain("UNEP", bare.getId()).paymentOrder());

        // An entry of another entity, and one with no entity at all, are not found for UNEP.
        AccountingEntry crossEntity = new AccountingEntry();
        crossEntity.entity = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
        crossEntity.period = period;
        crossEntity.accountingEvent = AccountingEvent.EARNING;
        crossEntity.amount = new BigDecimal("1.00");
        crossEntity.persist();
        assertThrows(ResourceNotFoundException.class, () -> query.entryChain("UNEP", crossEntity.getId()));
        AccountingEntry noEntity = new AccountingEntry();
        noEntity.period = period;
        noEntity.accountingEvent = AccountingEvent.EARNING;
        noEntity.amount = new BigDecimal("1.00");
        noEntity.persist();
        assertThrows(ResourceNotFoundException.class, () -> query.entryChain("UNEP", noEntity.getId()));
        assertThrows(ResourceNotFoundException.class, () -> query.entryChain("UNEP", -1L));
    }

    /**
     * The reconciliation sums acquired (earned, payable, paid) and paid lines per beneficiary and
     * globally (FIN-06); a line with no beneficiary is grouped under a dash.
     */
    @Test
    @TestTransaction
    void reconciles_acquired_and_paid() {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = M;
        period.state = PeriodState.CONTROLLED;
        period.persist();
        fr.alptis.remuneration.domain.Distributor d1 = new fr.alptis.remuneration.domain.Distributor();
        d1.partnerCode = "D1";
        d1.legalName = "D1";
        d1.persist();
        reconLine(period, LineStatus.EARNED, "100.00", d1);
        reconLine(period, LineStatus.PAID, "60.00", d1);
        reconLine(period, LineStatus.PAYABLE, "40.00", null);
        // A forecast line is not acquired and must be ignored.
        reconLine(period, LineStatus.FORECAST, "999.00", d1);

        var recon = query.reconciliation("UNEP");
        assertEquals(0, recon.acquired().compareTo(new BigDecimal("200.00")));
        assertEquals(0, recon.paid().compareTo(new BigDecimal("60.00")));
        assertEquals(0, recon.gap().compareTo(new BigDecimal("140.00")));
        var d1Row = recon.beneficiaries().stream().filter(b -> "D1".equals(b.distributor())).findFirst().orElseThrow();
        assertEquals(0, d1Row.acquired().compareTo(new BigDecimal("160.00")));
        assertEquals(0, d1Row.paid().compareTo(new BigDecimal("60.00")));
        var noneRow = recon.beneficiaries().stream().filter(b -> "—".equals(b.distributor())).findFirst().orElseThrow();
        assertEquals(0, noneRow.gap().compareTo(new BigDecimal("40.00")));
    }

    /** Posts a commission line for the reconciliation, with an optional beneficiary. */
    private static void reconLine(Period period, LineStatus status, String amount,
                                  fr.alptis.remuneration.domain.Distributor distributor) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = status;
        line.amount = new BigDecimal(amount);
        line.originDistributor = distributor;
        line.persist();
    }

    /**
     * An unknown entity and an unknown period are reported as not found.
     */
    @Test
    @TestTransaction
    void unknown_paths_are_not_found() {
        assertThrows(ResourceNotFoundException.class, () -> query.scheme("GHOST"));
        assertThrows(ResourceNotFoundException.class, () -> query.entries("UNEP", LocalDate.of(2099, 1, 1)));
    }

    /** Creates an accounting entry for a line or an order. */
    private static void entry(Period period, AccountingEvent event, CommissionLine line, PaymentOrder order) {
        AccountingEntry e = new AccountingEntry();
        e.entity = unep();
        e.period = period;
        e.accountingEvent = event;
        e.journal = "COM";
        e.entryDate = LocalDate.of(2026, 9, 30);
        e.debitAccount = "428600";
        e.creditAccount = "467200";
        e.amount = new BigDecimal("15.00");
        e.fiscalYear = (short) 2026;
        e.accountingMonth = M;
        e.commissionLine = line;
        e.paymentOrder = order;
        e.persist();
    }
}
