package fr.alptis.remuneration.api;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

/**
 * Loads the demonstration dataset at startup, in profile {@code dev} only (step 15). It runs after
 * the security, barème and accounting bootstraps so the September scene can be calculated and driven
 * to closing. It is idempotent through {@link DemoDataService#load()}.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DemoDataBootstrap {

    /** The demonstration dataset service. */
    private final DemoDataService service;

    /**
     * Creates the bootstrap.
     *
     * @param service the demonstration dataset service
     */
    @Inject
    public DemoDataBootstrap(DemoDataService service) {
        this.service = service;
    }

    /**
     * Loads the demonstration dataset at application startup.
     *
     * @param event the startup event
     */
    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 2000) StartupEvent event) {
        service.load();
    }
}
