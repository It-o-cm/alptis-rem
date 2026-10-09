package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.Period;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests of the end-to-end cycle-scene seeder (step 9): it plants a calculated period and a
 * distributor with a debtor and a creditor account for a known entity, and ignores an unknown one.
 */
@QuarkusTest
class CycleDemoSeederTest {

    @Inject
    CycleDemoSeeder seeder;

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    /**
     * Seeding a known entity plants the period, the distributor and its two accounts and lines.
     */
    @Test
    @TestTransaction
    void seeds_known_entity() {
        long accounts = DistributorAccount.count("entity.code = ?1", "UNEP");
        long lines = CommissionLine.count("entity.code = ?1", "UNEP");

        seeder.seed("UNEP", MONTH);

        assertNotNull(Period.find("entity.code = ?1 and periodMonth = ?2", "UNEP", MONTH).firstResult());
        assertNotNull(Distributor.find("partnerCode", CycleDemoSeeder.PARTNER).firstResult());
        assertEquals(accounts + 2, DistributorAccount.count("entity.code = ?1", "UNEP"));
        assertEquals(lines + 2, CommissionLine.count("entity.code = ?1", "UNEP"));
    }

    /**
     * Seeding an unknown entity plants nothing.
     */
    @Test
    @TestTransaction
    void ignores_unknown_entity() {
        long before = Period.count();
        seeder.seed("NOPE", MONTH);
        assertEquals(before, Period.count());
    }
}
