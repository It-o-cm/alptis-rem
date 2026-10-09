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
