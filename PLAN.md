# Plan de construction

Avancement de la construction décrite par `CLAUDE.md` et `LANCEMENT.md`.
Une étape n'est cochée que lorsque `mvn verify` est vert, la couverture Java est
à 100 % (lignes et branches), les tests de bout en bout de l'étape et des étapes
passées sont verts sans relance, la matrice de traçabilité est complète, les cas
listés passent, `README.md` et ce fichier sont à jour, et le commit est fait.

| N° | Étape | Fait | Cas à faire passer |
| --- | --- | --- | --- |
| 0 | Mise en place du dépôt et du socle | [x] | — |
| 1 | Modèle de domaine (68 tables, énumérations) | [x] | — |
| 2 | Sécurité, entités, audit | [x] | CR-70, CR-71 |
| 3 | Référentiels et intégration | [x] | CR-37, CR-38, CR-67, CR-68 |
| 4 | Paramétrage des règles | [x] | — |
| 5 | Moteur : cœur | [x] | CR-01, CR-09 à CR-12, CR-51, CR-73 à CR-75, CR-77 |
| 6 | Précompté, reprises, escompte, emprunteur | [x] | CR-02 à CR-08, CR-20 à CR-28, CR-78 à CR-81 |
| 7 | Épargne retraite | [x] | CR-30 à CR-36, CR-82 à CR-84 |
| 8 | Surcommissions et partage | [x] | CR-40 à CR-50, CR-76 |
| 9 | Cycle, contrôles, ajustements, comptes | [ ] | CR-60, CR-61, CR-63 |
| 10 | Paiement et documents | [ ] | CR-62, CR-64, CR-85, CR-86 |
| 11 | Comptabilité et restitutions | [ ] | CR-65, CR-66 |
| 12 | Simulation et volume | [ ] | CR-69, CR-72 |
| 13 | Interface : restitutions, administration, finition | [ ] | — |
| 14 | Forces de vente | [ ] | CR-87, CR-88 |
| 15 | Jeu de démonstration | [ ] | Parcours complet, de l'import à la clôture |
