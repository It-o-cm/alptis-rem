# Compléter le périmètre

L'application a été déclarée terminée alors qu'elle ne couvre pas tout l'inventaire des écrans ni toutes les exigences. Ce fichier demande de combler l'écart, et de le prouver autrement que par une déclaration. `CLAUDE.md` s'applique en entier (aucune question, jamais de production, Javadoc, couverture Java à 100 %, tests qui affirment). Les règles d'ergonomie d'`ERGONOMIE.md` (socle de composants, menu, garde-fous) et de graphiques de `RESTITUTIONS.md` s'appliquent à tout écran nouveau.

## Mise au point

Le travail rendu jusqu'ici n'est pas acceptable, et il faut que ce soit dit clairement.

- Tu as livré 42 écrans sur 73 et écrit dans `docs/DECISIONS.md` que tous les écrans existaient. C'est une fausse déclaration.
- Tu as rejoué 4 cas de recette par l'interface sur 70, 22 parcours sur 135, et déclaré la suite de bout en bout conforme. `TESTS-E2E.md` était normatif ; tu l'as remplacé, étape après étape, par « acceptation JUnit », en appelant cela une décision.
- Tu as rempli toi-même la liste que le garde-fou devait contrôler, ce qui lui a fait dire que tout était vert.
- Tu as reporté des pans de la spécification « faute de cas de recette » ou « au nom du périmètre strict ». Le périmètre strict interdit d'en faire plus que demandé. Il n'a jamais autorisé d'en faire moins.
- Tu as trouvé un défaut du moteur sur l'escompte et tu l'as contourné dans le jeu de démonstration, alors que la consigne était écrite : corriger le code, jamais contourner.
- Tu as coché toutes les étapes de `PLAN.md` et annoncé que tout était terminé.

Ce qui suit n'est pas négociable. Tout ce que demandent la spécification, `LANCEMENT.md` et `TESTS-E2E.md` doit être fait, en entier. Sont interdits, sans exception :

- reporter, différer, « consigner pour plus tard », livrer « le cœur » d'une exigence ;
- requalifier un manque en décision dans `docs/DECISIONS.md` ; ce fichier sert à trancher un silence de la spécification, pas à s'en dispenser ;
- remplacer un test de bout en bout par un test unitaire, ou un écran par une section d'un autre écran qui n'en a pas le contenu ;
- invoquer l'absence de cas de recette pour ne pas réaliser une règle écrite dans la spécification : dans ce cas, tu réalises la règle et tu écris les tests, avec des montants calculés à la main à partir du texte ;
- déclarer terminé ce qui ne l'est pas. Un compte rendu qui annonce un travail complet alors qu'il reste un manque est le pire résultat possible, bien pire qu'un compte rendu qui dit « il reste ceci ».

Seuls les deux motifs d'arrêt de `LANCEMENT.md` (contradiction dans la spécification, cas de recette impossible à faire passer) peuvent laisser un point non fait. Dans ce cas tu le dis en première ligne du compte rendu, avec les passages en cause, et tu fais tout le reste. « Trop long », « trop gros », « pas de cas de recette », « hors du périmètre de cette passe » ne sont pas des motifs.

## 1. Constat de départ

- L'inventaire de la section 4 de `LANCEMENT.md` compte 73 écrans et se dit lui-même « un minimum ». Le routeur en sert 42.
- `docs/DECISIONS.md` affirme pourtant, à la finition de l'étape 13, que « tous les écrans de l'inventaire existent comme vues réelles ». C'est faux. Le garde-fou de traçabilité ne l'a pas vu parce que sa liste d'exigences a été recopiée à la main, par celui-là même qu'elle devait contrôler.
- Exemples d'écrans de l'inventaire dont on ne trouve aucune trace dans l'interface : réclamations, exécutions de calcul et recalcul partiel, recherche et détail d'un événement, événements en attente par motif, validation métier avec comparaison au mois précédent, détail d'une ligne de commission et de son explication, échéanciers d'apurement, créances d'escompte, fiche d'une adhésion, rapprochement acquis et payé, commissions reçues des assureurs, recherche d'écritures, modèles de documents, attestations fiscales, mesures reçues des forces de vente, synthèse des variables à payer. Cette liste n'est pas complète : c'est à toi d'établir la vraie.
- Deux natures d'écritures comptables prévues par la spécification ne sont jamais générées : `ADVANCE_WRITE_OFF` et `INSURER_RECEIVABLE`. Vérifie et corrige.

## 2. Étape 1 : l'état des lieux, avant tout code

Produis `docs/PERIMETRE.md`, deux tableaux, remplis en lisant le code et non les comptes rendus précédents.

**Écrans.** Une ligne par ligne de l'inventaire de `LANCEMENT.md` (les 73, dans l'ordre), avec : la route qui la sert, la vue, les points d'accès serveur appelés, le fichier et le nom du test de bout en bout qui la prouve, et un état parmi `complet`, `partiel` (dire précisément ce qui manque par rapport au libellé de la ligne et à ses exigences), `absent`. Un écran de l'inventaire servi comme partie d'un autre écran est admis seulement si tout ce que décrit sa ligne y est réellement utilisable ; sinon il est `partiel`.

**Exigences.** Une ligne par exigence de la section 8 de la spécification (tous les identifiants, lus dans le fichier), avec : les classes serveur qui la réalisent, l'écran qui l'expose, les tests qui la prouvent, et le même état à trois valeurs. Une exigence dont le test ne vérifie que la présence d'un écran est `partiel`.

Ajoute les écrans que les exigences supposent et qui ne sont pas dans l'inventaire, comme le demande `LANCEMENT.md`.

Commit de cet état des lieux seul, avant toute correction : `scope: honest inventory`.

## 3. Étape 2 : combler

Traite toutes les lignes `absent` et `partiel` de l'état des lieux, y compris les simplifications de la section suivante, rubrique par rubrique, dans l'ordre de l'inventaire. Pour chaque écran :

- le serveur d'abord, selon la spécification : services, points d'accès sous `/api/ui/v1`, droits par rôle de la section 3, cloisonnement par entité, tests unitaires à 100 % ;
- l'écran ensuite, sur le socle de composants, selon les règles « écran présent dans la maquette » et « écran absent de la maquette » de la section 4 de `LANCEMENT.md` ;
- son entrée dans le menu, rangée dans la bonne rubrique, ou son accès depuis l'écran dont il est le détail ;
- ses données dans le jeu de démonstration, produites par les services réels, pour qu'il ne s'affiche pas vide ;
- ses tests de bout en bout aux quatre niveaux de `TESTS-E2E.md`.

Un écran n'est pas une coquille : ce que décrit sa ligne d'inventaire doit fonctionner de bout en bout, avec son effet visible depuis un autre écran. Plus aucune route ne pointe sur `PlaceholderView`, et ce composant est supprimé.

Quand la spécification est muette, applique la règle de `CLAUDE.md` (hypothèse de la section 15, sinon le plus simple, consigné dans `docs/DECISIONS.md`).

## 4. Simplifications fonctionnelles à reprendre

Voici celles que tu as toi-même consignées. Chacune est à réaliser selon la spécification, avec ses tests unitaires et de bout en bout. La liste n'est pas close : relis tout `docs/DECISIONS.md`, relève chaque ligne qui reporte, approxime, réduit ou contourne, et traite-la de la même façon. L'état des lieux de l'étape 1 les recense toutes.

**Moteur et règles**

- Les cas de recette CR-74 (prorata d'un taux changé en cours de période couverte, P12), CR-80 (escompte lors d'une cession de portefeuille) et CR-81 (rachat anticipé emprunteur) n'ont ni réalisation ni test. Les 71 cas doivent exister.
- Le défaut de `openAdvanceIfEligible`, qui rouvre une position d'escompte quand une garantie est retraitée, est corrigé dans le moteur, et l'apurement d'un escompte fonctionne d'un mois sur l'autre. Le contournement du jeu de démonstration (garantie dédiée sans événement) est retiré : le jeu porte un escompte réel suivi sur plusieurs mois.
- Épargne retraite : la règle linéaire à 1,20 % des versements programmés à partir de l'année 6 et le paramètre `seniorityOrigin` (section 6.6).
- Épargne retraite : le délai de versement de l'escompte suit le texte (« la période qui suit le premier versement programmé encaissé »), pas une approximation.
- Section 6.7 : le comportement `DEFER` (rattrapage du mois perdu), pas seulement `FORFEIT`.
- Commissions reçues des assureurs : le montant attendu est une donnée à part entière, pas un résultat de contrôle créé à la volée.

**Comptabilité et rapprochements**

- Les écritures `ADVANCE_WRITE_OFF` et `INSURER_RECEIVABLE` sont générées.
- FIN-06 (rapprochement des commissions acquises et payées) et FIN-08 (commissions reçues des assureurs, attendu et marge) ont chacun leur traitement et leur écran, au lieu d'être « servis par » le cut-off ou par un contrôle.

**Flux**

- L'export vers la plateforme Data est incrémental comme le demande la spécification, ce qui suppose de tenir à jour la date de modification des objets.
- CR-72 est exécuté au volume de la spécification (20 000 bénéficiaires, 420 000 événements), dans un test lancé à part de la construction courante, et son résultat mesuré est donné. Une extrapolation à partir de 60 bénéficiaires n'est pas une mesure.

**Forces de vente**

- Tout ce qui a été reporté est réalisé : qualitatif trimestriel (FDV-07), collectif annuel (FDV-08), régularisation annuelle (FDV-06), barèmes animateurs, responsables et vente directe (FDV-09, FDV-10), réduction sur résiliation (FDV-12), simulateur (FDV-16). Là où la spécification porte « à définir » sur un montant, le mécanisme est réalisé et le montant devient un paramètre du plan, renseigné dans le jeu de démonstration par une valeur vraisemblable et signalé dans `docs/DECISIONS.md`.

**Interface**

- La demande de confirmation est raccordée à toutes les actions irréversibles (clôture, validation, purge, rejet).
- Plus aucun code technique à l'écran : les énumérations (`OUTBOUND`, `CYCLE`, `PREMIUM_COLLECTED`, `LINEAR`, `CLAWBACK`…) sont affichées par leur libellé français du glossaire, les mois et les dates au format français.
- L'entité, le mois, le distributeur et toute valeur connue du système se choisissent dans une liste ou une recherche, plus jamais par saisie libre d'un code.
- Pour ces trois points, les tests de bout en bout sont adaptés en même temps : changer l'assertion d'un test parce que l'écran affiche désormais le libellé au lieu du code n'est pas l'affaiblir. L'argument « la suite existante vérifie les codes bruts » ne justifie pas de les laisser.

**Tests**

- Les tests de bout en bout tournent comme le demande la section 1 de `TESTS-E2E.md` (une instance d'application par processus de test), pas sur une instance unique partagée.

## 5. Étape 3 : le garde-fou ne se remplit plus à la main

Réécris le garde-fou de traçabilité pour qu'il tire ses listes des sources, à chaque construction :

- les écrans attendus sont lus dans les tableaux de la section 4 de `LANCEMENT.md` ;
- les exigences attendues sont lues dans la section 8 de la spécification ;
- les cas de recette attendus sont lus dans la section 13 ;
- les routes réelles sont lues dans le routeur, les tests réels dans les fichiers de tests.

La correspondance entre une ligne d'inventaire et sa route est déclarée dans un fichier unique, versionné. La construction échoue si une ligne d'inventaire n'a pas de route, si une route n'a pas de test de bout en bout qui s'y rend et y vérifie un contenu métier, si une exigence ou un cas de recette n'a pas de test, ou si `docs/PERIMETRE.md` contient encore une ligne `absent` ou `partiel`. Aucune liste d'exceptions.

Corrige la ligne fausse de `docs/DECISIONS.md` par une ligne datée qui la rectifie, sans effacer l'ancienne.

## 6. Étape 4 : rattraper les tests de bout en bout sur toute l'application

`TESTS-E2E.md` est normatif et n'a pas été respecté. État constaté dans `src/test/e2e/tests` : 4 cas de recette rejoués par l'interface sur 70 attendus (étiquette `@cr:`), 22 parcours métier sur les 135 du catalogue (étiquette `@path:`), 21 couples rôle-action. Le rattrapage porte sur tous les écrans, les anciens comme les nouveaux.

- **Niveau 1, par écran.** Chaque ligne de l'inventaire a son fichier de tests, avec tous les points de la liste de son genre (section 2 de `TESTS-E2E.md`).
- **Niveau 2, par cas de recette.** Chacun des cas CR de la section 13 de la spécification, sauf CR-72, est rejoué par l'interface comme le décrit la section 3 de `TESTS-E2E.md` : chargement des données du cas, calcul lancé depuis l'écran, montants vérifiés sur le compte, sur l'explication de la ligne, et selon le cas sur la convention d'escompte, le bordereau et les écritures. Les montants attendus sont ceux de la spécification, recopiés à la main. Un test JUnit ne remplace pas ce test par l'interface : les décisions de `docs/DECISIONS.md` qui renvoient des cas « au niveau JUnit » sont rectifiées.
- **Niveau 3, parcours métier.** Chaque ligne des tableaux de la section 4 de `TESTS-E2E.md` a son test, avec toutes les vérifications de sa colonne.
- **Niveau 4, transversal.** Les sept points de la section 5 de `TESTS-E2E.md`, sur la totalité des actions, listes, fiches, exports et ressources d'API : droits (le tableau rôle-action couvre toutes les actions de l'application), cloisonnement, séparation des tâches, audit, ajout seul, session, navigation.
- **Règles de solidité.** Celles de la section 1 de `TESTS-E2E.md`, sans exception : contenu affiché vérifié, effet vu depuis un autre écran, fichiers produits ouverts et contrôlés, chemin d'échec, aucune relance, aucun test désactivé, aucun test sans assertion métier. Passe en revue les tests existants et renforce ceux qui se contentent de vérifier qu'un écran s'affiche.

Le garde-fou lit aussi `TESTS-E2E.md` : les numéros de parcours dans les tableaux de la section 4, les genres d'écran de la section 2. La construction échoue si un cas CR n'a pas son test par l'interface, si un parcours n'a pas son test, si un couple rôle-action n'est pas testé, ou si un écran n'a pas tous les points de son genre.

## 7. Tests

- `mvn verify` passe, règle JaCoCo à 100 % comprise, et la suite de bout en bout passe en entier.
- La suite existante reste verte sans être affaiblie : aucun test supprimé, désactivé ou allégé.
- Chaque écran nouveau a ses tests de contenu, d'effet vu d'un autre écran, de droits par rôle et de chemin d'échec, et passe le test d'alignement d'`ERGONOMIE.md`.

## 8. Hors périmètre

Rien de ce qui relève de la mise en production : pas de raccordement à des systèmes réels, pas d'authentification unique, pas de migration de schéma, pas de durcissement. Aucune modification de la spécification ni des fichiers d'instructions existants.

## 9. Fin de travail

Un commit par rubrique, message `scope: <summary>`, jamais de `push`. Le travail est long : avance rubrique par rubrique sans t'arrêter pour demander, et ne t'arrête que sur l'un des deux motifs de `LANCEMENT.md`.

**Tu vérifies toi-même, au premier plan.** Tu t'es arrêté une première fois en disant que le reste du travail « repose sur les suites de bout en bout et de démonstration » et que tu ne pouvais pas les vérifier en arrière-plan. Ce n'est pas un motif d'arrêt, c'est une raison de les lancer.

- Tu as déjà lancé cette suite toi-même à plusieurs reprises (étapes 0 à 15, jeu de démonstration, ergonomie). Tu la lances de la même façon, au premier plan, en attendant son résultat, avec un délai d'attente suffisant.
- **Cycle de mise au point d'un parcours (court).** Tu ne lances pas `mvn verify` à chaque itération : il enchaîne la construction Vue, vitest, les 1 300 JUnit, JaCoCo et l'empaquetage, soit 5 à 10 minutes, et tu le relances deux ou trois fois par parcours. Pendant la mise au point :
  - côté Java : `mvn test -Dtest=ClasseTouchee` (ou `-Dtest='ClasseA,ClasseB'`), quelques secondes ;
  - côté Vue : `npx vitest run <fichier>` dans `src/main/webui` ;
  - côté bout en bout : tu démarres l'application une seule fois pour tout le parcours, en arrière-plan, avec `bash src/test/e2e/run-dev-app.sh 8099` (le script empaquette puis lance le jar). Tant qu'elle tourne, `npx playwright test tests/<fichier>.spec.ts` ou `--grep "@path:XXX-nn"` depuis `src/test/e2e` la réutilise (`reuseExistingServer`) sans rien reconstruire : un test ciblé coûte alors 30 secondes. Tu ne la redémarres qu'après un changement de code Java ou Vue (tu l'arrêtes, tu relances le script). La suite complète tourne déjà sur une seule instance, les tests sont conçus pour cela.
- **Vérification finale d'un parcours (une seule fois, avant le commit).** Quand le parcours est vert en cycle court : `mvn verify -DskipE2E=true` (JUnit complet, règle JaCoCo 100 %, garde-fou UX, empaquetage), puis le test de bout en bout ciblé sur l'application fraîchement empaquetée, puis le commit. Si cette vérification révèle un manque (une branche non couverte, par exemple), tu corriges en cycle court et tu ne relances la vérification finale qu'une fois.
- Un changement qui ne touche que `docs/` ne déclenche aucune construction.
- **Économie de contexte.** Ta fenêtre de contexte est la ressource qui t'a arrêté une fois (97 % consommés). Les sorties de construction la remplissent pour rien : tu lances Maven avec `-q`, tu rediriges toute commande longue vers un fichier (`> /tmp/build.log 2>&1`) et tu n'en lis que la fin (`tail -30`) ou les lignes utiles (`grep -E 'ERROR|FAIL|Tests run'`), jamais la sortie entière. Même chose pour Playwright (`--reporter=line`, sortie redirigée). Tu ne relis pas des fichiers entiers que tu connais déjà : `grep -n` puis la plage de lignes concernée.
- Après le commit, tu enchaînes immédiatement le parcours suivant dans le même tour. Tu ne termines pas ton tour après un commit : la boucle n'est qu'un filet, pas une cadence.
- À la fin de chaque rubrique (tous les parcours d'un domaine, tous les cas de recette manquants, le garde-fou), et avant le compte rendu final : `mvn verify` sans option, c'est-à-dire la suite de bout en bout complète, le garde-fou et le test de visibilité du jeu de démonstration. Si elle casse, tu corriges avant de passer à la rubrique suivante.
- Un changement « transversal » (plusieurs cas de recette, le jeu de démonstration et des écrans touchés ensemble) se traite dans un seul commit cohérent, tests adaptés compris. Qu'un changement touche plusieurs suites est normal ici ; ce n'est pas un risque à éviter, c'est le travail demandé.
- Si une suite casse, tu corriges. Tu ne t'arrêtes pas pour ne pas risquer de la casser.
- Tu es lancé en boucle (`/loop`). Chaque passage de la boucle est un tour de travail normal : tu y lances les commandes au premier plan et tu attends leur résultat, suites de bout en bout comprises. Il n'existe pas de « mode arrière-plan » qui t'empêcherait de vérifier.
- À chaque passage : tu lis `docs/PERIMETRE.md`, tu prends la première ligne non `complet`, tu la réalises entièrement avec ses tests, tu lances les vérifications, tu commites, tu mets l'état des lieux à jour. Puis tu enchaînes sur la ligne suivante tant que le passage le permet.
- **Tu ne demandes ni confirmation ni priorisation.** « Je commence X sauf si tu me rediriges », « dis-moi si tu préfères Y » : ce sont des fins de tour déguisées, qui coûtent un passage de boucle à chaque fois. L'ordre est fixé : d'abord la réécriture du garde-fou (étape 3), pour que la construction énumère elle-même les cas, parcours et couples manquants ; ensuite les parcours restants dans l'ordre des tableaux de la section 4 de `TESTS-E2E.md`. Qu'un parcours exige une fonctionnalité absente ou un montage lourd n'est pas un mur : c'est exactement ce que cette passe doit révéler et combler, une fonctionnalité ou un montage par commit, avec ses tests à tous les niveaux.
- **Tu ne mets jamais fin à la boucle toi-même.** Tu ne l'arrêtes pas, tu ne la suspends pas, tu ne conclus pas qu'il vaut mieux s'arrêter. La seule fin admise est celle où les quatre conditions ci-dessous sont vraies ; ce jour-là tu l'écris en première ligne du compte rendu, et c'est l'utilisateur qui arrête la boucle.

**Tout est exigé, et le travail n'est fini que lorsque tout est fait.** Les quatre conditions suivantes doivent être vraies en même temps :

1. `docs/PERIMETRE.md` ne contient plus aucune ligne `absent` ou `partiel` : les 73 écrans de l'inventaire, toutes les exigences de la section 8, toutes les simplifications de la section 4 de ce fichier et celles que tu auras relevées en plus ;
2. les 71 cas de recette existent, 70 sont rejoués par l'interface, tous les parcours de `TESTS-E2E.md` et tous les couples rôle-action ont leur test ;
3. le garde-fou réécrit, qui lit ses listes à la source, passe sans aucune exception ;
4. `mvn verify` et la suite de bout en bout complète viennent d'être exécutés et passent.

Tant qu'une de ces conditions est fausse, tu continues. La longueur du travail, le nombre d'écrans ou de tests restants et la taille de la session ne sont pas des raisons de s'arrêter ni de réduire : si la session s'interrompt, l'état des lieux tenu à jour dans `docs/PERIMETRE.md` et commité à chaque rubrique permet de reprendre exactement là où tu en étais, et relancer ce fichier reprend le travail à la première ligne non `complet`. Tiens donc cet état des lieux à jour à chaque commit.

Le compte rendu final donne des nombres, pas des adjectifs : lignes d'inventaire `complet` sur 73, exigences `complet` sur le total, cas de recette rejoués par l'interface sur 70, parcours testés sur le total du catalogue, couples rôle-action testés sur le total, écrans ajoutés, tests ajoutés, résultat de la dernière exécution de `mvn verify` et de la suite de bout en bout. Si une seule ligne reste `absent` ou `partiel`, ou s'il manque un seul cas, parcours ou couple, le travail n'est pas terminé : dis-le en première ligne du compte rendu, avec la liste, et ne coche rien.
