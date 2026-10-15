# État des lieux du périmètre

État des lieux établi en lisant le code (routeur, vues, ressources REST, services,
tests), et non les comptes rendus précédents, conformément à `PERIMETRE.md` étape 1.
Il fait foi pour la reprise : le travail de l'étape 2 traite les lignes `absent`
et `partiel` dans l'ordre de l'inventaire, et cet état est remis à jour à chaque
commit de rubrique.

Barème d'état : `complet` (tout ce que décrit la ligne est réalisé et prouvé),
`partiel` (une partie manque — le manque est nommé), `absent` (rien).

Méthode : un écran de l'inventaire servi comme partie d'un autre écran n'est
`complet` que si tout ce que décrit sa ligne y est réellement utilisable ; sinon
`partiel`. Les statuts de ce premier passage sont déduits de la lecture du code ;
ils sont resserrés écran par écran au fur et à mesure de l'étape 2.

## Décompte

Premier passage (conservé pour mémoire) : 37 `complet`, 24 `partiel`, 12 `absent` sur les 73 écrans.

État au 2026-10-12 (étape 2 en cours) :

- Écrans de l'inventaire (73) : **73 `complet`** — toutes les lignes de l'inventaire sont réalisées,
  avec serveur, écran, données de démonstration et tests de bout en bout.
- Exigences de la section 8 : en cours de resserrement ; les lignes encore `partiel`/`absent` du
  Tableau 2 sont traitées dans l'ordre (plusieurs sont des mentions de premier passage à vérifier
  contre le code livré depuis, d'autres des manques réels à combler — forces de vente FDV-06→12,
  rapprochements FIN-06/08, etc.).
- Cas de recette : **les 71 existent côté JUnit** ; le rejeu par l'interface (`@cr:`) et les parcours
  `@path:` restent à compléter (étape 4).
- `PlaceholderView` : supprimé, plus aucune route ne le référence.

État au 2026-10-14 (ADM-03 en cours) :

- **ADM-03 CLÔTURÉ** : helper `accounting.ts` (événement comptable, variante, rôle de compte, §10.2,
  100 % vitest) ; AccountingView (schéma, correspondance, écritures, chaîne) au libellé FR, `accounting.spec`
  adapté (3/3 verts). Balayage final : plus aucun code d'énumération brut à l'écran, plus aucune saisie libre
  d'une valeur connue. ADM-03 passe à `complet` dans le Tableau 2.
- **Balayage complet des énumérations hors tableau et des passthroughs (7 écrans)** : ContractsView,
  AccountingView, AdvanceAgreementsView, DiscountsView, DistributorAccountView, RulesView et TransfersView
  n'affichent plus aucun code d'énumération brut (statuts d'adhésion/ligne/événement/position/convention/
  version/cession, mode de rémunération, type d'événement, sens, nature, type d'ajustement). Nouveaux
  helpers `advance.ts` et `transfers.ts`, `versionStatusLabel` (rules.ts), `eventStatusLabel` nullable —
  vitest 100 % (176 tests), e2e 22/22 verts. Reste un seul point connu avant de clore ADM-03 : les codes
  d'évènement comptable du schéma (`acc-scheme`).
- **Balayage des colonnes de tableau (5 écrans)** : AdjustmentsView (type d'ajustement, nouveau
  `adjustmentTypeLabel` + options, 100 % vitest), FdvPlanView (type d'objectif), FdvCommercialView
  (statut de variable), RepriseView (statut d'import), SimulationView (nature) affichent désormais ces
  énumérations au libellé FR du glossaire via les helpers existants (`objectiveTypeLabel`,
  `variableStatusLabel`, `importStatusLabel`, `lineNatureLabel`) ou le nouveau. vitest 100 % (172 tests),
  e2e des 4 specs touchées vertes (16/16), build serveur inchangé.
- **Balayage des libellés d'énum (EnrollmentsView, DistributorsView)** : helpers de présentation
  `agreements.ts` (type d'accord, état de signature, statut d'inscription) et `distributors.ts`
  (type de distributeur, statut de distributeur/code de production), tous deux 100 % vitest. Les deux
  écrans n'affichent plus aucun code d'énum brut : type d'accord, signature, statut d'inscription, type
  de distributeur et statuts par entité/code de production sont au libellé FR du glossaire (§5.1, §5.4).
  Specs adaptés (`enrollments.spec`, `distributors.spec`) : assertions au libellé (« Signé », « Active »,
  « Actif »). vitest 100 % (170 tests), e2e des deux écrans verts (8/8), build serveur inchangé (vert).
- **ManualEventView converti** : socle `SourceSystemSelect` (store Pinia `sourceSystems`, 100 % vitest)
  et helper de présentation `coverage.ts` (`remunerationModeLabel`/`contractStatusLabel` + options,
  100 % vitest) ajoutés ; l'écran de saisie manuelle choisit désormais le système source et le produit
  dans une liste, et affiche le type d'événement, le mode de rémunération et le statut d'adhésion au
  libellé FR (plus aucune saisie libre de code connu, plus aucun code d'énum brut). Les chemins d'échec
  des tests qui saisissaient un produit inconnu (`manual-event.spec`) sont reprouvés par la sélection
  d'un produit connu avec un code de production inconnu (mise en attente conservée). Specs adaptés :
  `manual-event.spec` (sélections + assertions au libellé), `events.spec` et `held-events.spec`
  (`field-source` en `selectOption`). `mvn verify` vert (725 tests Java + JaCoCo 100 %), vitest 100 %,
  les trois specs e2e touchées vertes (12/12).

État au 2026-10-13 (ADM-03 en cours) :

- **Socle « liste / sélection » d'ADM-03 livré** : composants `CodeSelect`, `EntitySelect`, `MonthSelect`,
  `DistributorSelect`, `ProductSelect`, `GroupSelect`, `DelegationSelect`, `MemberSelect` ; magasins Pinia
  `periods`, `distributors`, `products`, `groupings`, `delegations`, `productionCodes` ; aide de
  présentation `months.ts` (`monthLabel`) ; `entities.ts` (`ALL_ENTITY_CODES`, `visibleEntities`) ;
  `lines.ts` (`NATURE_OPTIONS`). Tout est couvert à 100 % (vitest). ADM-03 reste `partiel` : la conversion
  des autres écrans est détaillée dans « ADM-03 — reste à faire ».
- **Conversion groupement/délégation/membre (2026-10-13)** : CommissionSearchView (groupement + délégation),
  DashboardsView (tous les filtres en liste) et BordereauxView (entité/mois/membre + mois au libellé FR)
  convertis ; points d'accès serveur `GET /delegations` et `GET /production-codes` ajoutés (partitionnés,
  testés JUnit) ; jeu de démonstration doté de délégations ; `reporting.spec` et `bordereaux.spec` adaptés
  (sélection au libellé, chemin d'échec du tableau de bord reproduit par la nature) et repassés verts
  (10/10). `mvn verify` vert (724 tests, JaCoCo 100 %).
- **Conversion plan/règle + exports/paramétrage/simulation/inscriptions (2026-10-13)** : stores `plans`/`rules`
  + composants `PlanSelect`/`RuleSelect` + point d'accès `GET /rules` (partitionné, testé JUnit) ;
  ParameterTransferView, ExportsView, SimulationView, EnrollmentsView convertis (entité/mois/plan/règle/
  distributeur/accord en liste) ; ImportsView et SupervisionView affichent leurs statuts au libellé FR
  (`importStatusLabel`/`healthLabel`). Specs adaptés (fill→selectOption, assertions au libellé) : exports,
  parameter-transfer, simulation, enrollments, imports, admin-supervision — 18/18 verts ; Java 725 tests +
  JaCoCo 100 % verts.
- **Correctif suite vitest** : `navigation.spec.ts` était rouge dans `HEAD` (il attendait 42 entrées et 9
  rubriques alors que la navigation livrée en porte 55 et 10, rubrique « Documents » comprise). Le test est
  réaligné sur la navigation réelle — la suite vitest repasse verte (128 tests, 100 % de couverture).

## Tableau 1 — Écrans de l'inventaire (section 4 de LANCEMENT.md)

Colonnes : écran (libellé de l'inventaire) · exigences · route / vue · points
d'accès serveur · test de bout en bout · état.

### Cycle mensuel

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Avancement des périodes, toutes entités visibles | WKF-04 | `periods` / PeriodsView | GET /periods | periods.spec | complet |
| 2 | Détail d'une période : états, transitions, calendrier, lots, reste à traiter | WKF-01, 7.1–7.3 | `periods` (détail) / PeriodsView | GET /periods/{e}/{m}, POST transition | periods.spec | complet — calendrier cible (§7.3), lots de paiement de la période et reste à traiter (événements en attente/à l'écart) ajoutés |
| 3 | Journal des imports et flux attendus | ING-05, ING-10 | `imports` / ImportsView | GET /imports, GET /flows catalog | imports.spec, flux.spec | complet |
| 4 | Détail d'un import : compteurs, rejets, lignes en attente, correction/mise à l'écart | ING-04, ING-06 | `imports` (détail) / ImportsView | GET rejections/held, POST correct/discard | imports.spec | complet |
| 5 | Téléversement d'un fichier, avec choix du flux et du système source | ING-01, ING-08 | `imports` / ImportsView | POST /imports, GET /imports/source-systems | imports.spec | complet — système source choisi dans une liste (GET source-systems, §9.1), flux affichés par leur libellé français ; import en masse de données complémentaires par les flux `coverages`/`events` (ING-08) |
| 6 | Saisie manuelle d'un événement ou d'une donnée complémentaire | ING-07 | `manualEvent` / ManualEventView | POST /events, POST /events/complementary-data | manual-event.spec | complet — saisie d'un événement et d'une donnée complémentaire (garantie pivot), chacune tracée et contrôlée comme un import |
| 7 | Recherche d'événements et détail d'un événement : statut, motif d'attente, lignes produites | ING-06, 5.3 | `events` / EventsView | GET /events, GET /events/{id} | events.spec | complet — recherche (clé, statut, type), détail avec statut, motif d'attente et lignes produites |
| 8 | Événements en attente, par motif | P8, ING-06 | `heldEvents` / HeldEventsView | GET /events/held-by-reason, GET /events?status=ON_HOLD | held-events.spec | complet — regroupement par motif et liste des événements en attente (jeu de démonstration en porte un par entité) |
| 9 | Exécutions de calcul : lancement, suivi, compteurs, unités en échec, recalcul partiel | CAL-01, CAL-12, 12.3 | `calculations` / CalculationsView | GET /calculations/{e}/{m}, POST run, POST recalc | calculations.spec | complet — lancement du calcul réel, suivi des exécutions et compteurs, unités en échec (événements en attente) et recalcul partiel par distributeur/produit/règle |
| 10 | Contrôles et anomalies de la période, décisions, liste de contrôle | CTL-02, CTL-03, CTL-05 | `controls` / ControlsView | GET anomalies/breakdown/catalog/checklist, POST run/decision/checklist | controls.spec | complet — liste de contrôle de la période à cocher (CTL-03) ajoutée, tracée (qui/quand) |
| 11 | Catalogue des contrôles : seuil, gravité, périmètre, activation, versions | CTL-01, CTL-04 | `controls` (onglet) / ControlsView | GET/POST /controls/{e}/catalog/{code}, GET versions | controls.spec | complet — édition par le métier (gravité, activation, paramètres/seuil) créant une nouvelle version, et historique des versions |
| 12 | Ajustements : liste, saisie, approbation, import en masse | REG-01→05 | `adjustments` / AdjustmentsView | GET/POST adjustments, POST approve, POST bulk | adjustments.spec | complet — import en masse (REG-04, fichier pivot) et contrepassation automatique (REG-05, option + reprise au mois suivant) livrés |
| 13 | Réclamations : liste, fiche, lignes contestées, suivi | REG-06 | `claims` / ClaimsView | GET /claims/{e}, GET /claims/detail/{id}, POST record/resolve | claims.spec | complet — liste, fiche avec lignes contestées, enregistrement et clôture avec régularisation |
| 14 | Validation métier : totaux, comparaison au mois précédent, second niveau | WKF-02, 7.2 | `validation` / ValidationView | GET /validation/{e}/{m}, POST validate | validation.spec | complet — totaux et ventilation par nature, écart au mois précédent, second niveau de validation sous règles paramétrables (total, écart, ajustements) |
| 15 | Lots de paiement de la période et détail d'un lot : ordres, exclusions, rapprochement, validation Finance | PAY-01→05 | `payment` / PaymentView | GET lots/detail, POST prepare/validate, GET sepa | payment.spec | complet |
| 16 | Création d'un lot hors cycle | PAY-07 | `payment` / PaymentView | POST off-cycle | payment.spec | complet |
| 17 | Résultat bancaire : ordres réglés, rejets, saisie de l'émission des chèques | PAY-04, PAY-06 | `payment` / PaymentView | POST orders/{id}/result, POST lots/{id}/cheque-emission | payment.spec | complet — saisie de l'émission des chèques (PAY-04) par la Finance : les ordres du lot de chèques passent réglés |
| 18 | Bordereaux de la période : liste, aperçu, publication, rectificatif, relevé informatif | BRD-01→07, BRD-11 | `statements` / BordereauxView | GET statements/detail/spreadsheet/pdf/workbook/member, POST publish/rectify | bordereaux.spec | complet — PDF et tableur xlsx réels produits sans dépendance (PDF écrit à la main, xlsx assemblé en ZIP OOXML) ; la publication archive l'empreinte SHA-256 du PDF réel et la référence du tableur ; téléchargements prouvés par bordereaux.spec |

### Distributeurs et contrats

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 19 | Recherche de distributeurs | REF-02, REP-03 | `distributorSearch` / DistributorsView | GET /distributors?q= | distributors.spec | complet |
| 20 | Fiche distributeur : identité, hiérarchie, statut, accords, inscriptions, comptes, indicateurs, historique | REF-10, CPT-01, CPT-06 | `distributorSearch` / DistributorsView | GET /distributors/{code} | distributors.spec | complet — fiche consolidée : identité, type, groupement (tête), statuts, codes de production, accords, inscriptions aux règles réservées, indicateurs de qualité et comptes par entité avec historique des soldes ; l'écran `distributor` reste pour les actions sur compte (blocage, compensation) |
| 21 | Vue d'un groupement et de ses membres | CPT-02, REF-03 | `groupings` / GroupingsView | GET /groups, GET /groups/{code} | groupings.spec | complet — liste des groupements (tête, nombre de membres) et détail des membres avec leurs codes de production |
| 22 | Blocage d'un compte ou d'une ligne, avec motif et date de levée | CPT-05, REF-08 | `distributor` / DistributorAccountView | POST /accounts/{id}/block+unblock, GET .../lines, POST /accounts/lines/{id}/block+unblock | distributor-account.spec | complet — blocage d'un compte et d'une ligne, chacun avec motif et date de levée saisis ; une ligne bloquée est tenue hors du paiement (son montant reste au compte, cf. DECISIONS 2026-10-12) |
| 23 | Détail d'une ligne de commission et de son explication | CAL-10, FIN-07 | `commissionSearch` (détail) / CommissionSearchView | GET /commission-lines/{id} | reporting.spec | complet — ouverte depuis la recherche de commissions : événement, version de règle, assiette, taux, contrat, garantie, bordereau, ordre de paiement et l'explication native du calcul (D14) affichée en clair |
| 24 | Recherche de lignes de commission : payé à qui, quand, pourquoi | REP-02, REP-03 | `commissionSearch` / CommissionSearchView | GET /reporting/{e}/search, export | reporting.spec | complet |
| 25 | Hiérarchie et codes de production, avec leur historique | REF-03 | `distributorSearch` / DistributorsView | GET /distributors/{code} | distributors.spec | complet — groupement (tête), codes de production et historique de la hiérarchie : les changements de groupement et les réaffectations de code de production sont tracés au journal d'audit (ADM-04) et affichés sur la fiche (qui/quand/avant/après) |
| 26 | Coordonnées bancaires et changements depuis le dernier paiement | REF-05 | `bankAccounts` / BankAccountsView | GET /bank-accounts | referentials.spec | complet — IBAN masqués, dates de validité, et signalement « À vérifier » d'une coordonnée entrée en vigueur après le dernier paiement réglé du titulaire (comparaison validFrom / dernier settledAt) |
| 27 | Règles de versement : bénéficiaire, parts, périodicité | REF-06 | `payeeRules` / PayeeRulesView | GET /payee-rules | payee-rules.spec | complet |
| 28 | Indicateurs de qualité par distributeur et par période | REF-07 | `qualityIndicators` / QualityIndicatorsView | GET /quality-indicators | referentials.spec | complet |
| 29 | Cessions de portefeuille et ordres de remplacement | REF-09 | `transfers` / TransfersView | GET /portfolio-transfers, /replacement-orders | transfers.spec | complet |
| 30 | Comptes débiteurs sans portefeuille actif et échéanciers d'apurement | CPT-07 | `distributor` / DistributorAccountView | GET /accounts/{e}/debtors, GET /accounts/{e}/repayment-schedules | distributor-account.spec | complet — liste des débiteurs et échéanciers d'apurement (CAL-14) : une avance exceptionnelle porte un échéancier de remboursement (ajustement à échéancier), affiché avec son titulaire, son montant et son statut |
| 31 | Conventions d'escompte : liste | PAR-09 | `advance` / AdvanceAgreementsView | GET /advance-agreements | advance-agreements.spec | complet |
| 32 | Convention d'escompte : paramètres, compteurs, garanties escomptées, simulation de rupture | PAR-09, CPT-08, 6.4 | `advance` / AdvanceAgreementsView | GET/POST /advance-agreements, GET /advance-agreements/{e}/{p} | advance-agreements.spec | complet — fiche convention : paramètres, compteurs (total escompté, apuré, créance), garanties escomptées (par garantie), et simulation de rupture (créance à récupérer si rompue aujourd'hui) |
| 33 | Créances d'escompte par convention et par distributeur | CPT-08, BRD-09 | `discounts` / DiscountsView | GET /discount-summary/{e} | discounts.spec | complet — synthèse des escomptes par convention (versé, apuré, créance) et détail par adhérent (garanties escomptées) |
| 34 | Recherche de contrats et de garanties | 5.2 | `contractSearch` / ContractsView | GET /contracts?q= | contracts.spec | complet |
| 35 | Fiche d'une adhésion et de ses garanties : affectations, choix de rémunération, événements, lignes, position d'escompte | 5.2, 5.5 | `contractSearch` (détail) / ContractsView | GET /contracts/{e}/{n}/coverages/{c} | contracts.spec | complet — fiche d'une garantie ouverte depuis la recherche : affectations (code de production, rôle, part, origine), choix de rémunération, événements reçus, lignes produites et position d'escompte |

### Règles

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 36 | Plans de rémunération : liste, fiche, duplication annuelle | PAR-01, PAR-12 | `rules` / RulesView | GET/POST /plans, POST duplicate | rules.spec | complet |
| 37 | Règles et versions d'un plan : liste, statuts, conflits détectés | PAR-03→05 | `rules` / RulesView | GET/POST rules, versions | rules.spec | complet |
| 38 | Paramétrage d'une version de règle, approbation par un second administrateur | PAR-01→08, PAR-14 | `rules` / RulesView | POST versions, approve, plausibility | rules.spec | complet — le formulaire affiche le schéma de la famille (PAR-02/06), saisit la politique de reprise et l'échéancier (PAR-08) et un éditeur de conditions de versement (PAR-07) ; la table des versions restitue reprise et conditions |
| 39 | Familles de calcul : un formulaire par famille | PAR-02 | `families` / FamiliesView | GET /families, /{f}/schema | families.spec | complet |
| 40 | Accords et inscriptions aux règles réservées, à l'unité et en masse | PAR-10 | `enrollments` / EnrollmentsView | GET/POST agreements, enrollments | enrollments.spec | complet |
| 41 | Simulation : lancement, table de substitution, résultat, historique | PAR-13 | `simulation` / SimulationView | POST /simulation/{e}/{m}, GET /simulation/{e}/history | simulation.spec | complet — lancement, table de substitution, résultat comparé, et historique des simulations (table `simulation_history` en ajout seul, cf. DECISIONS 2026-10-12) |
| 42 | Export et import du paramétrage | PAR-15 | `paramTransfer` / ParameterTransferView | GET export, POST import | parameter-transfer.spec | complet |

### Référentiels et flux

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 43 | Entités et leurs paramètres : calendrier, seuils, tolérances | REF-01, ADM-05 | `adminEntities` / EntityParamsView | GET/PUT /entities | entity-params.spec | complet |
| 44 | Assureurs, gammes, produits et options | REF-01 | `products` / ProductsView | GET /products, /insurers, /ranges, /options | referentials.spec | complet — assureurs (rôle), gammes (cible), produits et options (type) listés, les options partitionnées par l'entité de leur produit |
| 45 | Correspondance des codes externes par système source | REF-04 | `externalCodes` / ExternalCodesView | GET /external-codes | external-codes.spec | complet |
| 46 | Mappings de fichier : liste et versions | ING-02 | `mapping` / MappingView | GET /mappings | mapping.spec | complet |
| 47 | Édition et test d'un mapping | ING-02 | `mapping` / MappingView | POST /mappings/test, POST /mappings | mapping.spec | complet |
| 48 | Catalogue des flux | 9.1, ING-10 | `flows` / FluxView | GET /flows/catalog | flux.spec | complet |
| 49 | Exports et journal des exports | 9.11, 9.12, REP-04 | `exports` / ExportsView | GET/POST /flows/exports | exports.spec | complet |
| 50 | Formats d'échange | 9.2, 9.3, 9.9–9.11 | `formats` / FormatsView | GET /flows/formats | flux.spec | complet |
| 51 | Reprise d'historique : dépôt des fichiers d'ouverture et contrôle | ING-11 | `reprise` / RepriseView | POST /imports | reprise.spec | complet |

### Comptabilité

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 52 | Schéma comptable et correspondance des comptes par entité | FIN-02 | `accounting` / AccountingView | GET /accounting/{e}/scheme | accounting.spec | complet |
| 53 | Recherche d'écritures | FIN-01, FIN-05, FIN-07 | `accounting` / AccountingView | GET /accounting/{e}/{m}/entries, /{e}/entries, /{e}/entries/{id}/chain | accounting.spec | complet — recherche transverse d'écritures (tous mois, par compte et exercice) et chaîne d'audit flux→bordereau→ligne→règle→écriture (FIN-07) depuis une écriture |
| 54 | Extraction comptable du mois et clôture | FIN-03 | `accounting` / AccountingView | POST /accounting/{e}/{m}/extract | accounting.spec | complet |
| 55 | État de cut-off | FIN-04, FIN-09 | `cutoff` / CutoffView | GET /accounting/{e}/cutoff | cutoff.spec | complet |
| 56 | Rapprochement des commissions acquises et payées | FIN-06 | `reconciliation` / ReconciliationView | GET /accounting/{e}/reconciliation | reconciliation.spec | complet — commissions acquises (acquises, payables, payées) rapprochées des payées, par bénéficiaire et au global, avec le reste à payer |
| 57 | Commissions reçues des assureurs, attendu et marge | FIN-08, REP-08 | `margin` / MarginView | GET /reporting/{e}/margin, /insurer-reconciliation | reporting.spec | complet — marge (reçu − payé) par produit/assureur/distributeur, et rapprochement des commissions d'assureur attendu (assiette × taux) contre reçu, avec l'écart (FIN-08) |

### Documents, restitutions, administration

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 58 | Modèles de documents | BRD-05 | `documentTemplates` / DocumentTemplatesView | GET/POST /documents/templates/{e} | document-templates.spec | complet — liste des modèles par type et édition par l'administrateur métier (logo, mentions, colonnes, ordre), chaque enregistrement créant une nouvelle version tracée au journal d'audit |
| 59 | Attestations fiscales annuelles | BRD-08 | `taxCertificates` / TaxCertificatesView | GET /documents/tax-certificates/{e}, /{e}/{p} | tax-certificates.spec | complet — total des commissions payées à chaque bénéficiaire pour l'exercice (par défaut l'année courante), et l'attestation d'un bénéficiaire |
| 60 | Tableaux de bord : commissions, reprises, escomptes, comptes débiteurs, premiers distributeurs, anomalies | REP-01 | `dashboards` / DashboardsView | GET /reporting/{e}/dashboard | reporting.spec | complet |
| 61 | Vision consolidée du groupe | REP-07 | `groupView` / GroupView | GET /reporting/consolidated | reporting.spec | complet |
| 62 | Indicateurs de fonctionnement | REP-09 | `indicators` / IndicatorsView | GET /reporting/{e}/indicators | reporting.spec | complet |
| 63 | Correspondance entre groupes d'annuaire et rôles | section 3 | `adminRoleMappings` / RoleMappingsView | GET/POST/DELETE /role-mappings | role-mappings.spec | complet |
| 64 | Piste d'audit | ADM-04 | `adminAudit` / AuditView | GET /audit | audit.spec | complet |
| 65 | Supervision des traitements et des flux | section 3 | `supervision` / SupervisionView | GET /admin/supervision | admin-supervision.spec | complet |
| 66 | Purge et anonymisation des données personnelles | ADM-06 | `purge` / PurgeView | GET /admin/purge/eligible, POST /admin/purge | admin-supervision.spec | complet |

### Forces de vente

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 67 | Collaborateurs | FDV-01 | `fdvCollaborators` / FdvCollaboratorsView | GET/POST /fdv/{e}/collaborators, PUT /fdv/{e}/collaborators/{id} | fdv.spec | complet — écran de gestion dédié : création et mise à jour individuelles (poste, équipe, région, responsable, dates), en plus de l'alimentation en masse par le SI RH ; liste restituant la validité et le poste en clair |
| 68 | Mesures reçues : absences, production, activité, marge | FDV-02 | `fdvMeasures` / FdvMeasuresView | GET /fdv/{e}/measures | fdv.spec | complet — écran de consultation des mesures reçues (absences, production par marché, activité CRM, marge, cahiers des charges) alimentées par l'import SI RH/CRM ; types de mesure restitués en clair |
| 69 | Objectifs individuels et collectifs, versionnés | FDV-03 | `fdvObjectives` / FdvObjectivesView | GET/POST /fdv/{e}/objectives | fdv.spec | complet — écran de gestion : objectifs individuels et collectifs, quantitatifs et qualitatifs, par poste et période, versionnés en ajout seul (nouvel objectif de même clé = version suivante, le calcul lit la dernière) |
| 70 | Plan de variable par poste | FDV-04, FDV-09→12 | `fdvPlan` / FdvPlanView | GET/POST /fdv/{e}/plans, POST run | fdv.spec | complet — gestion des plans par poste et composante : enveloppe, mensualité, barèmes/critères/règles d'absence en paramètres JSON (dont paliers de croissance FDV-09, vente directe FDV-10, réduction sur résiliation FDV-12) et validité datée ; le calcul des composantes FDV-09/10/12 reste reporté à l'atelier direction commerciale (§8.14 Limites, H-21, voir DECISIONS) |
| 71 | Validation mensuelle par le responsable | FDV-13 | `fdvValidation` / FdvValidationView | GET results, POST validate/adjust | fdv.spec | complet |
| 72 | Mon variable, mes objectifs, mon historique, simulateur | FDV-15, FDV-16 | `fdvCommercial` / FdvCommercialView | GET /fdv/{e}/my-variable/{id}, GET /fdv/{e}/simulate/{id} | fdv.spec | complet — historique mois par mois (FDV-15) et simulateur (FDV-16) : production pondérée hypothétique ou mesurée, taux d'atteinte, mensualité simulée et alerte de seuil, sans persistance (même règle que le calcul réel) |
| 73 | Synthèse des variables à payer et export paie | FDV-14 | `fdvValidation` / FdvValidationView | GET /fdv/{e}/{m}/pay-summary, POST /fdv/{e}/{m}/pay-export | fdv.spec | complet — synthèse des variables à payer par personne (total validé + exporté par collaborateur) et export vers la paie (OUT-06) |

## Écrans impliqués par les exigences, hors inventaire

- **Avance exceptionnelle à échéancier (CAL-14)** : sous-écran de « Comptes débiteurs »
  (#30), pas encore présent — à rattacher à l'étape 9.
- **Rapprochement des commissions reçues des assureurs (FIN-08)** : distinct de la
  marge (#57) ; à créer à l'étape 11.

## Tableau 2 — Exigences (section 8 de la spécification)

Colonnes : exigence · classes serveur · écran · tests · état. Les tests JUnit
existent pour la quasi-totalité du moteur ; la colonne « tests » note surtout le
niveau de preuve de bout en bout (étiquette `@req`/`@cr`) quand il existe.

### 8.1 Référentiels (REF)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| REF-01 | EntityAdminService, ReferentialQueryService | #43, #44 | entity-params, referentials | complet — entités et paramètres gérés à l'écran (validité) ; assureurs, gammes, produits et options alimentés par import et consultés avec leur validité |
| REF-02 | ReferentialQueryService, DistributorsResource | #19 | distributors | complet |
| REF-03 | ReferentialQueryService | #25 | distributors | complet — hiérarchie groupement/distributeur/code portée et historisée (audit) |
| REF-04 | ReferentialQueryService | #45 | external-codes | complet |
| REF-05 | ReferentialQueryService (changedSinceLastPayment) | #26 | referentials | complet — coordonnées bancaires avec signalement d'un changement depuis le dernier paiement |
| REF-06 | ReferentialQueryService | #27 | payee-rules | complet |
| REF-07 | AccountQualityImporter, ReferentialQueryService | #28 | referentials | complet |
| REF-08 | PaymentService (payable, lien daté) | #45 | payment | complet — changement de statut appliqué à sa date d'effet : un distributeur résilié avant la période est exclu du paiement (PAYEE_INACTIVE), un statut prenant effet après la période laisse payer |
| REF-09 | TransferImportService, CalculationEngine (applyPortfolioTransfers, applyReplacementOrders) | #29 | transfers | complet — cessions de portefeuille (réaffectation + disposition d'escompte, CR-80) et ordres de remplacement appliqués par le moteur à leur date d'effet : la garantie principale du contrat passe au nouveau code de production, les primes postérieures lui sont commissionnées (prouvé par `CalculationEngineTest.ref09_...`, idempotent au recalcul) |
| REF-10 | DistributorsResource, AccountService | #20 | distributors | complet — fiche distributeur consolidée (identité, comptes, historique) |
| REF-11 | PublicApiService, partitioning | — | entity-params (CR-70) | complet |

### 8.2 Intégration (ING)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| ING-01 | ImportService, PublicApiResource | #5 | imports | complet |
| ING-02 | MappingAdminService, MappingImporter | #46, #47 | mapping | complet |
| ING-03 | ImportService (contrôles) | #4 | imports | complet |
| ING-04 | ImportService (rejets + codes motif) | #4 | imports | complet |
| ING-05 | ImportQueryService | #3 | imports | complet |
| ING-06 | ImportService (relance auto), EventQueryService | #7, #8 | events, held-events | complet — relance serveur, écrans « recherche d'événements » (statut, motif, lignes produites) et « en attente par motif » livrés |
| ING-07 | ManualEventService | #6 | manual-event | complet — événements et données complémentaires saisissables à la main, tracés |
| ING-08 | ImportService, AdjustmentImportService | #5, #12 | imports, adjustments | complet — import en masse de données complémentaires (garanties/événements, #5) et d'ajustements (REG-04, #12) |
| ING-09 | ExternalFamily (nature déclarée), CalculationEngine | #57 | cr51 + ing09 JUnit | complet — commissions déjà calculées chargées en ligne `EXTERNAL` et commissions reçues d'assureurs chargées en ligne `INSURER` de sens entrant (la famille EXTERNAL prend la nature déclarée par la règle, §6.10) ; prouvé par `CalculationEngineTest.ing09_insurer_commission_loads_an_insurer_line` et `FamilyProposeTest` |
| ING-10 | FlowCatalogService | #3, #48 | flux | complet |
| ING-11 | OpeningBalanceImporter | #51 | reprise | complet |

### 8.3 Paramétrage (PAR)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| PAR-01 | RuleConfigService | #36, #38 | rules | complet |
| PAR-02 | FamilyRegistry, *Family | #39 | families | complet |
| PAR-03 | RuleConfigService | #37 | rules | complet |
| PAR-04 | RuleConfigService | #38 | rules | complet |
| PAR-05 | RuleConfigService (conflits) | #37 | rules | complet |
| PAR-06 | TieredFamily, BaremeInitialService | #38 | — | complet — l'écran sert le schéma JSON de la famille à paliers depuis `/families/{f}/schema` pour guider la saisie des paramètres |
| PAR-07 | RuleCondition, CalculationEngine | #38 | — | complet |
| PAR-08 | RuleConfigService, CalculationEngine | #38 | — | complet — politique de reprise + mode `DEFER`/`FORFEIT` de la condition de versement (§6.7) désormais honorés par le moteur |
| PAR-09 | AgreementService | #31, #32 | advance-agreements | complet — saisie et suivi des conventions d'escompte (statut, plafond, compteurs) |
| PAR-10 | AgreementService | #40 | enrollments | complet |
| PAR-11 | RuleConfigService (generateAmendment) | #38 | rules | complet — l'avenant est généré depuis une version de règle (type selon la nature : avenant RCD/RCP ou convention), avec sa référence de document, et créé en attente de signature (`NOT_SIGNED`) ; la signature électronique elle-même est hors profil dev (§8 du cadrage) |
| PAR-12 | RuleConfigService (duplicate) | #36 | rules | complet |
| PAR-13 | SimulationService | #41 | simulation | complet |
| PAR-14 | RuleConfigService (plausibility) | #38 | rules | complet |
| PAR-15 | PlanTransferService | #42 | parameter-transfer | complet |

### 8.4 Calcul (CAL)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| CAL-01 | CalculationEngine, CalculationQueryService | #9 | calculations, cr* JUnit | complet — écran de lancement et de suivi des exécutions (compteurs, unités en échec) |
| CAL-02 | LinearFamily, UpfrontFamily | #24 | cr01–cr04 JUnit | complet |
| CAL-03 | AdvanceFamily, CalculationEngine | #31 | cr20–cr28 + apurement multi-mois + CR-78/80 JUnit | complet — escomptes, apurements (d'un mois sur l'autre), régularisations, write-off et cession traités par le moteur |
| CAL-04 | CalculationEngine (reprises) | #24 | cr04–cr07 JUnit | complet |
| CAL-05 | CalculationEngine (RCD/RCP) | #24 | cr40–cr48 + DEFER JUnit | complet — RCD/RCP et conditions ; rattrapage `DEFER` (§6.7) **implémenté** (évaluation mensuelle de la condition de versement) |
| CAL-06 | OutstandingFamily, CalculationEngine | — | cr30–cr36 + 1,20 % année 6 + délai §6.6 JUnit | complet — règle linéaire 1,20 % année 6, `seniorityOrigin` et délai de versement « période qui suit le premier versement » (§6.6) réalisés et testés |
| CAL-07 | CalculationEngine (co-courtage, partages) | #24 | cr49, cr50, cr76 JUnit | complet |
| CAL-08 | FeeFamily, FixedFamily | #24 | cr77 JUnit | complet |
| CAL-09 | CalculationEngine (règles temporaires) | #38 | cr75 JUnit | complet |
| CAL-10 | CalculationEngine (explication), CommissionLinesResource | #23 | reporting | complet — ligne rattachée à son événement/règle/assiette/taux avec explication, exposée par l'écran détail de ligne |
| CAL-11 | CalculationEngine, SimulationService | #41 | cr69 JUnit | complet |
| CAL-12 | CalculationEngine (recalcul partiel) | #9 | calculations, JUnit | complet — recalcul partiel par distributeur, produit ou règle depuis l'écran |
| CAL-13 | CalculationEngine (recordExpected), ReportingService (margin) | #57 | cr33/34/36 + margin JUnit | complet — commission reçue attendue calculée par les règles jumelles de sens INBOUND (P17), conservée avec le résultat du contrôle « reçu ≠ attendu » (§6.6), et marge nette reçu − versé par produit/assureur/distributeur ; prouvés par `SavingsEngineTest.cr33/34/36` et `ReportingServiceTest.margin_on_three_axes` |
| CAL-14 | AdjustmentService (repaymentSchedule) | #30 | distributor-account | complet — avance exceptionnelle remboursée selon un échéancier d'apurement |

### 8.5 Ajustements et réclamations (REG)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| REG-01 | AdjustmentService | #12 | adjustments | complet |
| REG-02 | AdjustmentService, AdjustmentsResource | #12 | adjustments | complet — motif obligatoire, référence de pièce jointe et lien à la ligne corrigée saisis à l'écran et restitués dans la liste (lien à la ligne prouvé par AdjustmentsResourceTest) |
| REG-03 | AdjustmentService (double signature, seuil) | #12 | adjustments | complet |
| REG-04 | AdjustmentImportService | #12 | adjustments | complet — import en masse d'ajustements depuis un fichier pivot |
| REG-05 | AdjustmentService (auto-reverse) | #12 | adjustments | complet — option de contrepassation automatique créant la reprise au mois suivant (M+1) |
| REG-06 | AdjustmentService (claims), ClaimsResource | #13 | claims | complet — enregistrement, lignes contestées et suivi jusqu'à la régularisation |

### 8.6 Contrôles (CTL)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| CTL-01 | ControlCatalogService, ControlService (edit) | #11 | controls | complet — catalogue éditable par le métier (gravité, activation, paramètres) |
| CTL-02 | ControlService, ControlCatalogService | #10 | controls, cycle JUnit | complet — les contrôles listés en §8.6 sont livrés dans les dix familles (complétude, qualité des données, calcul, escompte, reprises, surcommissions, comptes, paiement, comptabilité, flux externes), soit 33 contrôles paramétrables ; chacun évalué par `ControlService.run`, prouvé jambe par jambe par `ControlFamiliesTest`, et listé à l'écran du catalogue (controls.spec, catalog-row par famille) |
| CTL-03 | ControlService (checklist) | #10 | controls | complet — liste de contrôle par période, à cocher, tracée |
| CTL-04 | ControlService (edit → version), CycleQueryService (versions) | #11 | controls | complet — versionnement des contrôles (nouvelle version à chaque édition, historique consultable) |
| CTL-05 | ControlService (décision tracée) | #10 | controls | complet |
| CTL-06 | ExportService (CONTROL_RESULTS) | #49 | exports | complet — export CSV des données des contrôles de niveau 2 (avertissement) et 3 (bloquant) d'une période, hors contrôles de niveau 1 (information) |

### 8.7 Pilotage du cycle (WKF)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| WKF-01 | PeriodService | #2 | periods | complet |
| WKF-02 | ValidationService, ValidationResource | #14 | validation | complet — totaux, écart au mois précédent et présence d'ajustements déclenchant un second niveau paramétrable |
| WKF-03 | (trace journal) | — | — | complet — messagerie simulée par une trace (LANCEMENT §6) |
| WKF-04 | CycleQueryService | #1 | periods | complet |

### 8.8 Compte distributeur (CPT)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| CPT-01 | AccountService | #20 | distributor-account | complet |
| CPT-02 | AccountService, ReferentialQueryService (groups) | #20, #21 | distributor-account, groupings | complet — vue par distributeur (comptes des codes) et vue par groupement (membres) |
| CPT-03 | AccountService (report débiteur) | #30 | cr60 JUnit | complet |
| CPT-04 | AccountService (compensation) | #22 | distributor-account | complet |
| CPT-05 | AccountService (blocage) | #22 | distributor-account | complet — blocage de compte ou de ligne avec motif et date de levée |
| CPT-06 | AccountService | #20, #24 | — | complet |
| CPT-07 | AccountService (débiteurs) | #30 | distributor-account | complet — liste des distributeurs débiteurs sans portefeuille actif |
| CPT-08 | AgreementService (discountSummary) | #33 | discounts | complet — créance d'escompte suivie par convention et distributeur |

### 8.9 Paiement (PAY)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| PAY-01 | PaymentService | #15 | payment | complet |
| PAY-02 | PaymentService (exclusions) | #15 | payment | complet |
| PAY-03 | PaymentService (SEPA pain.001) | #15 | payment | complet |
| PAY-04 | PaymentService (cheque emission) | #17 | payment | complet — liste des chèques et saisie de leur émission (ordres réglés) |
| PAY-05 | PaymentService (alignement) | #15 | cr64 JUnit | complet |
| PAY-06 | PaymentService (résultat bancaire) | #17 | payment | complet |
| PAY-07 | PaymentService (lot hors cycle) | #16 | payment | complet |
| PAY-08 | AccountService (proposePayments par entité) | #15 | pay08 JUnit | complet — le paiement est proposé par entité : un distributeur commun à plusieurs entités reçoit un lot et un ordre par entité débitrice, prouvé par `PaymentServiceTest.pay08_one_order_per_entity_for_a_common_distributor` |

### 8.10 Bordereaux et documents (BRD)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| BRD-01 | StatementService, StatementDocuments | #18 | bordereaux | complet — PDF et tableur xlsx réels (produits sans dépendance externe), téléchargeables et archivés avec empreinte |
| BRD-02 | StatementService (render/section) | #18 | bordereaux | complet — contenu de bordereau par rubriques du §9.5 (commissions, reprises, escomptes, compléments, ajustements, report, net) |
| BRD-03 | StatementService | #18 | bordereaux | complet |
| BRD-04 | StatementService (render, grouping) | #18 | bordereaux | complet — bordereau décliné selon la préférence du distributeur (aucune, par assureur ou par produit) : chaque ligne porte le code assureur ou produit, restitué à l'écran et dans le tableur ; un bordereau est produit par entité (lot et ordre par entité, PAY-08) |
| BRD-05 | DocumentTemplateService | #58 | document-templates | complet — modèles de documents modifiables par l'administrateur, versionnés |
| BRD-06 | StatementService (archivage/rectificatif) | #18 | bordereaux | complet |
| BRD-07 | StatementService (report solde) | #18 | cr60 JUnit | complet |
| BRD-08 | TaxCertificateService | #59 | tax-certificates | complet — attestation fiscale annuelle par bénéficiaire (total payé de l'exercice) |
| BRD-09 | AgreementService (discountSummary) | #33 | discounts | complet — synthèse des escomptes par convention (versé, apuré, créance) |
| BRD-10 | PublicApiService (statements, lines, tax-certificates) | API 9.8 | PublicApiResourceTest | complet — bordereaux, lignes et attestations fiscales mis à disposition des extranets/GED/CRM par l'API publique `/api/v1` (consultation) ; le push éditique vers la GED est hors profil dev (§8) |
| BRD-11 | StatementService (member statement) | #18 | bordereaux | complet — relevé informatif de la part propre d'un membre payé par son groupement |
| BRD-12 | StatementService (spreadsheet/workbook) | #18 | bordereaux | complet — export des données du bordereau (tableur CSV et classeur xlsx réels) |

### 8.11 Comptabilité (FIN)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| FIN-01 | AccountingService | #53 | accounting | complet — une écriture par événement comptable : EARNING/PAYABLE_RECOGNITION, PAYMENT/PAYMENT_REJECTION, ADVANCE_* et, pour une ligne `INSURER` reçue, `INSURER_RECEIVABLE` à la validation (créance assureur contre produit de commission, §6.10) ; la ligne `INSURER` ne génère ni payable ni paiement |
| FIN-02 | AccountingSetupService, AccountingQueryService | #52 | accounting | complet |
| FIN-03 | AccountingService (extraction) | #54 | accounting | complet |
| FIN-04 | CutoffService | #55 | cutoff | complet |
| FIN-05 | AccountingService | #53 | accounting | complet |
| FIN-06 | AccountingQueryService (reconciliation) | #56 | reconciliation | complet — rapprochement des commissions acquises et payées, par bénéficiaire et global |
| FIN-07 | AccountingQueryService (searchEntries/entryChain) | #53 | accounting | complet — chaîne flux→bordereau→ligne→assiette/taux/règle→écriture exposée à l'écran |
| FIN-08 | ReportingService (insurerReconciliation, settleInsurerCommissions) | #57 | reporting | complet — rapprochement des commissions reçues des assureurs avec l'attendu (CAL-13) par assureur, et règlement de l'avis de paiement par la Finance qui solde (PAID) les lignes `INSURER` reçues de la période |
| FIN-09 | CutoffService | #55 | cutoff | complet |

### 8.12 Restitutions (REP)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| REP-01 | ReportingService | #60 | reporting | complet |
| REP-02 | ReportingService | #24 | reporting | complet |
| REP-03 | ReportingService | #24 | reporting | complet |
| REP-04 | ExportService | #49 | reporting, exports | complet |
| REP-05 | ExportService (dataExport incrémental) | #49 | exports | complet — alimentation Data incrémentale : un export Data avec une date « depuis » ne renvoie que les objets créés ou modifiés à partir de cette date (`coalesce(updatedAt, createdAt)`), l'export complet restant pour l'initialisation |
| REP-06 | PublicApiService | — | — | complet |
| REP-07 | ReportingService | #61 | reporting | complet |
| REP-08 | ReportingService | #57 | reporting | complet |
| REP-09 | ReportingService | #62 | reporting | complet |

### 8.13 Administration (ADM)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| ADM-01 | RoleMappingAdminService, partitioning | #63 | role-mappings (ADM-01) | complet |
| ADM-02 | EntityAdminService | #43 | entity-params | complet |
| ADM-03 | (interface française) ; socle `CodeSelect`/`EntitySelect`/`MonthSelect`/`DistributorSelect`/`ProductSelect`/`SourceSystemSelect`… + magasins `periods`/`distributors`/`products`/`sourceSystems`… + helpers de présentation (`events`, `lines`, `coverage`, `agreements`, `distributors`, `advance`, `transfers`, `accounting`, `rules`, `salesforce`, `imports`, `months`) | tous | par écran (assertions de libellé FR dans chaque spec) | complet — interface française achevée : (1) toute valeur connue (entité, mois, distributeur, produit, plan, règle, groupement, délégation, membre, système source, type d'ajustement…) se choisit dans une liste, plus aucune saisie libre d'un code connu ; (2) plus aucun code d'énumération brut à l'écran — statuts, natures, sens, types d'événement, modes, signatures, statuts d'inscription/convention/position/version/cession, événements comptables, variantes et rôles de compte sont affichés par leur libellé FR du glossaire via des helpers de présentation couverts à 100 % (vitest) ; (3) mois et dates au format français. Balayage systématique des colonnes, slots `#cell-*`, fiches `DetailRow` et interpolations fait sur tous les écrans ; les champs `reason`/`Motif` restants sont du texte libre, pas des énumérations. |
| ADM-04 | AuditService (chaîne de hachage) | #64 | audit | complet |
| ADM-05 | EntityAdminService | #43 | entity-params | complet |
| ADM-06 | PurgeService | #66 | admin-supervision | complet |

### 8.14 Forces de vente (FDV)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| FDV-01 | EmployeeImportService, SalesForceService (create/update), SalesForceQueryService | #67 | fdv | complet — gestion des collaborateurs à l'écran (création/mise à jour) et alimentation en masse par le SI RH |
| FDV-02 | EmployeeImportService (import), SalesForceQueryService (measures) | #68 | fdv | complet — mesures reçues par import SI RH/CRM et consultées à l'écran (absences, production, activité, marge, cahiers des charges) |
| FDV-03 | SalesForceService (createObjective), SalesForceQueryService | #69 | fdv | complet — objectifs individuels/collectifs, quantitatifs/qualitatifs, par poste et période, versionnés en ajout seul |
| FDV-04 | SalesForceService (savePlan) | #70 | fdv | complet — gestion des plans par poste et composante : enveloppe, mensualité, barèmes/critères/règles d'absence (paramètres JSON) et validité datée |
| FDV-05 | SalesForceService (runMonth) | #71 | cr88 JUnit | complet |
| FDV-06 | SalesForceService (runAnnualRegularization) | #70 | fdv | complet — régularisation annuelle individuelle de janvier N+1 : droit annuel = enveloppe effective × min(1, pondéré/objectif) − acomptes versés, enveloppe minorée des mensualités des mois d'absence (plancher 0), écrite en ANNUAL_ADJUSTMENT ; lancée par l'administration |
| FDV-07 | SalesForceService (runQuarterlyQualitative) | #70 | fdv | complet — variable trimestriel qualitatif : un montant par critère atteint sur le trimestre (productivité, efficacité, développement), complément nouveaux producteurs si le développement dépasse l'objectif de l'overshoot, non versé au seuil d'absence trimestriel ; montants, overshoot et seuil en paramètres de plan (§8.14, DEC 2026-10-13) |
| FDV-08 | SalesForceService (runAnnualCollective) | #70 | fdv (FDV-08), SalesForceServiceTest, FdvResourceTest | complet — variable annuel quantitatif collectif : l'enveloppe collective du poste × le taux d'atteinte collectif (production pondérée de l'entité sur l'objectif collectif entité, plafonné à 100 %) × la proratisation par mois travaillés (un mois d'absence retire un douzième), réglée en janvier N+1 et régularisée en juin (le second règlement n'écrit que le delta) ; enveloppes et objectif collectif en paramètres (DEC 2026-10-14) |
| FDV-09 | SalesForceService (runManagerVariable) | #70 | fdv (FDV-09), SalesForceServiceTest, FdvResourceTest | complet — variable des animateurs et responsables : un montant de palier de croissance de la production (`growthTiers` %→montant, la croissance étant l'excédent de la production pondérée sur le `growthBaseline` du plan) plus un montant de palier de critères qualitatifs atteints (`criteriaTiers` nombre→montant) ; barèmes et base en paramètres de plan (DEC 2026-10-14), réglé en janvier N+1 |
| FDV-10 | SalesForceService (runDirectSalesVariable) | #70 | fdv (FDV-10), SalesForceServiceTest, FdvResourceTest | complet — variable de la vente directe : part (`marginRate`) de la marge brute des affaires nouvelles + prime de dépassement (`overshootBonus` au-delà de `overshootThreshold`) + palier sur la valeur du portefeuille (`portfolioTiers`), payée à 70 % (`januaryFraction`) en janvier N+1 et soldée en juin ; taux, seuils, barème et dimensions en paramètres de plan (DEC 2026-10-14) |
| FDV-11 | SalesForceService (pondération) | #70 | cr87 JUnit | complet |
| FDV-12 | SalesForceService (runTerminationReduction) | #70 | fdv (FDV-12), SalesForceServiceTest, FdvResourceTest | complet — réduction du variable sur résiliation : un montant négatif égal au taux `reductionRate` × la production des contrats résiliés avant la durée paramétrée (mesure `EARLY_TERMINATION`, la durée étant appliquée à la source), qui net la synthèse à payer (FDV-14) ; taux, dimension et durée en paramètres de plan (DEC 2026-10-14), réglé en janvier N+1 |
| FDV-13 | SalesForceService (validate/adjust) | #71 | fdv | complet |
| FDV-14 | SalesForceQueryService (paySummary), SalesForceService (pay-export) | #73 | fdv | complet — synthèse des variables à payer par personne et export paie (OUT-06) |
| FDV-15 | SalesForceQueryService | #72 | fdv | complet |
| FDV-16 | SalesForceService (simulateMonthly) | #72 | fdv | complet — simulateur de variable (production hypothétique ou mesurée) et alerte d'atteinte de seuil, sans persistance |

## ADM-03 — conversion écran par écran (CLÔTURÉE le 2026-10-14)

**ADM-03 est `complet`.** Tous les écrans ont été convertis, écran par écran, chacun avec l'adaptation
de son test de bout en bout. Le relevé ci-dessous est conservé pour mémoire : chaque ligne a été traitée
(saisies libres de code → listes ; codes d'énumération → libellés FR) et le balayage final (colonnes,
slots `#cell-*`, `DetailRow`, interpolations, codes comptables) ne trouve plus aucun code brut à l'écran.

**Saisies libres de code à remplacer par une liste / recherche** (relevé des `type="text"`
liés à une valeur connue, audit à élargir — certains écrans en portent d'autres, p.ex. `v-scope`
de RulesView) :

- ~~ManualEventView~~ **converti (2026-10-14)** : `field-source`/`cov-source` en `SourceSystemSelect`,
  `cov-product` en `ProductSelect`, et les énumérations `field-type` (libellés d'événement),
  `cov-mode` (mode de rémunération) et `cov-status` (statut d'adhésion) affichées au libellé FR via
  `CodeSelect` ; `field-coverage`/`field-contract`/`cov-coverage`/`cov-contract`/`cov-holder`/`cov-production`
  restent libres (identifiants d'une garantie/adhésion/souscripteur créés à la saisie, comme `pt-newcode`).
- ~~FdvCommercialView~~ **converti (2026-10-14)** : `sim-month` (mois du simulateur) passé en `MonthSelect`
  (scopé à l'entité), `fdv.spec` adapté (`selectOption`).

**Balayage des libellés d'énum restant** (affichage d'un code technique dans un tableau/une fiche,
à remplacer par le libellé FR du glossaire — audit en cours) :

- ~~EnrollmentsView~~ **converti (2026-10-14)** : type d'accord, signature (`SIGNED`/`NOT_SIGNED`) et
  statut d'inscription affichés au libellé FR (helper `agreements.ts`).
- ~~DistributorsView~~ **converti (2026-10-14)** : `agreementType`, `signatureStatus`, statut d'inscription
  (helper `agreements.ts`) **et** type de distributeur + statut par entité + statut de code de production
  (helper `distributors.ts`, énums repérés en plus lors du balayage) affichés au libellé FR.
- ~~Audit des colonnes de tableau (2026-10-14)~~ **fait** : balayage systématique des colonnes `DataTable`
  liées à une énumération (clés `status`/`type`/`nature`/`direction`/`reason`/`severity`/`decision`/`state`…).
  Offenseurs réels corrigés — AdjustmentsView (type d'ajustement → `adjustmentTypeLabel`, sélecteur de type
  au libellé), FdvPlanView (type d'objectif → `objectiveTypeLabel`), FdvCommercialView (statut de variable →
  `variableStatusLabel`), RepriseView (statut d'import → `importStatusLabel`), SimulationView (nature →
  `lineNatureLabel`). Les autres colonnes repérées rendaient déjà par un slot `#cell-*` labellisé.
- ~~Audit hors tableau + passthroughs `value: () => x.enum ?? '—'` (2026-10-14)~~ **fait** : relevé
  exhaustif des rendus d'énumération dans les colonnes à `value:` qui renvoyaient le code brut, dans les
  slots `#cell-*`, les `DetailRow` et les interpolations `{{ }}`. Corrigés — ContractsView (statut
  d'adhésion, mode de rémunération, type d'événement, statut d'événement, nature et statut de ligne,
  statut de position d'escompte), AccountingView (type d'événement dans la chaîne d'audit),
  AdvanceAgreementsView (statut de convention + statut de position), DiscountsView (statut de position),
  DistributorAccountView (nature/statut de ligne, statut d'échéancier = statut d'ajustement), RulesView
  (statut de version → nouveau `versionStatusLabel`, sens → `directionLabel`), TransfersView (statut de
  cession → nouveau `transferStatusLabel`). Nouveaux helpers `advance.ts` (statut de convention + de
  position) et `transfers.ts`, `versionStatusLabel` dans `rules.ts`, `eventStatusLabel` rendu nullable —
  tous 100 % vitest (176 tests). e2e des 6 specs touchées vertes (22/22), build serveur inchangé.
  Les `reason`/`Motif` restants (CommissionSearchView, BordereauxView, PeriodsView) sont des champs de
  texte libre, pas des énumérations — laissés tels quels à dessein.
- ~~AccountingView « schéma comptable »~~ **fait (2026-10-14)** : nouveau helper `accounting.ts`
  (`accountingEventLabel`, `accountingVariantLabel`, `accountRoleLabel`, §10.2, 100 % vitest). Le schéma
  (événement, variante, rôles débit/crédit), la table de correspondance (rôle), la table des écritures
  (événement comptable) et la chaîne d'audit (nature de ligne) affichent désormais le libellé FR ;
  `accounting.spec` asserte « Constatation du prévisionnel » et « Provision pour commissions à payer ».
  Les numéros de compte, journaux et en-têtes de CSV restent des données/techniques, non des énumérations.
- **ADM-03 clôturé** : le balayage final (toutes colonnes, slots, fiches, interpolations, codes comptables)
  ne trouve plus aucun code d'énumération brut, et aucune saisie libre d'une valeur connue ne subsiste.

Socle « liste / sélection » complété le 2026-10-13 : stores Pinia `groupings`, `delegations`,
`productionCodes`, `plans`, `rules` (100 % vitest) ; composants `GroupSelect`, `DelegationSelect`,
`MemberSelect`, `PlanSelect`, `RuleSelect` ; points d'accès serveur `GET /delegations`,
`GET /production-codes` et `GET /rules` (partitionnés, testés JUnit) ; le jeu de démonstration porte une
délégation sur chaque distributeur (DemoReferentialBuilder) et un groupement + délégation sur le
distributeur de `seedCycle`.

Écrans déjà convertis : #24 CommissionSearchView (entité/mois/distributeur/produit/nature **+
groupement `GroupSelect` + délégation `DelegationSelect`**) ; DashboardsView (entité/mois Du-Au/
produit/distributeur/groupement/délégation/nature, tous en liste) ; BordereauxView (entité + mois +
membre `MemberSelect` sur code de production, et mois du détail au libellé FR) ;
ExportsView (entité + période ; `exports-since` reste une date native) ; ParameterTransferView
(entité + plan `PlanSelect`, nouveau code libre) ; SimulationView (entité + mois + règle `RuleSelect` +
distributeur `DistributorSelect`) ; EnrollmentsView (plan `PlanSelect`, règle `RuleSelect` filtrée par le
plan, distributeurs `DistributorSelect`, accord choisi dans la liste des accords ; référence d'accord
nouvelle restant libre) ; ImportsView (statut d'import au libellé FR) ; SupervisionView (statut de santé
au libellé FR) ;
RoleMappingsView (entité ; rôle et entité au libellé dans le formulaire et le tableau) ;
CutoffView, ReconciliationView, TaxCertificatesView, DocumentTemplatesView, RepriseView, IndicatorsView
(entité) ; AccountingView, ControlsView, PaymentView, FdvPlanView, FdvValidationView (entité + mois
`AAAA-MM-01` via `MonthSelect value-format="date"`) ; DistributorAccountView (entité + mois +
distributeur via `DistributorSelect`) ; FdvCollaboratorsView, FdvCommercialView, FdvMeasuresView,
FdvObjectivesView (entité) ; AdjustmentsView (entité + mois + statut au libellé FR), MarginView
(entité + mois) ; ClaimsView (distributeur via `DistributorSelect`) ;
ManualEventView (système source via `SourceSystemSelect`, produit via `ProductSelect`, type
d'événement/mode de rémunération/statut d'adhésion au libellé FR via `CodeSelect`) ;
FdvCommercialView (`sim-month` via `MonthSelect`). Chacun porte son assertion ADM-03 dans son test de
bout en bout.
Le `MonthSelect` lie le mois tel que le serveur le rend — la date premier-du-mois `AAAA-MM-01` que
`/periods` renvoie et que **tous** les points d'accès de mois analysent (vérifié : `2026-09` → 500,
`2026-09-01` → 200). Il n'y a donc qu'une seule forme de mois ; l'option `value-format` introduite sur
une lecture fausse de `/periods` (qui aurait rendu `AAAA-MM`) est retirée — elle produisait des options
`AAAA-MM-01-01` cassées dès qu'on les sélectionnait. `monthLabel` accepte les deux formes par prudence.
Le jeu `seedCycle` relie `P13897` à son entité (`DistributorEntity` ACTIVE) pour qu'il soit atteignable
depuis la recherche de distributeurs qui alimente le sélecteur.

**Affichages d'énum bruts encore en clair** (à remplacer par le libellé FR du glossaire) :

- Voir « Balayage des libellés d'énum restant » ci-dessus. (SupervisionView statut, ImportsView statut et
  BordereauxView mois désormais au libellé FR via `healthLabel`/`importStatusLabel`/`monthLabel`.)
  (ControlsView `severity`, FdvValidationView `status`, PaymentView `reason` et AdjustmentsView `status`
  sont désormais au libellé FR, cf. `severityLabel`/`exclusionReasonLabel`/`variableStatusLabel`/`adjustmentStatusLabel`.)

**Points de méthode repérés** (à traiter avec la spécification, pas à contourner) :

- Certains tests saisissent volontairement un code inconnu pour prouver le rejet serveur
  (`cov-product=P-UNKNOWN` dans manual-event.spec, `dash-product=PRODUIT-INEXISTANT` dans
  reporting.spec) : la liste empêche la saisie libre, donc le chemin d'échec se reprouve
  autrement (sélection d'une valeur connue sans donnée, et contrôle serveur conservé).
- Une seule sémantique de « mois » existe côté serveur, contrairement à ce qu'avait écrit un
  passage précédent : `/periods` rend le mois en date premier-du-mois `AAAA-MM-01`, et **tous** les
  points d'accès de mois (période ET recherche/règlement) l'analysent par `LocalDate.parse` (vérifié
  en frappant le serveur : `controls/.../2026-09/anomalies` → 500, `.../2026-09-01/anomalies` → 200 ;
  `reporting/.../search?month=2026-09` → 500, `?month=2026-09-01` → 200). Le `MonthSelect` lie donc la
  date telle quelle, sans transformation. L'option `value-format` ajoutée par le passage précédent
  reposait sur une lecture fausse de `/periods` et cassait les options des écrans « date » dès
  sélection ; elle est retirée.

## Cas de recette — état du rejeu

- **Existence (JUnit) :** **les 71 cas existent** (CR-74 P12, CR-80 cession avec escompte et
  CR-81 rachat anticipé emprunteur réalisés le 2026-10-11, avec tests). Reste à les **rejouer
  par l'interface** (68 sur 70, étape 4).
- **Rejeu par l'interface (`@cr:`) :** 54 sur 70 (`CR-01` à `CR-12`, `CR-20` à `CR-28`, `CR-30` à `CR-38`,
  `CR-40` à `CR-51`, `CR-60` à `CR-71`).
  **Toutes les sections de la §13 sont rejouées (§13.1 à §13.5).** Les 69 cas de recette hors
  performance (CR-72, volume, exclu) sont rejoués par l'interface. Il ne reste plus de cas à rejouer.
  **§13.1 et §13.2 complètes ; §13.3 entamée.** Escompte : versement (CR-20), apurement (CR-21),
  résiliation (CR-22), solde (CR-23), complément (CR-24), hors convention (CR-25), second impayé
  (CR-26 → −400), plafond (CR-27), déficit à 360 j (CR-28 → −30, réglé en décembre). La position
  d'escompte (statut/escompté/apuré) est lue sur l'écran de la convention `/escompte`, les
  régularisations/reprises sur la recherche de commissions. Épargne retraite (§13.3) : versement initial
  linéaire (CR-30 → 120,00 € sur un PER à 2 %), versement programmé linéaire (CR-31 → 9,00 € à 3 %),
  versement programmé escompté an 1 (CR-32 → 288,00 €, calcul de février sur un versement de janvier,
  §6.6), complément retraite (CR-35 → 351,00 € et 352,98 €, palier à 10 % sous 10 000 €) déclenché par la
  **clôture annuelle** depuis l'écran des exécutions. Restent à rejouer : CR-33/34 et CR-36 (rappel et
  surcommission assureur — produisent un **montant attendu**, pas de ligne, conservé avec le résultat du
  contrôle « commission reçue différente de l'attendu », à lire sur un écran de contrôle et non sur la
  recherche de commissions), CR-37/38 (fichier réel assureur — pilotés par l'import).
- **Clôtures des rémunérations complémentaires exposées à l'interface (2026-10-14)** : l'écran des
  exécutions porte désormais trois actions opérateur (`close-development` RCD, `close-portfolio` RCP,
  `close-annual` complément retraite ; §7.2 étape 4.7, CAL-05, CAL-06), chacune servie par un point
  d'accès `/api/ui/v1/calculations/{e}/{m}/close-*` partitionné et testé (JUnit : opérateur 200, hors
  périmètre 404). Elles lèvent le verrou qui empêchait le rejeu de CR-35 : la clôture annuelle est
  pilotable par l'opérateur, plus seulement par le cycle de démonstration.
- **Surcommissions santé rejouées par l'interface (2026-10-14, §13.4)** : RCD palier au premier euro
  (CR-42 → 6 000,00 € sur 120 000 € de production, déclenché par `close-development`) et RCP santé
  (CR-46 → 8 000,00 € sur 200 000 € de primes, conditions de qualité P/C et taux de chute remplies,
  déclenché par `close-portfolio`). Le chargeur `CrScenarioResource` gagne les barèmes marginaux, les
  conditions de qualité, les indicateurs et l'inscription à avenant signé.
- **Partage et commission externe rejoués par l'interface (2026-10-14, §13.4)** : partage avec un
  groupement (CR-49 → 27,00 € au distributeur et 3,00 € au groupement, règle de bénéficiaire 90/10 sur
  une commission linéaire de 30 €) et commission externe (CR-51 → ligne `EXTERNAL` de 17,50 € intégrée
  telle quelle, assiette/taux/montant précalculés), tous deux par `runReal` depuis l'écran des
  exécutions. Le chargeur gagne la règle de bénéficiaire (`PayeeRule`/`PayeeRuleShare`), le groupement et
  les champs précalculés de l'événement externe.
- **Cumul RCD sur deux mois et rétrocession rejoués par l'interface (2026-10-14, §13.4)** : CR-41 (deux
  clôtures de développement successives, 300,00 € en janvier puis 1 050,00 € en février sur un cumul de
  60 000 € — deux périodes semées) et CR-50 (rétrocession `SHARE` de 60 % sur une commission reçue de
  100 € → 60,00 €, par `runReal`).
- **RCP prévoyance au nombre rejouée par l'interface (2026-10-14, §13.4)** : CR-48 (12 adhésions
  nouvelles → palier 2 % d'un barème au nombre, 1 000,00 € sur 50 000 € de primes, par `close-portfolio`).
  Le chargeur gagne le barème au nombre (`countScale`).
- **Lignes à 0 € avec motif rejouées par l'interface (2026-10-14, §13.4)** : CR-43 (condition d'impayés
  à 6 % non remplie → ligne `RCD` de 0,00 € par `close-development`, motif `forfeited` lu en ouvrant le
  détail de la ligne sur la recherche de commissions) et CR-47 (P/C de 96 % cassant la condition de
  portefeuille → ligne `RCP` de 0,00 € par `close-portfolio`, détail de condition `LOSS_RATIO` lu de
  même). La recherche de commissions montre bien les lignes à 0 € (aucun filtre de montant), et le détail
  de ligne (`line-detail`) expose l'explication native stockée (CAL-10, D14). Restent de §13.4 : CR-40
  (production sous le premier palier → aucune ligne), CR-44 (avenant non signé → aucune ligne, anomalie
  de contrôle) et CR-45 (reprise de RCD). CR-40 et CR-44 ne produisent aucune ligne : leur rejeu lira
  l'anomalie sur un écran de contrôle (`ControlResult`), prochain chantier avec les montants attendus
  CR-33/34/36. **Point de UX relevé** : le détail de ligne affiche les clés techniques brutes de
  l'explication (`forfeited`, `rcp`, `conditions`…) et non un libellé français ; CAL-10 demande
  l'explication « en clair ». À traiter hors du rejeu, comme resserrement de CAL-10.
- **Cas sans ligne de §13.4 rejoués par l'interface (2026-10-14)** : CR-40 (production de 15 000 € sous
  le premier palier → aucune ligne `RCD` ; l'état vide de la recherche de commissions le prouve) et CR-44
  (inscription sans avenant signé → aucune ligne et une anomalie `ENR-01` « Avenant non signé » levée par
  la clôture et lue sur l'écran des contrôles `/controles`). Le chargeur gagne la variante d'inscription
  à avenant non signé.
- **Reprise de RCD rejouée par l'interface (2026-10-14, §13.4 close)** : CR-45 (une adhésion portant
  40 € de RCD déjà payé, allocation `SurcommissionAllocation` en période antérieure, résiliée au 8e mois
  → ligne `CLAWBACK` de −40,00 € au `runReal` de la résiliation, lue « Reprise » sur la recherche de
  commissions). Le chargeur gagne la pose d'une ligne `RCD` payée et de son allocation. **§13.4 est
  entièrement rejouée par l'interface (CR-40 à CR-51).** La variante à −20,00 € (15e mois, reprise de
  moitié) reste prouvée au niveau moteur (`SurcommissionEngineTest`).
- **Montants attendus assureur rejoués par l'interface (2026-10-14, §13.3)** : CR-33/34 (rappel de la
  commission reçue, −71,25 € et −158,10 €, par `runReal` sur un plan escompté suspendu) et CR-36
  (sur-commission assureur attendue de 1 008,00 €, par `close-annual`). Tous trois conservent le montant
  sur le contrôle `CAL-13` « Commission reçue différente de l'attendu » (`ControlResult`, règles jumelles
  de sens `INBOUND`, P17) sans produire de ligne ; la valeur est lue dans la colonne « Attendu » de
  l'écran des contrôles `/controles`. Le chargeur gagne une surcharge `version(..., Direction, ...)` pour
  les plans `INBOUND`. **§13.3 est complète sauf CR-37/38 (fichier assureur réel UMR, pilotés par
  l'import).**
- **Séparation des tâches rejouée par l'interface (2026-10-14, §13.5)** : CR-71 (un administrateur ne
  peut approuver sa propre version de règle). Le rejeu a révélé un **vrai défaut** corrigé dans la foulée :
  `SeparationOfDutiesException` n'avait pas de correspondance dans `UiExceptionMappers`, donc le refus
  remontait en 500 au lieu d'un `problem+json` 403 au message français. Ajout d'une correspondance 403
  « Séparation des tâches » ; l'écran des règles affiche désormais le motif du refus, la version restant
  « Brouillon ». Test d'API (`RulesConfigResourceTest`) + e2e (`rules.spec`).
- **Données erronées à l'import rejouées par l'interface (2026-10-14, §13.5)** : CR-67 (un fichier
  d'événements de cinq lignes — prime nulle, numéro de contrat manquant, date future, contrat inconnu —
  intègre 1 ligne, en rejette 3 avec leur motif et met 1 en attente, `1 intégrée(s), 3 rejetée(s),
  1 en attente` sur l'écran des imports). Le chargeur gagne `seedCr67` qui pose le contrat CLEVA visé.
- **Reproductibilité rejouée par l'interface (2026-10-14, §13.5)** : CR-69 (simuler la règle `R-CR01-LIN`
  sur la période de janvier qu'elle a calculée reproduit la ligne au centime — réel 15,00 €, simulé
  15,00 €, écart 0,00 €, 1 ligne rejouée, 0 modifiée — sur l'écran de simulation). Rejeu sans pipeline de
  paiement : la simulation relit les lignes réelles de la période et les compare, sans effet de bord.
- **Seuil de paiement rejoué par le pipeline complet (2026-10-14, §13.5)** : CR-63 (une commission de
  7,50 € sous le seuil de 10,00 € de l'entité n'est pas ordonnancée). **Chemin de rejeu du cycle complet
  établi et prouvé** : `seedCr63` sème la prime, puis le rejeu enchaîne calcul réel (`/calculs`),
  transitions d'état sur l'écran des périodes (`/` : `admin-unep` avance vers « Calculée » puis
  « Contrôlée », `validator-unep` vers « Validée métier » puis « Paiement préparé »), et lecture du lot
  sur l'écran de paiement (`/paiement`) : aucun ordre (`no-order`) et une exclusion « Sous le seuil de
  paiement ». Test d'API bout-en-bout (`CrScenarioResourceTest`, les quatre transitions avec le bon rôle
  puis `/payment/.../lots`) et e2e multi-rôles. Deux utilisateurs UNEP ajoutés aux helpers e2e
  (`admin-unep`, `validator-unep`). Ce chemin réutilisable débloque CR-60/61/62/64 (paiement) et
  CR-65/66 (comptabilité, via l'écran `/comptabilite` après la validation métier).
- **Alignement ordre / bordereau rejoué par le pipeline (2026-10-14, §13.5)** : CR-64 (une commission de
  15,00 € menée jusqu'au lot — un ordre de 15,00 €, total du lot 15,00 € sur l'écran de paiement, et un
  bordereau de même net 15,00 € sur l'écran des bordereaux). Réutilise le chemin du cycle complet
  (CR-63) et vérifie l'invariant PAY-05 (ordre = bordereau ; total du lot = somme). Test d'API bout-en-
  bout + e2e multi-rôles.
- **Vision comptable d'une résiliation rejouée par le pipeline (2026-10-14, §13.5)** : CR-66 (une
  résiliation au 6e mois régularise le prévisionnel de −90 € ; à la validation métier, une écriture
  `FORECAST_ADJUSTMENT` de 90,00 € contre-passe la provision, lue sur `/comptabilite`). Les lignes
  `FORECAST` sont posées comme état d'entrée (`seedCr66`), le pipeline s'arrête à la validation métier
  (pas de paiement nécessaire). Test d'API + e2e multi-rôles. Le chemin de lecture comptable est désormais
  prouvé et réutilisable.
- **Cycle comptable complet rejoué par le pipeline (2026-10-14, §13.5)** : CR-65 (§10.2 — provision de
  prévisionnel de 180 € puis acquisition et payable de 15 € à la validation métier, puis paiement de
  15 €). Le test d'API mène le cycle de bout en bout : lignes `FORECAST 180` et `EARNED 15` (dotée d'un
  code de production) posées en entrée, transitions jusqu'à la validation métier (écritures
  `FORECAST_RECOGNITION` 180, `EARNING` 15, `PAYABLE_RECOGNITION` 15), puis préparation du lot, validation
  Finance et enregistrement du résultat bancaire (écriture `PAYMENT` 15). L'e2e lit les écritures de
  reconnaissance sur `/comptabilite`. **§10.2 entièrement rejoué.**
- **Compensation entre comptes rejouée par le pipeline (2026-10-14, §13.5)** : CR-61 (un code à −200 €
  et un code à +500 € du même distributeur ; après validation métier, la compensation sur l'écran de la
  fiche distributeur `/distributeur` écrit deux lignes `TRANSFER` et laisse 300,00 € payables, lus comme
  un unique ordre de 300,00 € sur l'écran de paiement). Lignes acquises posées en entrée sur deux codes
  de production du même distributeur, distributeur rattaché à l'entité (`DistributorEntity`) pour être
  sélectionnable. **Au passage, correction d'un défaut** : les lignes posées sans `attachment_month`
  faisaient échouer l'écran des lignes de compte (`/accounts/.../lines`) par une `NullPointerException`
  à l'affichage du mois de rattachement ; les lignes semées portent désormais leur mois de rattachement,
  comme les lignes réelles du moteur. Test d'API + e2e multi-rôles.
- **Report du compte débiteur rejoué par le pipeline (2026-10-14, §13.5)** : CR-60 (un solde d'ouverture
  de −1 000 € ; en février une commission de 600 € laisse le compte à −400 €, aucun ordre ; en mars une
  commission de 700 € le ramène à +300 €, un ordre de 300,00 €). Deux périodes menées jusqu'à la
  préparation du paiement, le solde reporté d'un mois sur l'autre ; solde d'ouverture (`AccountEntry`
  `OPENING_BALANCE`) et lignes acquises posés en entrée. Test d'API + e2e multi-rôles.
- **Suspension ORIAS rejouée par le pipeline (2026-10-14, §13.5)** : CR-62 (un distributeur radié de
  l'ORIAS a sa commission de 110 € calculée, mais la préparation du lot de cycle annule son ordre et le
  liste en exclusion « bénéficiaire inactif » ; après réinscription, un lot hors cycle le règle). **Au
  passage, comblement d'un vrai manque** : le statut ORIAS n'était porté par aucun flux — l'import du
  référentiel distributeurs ignorait la colonne `orias_status`, de sorte qu'un distributeur radié ne
  pouvait jamais être réinscrit. L'import lit désormais `orias_status` (défaut : inscrit) ; la
  réinscription se fait par ré-import, comme le ferait la donnée ORIAS réelle. Test d'API + e2e
  multi-rôles (cycle, import de réinscription, lot hors cycle). **§13.5 est entièrement rejouée.**
- **Import mappé du fichier assureur rejoué par l'interface (2026-10-14, §13.3)** : CR-37 (le mapping du
  fichier de production UMR ne garde que les lignes de la mutuelle de l'entité — part Alptis 0,24 €, la
  ligne UMR 0,19 € étant filtrée — et dérive la commission reçue). **Vrai manque comblé** : l'import par
  l'écran (`importFile`) ne résolvait jamais les mappings stockés (`ImportMapping`), alors que le
  commentaire du service affirmait le contraire ; un fichier libre d'assureur (ING-02, IN-06) n'avait donc
  aucun chemin opérateur. Le flux `events` résout désormais le mapping actif de la source (s'il existe) et
  passe par l'import mappé, sinon l'import pivot ; la source `UMR` apparaît dans la liste dès qu'un mapping
  la déclare. Test d'API (compteurs 2 lues / 1 filtrée / 1 intégrée) + e2e (import sur l'écran).
- **Fichier de production UMR rejoué par l'interface (2026-10-15, §13.3 close)** : CR-38 (le fichier de
  juillet 2026 est importé par le mapping UMR, puis le calcul charge les commissions reçues en lignes
  `INSURER` de 449,08 € et 2,46 €, soit 451,54 € — le « montant de commission dû » du bordereau — lues sur
  la recherche de commissions au libellé « Commission reçue »). Le mapping UMR associe désormais la
  garantie au numéro de contrat pour que les événements importés se rattachent et se commissionnent. Test
  d'API (import → calcul → lignes) + e2e (import, calcul, recherche). **Toute la §13 est rejouée par
  l'interface.**
- **Rejeu des cas de recette : terminé.** Les 69 cas hors performance sont rejoués par l'interface
  (CR-01→12, CR-20→28, CR-30→38, CR-40→51, CR-60→71). CR-72 (volume de performance) reste hors périmètre
  du rejeu fonctionnel. **La deuxième des quatre conditions d'arrêt de l'étape 4 est atteinte.**
  **Infrastructure de rejeu livrée (2026-10-14)** : le point d'accès dev `/api/test/cr-scenario?cr=CR-xx`
  sème les données d'un cas et `recette.spec.ts` lance le moteur réel depuis l'écran des exécutions puis
  lit la ligne et son explication sur la recherche de commissions. Rejoués sur l'entité UNEP (sans barème
  initial, donc règle unique, comme les tests moteur) : CR-01 (linéaire), CR-02/03 (précompté an 1 / an 2),
  CR-04 (reprise 1re année −180,00 €), CR-05 (reprise 2e année −150,00 €), CR-06 (pas de reprise > 2 ans),
  CR-07 (sans suite −80,00 €), CR-08 (décès sans reprise). Montants recopiés à la main de la §13 et
  vérifiés par `CrScenarioResourceTest` (moteur) et `recette.spec` (interface). Patron réutilisable :
  étendre le `switch` de `CrScenarioResource` et ajouter un test `@cr:` par cas pour les 60 restants. Les
  autres cas restent renvoyés « au niveau JUnit » par des entrées de `docs/DECISIONS.md` à rectifier.

### Plan d'exécution de l'étape 4 (arrêté le 2026-10-14)

Mesure du manque, comptée à la source : **@cr 2/71** (il en manque 68, hors CR-72 performance),
**@path 8/135** (il en manque 127), **@req 106/135** (29 exigences sans test e2e, dont des exigences
serveur-seules admises : REF-11, REP-06, WKF-03, BRD-10, PAR-11…).

Le rejeu d'un cas de calcul par l'interface (TESTS-E2E §3) suppose une **infrastructure de chargement
de scénario** qui n'existe pas encore : il faut charger le produit, la règle/version, la garantie, le
choix de rémunération, le distributeur, le code de production et l'événement, puis **lancer le moteur
réel depuis l'écran des exécutions** et lire la ligne et son explication sur le compte / l'écran de
détail. Approche retenue : un point d'accès dev `/<…>/api/test/cr-scenario?cr=CR-xx` qui sème ces objets
pour l'entité ALPTIS (en reprenant le montage de `CalculationEngineTest` : `product`, `coverage`,
`linearChoice`/`upfrontChoice`, `version`, `premium`), plus la `DistributorEntity` ACTIVE et le compte
pour que la ligne soit visible à l'écran. Les montants attendus sont recopiés à la main de la §13.
Premier cas pilote : **CR-01** (linéaire 15 % sur 100 € → ligne `LINEAR` 15,00 €), qui établit le patron
réutilisé pour les 67 autres (par familles : CR-02/03 précompté, CR-04..08 reprises, etc.).

Les parcours (@path) se couvrent au niveau 3 : chaque ligne des tableaux §4 de `TESTS-E2E.md` a son test
avec les vérifications de sa colonne. Certains parcours déjà exercés par un test d'écran existant seront
étiquetés quand le test fait réellement les vérifications du parcours (jamais d'étiquette seule).

**Rejeu des cas de recette terminé (2026-10-15)** : les 69 cas hors performance sont rejoués ; place aux
parcours `@path`.

**Parcours `@path` — avancement (début 2026-10-15).** 13 sur ~135 (`ADM-01`, `BOOT-01`, `CR-70`, `DEM-01`,
`DEM-02`, `INT-01`, `INT-04`, `INT-05`, `INT-06`, `INT-07`, `INT-11`, `INT-13`, `PAR-01`, `PAR-03`, `PAR-05`). §4.1 intégration en cours :
INT-01 (dépôt valide : cinq compteurs au journal, **empreinte et auteur exposés sur le détail du lot**,
événement visible avec son statut sur `/evenements`), INT-05 (ré-import refusé sans doublon), INT-06
(deux systèmes source distingués — **colonne « Système source »** ajoutée au journal, §9.9), INT-07
(fichier réduit à l'en-tête). Méthode : n'étiqueter qu'avec les vérifications réelles, en exposant la
donnée à l'écran si besoin. **Point relevé (INT-01)** : l'auteur affiché est le libellé générique
« import » du profil dev, non l'identité de l'opérateur ; l'enregistrer demanderait de propager
l'utilisateur à travers la façade d'import (qui gère ses propres transactions par paquets) — raffinement
différé. INT-04 (mise à l'écart d'un doublon : ré-import d'une même clé source → rejet `DUPLICATE`,
écarté → statut `DISCARDED`, aucun événement en double sur `/evenements`) ajouté. PAR-03 (approbation par
l'auteur refusée / par un second administrateur) étiqueté sur ses deux tests existants (règle + CR-71).
PAR-05 (deux versions même groupe/priorité, périmètres recouvrants → approbation refusée) : le message de
refus **nomme désormais les règles en conflit** (`plan/règle vN`), non plus un simple décompte, et le test
le vérifie. CTL-06 (liste de contrôle de la période : un contrôle coché et son état persisté) étiqueté sur
le test existant de la liste de contrôle. CTL-02 (décision sur une anomalie) **complété** : l'écran des
contrôles offrait seulement « Accepter » ; ajout des actions « Corriger » (FIXED) et « Reporter »
(DEFERRED) et d'un **compteur d'anomalies ouvertes**, et un test décide trois anomalies — acceptée,
corrigée, reportée — vérifie leurs libellés et le décompte des ouvertes (−3). PAI-10 (fichier de
virements) **complété** : le fichier SEPA pain.001 ne portait ni le nombre d'ordres, ni la somme de
contrôle, ni la date d'exécution (champ `executionDate` jamais renseigné) ; ajout de `<NbOfTxs>`,
`<CtrlSum>`, `<ReqdExctnDt>` (date posée à la validation Finance), et le test lit ces champs plus
l'identifiant de bout en bout et le libellé. FDV-11 (simulateur du variable) : le test simule une
production très au-dessus de l'objectif et vérifie que l'atteinte est **plafonnée à 100 %** (déjà
implémenté côté moteur, `.min(1)`). 19 `@path`.

**Audit §4.3–4.6 (2026-10-15).** Restent à compléter avant étiquetage :
- PAI-02 : seulement deux des six motifs d'exclusion exercés (il en faut un par motif : compte bloqué,
  bénéficiaire inactif, solde non positif, sous le seuil, sans coordonnées bancaires, hors fréquence).
- FIN-05 : **étiqueté** (2026-10-15). Le test CR-65, après validation métier, produit l'extraction de la
  période UNEP (non encore clôturée, écritures non transmises) et lit le CSV OUT-02 : en-tête complet
  (§9.11), double partie débit/crédit, mois comptable `2026-01` et montant 180.00 — l'extraction du jeu de
  démonstration de septembre étant vide (déjà transmise). 20 `@path`.
- PAI-05 : **comblé et étiqueté (2026-10-15)**. Le lot de chèques n'avait pas de téléchargement de la
  liste des chèques (seul le virement avait son fichier SEPA) ; ajout d'un point d'accès
  `/payment/lots/{id}/cheque-list` (CSV bénéficiaire/montant/référence) et d'un bouton « Liste des
  chèques » sur l'écran de paiement, symétrique du fichier SEPA. Le test des chèques télécharge et lit la
  liste avant de saisir l'émission et de régler.

**FDV-06 étiqueté (2026-10-15).** Le test du variable collectif annuel règle l'enveloppe en janvier N+1
et vérifie l'enveloppe du poste CAC (550,00 €) sur l'écran de validation. La proratisation par absence
est prouvée au niveau unitaire (`SalesForceServiceTest`, `runAnnualCollective`). 31 `@path`.

**CPT-02 étiqueté (2026-10-15).** Le rejeu CR-61 (compensation : deux lignes de transfert, paiement du
net de 300,00 €) couvre aussi le parcours CPT-02 « Compensation entre deux comptes d'un même
distributeur ». 21 `@path`. CPT-01 (report débiteur sur **trois** périodes) reste à compléter — CR-60
n'en couvre que deux (février/mars).

**CAL-01 étiqueté (2026-10-15).** Le test de l'écran des exécutions vérifie désormais que l'exécution
porte ses compteurs (unités/lignes non nuls) en plus du type « Réel » et du statut « Terminé ». 22
`@path`. La « période verrouillée pendant le calcul » de CAL-01 est un état transitoire d'un calcul
synchrone, non observable en e2e (atomicité de l'endpoint de calcul).

**BRD-09 étiqueté (2026-10-15).** Le test des créances d'escompte vérifie désormais que la synthèse par
convention affiche versé/apuré/créance (montants) et que le détail ouvre une ligne par adhérent
(`discount-position-row`).

**BRD-01 et BRD-05 étiquetés (2026-10-15).** Le test des bordereaux couvre déjà BRD-01 (PDF et tableur
téléchargés — signatures `%PDF`/`PK` — avec rubriques, pied solde reporté/net) ; ajout pour BRD-05 de la
vérification que la version d'origine reste listée et consultable à côté de la rectificative v2.

**BRD-06 et BRD-08 étiquetés (2026-10-15).** BRD-06 (relevé informatif d'un membre payé par son
groupement : sa part) sur le test du relevé membre ; BRD-08 (attestation fiscale annuelle) renforcé pour
vérifier l'exercice (2026) et le total payé (€).

**FLX-01 étiqueté (2026-10-15).** Le test des exports produit chacun des trois exports à la demande de la
§9.12 (lignes de commission, comptes, données des contrôles), vérifie l'inscription au journal avec
auteur (l'opérateur), lignes et total, et le téléchargement du fichier.

**FLX-02 étiqueté (2026-10-15).** L'export Data produit un fichier incrémental par objet (lignes de
commission, comptes), chacun estampillé de la fenêtre `INCR:2026-09-01` — « un fichier par objet,
incrément du jour seulement ». 29 `@path`.

**CAL-05 étiqueté (2026-10-15).** Nouveau test : le rejeu CR-41 crée deux périodes UNEP consécutives
(janvier et février, toutes deux `LOADED`) ; un administrateur métier qui demande le calcul de février
tant que janvier n'est pas validé par le métier est **refusé par le garde-fou du cycle** (« la période
précédente n'est pas encore validée par le métier »), le refus s'affiche sur l'écran des périodes et
février reste `LOADED`. 32 `@path`.

**CAL-02 comblé et étiqueté (2026-10-15).** Manque de fond relevé au rejeu : `runReal` (plein calcul de
période, bouton « Lancer un calcul ») supprimait bien les lignes non validées (D13) mais ne rejouait que
les événements `PENDING` ; les événements déjà calculés passant à `PROCESSED`, **un second calcul sans
rien changer effaçait les lignes sans les recréer** — violation de D8 (« un calcul relancé dix fois donne
dix fois le même résultat »). Le test unitaire `rerun_is_idempotent` masquait le défaut en rouvrant les
événements à la main avant le second passage. Correctif (fonction d'abord) : `runReal` ramène à `PENDING`
les événements de la période (`eligiblePeriod`) restés `PROCESSED`/`NOT_COMMISSIONABLE` avant de rejouer,
à l'image de `runPartial`. Le test unitaire est renforcé (second passage nu, même ligne, même montant) et
un test e2e (rejeu CR-50, deux calculs d'affilée) vérifie qu'il reste **une seule** ligne de rétrocession
à 60,00 €. 33 `@path`.

**CTL-03 étiqueté (2026-10-15).** Nouveau test (écran des contrôles, contexte admin-alptis qui porte
BUSINESS_ADMIN + OPERATOR) : l'administrateur rend bloquant le contrôle de solde débiteur
(`ACCOUNT-DEBTOR`, nouvelle version active), lance les contrôles — l'anomalie est alors **bloquante et
reste ouverte** — puis tente de faire avancer la période de septembre depuis `CALCULATED` ; le garde-fou
du cycle **refuse** (« une anomalie bloquante reste ouverte ») et la période reste `Calculée`. 34 `@path`.

**REP-03 étiqueté (2026-10-15).** Le test de la vision consolidée est renforcé : il charge le jeu de
démonstration complet (cycle des deux entités du groupe) et vérifie que la vue réservée à l'auditeur
affiche **un total par entité** — les lignes `ALPTIS_ASSURANCES` et `UNEP` apparaissent chacune avec son
montant en euros — tandis que le consultant mono-entité en reste écarté (`group-forbidden`). 35 `@path`.

**PAI-01 étiqueté (2026-10-15).** Nouveau test : l'opérateur prépare le lot de cycle et le test vérifie
les deux points du parcours — **un ordre par bénéficiaire** (les libellés bénéficiaire des ordres sont
tous distincts, autant que d'ordres) et **le total du lot égal à la somme de ses ordres** (chacun la
somme des lignes rattachées d'un bénéficiaire, invariant CR-64 réaligné à la préparation). Testids
ajoutés à l'écran de paiement : `lot-total`, `order-amount-*`, `order-payee-*`. 36 `@path`.

**PAI-02 — motif `FREQUENCY` comblé (2026-10-15), étiquetage différé.** Audit du parcours « les six
motifs d'exclusion » : deux des six motifs n'étaient jamais produits. `FREQUENCY` (« périodicité de
paiement non échue », exigé par PAY-02) était un vrai manque — `PayeeRule.paymentFrequency` était importé
et affiché mais **jamais consulté** à la préparation du lot. Correctif (fonction d'abord) : `PaymentService`
exclut désormais un bénéficiaire dont la périodicité n'est pas échue le mois courant (`paymentDue` +
`paymentFrequencyOf`), les sommes restant au compte (§10.5) ; l'ancrage sur le calendrier civil
(trimestre → mars/juin/sept/déc, semestre → juin/déc, an → déc) est consigné dans `docs/DECISIONS.md`.
Le jeu de démonstration n'ayant que des règles `MONTHLY`, le comportement existant est inchangé ;
couverture JaCoCo 100 % tenue par `PaymentServiceTest.frequency_*` (chaque périodicité échue/non échue,
résolution code de production / distributeur / groupement). **`NO_BANK_DETAILS` n'est pas un manque** :
§10 (p. 1211-1212) règle les bénéficiaires sans coordonnées par le lot de chèques du trimestre, non par
une exclusion ; le motif ne vaudrait que pour des coordonnées *invalides*. **`NO_BANK_DETAILS` :
contradiction de spécification tranchée en faveur du lot de chèques (2026-10-15).** Une première tentative
d'exclure comme `NO_BANK_DETAILS` un bénéficiaire aux coordonnées échues a été **retirée** : §10 (p. 1211)
règle tout bénéficiaire sans coordonnées *utilisables* par le lot de chèques du trimestre, et l'exclure
plutôt que le router cassait le flux (compteurs du lot de cycle sensibles à la dérive d'horloge de la
suite e2e partagée). Décision : coordonnées absentes **ou échues** → lot de chèques ; le motif
`NO_BANK_DETAILS` ne vaut que pour des coordonnées structurellement invalides, cas non produit dans le
flux de cycle. **PAI-02 reste donc non étiquetable** tel quel : cinq motifs sont produits dans le lot de
cycle, le sixième (`NO_BANK_DETAILS`) est traité par le routage chèque. À revoir si une validation d'IBAN
est introduite.

**PAI-06 étiqueté (2026-10-15).** Le rejeu CR-62 performe déjà le parcours : un distributeur radié de
l'ORIAS est exclu du lot de cycle (« Bénéficiaire inactif »), puis, après réinscription (ré-import du
référentiel distributeurs avec `orias_status=REGISTERED`), un **lot hors cycle** règle les 110,00 € restés
bloqués, sur la même période de janvier — sans attendre le mois suivant. Étiquette `@path:PAI-06` posée
sur le test existant. 37 `@path`.

**FIN-07 étiqueté (2026-10-15).** Le test du cut-off est renforcé : il lit le cut-off à ses **quatre
mailles** (garantie, distributeur, produit, entité) et vérifie que le **total acquis est identique** aux
quatre — seul le regroupement change, pas le montant. Étiquette `@path:FIN-07`. 38 `@path`.

**CTL-05 étiqueté (2026-10-15).** Nouveau test (écran des contrôles, admin-alptis) : le contrôle de solde
débiteur se déclenche une première fois, puis est **édité** (gravité) et **désactivé** — ce qui écrit une
**nouvelle version** (version-row-2) et met le catalogue à « Actif = Non » — et **au calcul suivant** il
ne se déclenche plus (l'exécution n'itère que les contrôles actifs). Couvre gravité + désactivation +
version + effet au calcul suivant. 39 `@path`.

**AJU-04 et AJU-07 étiquetés (2026-10-15).** Deux tests existants de l'écran des ajustements performent
déjà leur parcours : AJU-04 (contrepassation automatique) — un ajustement à contrepassation approuvé crée
une **ligne « Contrepassation » dans la période suivante** (octobre), vérifiée après changement de mois ;
AJU-07 (import en masse) — l'import crée les lignes valides (une importée, la ligne au code inconnu
ignorée) **toutes au statut « Soumis » (à approuver)**. Étiquettes posées. 41 `@path`.

**FDV-02 étiqueté (2026-10-15).** Le test de l'objectif collectif versionné crée une version 1, puis
re-soumet la même clé pour créer une **version 2** ; assertion ajoutée que la **version 1 reste visible**
(ajout seul). Couvre §4 FDV-02 « Objectifs versionnés : nouvelle version ; ancienne conservée ». 42
`@path`. (FDV-04, pondération par marché à 13 500 €, non étiqueté : le test principal porte `@req:FDV-04`
mais ne vérifie pas le montant pondéré ; à renforcer.)

**REC-01 comblé et étiqueté (2026-10-15).** Le parcours réclamation demandait « ouverture, lien aux
lignes contestées, régularisation, clôture ; état à chaque étape ; ajustement lié ». Manquaient l'étape
intermédiaire et le lien à l'ajustement. Ajouts (fonction d'abord) : `AdjustmentService.takeClaim`
(transition `OPEN → IN_PROGRESS`, refusée hors `OPEN`) et point d'accès `POST /claims/{id}/take` ;
`claimDetail` expose désormais les **ajustements liés** — ceux dont la ligne corrigée (`correctedLine`,
REG-02) est une ligne contestée de la réclamation — sans inventer de clé étrangère absente de la
spécification. L'écran porte un bouton « Prendre en charge » (visible à l'état `OPEN`) et une section
« Régularisations liées ». Le test e2e ouvre la réclamation de démonstration, passe un ajustement
`CORRECTION` sur sa ligne contestée, la prend en charge (Ouverte → En cours), voit la régularisation liée,
puis la clôt (En cours → Close) — les trois états et l'ajustement lié sont vérifiés. Couverture unitaire
`AdjustmentServiceTest.claim_take_and_linked_adjustments` (toutes branches de `takeClaim`, détail avec et
sans lignes). 43 `@path`.

**SIM-03 étiqueté (2026-10-15).** Le test de simulation rejoue la version **active** (SANTE_EQUILIBRE
_LINEAR v1) de la période calculée : renforcé pour vérifier que l'**écart est 0,00 €** et qu'**aucune
ligne n'est modifiée** — le rejeu avec le paramétrage d'origine reproduit les lignes réelles au centime
(SIM-03, reproductibilité D8/CR-69 vue par l'interface). 44 `@path`.

**AJU-03 comblé et étiqueté (2026-10-15).** Le seuil d'approbation des ajustements (REG-03) était lu par
`AdjustmentService` mais **réglable nulle part** (ancien `EntityParameter ADJUSTMENT_APPROVAL_THRESHOLD`
jamais exposé). Correctif (fonction d'abord) : le seuil devient une **colonne** `adjustmentApproval
Threshold` de `LegalEntity` (cohérent avec les autres paramètres), exposée sur l'écran « Entités et
paramètres » (champ `field-approval-threshold`, vue/màj/audit `EntityAdminService`). Deuxième manque : le
bouton « Approuver » n'était visible que pour `OPERATOR`/`BUSINESS_ADMIN` (`canOperateCycle`) alors que le
**valideur métier** doit approuver au-dessus du seuil — ajout de `canApproveAdjustment`
(OPERATOR/BUSINESS_ADMIN/BUSINESS_VALIDATOR) pour la visibilité du bouton. Le test e2e AJU-03 règle le
seuil à 50 €, soumet un ajustement de 100 €, et vérifie les trois cas : **auteur → refus**, **second
non-valideur au-dessus du seuil → refus**, **valideur métier → appliqué**. Couverture : unitaire (seuil
via colonne, EntitiesResource lit/écrit le seuil), vitest (`canApproveAdjustment`). 45 `@path`.

**FDV-04 comblé et étiqueté (2026-10-15).** La pondération par marché (CR-87 : 10 000 € particulier +
5 000 € professionnel → 13 500 €) était calculée (`SalesForceService.weightedProduction`) et portée par
`SimulationResult.weightedProduction`, mais **non affichée**. Ajout (fonction d'abord) : la ligne
« Production pondérée par marché » (`sim-weighted`) sur le simulateur commercial. Le test e2e crée un
collaborateur dédié, importe ses mesures de septembre (10 000 particulier + 5 000 professionnel via le
flux `employee_measures`) puis simule sans hypothèse (production mesurée) → **13 500,00 €** affichés. 46
`@path`.

**Niveau 4 — matrice des droits amorcée (2026-10-15).** Nouveau `rights.spec.ts` : un tableau en code
(d'après la spécification §3) donne, par action, les rôles habilités ; un **test généré** parcourt le
tableau et vérifie, pour chaque couple rôle-action, que l'action aboutit (≠ 403) pour un rôle habilité et
est **refusée (403)** pour les autres — le `@RolesAllowed` du serveur étant la cible, un identifiant
bidon fait jouer l'autorisation avant toute logique métier (indépendant de l'état). Premier lot : **10
actions × 9 rôles = 90 couples** (purge → IT_ADMIN ; calcul/contrôles → OPERATOR/BUSINESS_ADMIN ;
approbation d'ajustement → +BUSINESS_VALIDATOR ; validation lot → FINANCE_CONTROLLER ; validation métier
→ BUSINESS_VALIDATOR ; variable → SALES_MANAGER/BUSINESS_ADMIN ; etc.). **Étendu le même jour à 21
actions × 9 rôles = 189 couples** (compensation, déblocage de compte, préparation/hors-cycle de lot,
correction de rejet, approbation de règle, régularisation annuelle, export paie FDV, rectification de
bordereau, correspondance de rôles → IT_ADMIN, correspondance de mapping → BUSINESS_ADMIN). **Robustesse
corrigée au passage** : `RoleMappingAdminService.parseRole` levait une `NullPointerException` (HTTP 500)
sur un rôle absent ; il rejette désormais proprement en `ValidationException` (HTTP 400), couvert par
`RoleMappingsResourceTest.a_mapping_without_a_role_is_rejected`. **Troisième extension (2026-10-15) :
38 actions × 9 rôles = 342 couples** (ajout des clôtures développement/portefeuille/annuelle, recalcul
partiel, variables FDV trimestriel/collectif/animateurs/vente directe/résiliation, plan et collaborateur
FDV, émission chèque, résultat d'ordre, blocage compte/ligne, écartement de rejet). Le recalcul partiel
exige ses paramètres de périmètre (`type`/`value`) sous peine de `NullPointerException` ; le test les
fournit (gap de robustesse mineur relevé, non bloquant pour la matrice). Couvre la quasi-totalité des
actions mutantes ; reste à étendre aux dernières avant l'étiquetage complet du couple rôle-action
(condition de fin d'étape 4).

**Deux manques relevés à l'audit des parcours « six motifs » / indicateurs (2026-10-15).**
- **INT-02 comblé et étiqueté (2026-10-15).** Les six codes de rejet (§8.2) existent tous dans
  `RejectionReasonCode` ; l'import d'événements en produisait cinq (`TYPE`, `MISSING_FIELD`, `AMOUNT`,
  `FUTURE_DATE`, `DUPLICATE`) mais jamais `STRUCTURE` : une ligne au nombre de colonnes **supérieur** à
  l'en-tête était tronquée silencieusement. Correctif (fonction d'abord, ING-04 « erreur de structure ») :
  `CsvFile` marque une ligne **plus longue** que l'en-tête (`malformed(index)`) ; `ImportJournal`
  (imports par rejecteur) et l'import d'événements (boucle propre, via une liste `malformed` alignée aux
  groupes) rejettent une telle ligne en `STRUCTURE` **avant** la validation métier. Une ligne plus courte
  reste complétée par des vides (colonnes optionnelles → `MISSING_FIELD` si un champ requis manque), donc
  aucun import existant n'est cassé. Test e2e : un fichier d'événements à une ligne fautive par motif →
  **six rejets, chacun avec son code et sa ligne brute**, la ligne valide intégrée. Couverture unitaire
  `CsvFileTest.flags_a_row_with_more_fields`, `EventImportServiceTest` et `EmployeeImportServiceTest`
  (chemin `ImportJournal`). 47 `@path`.
- **INT-23 — indicateurs « calculés en interne » non matérialisés.** Les indicateurs de qualité sont
  *reçus* (import) ou semés ; le moteur *lit* des valeurs pour les conditions de versement
  (`indicatorValue`) mais n'écrit aucun `QualityIndicator` calculé en interne. Le test de l'écran est
  **renforcé** (valeurs par distributeur `P13897` et par période `2026-01-31`, en pourcentage — première
  moitié d'INT-23), mais l'étiquette `@path:INT-23` attend la matérialisation des indicateurs calculés.

**AJU-02 étiqueté (2026-10-15).** Nouveau test : un ajustement sans motif est **refusé** (« un motif est exigé »), puis un ajustement avec motif, **pièce jointe** (`GED-2026-09-77`, consultable à la liste) et **lien à la ligne corrigée** (identifiant de ligne réel lu depuis la réclamation de démonstration, affiché « #id » à la colonne « Ligne corrigée ») est accepté. 48 `@path`.

**Manques relevés par le rejeu des parcours (à combler, datés 2026-10-15)** — le rejeu niveau 3 révèle,
comme attendu, des mécanismes déclarés complets mais partiels :
- INT-01 : l'auteur du lot est le libellé générique « import », pas l'opérateur (propager l'utilisateur
  à la façade d'import).
- INT-03 : « Corriger et relancer » marque le rejet `CORRECTED` et trace l'audit, mais **ne relance pas
  la ligne** ni ne met à jour les compteurs du lot (pas de reprocessing).
- INT-08 : les flux attendus n'ont que « Reçu »/« Manquant » ; **pas d'état « en retard » selon
  l'horloge**.
- INT-10 / INT-22 : un événement en attente (référence/code externe inconnu) **n'est pas débloqué** à
  l'arrivée du référentiel ou de la correspondance (pas de release/reprocess des `ON_HOLD`).
Ces manques seront comblés avec leur parcours (fonction d'abord, puis étiquette), pas contournés.

## Simplifications à reprendre (relevé de docs/DECISIONS.md)

Recensement des décisions qui reportent, approximent, réduisent ou contournent une
règle (à traiter en étape 2, puis à rectifier dans `docs/DECISIONS.md` par une
ligne datée sans effacer l'ancienne) :

1. Rejeu E2E des cas de recette du moteur remplacé par « acceptation JUnit » (entrées
   des 2026-10-09/10, étapes 6/7/8/9/10/12/14) → CR-60/61/63, CR-62/64/85/86, CR-69/72,
   CR-87/88 et les groupes escompte/RCD/PER.
2. `ADVANCE_WRITE_OFF` et `INSURER_RECEIVABLE` jamais produites (2026-10-09, §10.2).
   **`ADVANCE_WRITE_OFF` produite** à la validation métier (2026-10-11, CR-78).
   **`INSURER_RECEIVABLE` produite** (2026-10-13) : une ligne `INSURER` reçue donne, à la validation
   métier, l'écriture créance assureur (467100) contre produit de commission (706000) et n'entre ni
   dans un payable ni dans un paiement (§6.10, FIN-01), prouvé par
   `AccountingServiceTest.insurer_line_books_a_receivable`.
3. Contournement du défaut d'escompte dans le jeu de démonstration : garantie dédiée
   sans événement (2026-10-10, DONNEES-DEMO §5). **Correctif moteur fait** (2026-10-11,
   `openAdvanceIfEligible` + apurement multi-mois). **Contournement retiré** (2026-10-11) :
   la garantie d'escompte du jeu porte une prime mensuelle, le moteur ouvre la position en
   juin et l'apure d'un mois sur l'autre jusqu'au solde ; prouvé par
   `DemoDataServiceTest.follows_a_real_multi_month_discount`.
4. Épargne retraite : règle 1,20 % année 6 et `seniorityOrigin` non livrés (2026-10-09, §6.6).
   **Livrés** (2026-10-11, règle linéaire post-escompte à condition d'ancienneté).
5. Délai de versement de l'escompte retraite approximé (2026-10-09, §6.6).
   **Corrigé** (2026-10-11) : escompte dû dans la période qui suit le premier versement
   programmé encaissé ; repêchage mois sur mois par `savingsCandidates` ; cas de recette
   d'escompte retraite rejoués dans la période suivante.
6. `DEFER` (§6.7) non livré, seul `FORFEIT` (2026-10-09). **`DEFER` livré** (2026-10-11,
   évaluation mensuelle de la condition de versement, rattrapage du mois reporté).
7. FIN-06, FIN-08, REP-08 (marge et rapprochements) reportés/approchés (2026-10-09).
8. Export Data complet au lieu d'incrémental (2026-10-09, REP-04/05).
   **Levée** (2026-10-13) : l'export Data accepte une date « depuis » et ne renvoie alors que les objets
   créés ou modifiés à partir de cette date (`coalesce(updatedAt, createdAt)`, REP-05) ; prouvé par
   `ExportServiceTest.data_export_is_incremental_since_a_window`. L'export complet reste disponible pour
   l'initialisation.
9. CR-72 mesuré par extrapolation depuis 60 bénéficiaires (2026-10-09, §11).
10. FDV-08/09/10/12 reportés (2026-10-09, §8.14) ; FDV-06 (régularisation annuelle), FDV-07
    (trimestriel qualitatif) et FDV-16 (simulateur) levés depuis.
11. `ConfirmDialog` construit mais non raccordé aux actions irréversibles (2026-10-10, ERGONOMIE §3).
12. Déclaration fausse « tous les écrans de l'inventaire existent comme vues réelles »
    (2026-10-09, §4) — rectifiée par le présent état des lieux.
13. Une seule instance d'application partagée par la suite E2E au lieu d'une par
    processus (2026-10-07, TESTS-E2E §1).
14. Rendu bordereau « texte + empreinte » au lieu de PDF/tableur réel (2026-10-10, §10.6).
    **Levée** (2026-10-12) : PDF réel écrit à la main (format objets + xref) et tableur xlsx réel
    assemblé en ZIP OOXML, sans dépendance externe ni lecture d'horloge ; la publication archive
    l'empreinte SHA-256 du PDF réel et la référence du tableur (`StatementDocuments`).
15. Montants FDV « à définir » figés en constantes documentées plutôt qu'en paramètres
    du plan renseignés dans le jeu de démonstration (2026-10-09, §8.14).

## Garde-fou de traçabilité (état)

`src/test/e2e/scripts/traceability.mjs` construit ses listes attendues à partir d'un
objet **codé en dur** (`REQUIREMENTS_BY_STEP`) et ne vérifie la présence d'une
étiquette que **globalement** (trouvée n'importe où, pas rattachée à une route). Il ne
lit ni l'inventaire de LANCEMENT §4, ni la section 8, ni la section 13, ni le routeur.
À réécrire (étape 3) pour qu'il tire ses listes des sources et échoue sur toute ligne
`absent`/`partiel` de ce fichier.

### Plan d'exécution de l'étape 3 (arrêté le 2026-10-14)

Sources et format repérés pour la réécriture :

- **Écrans attendus** : table d'inventaire de `LANCEMENT.md` §4 (`| Écran | Exigences | Maquette |
  Étape |`, 73 lignes). Le lien ligne d'inventaire → route → étiquette `@screen` se déclare dans un
  **fichier versionné unique** (à créer, p.ex. `src/test/e2e/screen-routes.json`), dérivé du Tableau 1
  de ce fichier (colonne « Route / Vue ») ; le garde-fou échoue si une ligne n'a pas d'entrée, si sa
  route est absente du routeur (`src/main/webui/src/router/index.ts`), ou si son `@screen` n'a pas de
  test qui s'y rend et vérifie un contenu métier.
- **Exigences attendues** : identifiants de la §8 de la spécification. Attention : certaines exigences
  sont **serveur-seules** (pas d'écran : REF-11, REP-06, WKF-03, BRD-10, PAR-11…) ; le garde-fou doit
  les admettre comme prouvées par test unitaire, via une liste déclarée et justifiée (ce n'est pas une
  liste d'exceptions au sens interdit : c'est la distinction écran / hors-écran que porte déjà
  l'inventaire).
- **Cas de recette attendus** : CR-01..CR-71 de la §13 (hors CR-72, performance) ; un `@cr` par cas.
- **Parcours attendus** : identifiants des tableaux §4 de `TESTS-E2E.md` (135) ; un `@path` par parcours.
- **Genres d'écran** : §2 de `TESTS-E2E.md` (points obligatoires par genre).
- **Routes réelles** : routeur Vue ; **tests réels** : fichiers `*.spec.ts`.

Le `REQUIREMENTS_BY_STEP` codé en dur et le jalonnement par `PLAN.md` sont supprimés au profit de ces
dérivations. **Conséquence assumée** : une fois réécrit, le garde-fou **échoue** tant que l'étape 4
n'est pas finie (68 `@cr` et 127 `@path` manquants) — c'est l'état honnête qui remplace le faux vert
actuel. La construction **serveur** (`mvn verify -P '!e2e'`) reste verte ; seul le but `e2e-traceability`
est rouge jusqu'à l'achèvement de l'étape 4. L'étape 3 sera donc menée **en même temps que** l'étape 4
(ou juste avant son achèvement) pour converger vers le vert, plutôt que de laisser le but rouge des
dizaines de passes.
