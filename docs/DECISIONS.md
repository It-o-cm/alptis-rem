# Journal des décisions

Ce fichier consigne les choix faits quand la spécification est muette, ambiguë ou
contradictoire, selon la procédure de `CLAUDE.md` : d'abord l'hypothèse de la
section 15 (H-01 à H-57) ; sinon le choix le plus simple compatible avec les
sections 6 et 12.3, consigné ici.

Chaque entrée porte : date, section concernée, question, choix retenu, raison.

| Date | Section | Question | Choix retenu | Raison |
| --- | --- | --- | --- | --- |
| 2026-10-07 | CLAUDE.md socle | Version de Quarkus (« dernière version stable ») | Quarkus 3.40.1 | 4.0 n'existe qu'en Beta ; 3.40.1 est la dernière version stable de la ligne 3.x. La 3.29.4 d'abord essayée référençait une classe SBOM absente et échouait au goal `build`. |
| 2026-10-07 | 12.2 patron BaseEntity | Où placer `entity_id` du cloisonnement | `BaseEntity` ne porte que les colonnes communes à **toutes** les tables (id, version, created_at, created_by, updated_at) ; `entity_id` est déclaré sur les entités concernées, pas dans `BaseEntity` | La convention 5.7 exclut `entity_id` des référentiels partagés entre entités ; le mettre dans la base le rendrait obligatoire partout. |
| 2026-10-07 | TESTS-E2E / `/api/test` | Reset de base et chargement de jeux de données nommés dès l'étape 0 | Mécanisme `/api/test` livré à l'étape 0 avec le seul pilotage de l'horloge (ping, fix, reset) ; reset de base et jeux de données ajoutés à l'étape 1 avec les premières entités et chargeurs | La règle de couverture à 100 % des branches interdit d'écrire une logique « trouvé / introuvable » ou une boucle de troncature tant qu'aucune entité ni aucun chargeur n'existe : une seule branche serait exerçable. |
| 2026-10-07 | TESTS-E2E §1 environnement | « Une instance d'application par processus de test, sur son propre port » | À l'étape 0 : une seule instance dev partagée, lancée par le `webServer` de Playwright (via `run-dev-app.sh`), `workers=1` | Avec un seul fichier de tests, le parallélisme n'est pas exercé ; l'isolation par instance et par port sera mise en place quand le nombre de tests le justifiera. **Fragilité à lever** avant que la suite grossisse. |
| 2026-10-07 | 12.2 / profils | Cible d'exécution des tests E2E alors que la cible est PostgreSQL | L'app est empaquetée et lancée en profil `dev` (H2 en mémoire) pour l'E2E ; PostgreSQL reste la cible documentaire jamais exécutée (CLAUDE : « jamais de production ») | Le point d'accès `/api/test` est conditionné au profil `dev` (`@IfBuildProfile`) ; l'E2E doit donc tourner sur un paquet dev, conforme à « tout tourne en profil dev ». |
