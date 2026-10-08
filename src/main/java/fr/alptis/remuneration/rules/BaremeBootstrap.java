package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

/**
 * Seeds the 2026 Guide base barème (section 6.12) for Alptis Assurances at startup. Idempotent: the
 * barème is seeded once; the in-memory dev database is recreated at each launch, and the seed does
 * nothing if the plan already exists.
 */
@ApplicationScoped
public class BaremeBootstrap {

    /** The entity the initial barème belongs to (section 6.12). */
    private static final String ALPTIS = "ALPTIS_ASSURANCES";

    /** The barème seeding service. */
    private final BaremeInitialService service;

    /**
     * Creates the bootstrap.
     *
     * @param service the barème seeding service
     */
    @Inject
    public BaremeBootstrap(BaremeInitialService service) {
        this.service = service;
    }

    /**
     * Seeds the barème at application startup.
     *
     * @param event the startup event
     */
    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 1000) StartupEvent event) {
        service.seed(LegalEntity.<LegalEntity>find("code", ALPTIS).firstResult());
    }
}
