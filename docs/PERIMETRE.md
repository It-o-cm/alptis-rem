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

## Décompte (premier passage)

- Écrans de l'inventaire (73) : **37 `complet`, 24 `partiel`, 12 `absent`**.
- Exigences de la section 8 (135) : **≈ 79 `complet`, ≈ 41 `partiel`, ≈ 15 `absent`** (à resserrer).
- Cas de recette rejoués par l'interface : **2 sur 70** (`CR-68`, `CR-70`) ; **les 71 cas
  existent désormais côté JUnit** (CR-74, CR-80 et CR-81 réalisés et testés le 2026-10-11).
- Parcours métier `@path:` : **8** ; couples rôle-action : **21**.
- `PlaceholderView` : présent, plus référencé par aucune route vivante (à supprimer, étape 2).

Le travail n'est donc pas terminé : il reste 36 lignes d'inventaire non `complet`,
3 cas de recette inexistants, 68 cas non rejoués par l'interface, et l'essentiel
des parcours et couples rôle-action à écrire.

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
| 17 | Résultat bancaire : ordres réglés, rejets, saisie de l'émission des chèques | PAY-04, PAY-06 | `payment` / PaymentView | POST orders/{id}/result | payment.spec | partiel — saisie de l'émission des chèques (PAY-04) absente |
| 18 | Bordereaux de la période : liste, aperçu, publication, rectificatif, relevé informatif | BRD-01→07, BRD-11 | `statements` / BordereauxView | GET statements/detail/spreadsheet, POST publish/rectify | bordereaux.spec | partiel — relevé informatif du membre (BRD-11) absent ; rendu « texte + empreinte » au lieu de PDF/tableur réel |

### Distributeurs et contrats

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 19 | Recherche de distributeurs | REF-02, REP-03 | `distributorSearch` / DistributorsView | GET /distributors?q= | distributors.spec | complet |
| 20 | Fiche distributeur : identité, hiérarchie, statut, accords, inscriptions, comptes, indicateurs, historique | REF-10, CPT-01, CPT-06 | `distributorSearch` + `distributor` / DistributorsView + DistributorAccountView | GET /distributors/{code}, GET /accounts/... | distributors.spec, distributor-account.spec | partiel — fiche éclatée sur deux écrans ; accords, inscriptions, indicateurs et historique non réunis dans une fiche |
| 21 | Vue d'un groupement et de ses membres | CPT-02, REF-03 | — | — | — | absent |
| 22 | Blocage d'un compte ou d'une ligne, avec motif et date de levée | CPT-05, REF-08 | `distributor` / DistributorAccountView | POST /accounts/{id}/block, unblock | distributor-account.spec | partiel — blocage au niveau compte seulement (pas de ligne) ; motif et date de levée à ajouter |
| 23 | Détail d'une ligne de commission et de son explication | CAL-10, FIN-07 | — | — | — | absent |
| 24 | Recherche de lignes de commission : payé à qui, quand, pourquoi | REP-02, REP-03 | `commissionSearch` / CommissionSearchView | GET /reporting/{e}/search, export | reporting.spec | complet |
| 25 | Hiérarchie et codes de production, avec leur historique | REF-03 | `distributorSearch` / DistributorsView | GET /distributors/{code} | distributors.spec | partiel — codes de production affichés ; historique de la hiérarchie absent |
| 26 | Coordonnées bancaires et changements depuis le dernier paiement | REF-05 | `bankAccounts` / BankAccountsView | GET /bank-accounts | referentials.spec | partiel — signalement des changements intervenus depuis le dernier paiement absent |
| 27 | Règles de versement : bénéficiaire, parts, périodicité | REF-06 | `payeeRules` / PayeeRulesView | GET /payee-rules | payee-rules.spec | complet |
| 28 | Indicateurs de qualité par distributeur et par période | REF-07 | `qualityIndicators` / QualityIndicatorsView | GET /quality-indicators | referentials.spec | complet |
| 29 | Cessions de portefeuille et ordres de remplacement | REF-09 | `transfers` / TransfersView | GET /portfolio-transfers, /replacement-orders | transfers.spec | complet |
| 30 | Comptes débiteurs sans portefeuille actif et échéanciers d'apurement | CPT-07 | `distributor` / DistributorAccountView | GET /accounts/{e}/debtors | distributor-account.spec | partiel — échéanciers d'apurement (CAL-14) absents |
| 31 | Conventions d'escompte : liste | PAR-09 | `advance` / AdvanceAgreementsView | GET /advance-agreements | advance-agreements.spec | complet |
| 32 | Convention d'escompte : paramètres, compteurs, garanties escomptées, simulation de rupture | PAR-09, CPT-08, 6.4 | `advance` / AdvanceAgreementsView | GET/POST /advance-agreements | advance-agreements.spec | partiel — pas de fiche convention : compteurs, garanties escomptées et simulation de rupture absents |
| 33 | Créances d'escompte par convention et par distributeur | CPT-08, BRD-09 | — | — | — | absent |
| 34 | Recherche de contrats et de garanties | 5.2 | `contractSearch` / ContractsView | GET /contracts?q= | contracts.spec | complet |
| 35 | Fiche d'une adhésion et de ses garanties : affectations, choix de rémunération, événements, lignes, position d'escompte | 5.2, 5.5 | — | (ContractsView montre les garanties seules) | contracts.spec | partiel — pas de fiche d'adhésion (affectations, choix de rémunération, événements, lignes, position d'escompte) |

### Règles

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 36 | Plans de rémunération : liste, fiche, duplication annuelle | PAR-01, PAR-12 | `rules` / RulesView | GET/POST /plans, POST duplicate | rules.spec | complet |
| 37 | Règles et versions d'un plan : liste, statuts, conflits détectés | PAR-03→05 | `rules` / RulesView | GET/POST rules, versions | rules.spec | complet |
| 38 | Paramétrage d'une version de règle, approbation par un second administrateur | PAR-01→08, PAR-14 | `rules` / RulesView | POST versions, approve, plausibility | rules.spec | partiel — le formulaire de version ne couvre pas encore barèmes à paliers (PAR-06), conditions (PAR-07) et politique de reprise (PAR-08) par les schémas de famille |
| 39 | Familles de calcul : un formulaire par famille | PAR-02 | `families` / FamiliesView | GET /families, /{f}/schema | families.spec | complet |
| 40 | Accords et inscriptions aux règles réservées, à l'unité et en masse | PAR-10 | `enrollments` / EnrollmentsView | GET/POST agreements, enrollments | enrollments.spec | complet |
| 41 | Simulation : lancement, table de substitution, résultat, historique | PAR-13 | `simulation` / SimulationView | POST /simulation/{e}/{m} | simulation.spec | partiel — historique des simulations absent |
| 42 | Export et import du paramétrage | PAR-15 | `paramTransfer` / ParameterTransferView | GET export, POST import | parameter-transfer.spec | complet |

### Référentiels et flux

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 43 | Entités et leurs paramètres : calendrier, seuils, tolérances | REF-01, ADM-05 | `adminEntities` / EntityParamsView | GET/PUT /entities | entity-params.spec | complet |
| 44 | Assureurs, gammes, produits et options | REF-01 | `products` / ProductsView | GET /products | referentials.spec | partiel — seuls les produits sont listés ; assureurs, gammes et options non gérés |
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
| 53 | Recherche d'écritures | FIN-01, FIN-05, FIN-07 | `accounting` / AccountingView | GET /accounting/{e}/{m}/entries | accounting.spec | partiel — consultation par entité/mois seulement ; pas de recherche transverse d'écritures ni de chaîne flux→bordereau→ligne→écriture (FIN-07) |
| 54 | Extraction comptable du mois et clôture | FIN-03 | `accounting` / AccountingView | POST /accounting/{e}/{m}/extract | accounting.spec | complet |
| 55 | État de cut-off | FIN-04, FIN-09 | `cutoff` / CutoffView | GET /accounting/{e}/cutoff | cutoff.spec | complet |
| 56 | Rapprochement des commissions acquises et payées | FIN-06 | — | — | — | absent |
| 57 | Commissions reçues des assureurs, attendu et marge | FIN-08, REP-08 | `margin` / MarginView | GET /reporting/{e}/margin | reporting.spec | partiel — marge présente ; rapprochement attendu/reçu des assureurs (FIN-08) absent |

### Documents, restitutions, administration

| # | Écran | Exig. | Route / Vue | Points d'accès | Test E2E | État |
| --- | --- | --- | --- | --- | --- | --- |
| 58 | Modèles de documents | BRD-05 | — | — | — | absent |
| 59 | Attestations fiscales annuelles | BRD-08 | — | — | — | absent |
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
| 67 | Collaborateurs | FDV-01 | `fdvPlan` / FdvPlanView | GET /fdv/{e}/collaborators | fdv.spec | partiel — liste en lecture seule intégrée au plan ; pas d'écran de gestion des collaborateurs |
| 68 | Mesures reçues : absences, production, activité, marge | FDV-02 | — | — | — | absent |
| 69 | Objectifs individuels et collectifs, versionnés | FDV-03 | `fdvPlan` / FdvPlanView | GET /fdv/{e}/objectives | fdv.spec | partiel — objectifs en lecture seule ; versionnement et objectifs collectifs non exposés |
| 70 | Plan de variable par poste | FDV-04, FDV-09→12 | `fdvPlan` / FdvPlanView | GET /fdv/{e}/plans, POST run | fdv.spec | partiel — barèmes animateurs/responsables/vente directe (FDV-09/10) et réduction sur résiliation (FDV-12) absents |
| 71 | Validation mensuelle par le responsable | FDV-13 | `fdvValidation` / FdvValidationView | GET results, POST validate/adjust | fdv.spec | complet |
| 72 | Mon variable, mes objectifs, mon historique, simulateur | FDV-15, FDV-16 | `fdvCommercial` / FdvCommercialView | GET /fdv/{e}/my-variable/{id} | fdv.spec | partiel — simulateur (FDV-16) absent |
| 73 | Synthèse des variables à payer et export paie | FDV-14 | `fdvValidation` / FdvValidationView | POST /fdv/{e}/{m}/pay-export | fdv.spec | partiel — export paie présent ; écran de synthèse des variables à payer par personne absent |

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
| REF-01 | EntityAdminService, ReferentialQueryService | #43, #44 | entity-params, referentials | partiel — assureurs, gammes, options non gérés par écran |
| REF-02 | ReferentialQueryService, DistributorsResource | #19 | distributors | complet |
| REF-03 | ReferentialQueryService | #25 | distributors | partiel — historique de hiérarchie absent |
| REF-04 | ReferentialQueryService | #45 | external-codes | complet |
| REF-05 | ReferentialQueryService | #26 | referentials | partiel — changements depuis dernier paiement non signalés |
| REF-06 | ReferentialQueryService | #27 | payee-rules | complet |
| REF-07 | AccountQualityImporter, ReferentialQueryService | #28 | referentials | complet |
| REF-08 | AccountService | #22 | — | partiel — application datée des changements de statut à vérifier |
| REF-09 | TransferImportService, CalculationEngine | #29 | transfers | partiel — cessions de portefeuille désormais appliquées par le moteur (réaffectation + disposition d'escompte, CR-80) ; application des ordres de remplacement (`ReplacementOrder`) encore absente |
| REF-10 | DistributorsResource, AccountService | #20 | distributors | partiel — fiche incomplète (accords, inscriptions, indicateurs, historique) |
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
| ING-09 | EXTERNAL family, PayeeExternalImporter | #57 | — | partiel — attendu des assureurs non porté comme donnée |
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
| PAR-06 | TieredFamily, BaremeInitialService | #38 | — | partiel — édition des barèmes à paliers par écran à confirmer |
| PAR-07 | RuleCondition, CalculationEngine | #38 | — | complet |
| PAR-08 | RuleConfigService, CalculationEngine | #38 | — | complet — politique de reprise + mode `DEFER`/`FORFEIT` de la condition de versement (§6.7) désormais honorés par le moteur |
| PAR-09 | AgreementService | #31, #32 | advance-agreements | partiel — fiche convention (compteurs, garanties, simulation de rupture) absente |
| PAR-10 | AgreementService | #40 | enrollments | complet |
| PAR-11 | — | — | — | partiel — génération d'avenant absente ; envoi en signature électronique hors périmètre dev (LANCEMENT §6) |
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
| CAL-10 | CalculationEngine (explication) | #23 | cr* JUnit | partiel — explication produite ; écran « détail d'une ligne » absent |
| CAL-11 | CalculationEngine, SimulationService | #41 | cr69 JUnit | complet |
| CAL-12 | CalculationEngine (recalcul partiel) | #9 | calculations, JUnit | complet — recalcul partiel par distributeur, produit ou règle depuis l'écran |
| CAL-13 | CalculationEngine | #57 | — | partiel — attendu assureur et marge partiellement réalisés (FIN-08) |
| CAL-14 | AdjustmentService | #30 | — | partiel — avance à échéancier (échéancier d'apurement) absente |

### 8.5 Ajustements et réclamations (REG)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| REG-01 | AdjustmentService | #12 | adjustments | complet |
| REG-02 | AdjustmentService | #12 | adjustments | partiel — pièce jointe et lien à la ligne corrigée à confirmer |
| REG-03 | AdjustmentService (double signature, seuil) | #12 | adjustments | complet |
| REG-04 | AdjustmentImportService | #12 | adjustments | complet — import en masse d'ajustements depuis un fichier pivot |
| REG-05 | AdjustmentService (auto-reverse) | #12 | adjustments | complet — option de contrepassation automatique créant la reprise au mois suivant (M+1) |
| REG-06 | AdjustmentService (claims), ClaimsResource | #13 | claims | complet — enregistrement, lignes contestées et suivi jusqu'à la régularisation |

### 8.6 Contrôles (CTL)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| CTL-01 | ControlCatalogService, ControlService (edit) | #11 | controls | complet — catalogue éditable par le métier (gravité, activation, paramètres) |
| CTL-02 | ControlService | #10 | controls | partiel — couverture de toutes les familles de contrôle à vérifier |
| CTL-03 | ControlService (checklist) | #10 | controls | complet — liste de contrôle par période, à cocher, tracée |
| CTL-04 | ControlService (edit → version), CycleQueryService (versions) | #11 | controls | complet — versionnement des contrôles (nouvelle version à chaque édition, historique consultable) |
| CTL-05 | ControlService (décision tracée) | #10 | controls | complet |
| CTL-06 | ExportService | #49 | — | partiel — export des données de contrôle N2/N3 à confirmer |

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
| CPT-02 | AccountService | #20, #21 | distributor-account | partiel — vue par groupement absente |
| CPT-03 | AccountService (report débiteur) | #30 | cr60 JUnit | complet |
| CPT-04 | AccountService (compensation) | #22 | distributor-account | complet |
| CPT-05 | AccountService (blocage) | #22 | distributor-account | partiel — blocage de ligne, motif et date de levée absents |
| CPT-06 | AccountService | #20, #24 | — | complet |
| CPT-07 | AccountService (débiteurs) | #30 | distributor-account | partiel — échéancier d'apurement absent |
| CPT-08 | CalculationEngine (créance d'escompte) | #33 | — | partiel — écran des créances d'escompte absent |

### 8.9 Paiement (PAY)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| PAY-01 | PaymentService | #15 | payment | complet |
| PAY-02 | PaymentService (exclusions) | #15 | payment | complet |
| PAY-03 | PaymentService (SEPA pain.001) | #15 | payment | complet |
| PAY-04 | PaymentService | #17 | payment | partiel — saisie de l'émission des chèques absente |
| PAY-05 | PaymentService (alignement) | #15 | cr64 JUnit | complet |
| PAY-06 | PaymentService (résultat bancaire) | #17 | payment | complet |
| PAY-07 | PaymentService (lot hors cycle) | #16 | payment | complet |
| PAY-08 | PaymentService | #15 | — | partiel — un ordre par entité débitrice à vérifier |

### 8.10 Bordereaux et documents (BRD)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| BRD-01 | StatementService | #18 | bordereaux | partiel — rendu « texte + empreinte » au lieu d'un PDF/tableur réel |
| BRD-02 | StatementService | #18 | bordereaux | partiel — contenu §9.5 à vérifier intégralement |
| BRD-03 | StatementService | #18 | bordereaux | complet |
| BRD-04 | StatementService | #18 | — | partiel — déclinaison par assureur/produit et multi-entités à vérifier |
| BRD-05 | — | #58 | — | absent — modèles de documents modifiables |
| BRD-06 | StatementService (archivage/rectificatif) | #18 | bordereaux | complet |
| BRD-07 | StatementService (report solde) | #18 | cr60 JUnit | complet |
| BRD-08 | — | #59 | — | absent — attestation fiscale annuelle |
| BRD-09 | — | #33 | — | absent — synthèse des escomptes par convention |
| BRD-10 | PublicApiService | — | — | partiel — mise à disposition extranets/GED/CRM à confirmer |
| BRD-11 | — | #18 | — | absent — relevé informatif du membre payé par son groupement |
| BRD-12 | ExportService | #18 | — | partiel — export éditique (priorité S) à confirmer |

### 8.11 Comptabilité (FIN)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| FIN-01 | AccountingService | #53 | accounting | partiel — `ADVANCE_WRITE_OFF` **désormais produite** (CR-78) ; reste `INSURER_RECEIVABLE` (chaîne §6.10 à réaliser) |
| FIN-02 | AccountingSetupService, AccountingQueryService | #52 | accounting | complet |
| FIN-03 | AccountingService (extraction) | #54 | accounting | complet |
| FIN-04 | CutoffService | #55 | cutoff | complet |
| FIN-05 | AccountingService | #53 | accounting | complet |
| FIN-06 | — | #56 | — | absent — rapprochement acquis/payé |
| FIN-07 | AccountingQueryService | #53 | — | partiel — chaîne flux→bordereau→ligne→écriture non exposée |
| FIN-08 | — | #57 | — | absent — rapprochement des commissions reçues des assureurs (attendu + avis) |
| FIN-09 | CutoffService | #55 | cutoff | complet |

### 8.12 Restitutions (REP)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| REP-01 | ReportingService | #60 | reporting | complet |
| REP-02 | ReportingService | #24 | reporting | complet |
| REP-03 | ReportingService | #24 | reporting | complet |
| REP-04 | ExportService | #49 | reporting, exports | complet |
| REP-05 | ExportService (export Data) | #49 | — | partiel — export complet à chaque fois, non incrémental (pas de `updated_at`) |
| REP-06 | PublicApiService | — | — | complet |
| REP-07 | ReportingService | #61 | reporting | complet |
| REP-08 | ReportingService | #57 | reporting | complet |
| REP-09 | ReportingService | #62 | reporting | complet |

### 8.13 Administration (ADM)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| ADM-01 | RoleMappingAdminService, partitioning | #63 | role-mappings (ADM-01) | complet |
| ADM-02 | EntityAdminService | #43 | entity-params | complet |
| ADM-03 | (interface française) | tous | — | partiel — énumérations et codes techniques encore affichés en clair à l'écran |
| ADM-04 | AuditService (chaîne de hachage) | #64 | audit | complet |
| ADM-05 | EntityAdminService | #43 | entity-params | complet |
| ADM-06 | PurgeService | #66 | admin-supervision | complet |

### 8.14 Forces de vente (FDV)

| N° | Classes serveur | Écran | Tests | État |
| --- | --- | --- | --- | --- |
| FDV-01 | EmployeeImportService, SalesForceQueryService | #67 | fdv | partiel — pas d'écran de gestion des collaborateurs |
| FDV-02 | — | #68 | — | absent — mesures reçues (absences, production, activité, marge) |
| FDV-03 | SalesForceQueryService | #69 | fdv | partiel — versionnement et objectifs collectifs absents |
| FDV-04 | SalesForceService | #70 | fdv | partiel — enveloppes/critères des postes animateur/responsable/vente directe absents |
| FDV-05 | SalesForceService (runMonth) | #71 | cr88 JUnit | complet |
| FDV-06 | — | #73 | — | absent — régularisation annuelle |
| FDV-07 | — | — | — | absent — variable trimestriel qualitatif |
| FDV-08 | — | — | — | absent — variable annuel collectif |
| FDV-09 | — | #70 | — | absent — barèmes animateurs et responsables |
| FDV-10 | — | #70 | — | absent — variable de la vente directe |
| FDV-11 | SalesForceService (pondération) | #70 | cr87 JUnit | complet |
| FDV-12 | — | #70 | — | absent — réduction sur résiliation |
| FDV-13 | SalesForceService (validate/adjust) | #71 | fdv | complet |
| FDV-14 | SalesForceService (pay-export) | #73 | fdv | partiel — écran de synthèse des variables à payer absent |
| FDV-15 | SalesForceQueryService | #72 | fdv | complet |
| FDV-16 | — | #72 | — | absent — simulateur et alertes de seuil |

## Cas de recette — état du rejeu

- **Existence (JUnit) :** **les 71 cas existent** (CR-74 P12, CR-80 cession avec escompte et
  CR-81 rachat anticipé emprunteur réalisés le 2026-10-11, avec tests). Reste à les **rejouer
  par l'interface** (68 sur 70, étape 4).
- **Rejeu par l'interface (`@cr:`) :** 2 sur 70 (`CR-68`, `CR-70`). Tous les autres sont
  renvoyés « au niveau JUnit » par des entrées de `docs/DECISIONS.md` à rectifier (étape 4).

## Simplifications à reprendre (relevé de docs/DECISIONS.md)

Recensement des décisions qui reportent, approximent, réduisent ou contournent une
règle (à traiter en étape 2, puis à rectifier dans `docs/DECISIONS.md` par une
ligne datée sans effacer l'ancienne) :

1. Rejeu E2E des cas de recette du moteur remplacé par « acceptation JUnit » (entrées
   des 2026-10-09/10, étapes 6/7/8/9/10/12/14) → CR-60/61/63, CR-62/64/85/86, CR-69/72,
   CR-87/88 et les groupes escompte/RCD/PER.
2. `ADVANCE_WRITE_OFF` et `INSURER_RECEIVABLE` jamais produites (2026-10-09, §10.2).
   **`ADVANCE_WRITE_OFF` produite** à la validation métier (2026-10-11, CR-78) ; reste
   `INSURER_RECEIVABLE` (dépend de la ligne `INSURER` du §6.10, non encore réalisée).
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
9. CR-72 mesuré par extrapolation depuis 60 bénéficiaires (2026-10-09, §11).
10. FDV-06/07/08/09/10/12/16 reportés (2026-10-09, §8.14).
11. `ConfirmDialog` construit mais non raccordé aux actions irréversibles (2026-10-10, ERGONOMIE §3).
12. Déclaration fausse « tous les écrans de l'inventaire existent comme vues réelles »
    (2026-10-09, §4) — rectifiée par le présent état des lieux.
13. Une seule instance d'application partagée par la suite E2E au lieu d'une par
    processus (2026-10-07, TESTS-E2E §1).
14. Rendu bordereau « texte + empreinte » au lieu de PDF/tableur réel (2026-10-10, §10.6).
15. Montants FDV « à définir » figés en constantes documentées plutôt qu'en paramètres
    du plan renseignés dans le jeu de démonstration (2026-10-09, §8.14).

## Garde-fou de traçabilité (état)

`src/test/e2e/scripts/traceability.mjs` construit ses listes attendues à partir d'un
objet **codé en dur** (`REQUIREMENTS_BY_STEP`) et ne vérifie la présence d'une
étiquette que **globalement** (trouvée n'importe où, pas rattachée à une route). Il ne
lit ni l'inventaire de LANCEMENT §4, ni la section 8, ni la section 13, ni le routeur.
À réécrire (étape 3) pour qu'il tire ses listes des sources et échoue sur toute ligne
`absent`/`partiel` de ce fichier.
