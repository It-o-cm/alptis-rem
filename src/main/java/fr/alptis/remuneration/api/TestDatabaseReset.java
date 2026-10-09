package fr.alptis.remuneration.api;

import fr.alptis.remuneration.accounting.AccountingSetupService;
import fr.alptis.remuneration.cycle.ControlCatalogService;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.rules.BaremeInitialService;
import fr.alptis.remuneration.security.SecurityBootstrap;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.transaction.Transactional;

/**
 * Resets the database to its seeded baseline for the end-to-end suite: every table is emptied and
 * the security referential is re-seeded. Available outside production only; the H2-specific
 * referential-integrity switch is acceptable since dev and test run on H2.
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class TestDatabaseReset {

    /** Entity manager used for the native reset statements. */
    private final EntityManager em;

    /** Re-seeds the security referential after the wipe. */
    private final SecurityBootstrap bootstrap;

    /** Re-seeds the initial barème after the wipe. */
    private final BaremeInitialService bareme;

    /** Re-seeds the control catalogue after the wipe. */
    private final ControlCatalogService catalog;

    /** Re-seeds the accounting schema and mapping after the wipe. */
    private final AccountingSetupService accounting;

    /**
     * Creates the reset helper.
     *
     * @param em         the entity manager
     * @param bootstrap  the security bootstrap used to re-seed
     * @param bareme     the initial-barème service used to re-seed
     * @param catalog    the control-catalogue service used to re-seed
     * @param accounting the accounting setup service used to re-seed
     */
    @Inject
    public TestDatabaseReset(EntityManager em, SecurityBootstrap bootstrap, BaremeInitialService bareme,
                             ControlCatalogService catalog, AccountingSetupService accounting) {
        this.em = em;
        this.bootstrap = bootstrap;
        this.bareme = bareme;
        this.catalog = catalog;
        this.accounting = accounting;
    }

    /**
     * Empties every mapped table and re-seeds the baseline (security referential and the Alptis
     * initial barème), in a single transaction. Foreign-key checks are turned off so the entities
     * can be deleted in any order.
     */
    @Transactional
    public void reset() {
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        for (EntityType<?> entityType : em.getMetamodel().getEntities()) {
            em.createQuery("delete from " + entityType.getName()).executeUpdate();
        }
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
        bootstrap.seed();
        for (LegalEntity entity : LegalEntity.<LegalEntity>listAll()) {
            catalog.seed(entity);
            accounting.seed(entity);
        }
        bareme.seed(LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult());
    }
}
