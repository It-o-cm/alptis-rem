package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Policyholder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Purge and anonymisation of personal data at the end of the retention period (ADM-06, NFR-13/14,
 * hypothesis H-36: ten years). A policyholder whose contracts all ended more than ten years before
 * the application date is anonymised in place — name and birth date cleared, the technical identifier
 * and person type kept — while the commission lines, entries, statements and rule versions stay (they
 * carry no name). Idempotent: an already-anonymised policyholder is skipped.
 */
@ApplicationScoped
public class PurgeService {

    /** The anonymisation marker placed in the surname of a purged policyholder. */
    static final String ANONYMISED = "ANONYME";

    /** The retention period in years (H-36). */
    private static final int RETENTION_YEARS = 10;

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public PurgeService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Counts the policyholders eligible for anonymisation at the application date.
     *
     * @return the number of eligible policyholders
     */
    public long eligibleCount() {
        LocalDate cutoff = cutoff();
        return Policyholder.<Policyholder>listAll().stream().filter(p -> eligible(p, cutoff)).count();
    }

    /**
     * Anonymises every eligible policyholder.
     *
     * @return the number of anonymised policyholders
     */
    @Transactional
    public long purge() {
        LocalDate cutoff = cutoff();
        long count = 0;
        for (Policyholder policyholder : Policyholder.<Policyholder>listAll()) {
            if (eligible(policyholder, cutoff)) {
                policyholder.lastName = ANONYMISED;
                policyholder.firstName = null;
                policyholder.birthDate = null;
                count++;
            }
        }
        return count;
    }

    /**
     * The retention cutoff: the application date minus the retention period.
     *
     * @return the cutoff date
     */
    private LocalDate cutoff() {
        return clock.now().toLocalDate().minusYears(RETENTION_YEARS);
    }

    /**
     * Tells whether a policyholder is eligible: not already anonymised, with at least one contract,
     * all of whose contracts ended strictly before the cutoff.
     *
     * @param policyholder the policyholder
     * @param cutoff       the cutoff date
     * @return true when eligible
     */
    private boolean eligible(Policyholder policyholder, LocalDate cutoff) {
        if (ANONYMISED.equals(policyholder.lastName)) {
            return false;
        }
        List<Contract> contracts = Contract.list("policyholder = ?1", policyholder);
        if (contracts.isEmpty()) {
            return false;
        }
        for (Contract contract : contracts) {
            if (contract.endDate == null || !contract.endDate.isBefore(cutoff)) {
                return false;
            }
        }
        return true;
    }
}
