package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.LegalEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Seeds the delivered control catalogue (CTL-02, section 8.6) for an entity: the data-quality,
 * calculation, clawback and account controls the engine evaluates, as parameterable definitions the
 * business can later tune. Idempotent: a control already present for the entity is left untouched.
 */
@ApplicationScoped
public class ControlCatalogService {

    /** The delivered controls: code, label, family and severity. */
    private static final List<Seed> CATALOG = List.of(
            new Seed("DATA-PREMIUM", "Prime nulle, négative ou absente", ControlFamily.DATA_QUALITY,
                    ControlSeverity.WARNING),
            new Seed("DATA-FUTURE", "Date d'événement dans le futur", ControlFamily.DATA_QUALITY,
                    ControlSeverity.WARNING),
            new Seed("CALC-NO-RULE", "Événement sans règle", ControlFamily.CALCULATION, ControlSeverity.WARNING),
            new Seed("CLAWBACK-EXCESS", "Reprise supérieure à l'acquis", ControlFamily.CLAWBACK,
                    ControlSeverity.WARNING),
            new Seed("ACCOUNT-DEBTOR", "Solde débiteur", ControlFamily.ACCOUNT, ControlSeverity.WARNING));

    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public ControlCatalogService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Seeds the catalogue for an entity, creating the missing controls.
     *
     * @param entity the entity, ignored when null
     * @return the number of controls created
     */
    @Transactional
    public int seed(LegalEntity entity) {
        if (entity == null) {
            return 0;
        }
        int created = 0;
        for (Seed seed : CATALOG) {
            if (ControlDefinition.count("entity = ?1 and code = ?2", entity, seed.code) == 0) {
                ControlDefinition control = new ControlDefinition();
                control.entity = entity;
                control.code = seed.code;
                control.label = seed.label;
                control.family = seed.family;
                control.severity = seed.severity;
                control.active = Boolean.TRUE;
                control.setCreatedAt(clock.now());
                control.persist();
                created++;
            }
        }
        return created;
    }

    /** One catalogue control to seed. */
    private record Seed(String code, String label, ControlFamily family, ControlSeverity severity) {
    }
}
