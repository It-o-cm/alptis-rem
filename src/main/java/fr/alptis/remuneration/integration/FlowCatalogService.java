package fr.alptis.remuneration.integration;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Flow catalogue (section 9.1, requirement ING-10): the inventory of everything the application
 * receives and emits — number, direction, system, content, format and transport, frequency. It is
 * reference metadata, served to the catalogue screen so no flow description is hard-coded in the
 * interface. The last-exchange and live state of a flow are read from the import and export journals
 * on their own screens; the catalogue describes the flows themselves.
 */
@ApplicationScoped
public class FlowCatalogService {

    /** The direction of a flow. */
    public enum FlowDirection {
        /** An inbound flow (received). */
        INBOUND,
        /** An outbound flow (emitted). */
        OUTBOUND
    }

    /** The flow inventory of section 9.1, in order. */
    private static final List<FlowView> CATALOG = List.of(
            new FlowView("IN-01", FlowDirection.INBOUND, "Sésame (Alptis Assurances)",
                    "Distributeurs, groupements, codes de production, statuts, accords, indicateurs de qualité, "
                            + "cessions de portefeuille, ordres de remplacement",
                    "JSON par API REST, ou fichier pivot CSV", "Quotidienne"),
            new FlowView("IN-02", FlowDirection.INBOUND, "Salesforce (UNEP)", "Idem",
                    "JSON par API REST, ou fichier pivot CSV", "Quotidienne"),
            new FlowView("IN-03", FlowDirection.INBOUND, "CLEVA",
                    "Contrats, garanties, choix de rémunération, émissions, encaissements, annulations, "
                            + "événements de vie, coordonnées bancaires",
                    "Fichiers pivot CSV déposés par UseItFlow", "Quotidienne"),
            new FlowView("IN-04", FlowDirection.INBOUND, "RSI",
                    "Idem, périmètre individuel, jusqu'à son décommissionnement",
                    "Fichiers pivot CSV déposés par UseItFlow", "Quotidienne"),
            new FlowView("IN-05", FlowDirection.INBOUND, "Kelia (UNEP)",
                    "Contrats, versements, encours, coordonnées bancaires", "Fichiers pivot CSV", "Mensuelle"),
            new FlowView("IN-06", FlowDirection.INBOUND, "Assureurs et gestionnaires délégués",
                    "Fichiers de production, d'encaissement, de commissions ; un mapping par fichier",
                    "Excel ou CSV, dépôt ou téléversement",
                    "Mensuelle, trimestrielle ou annuelle selon le fichier"),
            new FlowView("IN-07", FlowDirection.INBOUND, "Référentiel produits",
                    "Gammes, produits, options, assureurs", "Fichier pivot CSV", "À chaque évolution"),
            new FlowView("IN-08", FlowDirection.INBOUND, "SI Finance",
                    "Résultat d'exécution des virements, rejets", "Fichier CSV ou saisie", "À chaque lot"),
            new FlowView("IN-09", FlowDirection.INBOUND, "SI RH, CRM",
                    "Collaborateurs, absences, production, activité commerciale, marge, cahiers des charges reçus "
                            + "(module forces de vente)",
                    "Fichiers CSV", "Mensuelle"),
            new FlowView("OUT-01", FlowDirection.OUTBOUND, "SI Finance, trésorerie",
                    "Ordres de virement ; liste des chèques",
                    "XML SEPA pain.001.001.03 ; liste des chèques en CSV ; dépôt de fichier", "À chaque lot"),
            new FlowView("OUT-02", FlowDirection.OUTBOUND, "SI Finance, comptabilité",
                    "Extraction des écritures après cut-off", "CSV, dépôt de fichier", "Mensuelle"),
            new FlowView("OUT-03", FlowDirection.OUTBOUND, "GED, éditique",
                    "Bordereaux et attestations, avec métadonnées d'indexation",
                    "PDF, tableur, JSON de métadonnées", "À chaque publication"),
            new FlowView("OUT-04", FlowDirection.OUTBOUND, "Alptis Connect, K4U, CRM",
                    "Comptes, lignes de commission, bordereaux, conventions d'escompte",
                    "JSON par API REST en lecture", "À la demande"),
            new FlowView("OUT-05", FlowDirection.OUTBOUND, "Plateforme Data",
                    "Lignes, événements commissionnés, écritures de compte, paiements, règles",
                    "Fichiers CSV incrémentaux", "Quotidienne"),
            new FlowView("OUT-06", FlowDirection.OUTBOUND, "Paie",
                    "Variables à payer par collaborateur (module forces de vente)",
                    "CSV, ou tableur de mêmes colonnes", "Mensuelle"),
            new FlowView("OUT-07", FlowDirection.OUTBOUND, "Supervision",
                    "État de santé et métriques des traitements", "HTTP", "Continue"));

    /**
     * Returns the flow catalogue, optionally filtered by direction.
     *
     * @param direction the direction to keep, or null for all flows
     * @return the catalogue rows
     */
    public List<FlowView> catalog(FlowDirection direction) {
        if (direction == null) {
            return CATALOG;
        }
        return CATALOG.stream().filter(flow -> flow.direction() == direction).toList();
    }

    /** The exchange-format overview of section 9.9: the main files and their naming. */
    private static final List<FormatFile> FORMAT_FILES = List.of(
            new FormatFile("IN", "Garanties et contrats (format pivot)", "CSV pivot",
                    "<entité>_coverages_<horodatage>.csv"),
            new FormatFile("IN", "Événements : émission, encaissement, annulation (format pivot)", "CSV pivot",
                    "<entité>_events_<horodatage>.csv"),
            new FormatFile("OUT-01", "Ordres de virement", "XML SEPA pain.001.001.03",
                    "<entité>_SEPA_<mois>_<lot>.xml"),
            new FormatFile("OUT-02", "Écritures comptables", "CSV", "<entité>_ECR_<mois>.csv"),
            new FormatFile("OUT-04", "Comptes, lignes, bordereaux, conventions d'escompte", "JSON par API",
                    "—"),
            new FormatFile("OUT-05", "Données pour la plateforme Data", "CSV incrémentaux",
                    "<entité>_DATA_<objet>_<date>.csv"));

    /** The common CSV conventions of section 9.9. */
    private static final List<String> CONVENTIONS = List.of(
            "Encodage UTF-8, sans marque d'ordre d'octets.",
            "Séparateur point-virgule ; un champ contenant un séparateur est entouré de guillemets doubles.",
            "Première ligne d'en-tête obligatoire, noms de champs en minuscules ; ordre des colonnes libre.",
            "Nombres à point décimal, sans séparateur de milliers ; montants à 2 décimales.",
            "Taux en fraction, jusqu'à 6 décimales : 15 % s'écrit 0.15.",
            "Dates AAAA-MM-JJ ; horodatages AAAA-MM-JJTHH:MM:SS, heure de Paris.",
            "Mois AAAA-MM dans un champ ; AAAAMM dans un nom de fichier ou un identifiant de lot.",
            "Booléens true ou false ; valeur absente = champ vide, jamais NULL ni zéro.",
            "Empreinte calculée sur le nom et le contenu du fichier.");

    /**
     * Returns the exchange-format reference: the main files and the common CSV conventions.
     *
     * @return the formats reference
     */
    public FormatsReference formats() {
        return new FormatsReference(FORMAT_FILES, CONVENTIONS);
    }

    /**
     * One catalogue row: a flow's identity, direction, counterpart system, content, format and
     * frequency.
     *
     * @param id        the flow number (IN-nn or OUT-nn)
     * @param direction the direction
     * @param system    the counterpart system
     * @param content   the content description
     * @param format    the format and transport
     * @param frequency the exchange frequency
     */
    public record FlowView(String id, FlowDirection direction, String system, String content, String format,
                           String frequency) {
    }

    /**
     * One format-overview row: a flow, its content, its format and its file-name pattern.
     *
     * @param flow     the flow number or direction
     * @param content  the content
     * @param format   the format
     * @param fileName the file-name pattern
     */
    public record FormatFile(String flow, String content, String format, String fileName) {
    }

    /**
     * The exchange-format reference: the files and the common conventions.
     *
     * @param files       the format-overview rows
     * @param conventions the common CSV conventions
     */
    public record FormatsReference(List<FormatFile> files, List<String> conventions) {
    }
}
