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
 * Seeds the delivered control catalogue (CTL-02, section 8.6) for an entity: the full set of controls
 * across the ten families (completeness, data quality, calculation, advance, clawback, supplement,
 * account, payment, accounting, external flow) the engine evaluates, as parameterable definitions the
 * business can later tune. Idempotent: a control already present for the entity is left untouched.
 */
@ApplicationScoped
public class ControlCatalogService {

    /** The delivered controls (section 8.6): code, label, family and severity, across the ten families. */
    private static final List<Seed> CATALOG = List.of(
            // Completeness.
            new Seed("COMP-FLOWS", "Tous les flux attendus sont reçus", ControlFamily.COMPLETENESS,
                    ControlSeverity.BLOCKING),
            new Seed("COMP-VOLUME", "Nombre d'événements par source en écart du mois précédent",
                    ControlFamily.COMPLETENESS, ControlSeverity.WARNING),
            // Data quality.
            new Seed("DATA-PREMIUM", "Prime nulle, négative ou anormale", ControlFamily.DATA_QUALITY,
                    ControlSeverity.WARNING),
            new Seed("DATA-FUTURE", "Date d'événement dans le futur", ControlFamily.DATA_QUALITY,
                    ControlSeverity.WARNING),
            new Seed("DATA-NO-DIST", "Garantie sans distributeur", ControlFamily.DATA_QUALITY,
                    ControlSeverity.WARNING),
            new Seed("DATA-DIST-INACTIVE", "Distributeur absent du référentiel ou non actif",
                    ControlFamily.DATA_QUALITY, ControlSeverity.WARNING),
            new Seed("DATA-IBAN", "IBAN manquant", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING),
            new Seed("DATA-RATE-OFF-GRID", "Choix de rémunération hors grille", ControlFamily.DATA_QUALITY,
                    ControlSeverity.WARNING),
            // Calculation.
            new Seed("CALC-NO-RULE", "Événement sans règle", ControlFamily.CALCULATION, ControlSeverity.WARNING),
            new Seed("CALC-RATE-OFF-SCALE", "Taux hors barème", ControlFamily.CALCULATION, ControlSeverity.WARNING),
            new Seed("CALC-NATURE-TOTAL", "Total par nature en écart du mois précédent ou de l'an passé",
                    ControlFamily.CALCULATION, ControlSeverity.WARNING),
            new Seed("CALC-DIST-VARIATION", "Variation par distributeur au-delà du seuil",
                    ControlFamily.CALCULATION, ControlSeverity.WARNING),
            new Seed("CALC-AVG-RATE", "Taux moyen d'un distributeur en rupture avec son historique",
                    ControlFamily.CALCULATION, ControlSeverity.WARNING),
            new Seed("CALC-TOP-20", "Revue des 20 premiers distributeurs", ControlFamily.CALCULATION,
                    ControlSeverity.INFO),
            // Advance.
            new Seed("ADV-NO-DISCOUNT", "Distributeur conventionné sans escompte calculé", ControlFamily.ADVANCE,
                    ControlSeverity.WARNING),
            new Seed("ADV-NO-MEMBER", "Convention sans adhésion", ControlFamily.ADVANCE, ControlSeverity.WARNING),
            new Seed("ADV-SETTLE-IN-PAY", "Apurement présent dans un paiement", ControlFamily.ADVANCE,
                    ControlSeverity.BLOCKING),
            new Seed("ADV-RECEIVABLE-CAP", "Créance d'escompte au-delà d'un plafond", ControlFamily.ADVANCE,
                    ControlSeverity.WARNING),
            // Clawback.
            new Seed("CLAWBACK-EXCESS", "Reprise supérieure à l'acquis", ControlFamily.CLAWBACK,
                    ControlSeverity.WARNING),
            new Seed("CLAWBACK-MISSING", "Résiliation précoce sans reprise", ControlFamily.CLAWBACK,
                    ControlSeverity.WARNING),
            // Supplement.
            new Seed("SUPP-NO-AGREEMENT", "Versement sans accord signé", ControlFamily.SUPPLEMENT,
                    ControlSeverity.WARNING),
            new Seed("SUPP-MISSING-INDICATOR", "Indicateur de condition manquant", ControlFamily.SUPPLEMENT,
                    ControlSeverity.BLOCKING),
            new Seed("SUPP-QUALITY-THRESHOLD", "Seuil de qualité franchi autorisant la résiliation de l'avenant",
                    ControlFamily.SUPPLEMENT, ControlSeverity.WARNING),
            // Account.
            new Seed("ACCOUNT-DEBTOR", "Solde débiteur", ControlFamily.ACCOUNT, ControlSeverity.WARNING),
            new Seed("ACCOUNT-OLD-DEBIT", "Débit plus ancien que n mois", ControlFamily.ACCOUNT,
                    ControlSeverity.WARNING),
            new Seed("ACCOUNT-COMPENSATION", "Compensation possible non faite", ControlFamily.ACCOUNT,
                    ControlSeverity.WARNING),
            // Payment.
            new Seed("PAY-LOT-BALANCE", "Total du lot égal à la somme de ses ordres", ControlFamily.PAYMENT,
                    ControlSeverity.BLOCKING),
            new Seed("PAY-ORDER-NO-STMT", "Ordre sans bordereau", ControlFamily.PAYMENT, ControlSeverity.WARNING),
            new Seed("PAY-PAYEE-INACTIVE", "Bénéficiaire non actif", ControlFamily.PAYMENT, ControlSeverity.WARNING),
            // Accounting.
            new Seed("ACC-BALANCED", "Écritures équilibrées", ControlFamily.ACCOUNTING, ControlSeverity.BLOCKING),
            new Seed("ACC-LINES-MATCH", "Écritures rattachées égales aux lignes", ControlFamily.ACCOUNTING,
                    ControlSeverity.BLOCKING),
            // External flow.
            new Seed("EXT-EXTERNAL-MISMATCH", "Commission externe différente de l'assiette × taux",
                    ControlFamily.EXTERNAL_FLOW, ControlSeverity.WARNING),
            new Seed("EXT-INSURER-MISMATCH", "Commission reçue d'un assureur différente de l'attendu",
                    ControlFamily.EXTERNAL_FLOW, ControlSeverity.WARNING));

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
                control.versionNo = 1;
                control.validFrom = clock.today();
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
