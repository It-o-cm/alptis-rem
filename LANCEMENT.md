# Lancement de la construction

Tu construis l'application décrite par `CLAUDE.md`, seul, par étapes, jusqu'au bout du plan ci-dessous. Tu ne poses aucune question.

## 1. Reprise

Si `PLAN.md` existe à la racine, le travail a déjà commencé : lis `CLAUDE.md`, `PLAN.md` et `docs/DECISIONS.md`, puis reprends à la première étape non cochée. Ne refais pas une étape cochée.

Sinon, commence à l'étape 0.

## 2. Étape 0 : mise en place du dépôt

Au départ, le répertoire ne contient que ce qui y a été déposé à la main :

```
CLAUDE.md
LANCEMENT.md
TESTS-E2E.md
SI-Remuneration-Specification.md
SI-Remuneration-Specification.pdf
maquette/                 les dix-neuf écrans, fichiers *.dc.html
.claude/settings.local.json
```

Tu fais, dans cet ordre :

1. Si le répertoire n'est pas un dépôt Git, `git init`. Crée un `.gitignore` adapté à Maven, Node et aux éditeurs, qui exclut aussi `.claude/settings.local.json`.
2. Crée `docs/`. Déplaces-y `SI-Remuneration-Specification.md`, `SI-Remuneration-Specification.pdf`, et `maquette/` en `docs/maquette/`. `CLAUDE.md`, `LANCEMENT.md` et `TESTS-E2E.md` restent à la racine.
3. Lis la spécification en entier (le markdown, plus les quatre diagrammes UML de la section 5.7 dans le PDF), puis les dix-neuf écrans de la maquette. Ces dix-neuf écrans ne sont que les principaux : l'inventaire complet de l'interface est en section 4.
4. Crée `docs/DECISIONS.md` (vide, avec son en-tête) et `PLAN.md` : le tableau de la section 3 en cases à cocher, une ligne par étape.
5. Crée le projet Quarkus et le squelette Vue servi par Quinoa, le profil `dev`, `BaseEntity`, `DateTimeProvider`, et un `README.md` qui dit comment lancer l'application et les tests.
6. Dans le squelette Vue, reporte dès maintenant le cadre commun de la maquette : la navigation latérale avec ses rubriques, l'en-tête de page (entité, période, rôle), les styles de base (couleurs, tableaux, étiquettes, boutons). Chaque entrée de navigation mène à une page vide portant son titre.
7. Vérifie que l'application démarre et que les tests passent, coche l'étape 0, commit.

L'arborescence visée à la fin de l'étape 0 :

```
CLAUDE.md
LANCEMENT.md
TESTS-E2E.md
PLAN.md
README.md
pom.xml
docs/
  SI-Remuneration-Specification.md
  SI-Remuneration-Specification.pdf
  DECISIONS.md
  maquette/
src/main/java/fr/alptis/remuneration/...
src/main/resources/
src/main/webui/           application Vue
src/test/java/...
```

## 3. Plan

À la fin de chaque étape : la construction complète est verte (`mvn verify`), tout le code Java de l'étape est couvert à 100 % en lignes et en branches par des tests unitaires (seuil vérifié par la construction), les tests de bout en bout de l'étape et de toutes les étapes passées sont verts sans relance et la matrice de traçabilité est complète pour les étapes cochées (`TESTS-E2E.md`), les cas listés passent, `README.md` et `PLAN.md` sont à jour, le commit est fait. Tu enchaînes sur l'étape suivante sans attendre.

Chaque étape livre aussi ses écrans : ceux que l'inventaire de la section 4 lui attribue, avec leurs points d'accès `/api/ui/v1`, branchés et utilisables à la fin de l'étape. Une étape n'est pas terminée tant qu'un de ses écrans manque.

| N° | Étape | Sections à relire | Livré | Cas à faire passer |
| --- | --- | --- | --- | --- |
| 0 | Mise en place | 1, 3, 12 | Voir section 2 | — |
| 1 | Modèle | 4, 5 | Les 68 tables de la section 5.7 en entités, toutes les énumérations des sections 5.1 à 5.6 et celles que cite la convention de 5.7, contraintes d'unicité | — |
| 2 | Sécurité, entités, audit | 3, 8.13, 12.2 | Rôles, cloisonnement par entité dans la couche d'accès aux données, séparation des tâches, piste d'audit chaînée | CR-70, CR-71 |
| 3 | Référentiels et intégration | 8.1, 8.2, 9.1 à 9.4, 9.9, 9.10 | Format pivot, fichiers entrants, mappings paramétrables, contrôles d'import, rejets et leurs codes, journal à cinq compteurs, flux attendus | CR-37, CR-38, CR-67, CR-68 |
| 4 | Paramétrage des règles | 5.4, 6.11, 6.12, 8.3 | Plans, règles, versions et leur cycle d'approbation, registre des familles et de leurs schémas, détection des conflits, conventions d'escompte, inscriptions, barème initial de la section 6.12 | — |
| 5 | Moteur : cœur | 6.1, 6.2, 6.10, 12.3 | Unités de travail, temps 0 à 3, propositions, arbitrage, explication, annulations ; familles `LINEAR`, `FIXED`, `EXTERNAL` | CR-01, CR-09 à CR-12, CR-51, CR-73 à CR-75, CR-77 |
| 6 | Précompté, reprises, escompte, emprunteur | 6.2 à 6.5 | Familles `UPFRONT` et `ADVANCE`, politique de reprise, sans-suite, apurement, solde, tolérance, ruptures, cession, rachat anticipé | CR-02 à CR-08, CR-20 à CR-28, CR-78 à CR-81 |
| 7 | Épargne retraite | 6.6 | Famille `OUTSTANDING`, escompte sans convention, rappel, réajustement, complément annuel, commissions reçues et attendues | CR-30 à CR-36, CR-82 à CR-84 |
| 8 | Surcommissions et partage | 6.7 à 6.9 | Familles `TIERED` mensuelle, `PORTFOLIO`, `SHARE` ; conditions de qualité, répartition par adhésion, co-courtage, groupements | CR-40 à CR-50, CR-76 |
| 9 | Cycle, contrôles, ajustements, comptes | 7, 8.5 à 8.8, 10.5 | États de la période et retours arrière, catalogue de contrôles, anomalies, liste de contrôle, ajustements à deux signatures, compte distributeur, compensation, report | CR-60, CR-61, CR-63 |
| 10 | Paiement et documents | 7.1, 8.9, 8.10, 9.5, 9.6, 9.11 | Lots de cycle, d'épargne retraite, de chèques et hors cycle ; fichier pain.001 ; bordereaux en PDF et en tableur ; résultat des virements | CR-62, CR-64, CR-85, CR-86 |
| 11 | Comptabilité et restitutions | 8.11, 8.12, 9.7, 9.8, 9.11, 9.12, 10.1 à 10.4 | Écritures générées à la validation métier, extraction comptable, état de cut-off, export Data, exports à la demande, API de la section 9.8 | CR-65, CR-66 |
| 12 | Simulation et volume | 8.3 (PAR-13), 8.4 (CAL-11, CAL-12), 11 | Simulation avec table de substitution, recalcul partiel, générateur d'un jeu de 20 000 bénéficiaires et 420 000 événements | CR-69, CR-72 |
| 13 | Interface : restitutions, administration, finition | 3, 8.12, 8.13, 11 | Tableaux de bord, recherches transversales, écrans d'administration ; puis revue de toute l'interface contre l'inventaire de la section 4 : aucun écran manquant, navigation complète, droits par rôle, cohérence d'aspect | — |
| 14 | Forces de vente | 8.14, 9.10, 9.11 | Plans, objectifs, mesures, calcul du variable, validation, export paie, et les écrans du module | CR-87, CR-88 |
| 15 | Jeu de démonstration | 7.3, 9.5, 10 | Données chargées au démarrage en profil `dev`, qui rejouent la période de septembre 2026 de bout en bout et redonnent à l'écran ce que montre la maquette (cabinet P13897, bordereau 2026-09-004512 de 110,00 €, lot 202609-L001) | Parcours complet, de l'import à la clôture |

Précisions :

- **CR-38** demande un fichier réel de l'assureur, absent du dépôt. Écris le test sur un fichier construit à la main qui redonne les totaux du cas, et note-le dans `docs/DECISIONS.md`.
- **CR-72** : mesure la durée et écris-la dans le compte rendu. Si elle dépasse quatre heures, cherche la cause dans le respect de D8, D11 et D15 avant toute autre optimisation.

## 4. Interface : inventaire des écrans

La maquette ne couvre que les dix-neuf écrans principaux, ceux qui demandaient une conception. L'application en compte bien davantage : tous ceux du tableau ci-dessous. La colonne « Maquette » donne le fichier de `docs/maquette/` quand l'écran y figure ; « — » signifie que tu le construis à partir de la spécification. La colonne « Étape » dit à quelle étape du plan il est livré.

Cet inventaire est un minimum. Si une exigence de la section 8 de la spécification suppose un écran qui n'y est pas, tu l'ajoutes, tu le rattaches à l'étape de son domaine et tu le notes dans `docs/DECISIONS.md`.

**Cycle mensuel**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Avancement des périodes, toutes entités visibles | WKF-04 | `Main.dc.html` | 9 |
| Détail d'une période : états, transitions, calendrier, lots, reste à traiter | WKF-01, 7.1 à 7.3 | `Main.dc.html` | 9 |
| Journal des imports et flux attendus | ING-05, ING-10 | `Imports.dc.html` | 3 |
| Détail d'un import : compteurs, rejets, lignes en attente, correction ou mise à l'écart d'un rejet | ING-04, ING-06 | `Imports.dc.html` | 3 |
| Téléversement d'un fichier, avec choix du flux et du système source | ING-01, ING-08 | — | 3 |
| Saisie manuelle d'un événement ou d'une donnée complémentaire | ING-07 | — | 3 |
| Recherche d'événements et détail d'un événement : statut, motif d'attente, lignes produites | ING-06, 5.3 | — | 5 |
| Événements en attente, par motif | P8, ING-06 | — | 5 |
| Exécutions de calcul : lancement, suivi, compteurs, unités en échec, recalcul partiel | CAL-01, CAL-12, 12.3 | — | 5 |
| Contrôles et anomalies de la période, décisions, liste de contrôle | CTL-02, CTL-03, CTL-05 | `Controles.dc.html` | 9 |
| Catalogue des contrôles : seuil, gravité, périmètre, activation, versions | CTL-01, CTL-04 | — | 9 |
| Ajustements : liste, saisie, approbation, import en masse | REG-01 à REG-05 | `Ajustements.dc.html` | 9 |
| Réclamations : liste, fiche, lignes contestées, suivi | REG-06 | — | 9 |
| Validation métier : totaux, comparaison au mois précédent, second niveau | WKF-02, 7.2 | — | 9 |
| Lots de paiement de la période et détail d'un lot : ordres, exclusions, rapprochement, validation Finance | PAY-01 à PAY-05 | `Paiement.dc.html` | 10 |
| Création d'un lot hors cycle | PAY-07 | — | 10 |
| Résultat bancaire : ordres réglés, rejets, saisie de l'émission des chèques | PAY-04, PAY-06 | — | 10 |
| Bordereaux de la période : liste, aperçu, publication, rectificatif, relevé informatif | BRD-01 à BRD-07, BRD-11 | — | 10 |

**Distributeurs et contrats**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Recherche de distributeurs | REF-02, REP-03 | — | 3 |
| Fiche distributeur : identité, hiérarchie, statut, accords, inscriptions, comptes, indicateurs, historique | REF-10, CPT-01, CPT-06 | `Distributeur.dc.html` | 9 |
| Vue d'un groupement et de ses membres | CPT-02, REF-03 | — | 9 |
| Blocage d'un compte ou d'une ligne, avec motif et date de levée | CPT-05, REF-08 | — | 9 |
| Détail d'une ligne de commission et de son explication | CAL-10, FIN-07 | `Ligne.dc.html` | 5 |
| Recherche de lignes de commission : payé à qui, quand, pourquoi | REP-02, REP-03 | — | 13 |
| Hiérarchie et codes de production, avec leur historique | REF-03 | — | 3 |
| Coordonnées bancaires et changements depuis le dernier paiement | REF-05 | — | 3 |
| Règles de versement : bénéficiaire, parts, périodicité | REF-06 | — | 3 |
| Indicateurs de qualité par distributeur et par période | REF-07 | — | 3 |
| Cessions de portefeuille et ordres de remplacement | REF-09 | — | 3 |
| Comptes débiteurs sans portefeuille actif et échéanciers d'apurement | CPT-07 | — | 9 |
| Conventions d'escompte : liste | PAR-09 | — | 6 |
| Convention d'escompte : paramètres, compteurs, garanties escomptées, simulation de rupture | PAR-09, CPT-08, 6.4 | `Escompte.dc.html` | 6 |
| Créances d'escompte par convention et par distributeur | CPT-08, BRD-09 | — | 6 |
| Recherche de contrats et de garanties | 5.2 | — | 3 |
| Fiche d'une adhésion et de ses garanties : affectations, choix de rémunération, événements, lignes, position d'escompte | 5.2, 5.5 | — | 6 |

**Règles**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Plans de rémunération : liste, fiche, duplication annuelle | PAR-01, PAR-12 | — | 4 |
| Règles et versions d'un plan : liste, statuts, conflits détectés | PAR-03 à PAR-05 | — | 4 |
| Paramétrage d'une version de règle, approbation par un second administrateur | PAR-01 à PAR-08, PAR-14 | `Regle.dc.html` | 4 |
| Familles de calcul : un formulaire par famille | PAR-02 | `Familles.dc.html` | 4, complété aux étapes 5 à 8 |
| Accords et inscriptions aux règles réservées, à l'unité et en masse | PAR-10 | — | 4 |
| Simulation : lancement, table de substitution, résultat, historique | PAR-13 | `Simulation.dc.html` | 12 |
| Export et import du paramétrage | PAR-15 | — | 12 |

**Référentiels et flux**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Entités et leurs paramètres : calendrier, seuils, tolérances | REF-01, ADM-05 | — | 2 |
| Assureurs, gammes, produits et options | REF-01 | — | 3 |
| Correspondance des codes externes par système source | REF-04 | — | 3 |
| Mappings de fichier : liste et versions | ING-02 | — | 3 |
| Édition et test d'un mapping | ING-02 | `Mapping.dc.html` | 3 |
| Catalogue des flux | 9.1, ING-10 | `Flux.dc.html` | 11 |
| Exports et journal des exports | 9.11, 9.12, REP-04 | `Exports.dc.html` | 11 |
| Formats d'échange | 9.2, 9.3, 9.9 à 9.11 | `Formats.dc.html` | 11 |
| Reprise d'historique : dépôt des fichiers d'ouverture et contrôle | ING-11 | — | 12 |

**Comptabilité**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Schéma comptable et correspondance des comptes par entité | FIN-02 | — | 11 |
| Recherche d'écritures | FIN-01, FIN-05, FIN-07 | — | 11 |
| Extraction comptable du mois et clôture | FIN-03 | — | 11 |
| État de cut-off | FIN-04, FIN-09 | `Cutoff.dc.html` | 11 |
| Rapprochement des commissions acquises et payées | FIN-06 | — | 11 |
| Commissions reçues des assureurs, attendu et marge | FIN-08, REP-08 | — | 11 |

**Documents, restitutions, administration**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Modèles de documents | BRD-05 | — | 10 |
| Attestations fiscales annuelles | BRD-08 | — | 10 |
| Tableaux de bord : commissions, reprises, escomptes, comptes débiteurs, premiers distributeurs, anomalies | REP-01 | — | 13 |
| Vision consolidée du groupe | REP-07 | — | 13 |
| Indicateurs de fonctionnement | REP-09 | — | 13 |
| Correspondance entre groupes d'annuaire et rôles | section 3 | — | 2 |
| Piste d'audit | ADM-04 | — | 2 |
| Supervision des traitements et des flux | section 3 | — | 13 |
| Purge et anonymisation des données personnelles | ADM-06 | — | 13 |

**Forces de vente**

| Écran | Exigences | Maquette | Étape |
| --- | --- | --- | --- |
| Collaborateurs | FDV-01 | — | 14 |
| Mesures reçues : absences, production, activité, marge | FDV-02 | — | 14 |
| Objectifs individuels et collectifs, versionnés | FDV-03 | — | 14 |
| Plan de variable par poste | FDV-04, FDV-09 à FDV-12 | `FdvPlan.dc.html` | 14 |
| Validation mensuelle par le responsable | FDV-13 | `FdvValidation.dc.html` | 14 |
| Mon variable, mes objectifs, mon historique, simulateur | FDV-15, FDV-16 | `FdvCommercial.dc.html` | 14 |
| Synthèse des variables à payer et export paie | FDV-14 | — | 14 |

**Comment lire un fichier de maquette.** Ce n'est pas du Vue. Le balisage est entre `<x-dc>` et `</x-dc>` ; `{{ nom }}` est une valeur fournie par le script, `<sc-for list="{{ items }}" as="it">` une répétition, `<sc-if value="{{ x }}">` une condition. Le script en bas de fichier (`class Component`, méthode `renderVals`) porte les données fictives et l'état de l'écran. Le fichier `support.js` qu'ils référencent n'est pas fourni et ne te sert à rien.

**Écran présent dans la maquette.** Tu le reportes :

- un composant de page Vue de même structure : titre, sous-titre, cartes, tableaux, onglets, formulaires, boutons, notes ;
- les mêmes libellés, les mêmes colonnes, les mêmes codes de statut, mot pour mot ;
- les mêmes enchaînements : chaque lien entre écrans devient une route, chaque action (valider, refuser, cocher, basculer d'onglet) fonctionne ;
- les données fictives du script sont remplacées par des appels au serveur ; aucune donnée en dur ne reste dans l'interface ;
- les notes explicatives (rappels de règles, renvois à une hypothèse) sont conservées : ce sont des textes d'aide.

Un fichier de maquette peut couvrir plusieurs écrans de l'inventaire (liste et détail, par exemple) : tu les sépares quand la navigation le demande.

**Écran absent de la maquette.** Tu le construis sur le modèle des écrans maquettés du même genre :

- une liste : filtres en tête, tableau paginé côté serveur, tri, export de la liste (REP-04), lien vers la fiche ;
- une fiche : en-tête avec identité et statut, cartes de synthèse, onglets pour le détail et l'historique ;
- un formulaire : champs de la table correspondante en section 5.7, libellés français tirés du glossaire (section 4), contrôles de saisie de la spécification, message d'erreur du serveur affiché tel quel ;
- les colonnes, statuts et codes sont ceux de la spécification, jamais inventés.

**Dans tous les cas.** Le rôle affiché dans l'en-tête est celui de l'utilisateur connecté ; ce qu'il n'a pas le droit de faire est masqué, et refusé par le serveur s'il le tente. La navigation latérale reprend les rubriques de la maquette et reçoit une entrée pour chaque liste de l'inventaire. Quand un écran a besoin d'une donnée que le serveur ne fournit pas encore, c'est le serveur que tu complètes, dans la limite de la spécification. Quand la maquette contredit la spécification sur une règle ou un montant, la spécification l'emporte : corrige à l'écran et note l'écart dans `docs/DECISIONS.md`.

L'écran « Familles de calcul » tire ses formulaires des schémas servis par le registre des familles, pas de formulaires écrits à la main.

## 5. Tests de bout en bout

`TESTS-E2E.md`, à la racine, est normatif au même titre que ce plan. Lis-le en entier à l'étape 0 et relis-le au début de chaque étape qui livre un écran.

Il définit quatre niveaux, tous obligatoires : un fichier de tests par écran de l'inventaire, chaque cas de recette rejoué par l'interface, un catalogue de parcours métier, et des tests transversaux (droits, cloisonnement, audit). Il fixe aussi les règles de solidité et le garde-fou de traçabilité qui fait échouer la construction quand un écran, une exigence, un cas ou un couple rôle-action n'a pas son test.

À l'étape 0, tu mets en place l'outillage : Playwright, le point d'accès `/api/test` réservé au profil `dev`, les fabriques de données, le script de traçabilité, et un premier test qui prouve que la chaîne fonctionne.

## 6. Hors périmètre, simulé en profil `dev`

| Système réel | Ce que tu livres à la place |
| --- | --- |
| Fournisseur d'identité, passerelle d'API | Utilisateurs en configuration |
| Transfert de fichiers, CRM, outils de gestion, assureurs | Répertoires locaux d'entrée, un par système source |
| Trésorerie, SI Finance, GED, plateforme Data, paie | Répertoires locaux de sortie, un par destinataire |
| Banque | Un service qui produit un fichier de résultat des virements pour un lot émis |
| Messagerie (WKF-03) | Une trace au journal |
| Signature électronique (PAR-11) | Non livré |

Disponibilité, sauvegarde, reprise après sinistre et partitionnement des tables (section 11) relèvent de l'exploitation : tu ne les traites pas.

## 7. Comptes rendus et arrêts

À la fin de chaque étape, écris un compte rendu de quinze lignes au plus : ce qui est livré, les cas de recette qui passent, le taux de couverture mesuré, le nombre de tests de bout en bout ajoutés et l'état de la matrice de traçabilité, les décisions ajoutées à `docs/DECISIONS.md`, ce qui reste fragile. Puis continue.

Tu t'arrêtes, et tu expliques pourquoi, dans deux cas seulement :

- un cas de recette reste rouge après trois corrections distinctes, ou ne peut passer qu'en contredisant un autre cas ou une règle de la spécification ;
- deux passages de la spécification se contredisent sur un montant ou une règle de calcul, sans hypothèse de la section 15 pour trancher.

Dans ces deux cas, cite les passages en cause, dis ce que tu as essayé, et donne le choix que tu ferais.
