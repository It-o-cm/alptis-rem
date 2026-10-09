package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CutoffExtract;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Statement;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance of the demonstration dataset (step 15): loading replays September 2026 end-to-end for
 * cabinet P13897 — a 110,00 € commission computed, paid on a cycle lot, accounted and the period
 * closed with its extraction. A second load is idempotent.
 */
@QuarkusTest
class DemoDataServiceTest {

    @Inject
    DemoDataService service;
    @Inject
    TestDatabaseReset reset;

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    /** Resets the database before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
    }

    /**
     * The demo parcours produces the P13897 110,00 € commission, its bordereau and lot, and closes the
     * period; a second load changes nothing.
     */
    @Test
    void replays_september_from_import_to_closing() {
        service.load();

        assertEquals(1, Distributor.count("partnerCode = ?1", "P13897"));
        Period period = Period.<Period>find("entity.code = ?1 and periodMonth = ?2", "ALPTIS_ASSURANCES",
                MONTH).firstResult();
        assertEquals(PeriodState.CLOSED, period.state);

        CommissionLine line = CommissionLine.<CommissionLine>find("period = ?1 and nature = ?2", period,
                LineNature.LINEAR).firstResult();
        assertEquals(0, new BigDecimal("110.00").compareTo(line.amount));
        assertEquals(LineStatus.PAID, line.status);

        Statement statement = Statement.<Statement>find("period = ?1", period).firstResult();
        assertEquals(0, new BigDecimal("110.00").compareTo(statement.netAmount));

        assertTrue(PaymentBatch.count("period = ?1 and batchType = ?2", period, BatchType.CYCLE) > 0);
        assertTrue(CutoffExtract.count("period = ?1", period) > 0);

        // Idempotent: the demo distributor already exists, so a second load does nothing new.
        service.load();
        assertEquals(1, Distributor.count("partnerCode = ?1", "P13897"));
    }
}
