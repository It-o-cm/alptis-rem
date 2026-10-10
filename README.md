# SI Rémunération

Application de calcul, de contrôle, de paiement et de comptabilisation des commissions
versées aux distributeurs d'un groupe d'assurance. Serveur Quarkus (Java 21), interface
Vue 3 servie par l'application via Quinoa.

La spécification normative est `docs/SI-Remuneration-Specification.md`. Le plan de
construction est dans `LANCEMENT.md`, les règles de session dans `CLAUDE.md`, l'avancement
dans `PLAN.md`, les décisions dans `docs/DECISIONS.md`.

## Prérequis

- **Java 21** (p. ex. `/opt/homebrew/opt/openjdk@21`). Maven doit tourner sur Java 21 :
  exportez `JAVA_HOME` en conséquence.
- **Maven 3.9+**
- **Node 22+ et npm 10+** (pour l'interface et les tests de bout en bout).

## Lancer l'application

L'application ne tourne qu'en profil `dev` : base H2 en mémoire recréée à chaque
lancement, systèmes externes simulés.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
mvn quarkus:dev
```

- Interface : <http://localhost:9010/>
- Description OpenAPI : <http://localhost:9010/q/openapi>, Swagger UI : <http://localhost:9010/q/swagger-ui>

En mode `quarkus:dev`, Quinoa gère le serveur de développement Vite.

### Authentification en profil dev

Le SSO réel est hors périmètre : en profil `dev`, les utilisateurs sont déclarés en
configuration (`application.properties`) avec leurs groupes d'annuaire ; leurs rôles et
entités visibles sont déduits de la table `role_mapping` (éditable à l'écran « Groupes et
rôles »). L'authentification se fait en HTTP Basic (le navigateur la propose ou on la
fournit). Quelques comptes (mot de passe = identifiant) :

| Identifiant | Rôle(s) | Entité |
| --- | --- | --- |
| `admin-alptis` | administrateur métier, gestionnaire | Alptis Assurances |
| `operator-alptis` | gestionnaire | Alptis Assurances |
| `validator-alptis` | valideur métier | Alptis Assurances |
| `finance-alptis` | contrôleur Finance | Alptis Assurances |
| `viewer-alptis` | consultation | Alptis Assurances |
| `operator-unep` | gestionnaire | UNEP |
| `it-admin` | administrateur technique | toutes |
| `auditor` | audit (lecture seule) | toutes |

### Lancer le paquet dev (ce que fait la suite E2E)

```bash
mvn -Dquarkus.profile=dev -DskipTests package
java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar
```

Ce paquet sert le SPA construit (sans serveur Vite) et expose le point d'accès de test
`/api/test`, réservé au profil `dev`.

## Lancer les tests

Tout passe par `mvn verify` :

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
mvn verify
```

`mvn verify` enchaîne :

1. **Tests unitaires du serveur** (JUnit 5) et **couverture JaCoCo** : la construction
   échoue sous 100 % de lignes ou de branches.
2. **Empaquetage** de l'application.
3. **Tests de bout en bout** (Playwright/Chromium) contre l'application réelle en profil
   `dev` : la suite construit puis lance le paquet dev (`src/test/e2e/run-dev-app.sh`).
4. **Garde-fou de traçabilité** : génère `docs/E2E-MATRIX.md` et échoue en cas de lacune
   (`src/test/e2e/scripts/traceability.mjs`).

Pour itérer sans la suite E2E : `mvn verify -DskipE2E=true`.

### Tests de l'interface (Vitest)

Les magasins Pinia et les fonctions de présentation sont couverts à 100 % :

```bash
cd src/main/webui
npm install
npm run coverage
```

### Tests de bout en bout seuls

```bash
cd src/test/e2e
npm install
npx playwright install chromium
npx playwright test
```

## Jeu de démonstration

En profil `dev`, le jeu de démonstration est chargé au démarrage (idempotent par la présence
du cabinet `P13897`). Il peut être rechargé par la suite E2E via `POST /api/test/demo` après
`POST /api/test/reset` (la remise à zéro ne charge pas le jeu). Il est produit par les
services réels : seules les données de référence sont créées directement, tout ce que
l'application calcule (lignes, contrôles, comptes, écritures, lots, bordereaux, extractions,
variable) vient des services.

### Ce qu'il contient

- **Deux entités** complètes, `ALPTIS_ASSURANCES` et `UNEP`.
- **26 distributeurs** couvrant tous les types, les deux statuts ORIAS et tous les statuts
  partenaire ; deux groupements et une hiérarchie code de production → distributeur →
  groupement ; plusieurs codes de production ; coordonnées bancaires pour la plupart, deux
  bénéficiaires sans coordonnées (exclusion de paiement) et un distributeur radié ORIAS.
- **Neuf produits** sur plusieurs gammes ; les **neuf familles** de calcul produisent des
  lignes (plan `DEMO_2026` par entité, plus le barème `GUIDE_2026` pour Alptis) ; une règle
  linéaire à versions close/active/brouillon (le brouillon en attente de validation à quatre
  yeux) ; barèmes à tranches, accords, inscriptions, codes externes et règle de versement à
  plusieurs bénéficiaires.
- **Conventions d'escompte** dans des états différents (valide proche de son plafond,
  suspendue, close) et une dette à reprendre (cession en cours).
- **~180 contrats, ~230 garanties**, du co-courtage, deux cessions de portefeuille (une
  réalisée, une en cours) et des ordres de remplacement.
- **Périodes.** Alptis : juin à septembre 2026 **closes** (septembre garde la scène de la
  maquette — bordereau net de **110,00 €** du cabinet P13897), octobre **calculée** avec des
  anomalies ouvertes des trois sévérités dont une bloquante et cinq ajustements de statuts
  différents, novembre **ouverte** avec des événements en attente. UNEP : septembre **close**,
  octobre **arrêtée** entre la validation métier et la validation Finance, lot préparé non
  validé.
- **Cycle.** Contrôles de chaque sévérité et de plusieurs familles, décisions prises sur une
  partie, lots payés, un ordre rejeté par la banque puis réémis, un lot hors cycle, des
  bordereaux publiés dont un rectificatif, des comptes débiteurs et créditeurs avec report de
  solde, des écritures comptables et un état de cut-off par période close.
- **Flux et administration.** Lots d'import terminés et un en erreur partielle (rejets de
  plusieurs motifs, dont certains traités) avec événements en attente, journal d'export,
  paramètres d'entité et une piste d'audit aux auteurs variés.
- **Forces de vente.** Un plan par poste et par entité, douze collaborateurs sur deux niveaux
  d'encadrement, objectifs, mesures sur trois mois, un mois validé et exporté (septembre) et un
  mois en attente (octobre).

### Par où commencer, selon l'utilisateur

| Identifiant | Ce qu'il voit | Commencer par |
| --- | --- | --- |
| `operator-alptis`, `admin-alptis` | tout Alptis : distributeurs, périodes (octobre calculée, novembre ouverte), imports, contrôles, ajustements, paiement, bordereaux, comptabilité | **Distributeurs** (chercher `P13897`) |
| `validator-alptis` | validation métier des périodes et des ajustements | **Périodes** |
| `finance-alptis` | paiement, bordereaux (dont le 110,00 € de septembre), cut-off, comptabilité | **Bordereaux** (Alptis, mois `2026-09-01`) |
| `viewer-alptis` | lecture : tableaux de bord, recherche de commissions, bordereaux | **Tableaux de bord** |
| `manager-alptis` | forces de vente : plan, collaborateurs, validation du variable | **Forces de vente — Plan** |
| `rep-alptis` | son propre variable | **Forces de vente — Mon variable** (`rep-alptis`) |
| `operator-unep`, `admin-unep` | tout UNEP : périodes (octobre préparée), bordereaux de septembre, distributeurs | **Périodes** |
| `validator-unep`, `finance-unep`, `viewer-unep` | leurs écrans UNEP respectifs | **Périodes** / **Bordereaux** |
| `it-admin` | supervision des traitements, groupes et rôles | **Supervision** |
| `auditor` | piste d'audit, vision groupe consolidée, tout en lecture | **Audit** |

Les périodes closes (juin à septembre) ne figurent pas à l'écran « Périodes » (qui ne liste
que les périodes non closes) : leur contenu se consulte aux écrans **Bordereaux**,
**Comptabilité**, **Paiement** et **Cut-off** en choisissant l'entité et le mois.

## Structure

```
pom.xml                         projet Maven / Quarkus
src/main/java/fr/alptis/remuneration/
  domain/                       modèle de domaine (68 entités, énumérations), BaseEntity, DateTimeProvider
  api/                          points d'accès REST (dont /api/test en dev)
src/main/resources/             configuration (profils dev / test / cible PostgreSQL)
src/main/webui/                 application Vue 3 (TypeScript, Vite, Router, Pinia)
src/test/java/                  tests unitaires du serveur
src/test/e2e/                   suite Playwright + garde-fou de traçabilité
docs/                           spécification, maquette, décisions, matrice E2E
```
