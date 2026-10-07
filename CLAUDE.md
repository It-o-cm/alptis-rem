# SI Rémunération

Application de calcul, de contrôle, de paiement et de comptabilisation des commissions versées aux distributeurs d'un groupe d'assurance. Serveur Quarkus, interface Vue.js.

Le plan de construction et la marche à suivre sont dans `LANCEMENT.md`. Ce fichier-ci porte les règles qui valent à chaque session.

## Ce qui fait foi

- `docs/SI-Remuneration-Specification.md` est la référence normative. Elle n'est jamais modifiée. Relis les sections concernées avant de coder une étape.
- `docs/SI-Remuneration-Specification.pdf` est le même document, à la même révision. Il ne sert qu'à une chose : les quatre diagrammes de classes UML de la section 5.7, que le markdown remplace par une mention « embedded content ». Lis ces quatre pages dans le PDF avant l'étape 1. Pour tout le reste, c'est le markdown qui fait foi.
- `docs/maquette/` contient les dix-neuf écrans principaux de l'interface. Pour ces écrans, ils font foi : libellés, colonnes, enchaînements. Leur technique ne t'engage pas. L'application compte bien d'autres écrans : l'inventaire complet est en section 4 de `LANCEMENT.md`.
- En cas de conflit : ce fichier et `LANCEMENT.md` l'emportent pour la technique (socle, structure, tests, conduite) ; la spécification pour tout le reste ; la spécification l'emporte sur la maquette pour une règle ou un montant.

Quand la spécification est muette, ambiguë ou contradictoire :

1. applique le choix par défaut de l'hypothèse correspondante en section 15 (H-01 à H-57) ;
2. sinon, prends le choix le plus simple compatible avec les sections 6 et 12.3 et consigne-le dans `docs/DECISIONS.md` (date, section, question, choix, raison) ;
3. continue.

## Conduite

- **Aucune question à l'utilisateur.** Tu décides, tu consignes, tu avances. Les deux seuls motifs d'arrêt sont dans `LANCEMENT.md`.
- **Périmètre strict.** Tu fais ce que demande l'étape en cours. Pas de fonctionnalité, d'abstraction ou de configuration « au cas où ».
- **Jamais de production.** Tout tourne en profil `dev` : base H2 en mémoire recréée à chaque lancement (`drop-and-create`), données de démonstration rechargées au démarrage, systèmes externes simulés. Pas de migration de schéma, pas de durcissement, pas de gestion de secrets.
- **Langue.** Code, identifiants, commentaires, Javadoc, messages de commit, journaux techniques : en anglais. Tout ce que voit l'utilisateur de l'application (écrans, messages, documents générés) : en français.
- **Javadoc.** Toute méthode Java est documentée, sans exception : constructeurs, accesseurs, méthodes privées, méthodes redéfinies, rappels de cycle de vie.
- **Git.** Un commit à la fin de chaque étape, message en anglais `step NN: <summary>`. Jamais de `push`, jamais de création ni de changement de branche, jamais de réécriture d'historique.
- **Pas de code mort.** Pas de classe vide en attente, pas de `TODO` laissé derrière une étape terminée.

## Socle technique

**Serveur**

- Java 21, Maven, Quarkus (dernière version stable), un seul module, une seule application.
- Hibernate ORM avec Panache. Cible PostgreSQL ; en profil `dev`, H2 en mémoire en mode PostgreSQL. Les colonnes `jsonb` de la section 5.7 sont mappées en JSON portable entre les deux.
- REST avec Jackson, description OpenAPI.
- Sécurité : les neuf rôles de la section 3. En profil `dev`, utilisateurs et rôles déclarés en configuration, un utilisateur par rôle et par entité.
- Montants en `BigDecimal`, jamais de flottant. Arrondi au centime selon P13 (`RoundingMode.HALF_UP`), ligne par ligne. Comparaison par `compareTo`, jamais `equals`.
- Un `DateTimeProvider` injecté partout où le code lit « maintenant » ou « aujourd'hui ». Aucun appel direct à l'horloge système.

**Interface**

- Vue 3, TypeScript, Vite, Vue Router, Pinia. Composants en `<script setup lang="ts">`.
- Application monopage servie par Quarkus (extension Quinoa), sources dans `src/main/webui`.
- Pas de bibliothèque de composants : composants maison, à l'aspect de la maquette (mêmes couleurs, même structure de page, même navigation).
- Aucune règle métier dans l'interface : elle affiche ce que renvoie le serveur et lui envoie des actions. Les droits sont contrôlés côté serveur ; l'interface masque seulement ce qui n'est pas permis.

**API**

- `/api/v1/...` : l'API de la section 9.8, telle que spécifiée.
- `/api/ui/v1/...` : les points d'accès de l'interface. Objets d'échange en `record`, pagination côté serveur, erreurs en `application/problem+json` avec un message en français.

## Patrons

- **Entité de base.** Toutes les entités héritent d'une `BaseEntity` qui porte les colonnes communes des conventions de la section 5.7, plus une version pour le verrou optimiste. Les API cherchent par clé naturelle (codes métier), jamais par identifiant technique.
- **Familles de calcul.** Chaque famille de la section 5.6 (`LINEAR`, `UPFRONT`, `ADVANCE`, `TIERED`, `PORTFOLIO`, `SHARE`, `OUTSTANDING`, `FIXED`, `EXTERNAL`) est un bean CDI découvert par un registre. Elle déclare le JSON Schema de ses paramètres. Le registre valide toute version de règle contre ce schéma, à la saisie comme à l'import, et sert le schéma à l'interface, qui en tire le formulaire de la famille. Ajouter une famille ne touche ni au moteur ni au registre.
- **Imports en masse.** Lecture en flux, écriture par paquets, repli ligne à ligne quand un paquet échoue. Idempotence par clé source (section 9.3) et par empreinte de fichier (section 5.7).
- **Packages.** Racine `fr.alptis.remuneration`. Un package par composant de la section 12.1 : `domain`, `integration`, `rules`, `engine`, `engine.families`, `cycle`, `accounts`, `payment`, `accounting`, `documents`, `reporting`, `security`, `api`. Pas de sous-découpage sans nécessité ; jamais de package à classe unique.
- **Données en ajout seul.** Événements, lignes de commission, écritures et audit ne sont ni modifiés ni supprimés après validation. Seules les lignes non validées d'une période sont supprimées par un recalcul.

## Moteur

La section 12.3 de la spécification est normative, décision par décision (D1 à D15). Là où une implémentation naïve se trompe :

- Une famille ne crée aucune ligne et ne modifie aucun état : elle rend une proposition (D2). Le moteur arbitre, partage, fixe le statut et écrit.
- L'unité de travail du temps 1 est la garantie, pas le distributeur (D4). Ses événements sont traités dans l'ordre chronologique (D5).
- L'état de travail d'une unité reste en mémoire ; une seule transaction à la fin (D7). Le calcul réel et la simulation partagent le même déroulé : seule la destination de l'écriture change.
- L'état de départ d'une unité se reconstruit à partir des lignes validées ; les colonnes de cumul sont une matérialisation (D8). Un calcul relancé dix fois donne dix fois le même résultat.
- Le plafond d'une convention d'escompte se décide dans un temps 0 séquentiel (D9). Les cumuls viennent après les garanties, les comptes après les cumuls (D10).
- Une annulation se calcule avec la version de règle d'origine, pas avec la version en vigueur (D12).
- L'explication stockée avec la ligne est produite par le calcul lui-même (D14).

Les familles sont des classes sans accès à la base : elles reçoivent l'événement, la version de règle et une vue en lecture de l'état.

## Tests

- **Cas de recette.** Les 71 cas CR de la section 13 sont des tests automatisés, un test par cas, nommé d'après son numéro (`cr04_...`). Les montants attendus sont recopiés à la main depuis la spécification, jamais recalculés par le code testé.
- **Exemples chiffrés.** Chaque exemple chiffré des sections 6 et 10 est aussi un test.
- **Familles.** Testées en unitaire pur : sans base, sans conteneur Quarkus, sans simulacre.
- **Couverture par jambe.** Pour toute condition booléenne composée (`a || b || c`, `a && b`), un cas de test par jambe, pas seulement les deux branches du `if`.
- **Couverture totale du serveur, à chaque étape.** Tout le code Java produit par une étape est couvert par des tests unitaires avant la fin de cette étape : 100 % des lignes et 100 % des branches. Les tests s'écrivent avec le code, pas dans une étape de rattrapage.
- **Mesure et garde-fou Java.** `jacoco-maven-plugin` sur tous les tests, y compris les unitaires purs, avec une règle `check` à 100 % de lignes et de branches qui fait échouer `mvn verify`. Seules exclusions admises : le code généré et la classe de démarrage ; toute autre exclusion est interdite.
- **Interface : tests unitaires légers.** Vitest sur les magasins Pinia et les fonctions de présentation (formatage, calculs d'affichage, droits), couverts à 100 %. Les composants Vue n'ont pas de seuil de couverture : leur bon fonctionnement est prouvé par les tests de bout en bout.
- **Tests de bout en bout.** `TESTS-E2E.md` est normatif. Playwright contre l'application réelle en profil `dev`, sans rien simuler du serveur. Quatre niveaux obligatoires : par écran, par cas de recette, par parcours métier, transversal. Un test vérifie le contenu affiché, l'effet vu depuis un autre écran, les fichiers produits et le chemin d'échec. Aucune relance, aucun test désactivé, aucun test sans assertion métier. Un garde-fou de traçabilité fait échouer la construction quand un écran, une exigence, un cas ou un couple rôle-action n'a pas son test.
- **Des tests qui affirment.** Un test vérifie un résultat attendu, pas seulement que le code s'exécute. Une couverture obtenue par des tests sans assertion ne compte pas.

Un test qui échoue se corrige en corrigeant le code. La valeur attendue d'un cas de recette ne change que si la spécification dit autre chose que ce qui avait été recopié.
