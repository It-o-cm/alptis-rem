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
