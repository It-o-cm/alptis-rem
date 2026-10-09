package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

/**
 * Seeds the delivered control catalogue (CTL-02) for every entity at startup. Idempotent: the
 * in-memory dev database is recreated at each launch, and the seed skips a control already present.
 */
@ApplicationScoped
public class ControlCatalogBootstrap {

    /** The control catalogue seeding service. */
    private final ControlCatalogService service;

    /**
     * Creates the bootstrap.
     *
     * @param service the catalogue seeding service
     */
    @Inject
    public ControlCatalogBootstrap(ControlCatalogService service) {
        this.service = service;
    }

    /**
     * Seeds the catalogue of every entity at application startup.
     *
     * @param event the startup event
     */
    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 1100) StartupEvent event) {
        for (LegalEntity entity : LegalEntity.<LegalEntity>listAll()) {
            service.seed(entity);
        }
    }
}
