# Tests de bout en bout

Les composants Vue n'ont pas de seuil de couverture unitaire : c'est cette suite qui prouve que l'application fonctionne, écran par écran et règle par règle. Elle doit être solide. Un test de bout en bout superficiel est pire qu'un test absent, parce qu'il rassure à tort.

La suite a quatre niveaux, tous obligatoires :

1. **par écran** : chacun des écrans de l'inventaire (`LANCEMENT.md`, section 4) a son fichier de tests, qui couvre tout ce que l'écran permet ;
2. **par cas de recette** : chaque cas CR de la spécification est rejoué par l'interface ;
3. **par parcours métier** : le catalogue de la section 4 de ce fichier ;
4. **transversal** : droits, cloisonnement, traçabilité, robustesse.

Un garde-fou automatique (section 6) fait échouer la construction si un écran, une exigence, un cas de recette ou un couple rôle-action n'a pas son test.

## 1. Règles de solidité

**Environnement**

- Playwright, Chromium. Lancé par `mvn verify`.
- L'application réelle, en profil `dev`, avec sa vraie base H2 et son vrai serveur. Aucun appel au serveur n'est simulé. Seuls les systèmes externes le sont, par les répertoires locaux prévus en section 6 de `LANCEMENT.md`.
- Une instance d'application par processus de test, sur son propre port, pour que les tests tournent en parallèle sans se gêner.
- Un point d'accès réservé au profil `dev`, `/api/test`, remet la base à zéro, charge un jeu de données nommé et fixe l'horloge du `DateTimeProvider`. Il n'existe dans aucun autre profil. C'est le seul raccourci autorisé : toute action métier passe par l'interface.

**Écriture des tests**

- Chaque test est indépendant : il charge son jeu de données, fixe son horloge, se connecte avec l'utilisateur d'un rôle, et ne suppose rien d'un autre test.
- Un objet de page par écran. Sélecteurs par rôle d'accessibilité et libellé, à défaut par `data-testid`. Jamais par classe CSS ni par position.
- Aucune attente fixe. Aucune relance automatique : `retries` vaut 0. Un test instable est un défaut à corriger, dans le test ou dans l'application.
- Les jeux de données sont construits par des fabriques lisibles (`aDistributor().withAdvanceAgreement()...`), pas par des fichiers SQL opaques.

**Ce qu'un test doit vérifier**

- Le contenu affiché : libellés, montants au centime, dates, statuts, compteurs. Les montants attendus sont ceux de la spécification, recopiés à la main.
- L'effet, vu depuis un autre écran. Après l'approbation d'un ajustement, le test va lire le compte du distributeur ; après la validation d'un lot, il va lire la période, le bordereau et la ligne. Un message « enregistré » ne prouve rien.
- La persistance : après rechargement de la page, l'état est toujours là.
- Les fichiers produits : le test télécharge le fichier et l'analyse. CSV lu colonne par colonne, XML pain.001 lu élément par élément, PDF dont le texte est extrait, tableur ouvert. Totaux, nombre de lignes et valeurs clés sont comparés à l'attendu.
- La trace : toute action manuelle apparaît dans la piste d'audit avec son auteur, sa date, l'avant et l'après.
- Le chemin d'échec : pour chaque action, au moins un cas où elle doit être refusée, avec le message d'erreur exact.

**Ce qui est interdit**

- Un test sans assertion sur un contenu métier.
- Un test qui ne vérifie que l'adresse de la page, son titre ou l'absence d'erreur.
- Une comparaison d'image ou d'instantané du balisage à la place d'assertions.
- `test.skip`, `test.fixme`, ou un test vidé de ses assertions pour passer.
- Modifier une valeur attendue pour faire passer un test, sauf si la spécification dit autre chose que ce qui avait été recopié.

## 2. Niveau 1 : par écran

Chaque écran de l'inventaire a un fichier de tests à son nom, qui couvre tous les points ci-dessous applicables à son genre. Un écran peut relever de plusieurs genres.

**Tout écran**

- affichage nominal avec un jeu de données connu : chaque zone, chaque colonne, chaque valeur vérifiée ;
- état vide (aucune donnée) et son message ;
- état d'erreur : le serveur refuse ou échoue, le message s'affiche, l'écran reste utilisable ;
- accès direct par son adresse, rechargement, retour arrière du navigateur ;
- chaque lien sortant mène au bon écran, sur le bon objet ;
- pour chacun des neuf rôles : l'écran est visible ou refusé, conformément à la section 3 de la spécification ;
- cloisonnement : un utilisateur d'une autre entité n'y voit aucune donnée de celle-ci, y compris par l'adresse directe d'un objet ;
- aucune erreur ni avertissement dans la console du navigateur ;
- contrôle d'accessibilité automatique (axe-core) sans violation grave ou critique.

**Liste**

- chaque filtre, seul puis combiné à un autre ; remise à zéro des filtres ;
- tri sur chaque colonne triable, dans les deux sens ;
- pagination : première page, page suivante, dernière page, changement de taille de page ; les totaux ne changent pas avec la page ;
- les filtres survivent au rechargement et au retour depuis une fiche ;
- export de la liste : le fichier téléchargé contient exactement les lignes filtrées, à la maille de la ligne ;
- formats français : montants, pourcentages, dates.

**Fiche**

- chaque onglet, chaque carte, avec ses valeurs ;
- l'historique montre les valeurs passées avec leurs dates de validité ;
- un objet inexistant ou d'une autre entité donne un refus propre, pas une page cassée.

**Formulaire**

- saisie valide : enregistrée, visible dans la liste et la fiche, tracée dans l'audit ;
- chaque champ obligatoire laissé vide : refus avec le message du champ ;
- chaque règle de validation de la spécification : une valeur juste sous la borne, sur la borne, juste au-dessus ;
- valeurs limites de forme : montant à trois décimales, taux hors bornes de vraisemblance, date de fin antérieure à la date de début, texte trop long ;
- annulation : rien n'est enregistré ;
- double soumission (double clic) : un seul enregistrement ;
- modification concurrente : deux sessions ouvrent le même objet, la seconde à enregistrer reçoit un refus explicite et ne perd pas sa saisie.

**Action** (valider, approuver, refuser, bloquer, publier, lancer, clôturer)

- le cas permis, avec son effet vérifié sur un autre écran ;
- chaque précondition non remplie : action indisponible ou refusée, avec le motif ;
- chaque rôle non habilité : action absente de l'écran, et refusée par le serveur si on la force ;
- séparation des tâches quand la spécification l'exige : l'auteur ne peut pas approuver son propre objet ;
- motif obligatoire quand la spécification le demande ;
- l'action répétée (rejouée après coup) est refusée ou sans effet.

## 3. Niveau 2 : par cas de recette

Chacun des 71 cas CR de la section 13 de la spécification est rejoué par l'interface, en plus de son test unitaire. Le test charge les données du cas, lance le calcul depuis l'écran des exécutions, puis vérifie le résultat là où un utilisateur le lirait :

- les lignes et leurs montants sur le compte du distributeur ;
- l'explication de chaque ligne sur l'écran de détail : règle retenue, règles écartées, assiette, taux, étapes ;
- la position d'escompte sur l'écran de la convention, quand le cas en a une ;
- le bordereau, quand le cas va jusqu'au paiement ;
- les écritures, quand le cas porte sur la comptabilité.

Seule exception : CR-72 (volume) reste un test de performance hors interface.

Chaque test porte l'étiquette de son cas (`@cr:CR-04`). Il est livré à l'étape qui livre le cas.

## 4. Niveau 3 : parcours métier

Chaque ligne est un test au moins ; « chacun », « chaque » ou une énumération signifient un test par élément. La colonne « Étape » dit quand le parcours est livré.

### 4.1 Intégration et référentiels (étape 3)

| N° | Parcours | Vérifications |
| --- | --- | --- |
| INT-01 | Dépôt d'un fichier d'événements valide | Journal : cinq compteurs ; événements visibles avec leur statut ; empreinte et auteur |
| INT-02 | Fichier contenant une ligne fautive pour chacun des six codes de rejet | Un rejet par code, avec son libellé et la ligne brute ; les autres lignes sont intégrées |
| INT-03 | Correction d'un rejet à l'écran | La ligne repart seule ; compteurs mis à jour ; trace d'audit |
| INT-04 | Mise à l'écart d'un doublon | Statut du rejet ; aucun événement créé |
| INT-05 | Second dépôt du même fichier | Refus ; aucun événement en double |
| INT-06 | Même nom de fichier, deux systèmes source | Les deux sont acceptés et distingués au journal |
| INT-07 | Fichier vide réduit à son en-tête | Flux marqué « reçu », zéro ligne |
| INT-08 | Flux attendus | Reçu, en retard, manquant, selon l'horloge |
| INT-09 | Ligne négative : avec clé d'annulation ; sans clé mais rapprochable ; sans correspondance | Annulation liée ; annulation rapprochée ; mise en attente avec son motif |
| INT-10 | Événement dont le code de production est inconnu | En attente avec son motif ; calculé seul à l'arrivée du référentiel |
| INT-11 | Saisie manuelle d'un événement | Événement créé, tracé, pris par le calcul suivant |
| INT-12 | Import en masse de données complémentaires | Données visibles sur les garanties concernées |
| INT-13 | Mapping : création, correspondance de colonnes, valeur par défaut, transcodage, expression | Test du mapping sur un fichier ; résultat pivot affiché |
| INT-14 | Fichier d'assureur avec filtre sur la part de l'entité | Lignes filtrées comptées ; seule la part de l'entité intégrée (0,24 € sur 0,43 €) |
| INT-15 | Modification d'un mapping | Nouvelle version ; les imports passés gardent l'ancienne |
| INT-16 | Mise à jour d'un distributeur par le CRM | Fiche à jour ; attributs propres à l'application conservés |
| INT-17 | Radiation puis réinscription d'un distributeur, à leur date d'effet | Paiements bloqués puis débloqués aux bonnes dates |
| INT-18 | Coordonnées bancaires : IBAN de structure invalide ; changement depuis le dernier paiement | Refus ; signalement avant le paiement suivant |
| INT-19 | Règle de versement : parts dont le total n'est pas 100 % ; périodicité | Refus ; périodicité appliquée au lot |
| INT-20 | Cession de portefeuille et ordre de remplacement | Garanties réaffectées à la date d'effet ; historique des affectations |
| INT-21 | Changement de groupement à une date | Hiérarchie historisée ; fiche du groupement à jour |
| INT-22 | Correspondance des codes externes | Un code mis en correspondance débloque les événements en attente |
| INT-23 | Indicateurs de qualité reçus et calculés en interne | Valeurs par distributeur et par période |
| INT-24 | Produits, gammes, options, assureurs avec dates de validité | Création, fin de validité, refus d'un chevauchement |

### 4.2 Paramétrage des règles (étape 4)

| N° | Parcours | Vérifications |
| --- | --- | --- |
| PAR-01 | Création d'une version de règle pour chacune des neuf familles | Formulaire tiré du schéma de la famille ; version en projet enregistrée ; relue à l'identique |
| PAR-02 | Paramètres invalides pour chacune des neuf familles | Refus avec le message du schéma |
| PAR-03 | Approbation par l'auteur ; par un second administrateur | Refus ; version approuvée, non modifiable ensuite |
| PAR-04 | Passage du temps : date de début, date de fin | `ACTIVE` puis `RETIRED` sans intervention |
| PAR-05 | Deux versions du même groupe, même priorité, périmètres recouvrants | Approbation refusée ; les règles en conflit sont nommées |
| PAR-06 | Périmètre : chacun des critères de la section 5.4 | Un événement couvert et un événement non couvert par critère |
| PAR-07 | Taux hors bornes de vraisemblance | Alerte à la saisie |
| PAR-08 | Barème à paliers : mode marginal ; mode global ; bornes | Montant juste sous, sur et au-dessus de chaque borne |
| PAR-09 | Politique de reprise : aucune ; par paliers d'ancienneté ; au prorata | Reprise calculée selon la politique |
| PAR-10 | Duplication annuelle d'un plan | Toutes les versions copiées en projet, aux dates de l'année suivante |
| PAR-11 | Inscription à une règle réservée : accord non signé ; accord signé ; import en masse | Inactive ; active ; lignes importées |
| PAR-12 | Convention d'escompte : création, suspension, clôture, échéance | Effets de la section 6.4 sur les escomptes nouveaux et en cours |
| PAR-13 | Export puis import du paramétrage | Paramétrage identique après l'aller-retour |

### 4.3 Calcul (étapes 5 à 8)

Les montants sont couverts par le niveau 2. S'y ajoutent :

| N° | Parcours | Vérifications | Étape |
| --- | --- | --- | --- |
| CAL-01 | Lancement d'un calcul | Compteurs de l'exécution ; période verrouillée pendant le calcul | 5 |
| CAL-02 | Calcul relancé sans rien changer | Mêmes lignes, mêmes montants, aucun doublon | 5 |
| CAL-03 | Unité en échec | Signalée avec son motif ; les autres sont calculées ; relance de la seule unité fautive | 5 |
| CAL-04 | Recalcul partiel : un distributeur ; un produit ; une règle | Seules les lignes concernées changent | 12 |
| CAL-05 | Calcul d'une période dont la précédente n'est pas validée | Refus | 9 |
| CAL-06 | Événement sans règle de base | En attente, anomalie, recalculé après ajout de la règle | 5 |
| CAL-07 | Deux règles concurrentes au calcul | Événement en attente avec son motif | 5 |
| CAL-08 | Garantie en co-courtage, cédée en cours de période | Une seule unité ; lignes sur les deux comptes ; position d'escompte unique | 8 |
| CAL-09 | Plafond d'adhésions d'une convention atteint dans la période | Admission par date d'enregistrement ; les suivantes sans escompte | 6 |
| CAL-10 | Escompte fractionné | Second versement à sa date ; rupture avant le second versement | 6 |
| CAL-11 | Annulation d'un encaissement apuré | Cumul apuré diminué ; ligne inverse hors solde | 6 |
| CAL-12 | Annulation après changement de barème | Ligne inverse calculée avec la version d'origine | 5 |
| CAL-13 | Calcul prévisionnel | Lignes prévisionnelles, jamais payées, régularisées à la résiliation | 5 |
| CAL-14 | Échéance sans événement : solde d'un escompte au 360e jour sur une garantie silencieuse | Escompte soldé, régularisation créée | 6 |

### 4.4 Cycle, contrôles, ajustements, comptes (étape 9)

| N° | Parcours | Vérifications |
| --- | --- | --- |
| CYC-01 | Chacune des transitions d'état de la section 7.1, par le rôle habilité | État suivant ; trace |
| CYC-02 | Chacune de ces transitions, par chacun des autres rôles | Refus |
| CYC-03 | Chaque retour arrière autorisé ; un retour après `FINANCE_VALIDATED` | Effets de la section 7.1 ; refus |
| CYC-04 | Deux périodes ouvertes en même temps | Les événements reçus après la date d'arrêté vont à la suivante |
| CYC-05 | Second niveau de validation selon le total, l'écart au mois précédent, la présence d'ajustements | Exigé ou non selon le paramétrage |
| CTL-01 | Une anomalie pour chacune des dix familles de contrôle de la section 8.6 | Anomalie affichée avec sa gravité et son constat |
| CTL-02 | Décision : acceptée avec justification ; corrigée ; reportée | Trace ; compteur d'anomalies ouvertes |
| CTL-03 | Anomalie bloquante ouverte | Validation de la période refusée |
| CTL-04 | Indicateur de qualité manquant pour la RCP | Anomalie qui ne peut pas être acceptée |
| CTL-05 | Catalogue : modification d'un seuil, d'une gravité, désactivation | Effet au calcul suivant ; version du contrôle |
| CTL-06 | Liste de contrôle de la période | Cochée, persistée, tracée |
| AJU-01 | Un ajustement de chacun des cinq types | Ligne au compte ; bordereau suivant |
| AJU-02 | Motif absent ; pièce jointe ; lien à la ligne corrigée | Refus ; pièce consultable ; lien visible |
| AJU-03 | Approbation par l'auteur ; par un second ; au-delà du seuil par un non-valideur | Refus ; approuvé ; refus |
| AJU-04 | Contrepassation automatique | Ligne inverse créée dans la période suivante |
| AJU-05 | Avance exceptionnelle à échéancier | Remboursements aux échéances |
| AJU-06 | Transfert entre comptes | Deux lignes, somme nulle |
| AJU-07 | Import d'ajustements en masse | Lignes créées, toutes à approuver |
| REC-01 | Réclamation : ouverture, lien aux lignes contestées, régularisation, clôture | État à chaque étape ; ajustement lié |
| CPT-01 | Solde débiteur reporté sur trois périodes | Aucun paiement tant que le solde n'est pas positif ; report en une ligne |
| CPT-02 | Compensation entre deux comptes d'un même distributeur | Deux lignes de transfert ; paiement du net |
| CPT-03 | Compensation entre deux sociétés distinctes | Jamais faite |
| CPT-04 | Blocage d'une ligne, d'un compte, avec date de levée | Exclu du lot ; levé à la date |
| CPT-05 | Vue d'un groupement | Comptes de ses membres regroupés |
| CPT-06 | Débiteurs sans portefeuille actif | Liste ; échéancier d'apurement |
| CPT-07 | Statut de chaque commission d'un distributeur | Les six statuts visibles avec leur motif |

### 4.5 Paiement et documents (étape 10)

| N° | Parcours | Vérifications |
| --- | --- | --- |
| PAI-01 | Constitution du lot de cycle | Un ordre par bénéficiaire ; total égal à la somme des lignes rattachées |
| PAI-02 | Exclusion pour chacun des six motifs | Motif affiché ; somme restée au compte |
| PAI-03 | Période avec épargne retraite | Lot distinct, bordereau propre ; période `PAID` après le dernier lot seulement |
| PAI-04 | Net négatif d'un lot, positif de l'autre | Imputation en une ligne sur le bordereau du lot qui paie |
| PAI-05 | Lot de chèques du trimestre | Liste téléchargée et lue ; émission saisie ; ordre réglé |
| PAI-06 | Lot hors cycle après réinscription d'un distributeur | Paiement libéré sans attendre le mois suivant |
| PAI-07 | Validation par celui qui a validé les montants | Refus |
| PAI-08 | Refus du lot de cycle par la Finance | Période revenue en arrière ; lignes acquises ; écritures contre-passées ; bordereaux provisoires supprimés |
| PAI-09 | Refus d'un lot secondaire | Lot revenu en préparation ; période inchangée |
| PAI-10 | Fichier de virements | XML téléchargé et lu : nombre d'ordres, somme, identifiant de bout en bout, libellé, date d'exécution |
| PAI-11 | Résultat bancaire : tout réglé ; un rejet | Lignes payées ; lignes revenues payables, anomalie, réémission avec son suffixe |
| PAI-12 | Distributeur commun à deux entités | Un ordre par entité débitrice |
| BRD-01 | Bordereau d'un bénéficiaire | PDF et tableur téléchargés et lus : en-tête, rubriques de la section 9.5, total, solde reporté, net |
| BRD-02 | Bordereau à chaque niveau : code de production, distributeur, groupement | Bon bénéficiaire, bonnes lignes |
| BRD-03 | Bordereau multi-entités | Sous-total par entité |
| BRD-04 | Publication | Le lendemain de l'exécution ; métadonnées déposées pour la GED |
| BRD-05 | Rectificatif | Version 2 publiée ; version 1 intacte et toujours consultable |
| BRD-06 | Relevé informatif d'un membre payé par son groupement | Sa part seulement |
| BRD-07 | Modification d'un modèle : logo, mentions, colonnes | Bordereau suivant conforme ; anciens inchangés |
| BRD-08 | Attestation fiscale annuelle | Total de l'année civile |
| BRD-09 | Synthèse des escomptes d'une convention | Versé, apuré, créance, détail par adhérent |

### 4.6 Comptabilité, restitutions, flux (étapes 11 à 13)

| N° | Parcours | Vérifications | Étape |
| --- | --- | --- | --- |
| FIN-01 | Avant la validation métier | Aucune écriture | 11 |
| FIN-02 | Après la validation | Écritures de chaque événement comptable de la section 10.2, équilibrées | 11 |
| FIN-03 | Chaque variante du schéma | Sens des écritures | 11 |
| FIN-04 | Modification du schéma comptable | Nouveau compte dans l'extraction suivante | 11 |
| FIN-05 | Extraction comptable | Fichier lu : colonnes, équilibre, mois comptable, total égal aux lignes | 11 |
| FIN-06 | Clôture | Plus rien ne change ; toute action sur la période est refusée | 11 |
| FIN-07 | État de cut-off à ses quatre mailles | Même total aux quatre mailles ; exemple de la section 10.4 | 11 |
| FIN-08 | Rapprochement acquis et payé | Par bénéficiaire et au global | 11 |
| FIN-09 | Commission reçue différente de l'attendu | Écart affiché ; anomalie ; marge | 11 |
| FLX-01 | Chacun des exports à la demande de la section 9.12 | Fichier lu ; inscription au journal avec auteur, lignes, total | 11 |
| FLX-02 | Export Data | Un fichier par objet ; incrément du jour seulement | 11 |
| FLX-03 | Chaque ressource de l'API de la section 9.8, avec et sans le paramètre d'entité | Contenu ; cloisonnement | 11 |
| FLX-04 | Catalogue des flux et formats d'échange | Conformes à la section 9 | 11 |
| SIM-01 | Simulation d'une version en projet | Écart limité au taux modifié ; aucune ligne réelle, aucun compte, aucune écriture touchés | 12 |
| SIM-02 | Version qui retire un taux : avec table de substitution ; sans | Recalcul au nouveau taux ; événements comptés en attente | 12 |
| SIM-03 | Période close rejouée avec son paramétrage | Lignes identiques au centime | 12 |
| HIS-01 | Reprise d'historique | Soldes, positions d'escompte et cumuls ouverts ; première période calculée dessus | 12 |
| REP-01 | Chaque tableau de bord | Chiffres égaux aux sommes du jeu de données | 13 |
| REP-02 | « Payé à qui, quand, pourquoi » | D'un virement au bordereau, à la ligne, à l'événement, à la règle, à l'écriture | 13 |
| REP-03 | Vision consolidée du groupe | Réservée aux habilités ; totaux par entité | 13 |
| ADM-01 | Correspondance entre groupes d'annuaire et rôles | Un changement modifie les droits à la connexion suivante | 2 |
| ADM-02 | Paramètres d'une entité : seuil de paiement, tolérances, calendrier | Effet au cycle suivant | 9 |
| ADM-03 | Purge à l'échéance de conservation | Données personnelles anonymisées ; montants conservés | 13 |
| ADM-04 | Supervision | Traitement en échec visible et relançable | 13 |

### 4.7 Forces de vente (étape 14)

| N° | Parcours | Vérifications |
| --- | --- | --- |
| FDV-01 | Import des collaborateurs et des mesures | Fiches ; mesures par période |
| FDV-02 | Objectifs versionnés | Nouvelle version ; ancienne conservée |
| FDV-03 | Mensuel : objectif atteint ; manqué ; dix jours ouvrés d'absence | Mensualité ; rien ; rien |
| FDV-04 | Pondération par marché | 13 500 € pour 10 000 € et 5 000 € |
| FDV-05 | Trimestriel qualitatif : par critère ; six semaines d'absence | Montant par critère atteint ; rien |
| FDV-06 | Annuel collectif ; prorata d'absence | Enveloppe ; proratisée |
| FDV-07 | Régularisation annuelle : positive ; négative | Montant ; écart soumis au responsable |
| FDV-08 | Prime accordée malgré un objectif manqué : sans motif ; avec motif | Refus ; composante d'ajustement manuel tracée |
| FDV-09 | Export paie | Fichier lu ; lancé par le seul contrôleur Finance, après validation de tous les résultats |
| FDV-10 | Commercial ; responsable | Ne voit que son variable ; ne voit que son équipe |
| FDV-11 | Simulateur | Plafonné à 100 % ; exemple à 95 % de la spécification |

### 4.8 Démonstration (étape 15)

| N° | Parcours | Vérifications |
| --- | --- | --- |
| DEM-01 | Période de septembre 2026 de bout en bout sur le jeu de démonstration | De l'import à la clôture, chaque transition par son rôle |
| DEM-02 | Chacun des dix-neuf écrans de la maquette | Les chiffres clés de la maquette s'affichent à l'identique |

## 5. Niveau 4 : transversal

- **Droits.** Un tableau, tenu dans le code des tests, donne pour chaque action de l'application les rôles habilités, d'après la section 3 de la spécification. Un test généré parcourt ce tableau : pour chaque couple rôle-action, l'action est offerte et aboutit, ou elle est absente et refusée par le serveur.
- **Cloisonnement.** Pour chaque liste, chaque fiche, chaque export et chaque ressource de l'API : un utilisateur de l'entité B ne voit, ne télécharge et ne modifie rien de l'entité A.
- **Séparation des tâches.** Les trois cas de la spécification : version de règle, ajustement, paiement.
- **Audit.** Chaque écran qui modifie une donnée : la modification apparaît dans la piste d'audit avec l'auteur, la date, l'avant et l'après.
- **Ajout seul.** Après validation, aucune action de l'interface ne modifie ni ne supprime une ligne, une écriture ou un événement.
- **Session.** Session expirée en cours de saisie : retour à la connexion, puis reprise sans perte.
- **Navigation.** Toutes les entrées du menu, tous les fils de liens ; aucun lien mort.

## 6. Traçabilité et garde-fou

Chaque test porte des étiquettes : l'écran (`@screen:`), les exigences de la section 8 qu'il vérifie (`@req:REF-10`), le cas de recette (`@cr:`), le parcours (`@path:INT-02`).

Un script, lancé par la construction, lit ces étiquettes et produit `docs/E2E-MATRIX.md`. Il fait échouer la construction si, pour les étapes déjà cochées dans `PLAN.md` :

- un écran de l'inventaire n'a pas de fichier de tests, ou qu'il manque à ce fichier un point de la liste de son genre (section 2) ;
- une exigence de la section 8 servie par un écran n'est vérifiée par aucun test ;
- un cas CR n'a pas son test par l'interface ;
- un parcours de la section 4 n'a pas son test ;
- un couple rôle-action du tableau des droits n'est pas testé ;
- un test est désactivé ou ne contient aucune assertion.

Le compte rendu de chaque étape donne le nombre de tests de bout en bout ajoutés, leur durée, et l'état de la matrice.

## 7. Rattachement aux étapes

Les tests d'un écran sont livrés avec l'écran, à l'étape que lui attribue l'inventaire. Une étape n'est cochée que si tous les tests de bout en bout livrés jusque-là sont verts, sans relance.

Quand une étape ultérieure enrichit un écran déjà livré, elle enrichit aussi ses tests.
