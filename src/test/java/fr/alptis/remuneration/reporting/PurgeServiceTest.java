package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PersonType;
import fr.alptis.remuneration.domain.Policyholder;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the purge/anonymisation (ADM-06): a policyholder whose contracts all ended beyond the
 * retention period is anonymised; an active, a recent, a contract-less and an already-anonymised one
 * are kept. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class PurgeServiceTest {

    @Inject
    PurgeService service;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a policyholder with one contract ended at a date (null = active, absent = no contract). */
    private static Policyholder holder(String name, LocalDate endDate, boolean withContract) {
        Policyholder holder = new Policyholder();
        holder.entity = unep();
        holder.sourceSystem = "SYS";
        holder.externalId = name;
        holder.lastName = name;
        holder.firstName = "Jean";
        holder.birthDate = LocalDate.of(1970, 1, 1);
        holder.personType = PersonType.NATURAL;
        holder.persist();
        if (withContract) {
            Contract contract = new Contract();
            contract.entity = unep();
            contract.sourceSystem = "SYS";
            contract.contractNumber = "C-" + name;
            contract.policyholder = holder;
            contract.endDate = endDate;
            contract.persist();
        }
        return holder;
    }

    /**
     * Only the long-ended policyholder is eligible; the purge anonymises it and is then idempotent.
     */
    @Test
    @TestTransaction
    void anonymises_only_the_expired_policyholder() {
        Policyholder old = holder("OLD", LocalDate.of(2000, 1, 1), true);
        holder("ACTIVE", null, true);
        holder("RECENT", LocalDate.of(2300, 1, 1), true);
        holder("NOCONTRACT", null, false);

        assertEquals(1, service.eligibleCount());
        assertEquals(1, service.purge());

        Policyholder reloaded = Policyholder.findById(old.getId());
        assertEquals(PurgeService.ANONYMISED, reloaded.lastName);
        assertEquals(null, reloaded.firstName);
        assertEquals(null, reloaded.birthDate);
        // Idempotent: the anonymised one is no longer eligible.
        assertEquals(0, service.eligibleCount());
    }
}
