# Jeu de démonstration : refonte

Le jeu chargé au démarrage en profil `dev` est trop pauvre pour montrer l'application : un seul distributeur, un contrat, un encaissement, une période déjà close. Ce fichier demande de le remplacer par un jeu riche. `CLAUDE.md` s'applique en entier (aucune question, périmètre strict, jamais de production, Javadoc, couverture Java à 100 %, tests qui affirment).

## 1. Constat de départ

- `DemoDataService` plante le cabinet P13897 sans aucune ligne `DistributorEntity`. La recherche de distributeurs filtre sur les entités visibles : pour `admin-alptis` ou `operator-alptis`, la liste est vide. C'est un défaut, pas un choix.
- L'écran « Périodes » affiche « Aucune période ouverte » : la seule période existante est close.
- Une seule famille de calcul sur neuf est représentée, rien pour l'entité `UNEP`, aucune anomalie, aucun ajustement, aucune convention d'escompte, aucune donnée de force de vente.

## 2. Objectif

Après `mvn quarkus:dev`, chaque écran du menu affiche des données réalistes et cohérentes entre elles, pour chaque utilisateur déclaré dans `application.properties` qui a le droit de voir cet écran. Un écran vide pour un utilisateur qui y a accès est un échec, sauf les écrans de saisie pure (formulaire vierge) et les écrans listés en 7.

## 3. Comment les données sont produites

- Le référentiel (distributeurs, produits, contrats, règles, coordonnées bancaires) est créé directement ou par les services d'import existants, au choix le plus simple.
- Tout ce que l'application calcule est obtenu en appelant les services réels, jamais en insérant le résultat : lignes de commission par le moteur, résultats de contrôle par les contrôles, comptes et écritures par la validation métier, lots et bordereaux par le paiement, extractions par le cut-off, variable par le service de force de vente.
- Aucun montant calculé n'est écrit en dur dans le jeu. Les montants attendus n'apparaissent que dans les tests.
- Là où la spécification donne un exemple chiffré (sections 6 et 10) ou la maquette un cas (cabinet P13897, bordereau de 110,00 € en septembre 2026), le jeu reprend ce cas tel quel et le test vérifie le montant de la spécification.
- Le chargement reste idempotent, limité au profil de construction `dev`, et ne ralentit pas le démarrage de plus de quelques secondes.

## 4. Contenu minimal

Les nombres sont des planchers. Les libellés sont en français et vraisemblables (pas de « Test 1 », « Démo 2 »).

**Entités.** Les deux entités `ALPTIS_ASSURANCES` et `UNEP` ont chacune un jeu complet. Au moins trois distributeurs travaillent avec les deux.

**Distributeurs.** Au moins 25, tous rattachés à leurs entités par `DistributorEntity`, couvrant tous les types de `DistributorType`, tous les statuts ORIAS et tous les statuts partenaire. Au moins deux groupes de distributeurs, une hiérarchie à trois niveaux, plusieurs codes de production par distributeur pour une partie d'entre eux, des coordonnées bancaires pour la plupart et au moins deux bénéficiaires sans coordonnées valides (pour l'exclusion de paiement), au moins un distributeur dont l'ORIAS bloque le paiement.

**Produits et règles.** Au moins 8 produits répartis sur plusieurs gammes. Chacune des neuf familles (`LINEAR`, `UPFRONT`, `ADVANCE`, `TIERED`, `PORTFOLIO`, `SHARE`, `OUTSTANDING`, `FIXED`, `EXTERNAL`) a au moins une règle active qui produit des lignes dans le jeu. Au moins une règle a plusieurs versions (une close, une active, un brouillon), au moins une est en attente de validation à quatre yeux. Barèmes à tranches, accords et inscriptions, codes externes et règles de versement à plusieurs bénéficiaires sont présents.

**Contrats et événements.** Au moins 150 contrats et 200 garanties, avec plusieurs apporteurs sur certaines garanties. Des événements de chaque valeur de `EventType` utilisée par le moteur, étalés sur six mois, dont des annulations, des impayés et des reprises qui exercent la décision D12 (annulation avec la version de règle d'origine). Au moins une cession de portefeuille réalisée et une en cours, au moins un ordre de remplacement.

**Escompte.** Au moins trois conventions d'escompte dans des états différents, dont une proche de son plafond et une avec dette à reprendre.

**Périodes.** Pour `ALPTIS_ASSURANCES` : juin, juillet, août et septembre 2026 closes (septembre garde la scène de la maquette), octobre 2026 à l'état calculé avec des anomalies ouvertes dont au moins une bloquante, novembre 2026 ouverte avec des événements en attente. Pour `UNEP` : septembre 2026 close, octobre 2026 arrêtée entre la validation métier et la validation Finance, avec un lot de paiement préparé non validé.

**Cycle.** Dans la période d'octobre : des résultats de contrôle de chaque sévérité et de plusieurs familles de contrôle, des décisions déjà prises sur une partie, au moins cinq ajustements dans des statuts différents (dont un en attente de validation et un refusé). Dans les périodes closes : des lots payés, un ordre rejeté par la banque puis réémis, un lot hors cycle, des bordereaux publiés dont un rectificatif, des comptes distributeurs débiteurs et créditeurs avec report de solde d'un mois sur l'autre.

**Flux.** Des lots d'import terminés, un en erreur partielle avec des rejets de plusieurs motifs (dont certains traités), des lots d'export émis, des correspondances de fichier et des formats d'échange définis.

**Comptabilité.** Schéma et correspondances de comptes pour les deux entités, écritures de toutes les périodes validées, un état de cut-off par période close.

**Force de vente.** Un plan de variable par entité, au moins 12 collaborateurs sur deux niveaux d'encadrement, des objectifs, des mesures sur trois mois, des résultats dont un mois validé et exporté et un mois en attente. Les utilisateurs `manager-alptis` et `rep-alptis` voient leur propre périmètre non vide.

**Administration.** Des paramètres d'entité renseignés, et une piste d'audit qui reflète les actions du jeu avec des auteurs variés (pas un unique utilisateur « demo »), en respectant la séparation des tâches.

## 5. Défauts rencontrés en chemin

Le jeu passe par les services réels : il révélera des défauts de l'application. Quand un service échoue ou produit un résultat contraire à la spécification, corrige le code, ajoute le test qui le prouve et consigne-le dans `docs/DECISIONS.md`. Ne contourne jamais un défaut en insérant le résultat à la main.

## 6. Tests

- `DemoDataServiceTest` est réécrit : il charge le jeu et vérifie les planchers de la section 4 un par un, les montants des cas repris de la spécification, les états des périodes, et l'idempotence d'un second chargement.
- Un test serveur de visibilité : pour chaque utilisateur déclaré et chaque point d'accès de liste de `/api/ui/v1` qu'il a le droit d'appeler, la réponse est non vide après chargement du jeu. La liste des points d'accès est lue dans le code (pas recopiée à la main) ; les exceptions de la section 7 sont nommées explicitement dans le test, avec leur raison.
- Un fichier Playwright `demo-data.spec.ts` : il recharge le jeu par un point d'accès `dev` dédié sous `/api/test` (la remise à zéro existante ne charge pas le jeu, ne la modifie pas), puis, pour un utilisateur de chaque rôle, ouvre chaque entrée du menu visible et vérifie qu'un contenu métier du jeu y est affiché (un code de distributeur, un montant, un numéro de lot), pas seulement l'absence de message d'erreur.
- La suite existante reste verte sans être affaiblie : aucun test supprimé, désactivé ou allégé. `mvn verify` passe avec la règle JaCoCo à 100 %, et la suite de bout en bout passe en entier.

## 7. Écrans qui peuvent rester vides

Seulement ceux dont le vide est l'état normal d'une installation neuve. Chaque exception est justifiée en une ligne dans `docs/DECISIONS.md` et reprise dans le test de visibilité. À défaut de justification, l'écran doit avoir des données.

## 8. Hors périmètre

Aucun nouvel écran, aucune nouvelle fonctionnalité, aucune modification de la spécification, de `CLAUDE.md`, de `LANCEMENT.md` ou de `TESTS-E2E.md`. Les amorçages des tests de bout en bout (`CycleDemoSeeder`, `FdvDemoResource`) ne changent pas.

## 9. Fin de travail

Un commit par bloc cohérent, message `demo: <summary>`, jamais de `push`. À la fin, ajoute au `README.md` une section « Jeu de démonstration » : ce que contient le jeu, et pour chaque utilisateur ce qu'il voit et par quel écran commencer. Termine par un compte rendu honnête : planchers atteints ou non, défauts corrigés, exceptions de la section 7, tests ajoutés. Ne déclare rien terminé qui n'a pas été vérifié par un test exécuté.
