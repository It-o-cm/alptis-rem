# Restitutions : mise au niveau de la spécification et graphiques

Les écrans de restitution sont en deçà de la spécification et n'affichent que des tableaux. Ce fichier demande deux choses : amener les restitutions au niveau des exigences REP-01 à REP-09, puis ajouter des graphiques partout où ils aident à lire les données. `CLAUDE.md` s'applique en entier (aucune question, périmètre strict, jamais de production, Javadoc, couverture Java à 100 %, tests qui affirment), sauf le point 4.1 ci-dessous qui le précise.

Ce travail suppose que `DONNEES-DEMO.md` est terminé : les graphiques n'ont de sens qu'avec plusieurs mois de données. Si le jeu de démonstration riche n'est pas en place, arrête-toi et dis-le.

## 1. Constat de départ

- « Tableaux de bord » donne les commissions par nature pour un seul mois, figé dans le code sur septembre 2026. REP-01 demande les commissions par mois, nature, gamme et entité, les reprises, les escomptes versés, apurés et les créances, les comptes débiteurs, les premiers distributeurs et les anomalies.
- « Vision consolidée groupe » donne un seul total par entité.
- « Indicateurs de fonctionnement » donne trois chiffres ; REP-09 en demande quatre, dont la durée du cycle.
- Aucun écran ne contient de graphique et aucune bibliothèque graphique n'est installée.

## 2. Partie A : atteindre la spécification

Relis dans `docs/SI-Remuneration-Specification.md` les exigences REP-01 à REP-09 et toutes les sections qui les détaillent. Pour chacune, compare ce que demande le texte à ce que font réellement le serveur et l'écran, puis comble chaque écart. Écris le résultat de cette comparaison dans `docs/DECISIONS.md`, une ligne par exigence : ce qui manquait, ce qui a été fait.

Écarts déjà connus, à traiter sans attendre la comparaison :

- **REP-01.** Tableau de bord sur une plage de mois choisie par l'utilisateur (par défaut les six derniers mois ayant des données), avec les axes mois, nature, gamme et entité, et toutes les rubriques citées par l'exigence. Aucun mois écrit en dur dans l'interface.
- **REP-03.** Les filtres produit, distributeur et hiérarchie, délégation commerciale, nature et période s'appliquent aux tableaux de bord comme à la recherche de commissions.
- **REP-04.** Toute liste des écrans de restitution s'exporte en CSV et en tableur, à la maille de la ligne.
- **REP-07.** Vision groupe par entité et par mois, avec les mêmes rubriques que le tableau de bord d'une entité, réservée aux utilisateurs habilités.
- **REP-08.** Marge par produit, par assureur et par distributeur.
- **REP-09.** Les quatre indicateurs (règles actives, durée du cycle, part des lignes ajustées à la main, montants repris), mois par mois.

Tous les agrégats sont calculés par le serveur, à partir des lignes de commission, des comptes et des écritures, dans le respect du cloisonnement par entité. L'interface n'additionne rien.

## 3. Partie B : graphiques

### 3.1 Liste minimale

| Écran | Graphique | Ce qu'il montre |
|---|---|---|
| Tableaux de bord | Barres empilées par mois | Commissions mois par mois, ventilées par nature |
| Tableaux de bord | Anneau | Répartition de la période par nature, et un second par gamme |
| Tableaux de bord | Barres horizontales | Premiers distributeurs |
| Tableaux de bord | Courbes | Escomptes versés, apurés et créance restante dans le temps |
| Tableaux de bord | Courbe | Reprises mois par mois |
| Vision consolidée groupe | Barres groupées | Les entités côte à côte, mois par mois |
| Marge (REP-08) | Barres horizontales | Marge par produit, par assureur, par distributeur |
| Simulation | Barres groupées réel / simulé | Écart par nature et par distributeur |
| État de cut-off | Barres empilées | Acquis, payé, solde à payer, reste à acquérir |
| Fiche et compte | Courbe | Solde du compte du distributeur dans le temps |
| Mon variable | Jauge par composante | Atteinte de l'objectif |
| Validation mensuelle (force de vente) | Barres | Atteinte par collaborateur |
| Contrôles | Barres | Anomalies par sévérité et par famille de contrôle |
| Indicateurs de fonctionnement | Courbes | Les quatre indicateurs de REP-09 mois par mois |

### 3.2 Au-delà de la liste

Passe ensuite en revue tous les autres écrans. Ajoute un graphique quand les trois conditions sont réunies : l'écran affiche une série chiffrée (dans le temps, par catégorie ou en parts d'un total), le graphique fait voir quelque chose que le tableau ne montre pas d'un coup d'œil, et les données existent ou se calculent côté serveur. Dans le cas contraire, n'en mets pas : pas de graphique décoratif, pas de graphique sur un compteur isolé, un état ou une liste de référentiel. Consigne dans `docs/DECISIONS.md` les écrans examinés et la décision prise pour chacun, en une ligne.

### 3.3 Choix de la forme

- Évolution dans le temps : courbe, ou barres quand il y a moins de quatre points.
- Parts d'un total : anneau, seulement jusqu'à six parts ; au-delà, regrouper le reste dans « Autres » ou passer en barres horizontales.
- Comparaison entre catégories : barres, triées par valeur décroissante sauf ordre naturel (mois, tranches).
- Atteinte d'un objectif : jauge avec la cible marquée.
- Pas de 3D, pas de double axe, pas d'axe des ordonnées tronqué sur des barres.

## 4. Réalisation des graphiques

### 4.1 Composants maison en SVG

Aucune bibliothèque graphique. Les graphiques sont des composants Vue maison qui produisent du SVG, dans `src/main/webui/src/components/charts`, un composant par forme (courbes, barres avec variantes empilées, groupées et horizontales, anneau, jauge). Toute la géométrie (échelles, graduations, arrondis des graduations, angles des parts, empilement, regroupement en « Autres ») est faite de fonctions pures dans `src/main/webui/src/presentation`, couvertes à 100 % par Vitest comme le reste de ce dossier.

### 4.2 Règles communes

- Chaque graphique a un titre qui dit ce qu'il montre, l'unité, la période et l'entité concernées.
- Montants et pourcentages au format français, par les fonctions de formatage existantes.
- Une couleur désigne la même chose sur tous les écrans (une nature, une entité, réel contre simulé). La palette part des couleurs de la maquette, reste lisible pour un daltonien et ne repose jamais sur la couleur seule : légende textuelle et valeurs lisibles.
- Au survol et au clavier, chaque point, barre ou part donne sa valeur exacte.
- Le tableau de données reste présent sous le graphique : le graphique s'ajoute, il ne remplace rien.
- Chaque élément tracé porte un `data-testid` et un libellé accessible contenant sa catégorie et sa valeur, pour que les tests lisent ce qui est dessiné.
- Cas limites traités et testés : aucune donnée (message, pas de repère vide), une seule valeur, valeurs négatives (reprises, soldes débiteurs), valeurs toutes nulles, libellés longs.
- Le graphique suit les filtres de l'écran et se redessine quand ils changent.

## 5. Tests

- **Serveur.** Chaque agrégat nouveau a ses tests unitaires, avec des montants attendus posés à la main : par mois, par nature, par gamme, par entité, marge, indicateurs, cloisonnement par entité, plage vide. Règle JaCoCo à 100 % respectée.
- **Vitest.** Les fonctions de géométrie à 100 %, cas limites compris.
- **Playwright.** Pour chaque graphique : il est présent, le nombre d'éléments tracés correspond aux données, les valeurs portées par les éléments sont celles du tableau affiché dessous et celles attendues du jeu chargé, et un changement de filtre change le tracé. Un utilisateur d'une entité ne voit jamais les chiffres de l'autre. Les exports de REP-04 sont téléchargés et leur contenu vérifié.
- La suite existante reste verte sans être affaiblie : aucun test supprimé, désactivé ou allégé. Le garde-fou de traçabilité est complété pour les exigences REP et pour chaque graphique.

## 6. Hors périmètre

Rien en dehors des restitutions et des graphiques : pas d'autre rattrapage de la spécification, pas de refonte des écrans, pas de modification de la spécification, de `CLAUDE.md`, de `LANCEMENT.md`, de `TESTS-E2E.md` ni de `DONNEES-DEMO.md`. Un défaut rencontré hors périmètre est consigné dans `docs/DECISIONS.md`, pas corrigé, sauf s'il empêche ce travail.

## 7. Fin de travail

Un commit par bloc cohérent, message `reporting: <summary>`, jamais de `push`. Termine par un compte rendu honnête : exigence par exigence ce qui est atteint ou non, la liste des graphiques posés écran par écran, les écrans écartés en 3.2, les tests ajoutés. Ne déclare rien terminé qui n'a pas été vérifié par un test exécuté.
