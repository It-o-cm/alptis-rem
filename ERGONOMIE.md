# Passe ergonomique

L'interface fonctionne mais elle a été mise en page écran par écran, sans socle commun : menu de 45 entrées toujours dépliées, formulaires dont les libellés et les champs ne sont pas alignés, 356 styles écrits en ligne dans les vues pour une feuille commune de 215 lignes. Ce fichier demande une passe ergonomique complète. `CLAUDE.md` s'applique en entier (aucune question, périmètre strict, jamais de production, tests qui affirment).

Ce travail se lance après `DONNEES-DEMO.md` et `RESTITUTIONS.md`. Il porte sur tous les écrans existants à ce moment-là, graphiques compris.

## 1. Principe

On ne retouche pas les écrans un par un. On construit d'abord un socle de composants et de règles de mise en page, puis on y fait passer tous les écrans sans exception. À la fin, aucune vue ne porte sa propre mise en page.

L'aspect reste celui de la maquette (`docs/maquette/`) : mêmes couleurs, même typographie, même structure de page. Cette passe ne change ni les fonctions, ni les droits contrôlés par le serveur, ni les libellés métier, ni les `data-testid` existants.

## 2. Menu

### 2.1 Rubriques dépliables

- Chaque rubrique du menu se replie et se déplie d'un clic sur son titre, et au clavier. L'état ouvert ou fermé est annoncé aux lecteurs d'écran.
- La rubrique de l'écran courant est toujours ouverte, et l'entrée courante est visible sans faire défiler.
- L'état de chaque rubrique est mémorisé par utilisateur dans le navigateur, avec repli propre si le stockage est indisponible.
- Le menu ne fait jamais défiler la page entière : s'il dépasse la hauteur de la fenêtre, lui seul défile.

### 2.2 Un menu à la mesure de chaque rôle

Aujourd'hui une entrée s'affiche dès que le rôle a le droit de consulter l'écran, ce qui donne presque tout le menu à presque tout le monde. Les droits ne changent pas ; c'est la présentation qui change.

- À partir de la section 3 de la spécification (missions de chaque rôle), établis pour chacun des neuf rôles ses rubriques de travail : celles où il agit au quotidien. Écris ce tableau dans `docs/DECISIONS.md`.
- À la première connexion, seules les rubriques de travail du rôle sont ouvertes ; les autres sont repliées. Pour un utilisateur à plusieurs rôles, c'est l'union.
- Dans chaque rubrique, les écrans où le rôle agit viennent avant ceux qu'il ne fait que consulter.
- Une rubrique sans aucune entrée visible n'apparaît pas.
- La correspondance rôle, rubriques de travail est une fonction pure dans `src/main/webui/src/presentation`, couverte à 100 %.

### 2.3 Repères

Chaque écran affiche un titre et, au-dessus, le nom de sa rubrique, de façon identique partout. L'en-tête garde l'entité et le rôle.

## 3. Socle de composants

Dans `src/main/webui/src/components`, des composants maison, sans bibliothèque :

- **Formulaire et champ.** Les libellés forment une colonne, les champs une autre, alignées sur toute la hauteur du formulaire. Les formulaires longs passent sur deux colonnes de couples libellé-champ au-delà d'une largeur donnée, et reviennent à une seule sur écran étroit. Chaque champ a son libellé relié (`for`/`id`), sa marque d'obligation, son aide et son message d'erreur au même endroit partout. La largeur d'un champ suit sa nature : court pour une date, un taux ou un montant, long pour un libellé.
- **Barre de filtres.** Les critères de recherche au-dessus d'une liste ont une présentation unique, avec les actions « Rechercher » et « Réinitialiser » au même endroit.
- **Tableau.** En-têtes homogènes, montants et nombres alignés à droite en chiffres à chasse fixe, dates et codes dans un format unique, ligne vide explicite, pagination identique partout, colonnes d'action en dernier.
- **Boutons.** Trois niveaux seulement (action principale, secondaire, dangereuse), une seule action principale par écran, ordre et emplacement constants.
- **Messages.** Succès, erreur et avertissement au même endroit sur tous les écrans, annoncés aux lecteurs d'écran.
- **États de chargement.** Tant qu'une liste charge, l'écran le dit ; « Aucun résultat » ne s'affiche qu'après la réponse du serveur. Un bouton d'action est neutralisé pendant son appel.
- **Fiche.** Le bloc détail d'un objet (couples libellé-valeur en lecture) a lui aussi ses colonnes alignées.
- **Confirmation.** Toute action irréversible (clôture, validation, purge, rejet) passe par la même demande de confirmation.

Les espacements, tailles et couleurs viennent de variables communes dans `styles.css`. Plus aucun attribut `style` dans les vues ni dans les composants, sauf valeur calculée à l'exécution (géométrie d'un graphique, largeur d'une jauge).

## 4. Revue de tous les écrans

Après le socle, passe chaque écran du routeur sur les composants communs. Puis fais une revue visuelle réelle : lance l'application avec le jeu de démonstration, capture chaque écran avec Playwright pour un utilisateur qui y a accès, en 1440 et en 1024 pixels de large, et regarde chaque capture. Relève et corrige tout ce qui tombe dans cette liste, et tout défaut du même ordre :

- libellés et champs non alignés, champs de largeurs incohérentes, formulaire qui s'étire sur toute la largeur sans raison ;
- contenu qui déborde, se chevauche, est tronqué sans indication, ou barre de défilement horizontale de page ;
- tableau illisible : trop de colonnes sans priorité, montants non alignés, en-têtes qui passent à la ligne ;
- espacements irréguliers entre blocs, titres de niveaux incohérents ;
- boutons d'apparence, d'ordre ou de place différents d'un écran à l'autre ;
- valeurs techniques montrées telles quelles : codes d'énumération en anglais, identifiants internes, dates au format ISO, `null`, `undefined`, booléens `true`/`false` ;
- saisie d'une valeur connue du système dans un champ libre (code d'entité, mois, code distributeur) au lieu d'une liste ou d'une recherche ;
- valeurs par défaut écrites en dur (un mois, une entité) au lieu de découler de la session et des données ;
- écran sans état vide, sans état de chargement ou sans message d'erreur lisible ;
- contraste insuffisant, cible cliquable trop petite, ordre de tabulation illogique, champ sans libellé accessible.

Consigne dans `docs/DECISIONS.md` un tableau des écrans : défauts relevés, corrections faites. Les captures restent dans un dossier ignoré par git.

## 5. Tests

- **Vitest.** Les fonctions de présentation nouvelles à 100 %.
- **Playwright, menu.** Repli et dépli à la souris et au clavier, rubrique courante ouverte, état conservé après rechargement, rubriques ouvertes par défaut conformes au tableau pour un utilisateur de chaque rôle, aucune rubrique vide.
- **Playwright, alignement.** Un test générique parcourt tous les écrans du routeur et mesure les positions réelles : dans un même formulaire ou une même fiche, tous les libellés ont le même bord gauche et tous les champs le même bord gauche ; les cellules de montant sont alignées à droite ; aucune barre de défilement horizontale de page en 1440 et en 1024.
- **Playwright, états.** Sur une réponse ralentie, l'état de chargement s'affiche et « Aucun résultat » n'apparaît pas avant la réponse.
- **Garde-fous de construction.** La construction échoue si une vue ou un composant contient un attribut `style` hors de la liste d'exceptions nommées, si une vue contient un `<label>`, un `<table>` ou un `<button>` nu au lieu du composant commun, ou si un écran du routeur n'est pas couvert par le test d'alignement.
- La suite existante reste verte sans être affaiblie : aucun test supprimé, désactivé ou allégé, aucun `data-testid` renommé.

## 6. Hors périmètre

Aucune nouvelle fonction, aucun nouvel écran, aucun changement des droits côté serveur, aucune modification de la spécification, de `CLAUDE.md`, de `LANCEMENT.md`, de `TESTS-E2E.md`, de `DONNEES-DEMO.md` ni de `RESTITUTIONS.md`. Pas de thème sombre, pas de version mobile : la cible est un poste de bureau, de 1024 pixels de large à plus.

## 7. Fin de travail

Un commit par bloc cohérent (socle, menu, puis écrans par rubrique), message `ux: <summary>`, jamais de `push`. Termine par un compte rendu honnête : composants créés, tableau rôle par rubriques, nombre d'écrans passés sur le socle et ceux qui ne le sont pas, défauts relevés et corrigés, défauts laissés et pourquoi. Ne déclare rien terminé qui n'a pas été vérifié par un test exécuté ou par une capture regardée.
