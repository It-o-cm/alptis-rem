package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Distributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Loads the rich demonstration dataset (brief {@code DONNEES-DEMO.md}). It builds the referential
 * directly and then drives the real services — the calculation engine, the period cycle, payment,
 * accounting, documents, cut-off and the sales-force module — so every screen shows coherent,
 * end-to-end data for both Alptis Assurances and UNEP.
 *
 * <p>Nothing the application computes is ever inserted as a result; only referential objects are
 * created directly. Loading is idempotent through the presence of the mock-up cabinet {@link
 * DemoCodes#CABINET}: once it exists, a second load does nothing.</p>
 */
@ApplicationScoped
public class DemoDataService {

    /** Builds the static referential (distributors, products, rules, agreements, …). */
    private final DemoReferentialBuilder referential;

    /** Builds the contracts, coverages, assignments and choices. */
    private final DemoContractsBuilder contracts;

    /** Drives the monthly cycle through the real services. */
    private final DemoCycleRunner cycle;

    /** Builds and runs the sales-force variable-pay scene. */
    private final DemoSalesForceBuilder salesForce;

    /** Builds the flows journal and the audit trail. */
    private final DemoFluxBuilder flux;

    /**
     * Creates the service.
     *
     * @param referential the referential builder
     * @param contracts   the contracts builder
     * @param cycle       the cycle runner
     * @param salesForce  the sales-force builder
     * @param flux        the flows and audit builder
     */
    @Inject
    public DemoDataService(DemoReferentialBuilder referential, DemoContractsBuilder contracts,
                           DemoCycleRunner cycle, DemoSalesForceBuilder salesForce, DemoFluxBuilder flux) {
        this.referential = referential;
        this.contracts = contracts;
        this.cycle = cycle;
        this.salesForce = salesForce;
        this.flux = flux;
    }

    /**
     * Loads the whole demonstration dataset. Idempotent: does nothing when the mock-up cabinet already
     * exists.
     */
    @Transactional
    public void load() {
        if (Distributor.count("partnerCode", DemoCodes.CABINET) > 0) {
            return;
        }
        DemoWorld world = new DemoWorld();
        referential.build(world);
        contracts.build(world);
        cycle.run(world);
        salesForce.build(world);
        flux.build(world);
    }
}
