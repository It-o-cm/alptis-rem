package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

/**
 * Seeds the accounting schema and account mapping (FIN-02) of every entity at startup. Idempotent:
 * the in-memory dev database is recreated at each launch, and the seed skips a row already present.
 */
@ApplicationScoped
public class AccountingBootstrap {

    /** The accounting setup service that seeds the schema and the mapping. */
    private final AccountingSetupService service;

    /**
     * Creates the bootstrap.
     *
     * @param service the accounting setup service
     */
    @Inject
    public AccountingBootstrap(AccountingSetupService service) {
        this.service = service;
    }

    /**
     * Seeds the schema and the mapping of every entity at application startup.
     *
     * @param event the startup event
     */
    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 1200) StartupEvent event) {
        for (LegalEntity entity : LegalEntity.<LegalEntity>listAll()) {
            service.seed(entity);
        }
    }
}
