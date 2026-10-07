# SI Rémunération — Spécification de l'application

Oct 6, 2026 · @Henri

## 1. Objet et périmètre

Ce document spécifie l'application « SI Rémunération » du groupe Alptis en vue d'un développement spécifique : elle calcule, contrôle, valide, restitue et comptabilise la rémunération versée aux distributeurs (courtiers, apporteurs, partenaires) et remplace le calcul des commissions aujourd'hui réparti entre RSI, CLEVA et des fichiers manuels.

Il est construit à partir du dossier d'étude et d'appel d'offres de la DSSI (cahier des charges RFP, macro-exigences, cas d'usage, guides et conventions de rémunération, bordereaux réels, documentation du calcul RSI). Quand les sources se contredisent ou se taisent, le choix retenu est tracé en section 15.

### Dans le périmètre

L'application couvre la chaîne complète, pour toutes les entités du groupe et tous les produits :

1. Collecte et normalisation des données contractuelles et financières issues des outils de gestion et des assureurs.
2. Paramétrage des règles de rémunération, versionnées et datées.
3. Calcul des commissions, avances, reprises, surcommissions et rétrocessions.
4. Contrôles, ajustements manuels et circuit de validation.
5. Tenue du compte de chaque distributeur et préparation des paiements.
6. Production des bordereaux de commissions et mise à disposition sur les espaces partenaires.
7. Alimentation du SI Finance et de la plateforme Data.
8. Historisation complète et piste d'audit.

Le périmètre prioritaire est le pôle courtage grossiste : Alptis Assurances, puis UNEP et Insured Services. La rémunération variable des forces de vente internes est spécifiée en section 8.14 et construite en dernier.

### Hors périmètre

L'application ne gère pas les contrats, ne fait pas l'encaissement des cotisations, n'émet pas les virements bancaires (elle fournit les ordres au SI Finance) et ne porte pas le référentiel maître des distributeurs, qu'elle consomme.

### Comment lire ce document

Le document se lit dans l'ordre, mais chaque lecteur a ses sections :

| Lecteur | Sections utiles | Ce qu'il y trouve |
| --- | --- | --- |
| Métier (pilotage des courtiers) | 4, 6, 7, 13, 15 | Le vocabulaire, les règles avec leurs exemples chiffrés, le déroulé du mois, les choix à valider |
| Finance | 7, 10, 15.2 | Le cycle de validation, les écritures, le cut-off, les questions comptables ouvertes |
| Développeur | 5, 6, 9, 12, 13 | Les objets, les tables, les formules, les formats, les tests d'acceptation |
| DSSI | 3, 9, 11, 12, 14 | Les habilitations, les flux, les exigences techniques, le lotissement |

Trois conventions reviennent partout :

- Les noms en caractères de code (`CommissionLine`, `EARNED`) sont ceux du logiciel ; ils sont en anglais et définis en sections 4 et 5.
- Chaque sujet est présenté de la même façon : la règle, puis « Comment ça marche », un exemple chiffré, et les limites ou cas non couverts.
- Un renvoi H-xx désigne un choix fait par ce document en l'absence de réponse dans les sources ; ils sont tous listés en section 15.

## 2. Contexte et existant

Alptis Assurances est un courtier grossiste (Lyon) qui rémunère environ 7 000 à 8 000 partenaires distributeurs pour 40 M€ de commissions par an ; le calcul est aujourd'hui éclaté entre six outils et des fichiers Excel, sans bordereau unique ni vision consolidée.

Le COPIL du 24/09/2026 a retenu le développement interne comme solution de repli si l'appel d'offres n'aboutit pas : les licences SaaS ressortent à 200–400 K€ par an pour un budget de 150 K€. Cette spécification décrit cette solution développée.

### Existant par périmètre

| Périmètre | Données de gestion | Calcul des commissions | Paiement et bordereau | Constat |
| --- | --- | --- | --- | --- |
| Santé et prévoyance individuelles (Alptis Assurances) | RSI (legacy), migration vers CLEVA lot 2 prévue en 2028 | RSI : batchs mensuels ffcap09 (suivi, linéaire), ffcap10 (apport), ffcap11 (reprises), précompte phases 1 et 2 | Titres de paiement RSI, virement par la comptabilité, bordereaux sur Alptis Connect | Fiable mais opaque ; dépend de quelques experts ; pas de compensation automatique entre codes d'un même partenaire |
| Surcommissions RCD et RCP | Indicateurs issus de RSI et du CRM Sésame | Outil interne « Incentive », paramétré par le service Pilotage et Gestion Courtiers | Intégré au cycle RSI | Outil non maintenu, accès en écriture non restreint, saisies piégeuses, un seul sachant (audit interne 2023) |
| Santé et prévoyance collectives | CLEVA | Module commissions de CLEVA | Remises validées dans CLEVA, virement par la comptabilité | Ne couvre pas toutes les commissions ; pas de reprise sur l'apport |
| Emprunteur | Digital Insure (gestionnaire délégué) | Délégué au gestionnaire | Bordereaux du gestionnaire | Gestion à reprendre en main |
| Épargne retraite (PER UMR) | Fichiers Excel de l'assureur UMR | Excel, puis intégration manuelle dans RSI | Cycle RSI | Entièrement manuel |
| IARD Pro (lancement) | Fichiers assureur | Excel | Manuel | À industrialiser dès le démarrage |
| UNEP (épargne retraite, assurance vie) | Kelia, fichiers assureurs | Module Kelia et Excel | « Moulinette » PDF, extranet K4U | Repose sur une seule personne |
| Insured Services (assurances liées à l'immobilier) | Outil maison, Excel | Manuel | Manuel | 1,2 M€ de commissions en 2024 |

### Volumes de référence

| Donnée | Alptis Assurances | UNEP |
| --- | --- | --- |
| Commissions versées par an | 40 M€ (2025) | 12 M€ (2024) |
| dont montants escomptés | 8 à 10 M€ | — |
| dont montants repris | 3 à 4 M€ | — |
| Distributeurs rémunérés | 7 000 à 8 000 | 1 600 |
| Lignes de flux par an | 4,5 à 5 millions | 200 000 |
| Garanties en portefeuille | 300 000, dont 3 000 nouvelles par mois | — |
| Fichiers partenaires externes | 5 partenaires × 4 fichiers, +2 partenaires par an | 5 partenaires × 4 fichiers |
| Utilisateurs internes | 10, plus 80 pour les forces de vente | 6, plus 10 |

### Irritants que l'application doit lever

- Plusieurs paiements et plusieurs bordereaux par courtier, selon la gamme et le produit.
- Pas de consolidation à la maille voulue de la hiérarchie du distributeur ; un courtier membre d'un groupement voit le montant du groupement et non le sien.
- Calculs Excel et restitution comptable manuelle sur les produits de diversification.
- Pas de reprise calculée sur certaines commissions d'apport, d'où un manque à gagner.
- Compensations entre comptes débiteurs et créditeurs d'un même partenaire faites à la main chaque mois.
- Aucune vision consolidée, ni par entité ni pour le groupe.
- Surcommissions versées sans contrôle automatique de la signature de l'avenant ni de toutes les conditions.

## 3. Acteurs, rôles et habilitations

L'application est réservée aux collaborateurs internes ; les distributeurs n'y accèdent pas et consultent leurs commissions sur leur extranet (Alptis Connect, K4U). Chaque utilisateur est rattaché à une ou plusieurs entités et ne voit que leurs données.

### Rôles applicatifs

| Rôle | Qui | Droits |
| --- | --- | --- |
| `BUSINESS_ADMIN` | Service Pilotage et Gestion Courtiers | Paramètre les règles, barèmes, conventions et contrôles ; lance les calculs ; approuve les versions de règles créées par un autre administrateur et les ajustements saisis par un autre utilisateur |
| `OPERATOR` | Gestionnaires du même service | Suit les imports, traite les rejets, saisit les ajustements manuels, exécute les contrôles, prépare la validation, approuve les ajustements saisis par un autre utilisateur |
| `BUSINESS_VALIDATOR` | Responsables Direction du Développement | Valide le calcul du mois et les ajustements au-delà d'un seuil |
| `FINANCE_CONTROLLER` | Comptabilité et trésorerie | Contrôle le rapprochement, valide le lot de paiement, déclenche les extractions comptables, clôt la période |
| `VIEWER` | Commerciaux, contrôle de gestion | Consultation des comptes, bordereaux et tableaux de bord de son périmètre |
| `AUDITOR` | Contrôle interne, audit, commissaires aux comptes | Lecture seule de tout, y compris piste d'audit et versions de règles |
| `IT_ADMIN` | DSSI | Gère les correspondances groupes d'annuaire vers rôles, supervise les traitements et les flux ; aucun droit sur les montants |
| `SALES_MANAGER` | Responsables régionaux | Valide les variables de son équipe (module forces de vente) |
| `SALES_REP` | Commerciaux | Consulte son propre variable et son historique (module forces de vente) |

### Règles d'habilitation

1. L'authentification passe par le SSO d'Alptis (OpenID Connect, Entra ID ou Keycloak). Aucun mot de passe n'est géré par l'application.
2. Les rôles et les entités visibles sont déduits des groupes de l'annuaire ; l'application ne crée pas de compte local.
3. Le cloisonnement se fait par entité : un utilisateur ne voit que les entités auxquelles il est rattaché, et un opérateur UNEP ne voit aucune donnée Alptis Assurances. Seuls `IT_ADMIN`, `AUDITOR` et les utilisateurs du niveau groupe sont rattachés à toutes les entités.
4. Séparation des tâches : celui qui crée une version de règle ou un ajustement ne peut pas l'approuver ; celui qui lance le calcul n'en valide pas les montants ; celui qui valide les montants ne valide pas le paiement.
5. Toute attribution ou retrait de droit est journalisé.

**Comment ça marche.** Sur un mois, les rôles se passent la main dans cet ordre : l'`OPERATOR` suit les imports et traite les rejets ; le `BUSINESS_ADMIN` lance le calcul ; l'`OPERATOR` déroule les contrôles ; le `BUSINESS_VALIDATOR` valide les montants ; le `FINANCE_CONTROLLER` valide le paiement puis clôt. Trois séparations sont imposées : nul n'approuve ce qu'il a créé ou saisi lui-même ; celui qui lance le calcul d'une période n'en valide pas les montants ; celui qui valide les montants ne valide pas le paiement.

**Exemple.** Une administratrice crée la version 2027 du barème Santé Select. Elle peut la simuler, mais le bouton d'approbation lui est refusé : un second administrateur doit l'approuver. De même, l'opérateur qui saisit une avance exceptionnelle de 500 € ne peut pas l'approuver lui-même.

**Limites.** La séparation des tâches impose un effectif minimal par entité : deux administrateurs métier, pour que l'un approuve ce que l'autre crée (chacun pouvant cumuler `BUSINESS_ADMIN` et `OPERATOR`, ainsi que le rôle de valideur métier pour les montants que l'autre a calculés), et une personne côté Finance. À l'UNEP, où tout repose aujourd'hui sur une seule personne, l'organisation devra changer ou déléguer une validation au niveau groupe. L'application ne prévoit pas de dérogation à cette règle.

### Systèmes externes

| Système | Rôle vis-à-vis de l'application |
| --- | --- |
| Sésame (CRM, instance e-Deal) et Salesforce (UNEP) | Fournissent le référentiel distributeurs et les indicateurs de qualité ; reçoivent les rémunérations calculées |
| CLEVA, RSI, Kelia | Fournissent contrats, émissions, encaissements et événements de vie |
| Assureurs et gestionnaires délégués (UMR, Digital Insure, autres) | Fournissent des fichiers de production, d'encaissement ou de commissions déjà calculées |
| SI Finance (Sage, Agicap) | Reçoit les ordres de paiement SEPA et l'extraction comptable mensuelle |
| Alptis Connect, K4U | Affichent bordereaux, compte et détail des commissions aux distributeurs |
| Plateforme Data groupe (Snowflake) | Reçoit l'export détaillé des rémunérations |
| GED et éditique | Archivent les bordereaux et attestations produits |
| UseItFlow | Transporte et ordonnance les fichiers échangés |
| Centreon | Supervise la disponibilité |

## 4. Glossaire

Les sources emploient « précompte » pour trois notions différentes ; ce glossaire fixe un seul sens par terme, utilisé dans tout le document et dans le code.

| Terme retenu | Code | Définition | Autres noms dans les sources |
| --- | --- | --- | --- |
| Entité | `LegalEntity` | Société du groupe qui rémunère des distributeurs : Alptis Assurances, UNEP, Insured Services, Promantis Conseil | Filiale |
| Distributeur | `Distributor` | Cabinet de courtage, conseiller en gestion de patrimoine ou mandataire qui distribue les produits ; identifié par un code partenaire `P` suivi de 5 chiffres dans Sésame ; les codes des autres CRM y sont rattachés par une table de correspondance | Partenaire, courtier, apporteur, cabinet |
| Groupement | `DistributorGroup` | Regroupement de distributeurs, code `G` suivi de chiffres ; peut percevoir tout ou partie de la rémunération de ses membres | Réseau, tête de groupement |
| Code de production | `ProductionCode` | Subdivision d'un distributeur (agence, point de vente, territoire) à laquelle les affaires sont rattachées ; 5 chiffres | Code apporteur, point de vente, secteur commercial |
| Bénéficiaire de règlement | payee\_type, payee\_id | Titulaire du compte bancaire qui reçoit le paiement : code de production, distributeur ou groupement. Ce n'est pas un objet propre : il est désigné par un type et un identifiant | Bénéficiaire |
| Adhésion | `Contract` | Contrat souscrit par un assuré ou une entreprise | Contrat, affaire, dossier |
| Garantie | `Coverage` | Ligne de garantie d'une adhésion ; c'est la maille du calcul et de l'affectation au distributeur | LGGC, formule, niveau |
| Adhésion nouvelle | — | Adhésion présentée et acceptée dans l'année civile, certificat édité. Les ventes additionnelles comptent ; un remplacement de garantie santé ou une modification (ayant droit, niveau) ne comptent pas | Affaire nouvelle |
| Prime émise | `PREMIUM_ISSUED` | Cotisation appelée, hors taxes et contributions | Émission, flux émis |
| Prime encaissée | `PREMIUM_COLLECTED` | Cotisation effectivement payée, hors taxes et contributions ; assiette par défaut des commissions | Encaissement |
| Prime annualisée | — | Estimation des cotisations hors taxes des 12 premiers mois d'adhésion | PPM, prime annuelle théorique |
| Commission linéaire | `LINEAR` | Taux identique chaque année, appliqué à la prime encaissée | — |
| Commission précomptée | `UPFRONT` | Commission d'apport la première année, plus commission de suivi toute la vie du contrat ; payées sur encaissement | Apport + suivi |
| Commission d'apport | `ACQUISITION` | Part versée pendant les 12 premiers mois d'une garantie précomptée | Apport |
| Commission de suivi | `SERVICING` | Part versée toute la vie d'une garantie précomptée | Suivi |
| Escompte | `ADVANCE` | Avance des commissions des 12 premiers mois, calculée sur la prime annualisée et versée dans le cycle du mois d'effet ou plus tôt. En épargne retraite : commission des cinq premières années de versements programmés, versée d'avance (section 6.6) | « Précompte » dans RSI et chez UMR, commission escomptée |
| Convention d'escompte | `AdvanceAgreement` | Accord daté qui ouvre l'escompte à un distributeur pour certains produits | Convention de précompte |
| Apurement | `OFFSET` (statut) | Imputation des commissions acquises sur l'escompte déjà versé, au lieu de leur paiement | Apurement de précompte |
| Régularisation d'escompte | `ADVANCE_ADJUSTMENT` | Écart positif ou négatif constaté quand l'escompte est soldé ou rompu | Régularisation de précompte, reprise de précompte indu |
| Reprise | `CLAWBACK` | Récupération d'une commission déjà acquise, versée ou apurée, à la suite d'un événement du contrat | Reprise d'apport, rappel de commission |
| RCD | `RCD` | Rémunération complémentaire de développement : surcommission sur les primes annualisées des adhésions nouvelles, par paliers, versée chaque mois | Surcommission sur affaires nouvelles |
| RCP | `RCP` | Rémunération complémentaire de portefeuille : surcommission annuelle sur les primes encaissées du portefeuille, sous conditions de qualité | Surcommission de portefeuille |
| Rétrocession | `RETROCESSION` | Commission calculée en pourcentage d'une commission reçue par l'entité, ou part des frais d'un support financier reversée au distributeur | Commission sur commission |
| Commission reçue | `INBOUND` | Commission versée par un assureur à l'entité | Commission brute |
| Commission versée | `OUTBOUND` | Commission due par l'entité à un distributeur | Commission courtier |
| Compte distributeur | `DistributorAccount` | Compte métier, par entité et par bénéficiaire, qui porte toutes les lignes de commission et de paiement | Compte fournisseur métier, compte commissions |
| Compte débiteur | — | Compte distributeur dont le solde est négatif ; aucun paiement n'est émis tant qu'il le reste | — |
| Bordereau | `Statement` | Document qui détaille, ligne à ligne, les commissions d'un bénéficiaire sur une période et justifie le paiement éventuel | Relevé de commissions |
| Cut-off | `CutoffExtract` | Arrêté mensuel des écritures transmises au SI Finance. À ne pas confondre avec la date d'arrêté des données, qui gèle les imports d'une période (section 7.2) | Arrêté |
| Rappel (épargne retraite) | `CLAWBACK` | Reprise de l'escompte retraite ou de la rémunération complémentaire quand le contrat cesse d'être alimenté avant cinq ans. L'escompte retraite n'a pas de régularisation d'escompte : sa rupture passe par ce rappel | Rappel de précompte |
| Complément | `COMPLEMENT` | Réservé à la rémunération complémentaire retraite. Le solde positif d'un escompte s'appelle complément d'escompte, la hausse du réajustement annuel supplément d'escompte | Complément retraite |
| Période | `Period` | Mois de traitement d'une entité, de l'ouverture à la clôture | Campagne mensuelle |
| Sans suite | `NOT_PURSUED` | Adhésion classée sans suite ou refusée après enregistrement | — |
| Cession de portefeuille | `PortfolioTransfer` | Transfert des garanties d'un distributeur à un autre | — |
| Ordre de remplacement | `ReplacementOrder` | Demande d'un client de changer de courtier sur son contrat | OR |

**Les trois modes sur un même contrat.** La confusion la plus fréquente porte sur linéaire, précompté et escompté. Pour une cotisation de 100 € hors taxes par mois, voici ce que touche le distributeur :

| Mode | Chaque mois de la 1re année | Total 1re année | Chaque mois ensuite | Total sur 3 ans |
| --- | --- | --- | --- | --- |
| Linéaire à 15 % | 15 € | 180 € | 15 € | 540 € |
| Précompté 30 + 10 | 40 € (30 d'apport, 10 de suivi) | 480 € | 10 € | 720 € |
| Précompté 30 + 10, escompté | 480 € d'un coup dans le cycle du mois d'effet, puis rien pendant 12 mois | 480 € | 10 € | 720 € |

Le précompté change la répartition dans le temps : plus au début, moins ensuite. L'escompte ne change pas le montant : il avance seulement la date de versement, et crée une créance que les commissions des 12 mois suivants remboursent. Un contrat linéaire peut lui aussi être escompté : il touche alors 180 € dans le cycle du mois d'effet.

La contrepartie de l'avance est le risque de reprise : si le contrat tombe au 6e mois, le distributeur précompté doit rendre l'apport perçu, et le distributeur escompté doit en plus rendre ce qui lui a été avancé pour les mois non courus (section 6.4).

## 5. Modèle de domaine

Le modèle repose sur un pivot indépendant du produit : des événements métier rattachés à une garantie, des règles versionnées qui les transforment en lignes de commission immuables, et un compte par bénéficiaire qui porte ces lignes jusqu'au paiement. Les noms de code sont en anglais ; toute entité métier porte `entityId` (cloisonnement), des dates de validité quand elle est historisée, et ses identifiants dans les systèmes sources.

### 5.1 Référentiels

| Objet | Rôle | Attributs clés |
| --- | --- | --- |
| `LegalEntity` | Entité du groupe | code, raison sociale, IBAN émetteur, calendrier de paiement ; tous les montants sont en euros |
| `Insurer` | Assureur ou gestionnaire délégué | code, nom, rôle (porteur de risque, gestionnaire) |
| `ProductRange` | Gamme ou marché | code (santé individuelle, prévoyance individuelle, emprunteur, santé collective, prévoyance collective, retraite, assurance vie, IARD pro, immobilier), cible (particulier, professionnel, entreprise) |
| `Product` | Produit commercialisé | code, libellé, gamme, assureur, mode de gestion (interne, délégué), décalage de référence en mois, constitution du prévisionnel, dates de commercialisation |
| `ProductOption` | Niveau, formule, renfort, pack, support financier | code, libellé, type, produit |
| `Distributor` | Distributeur | code partenaire, raison sociale, adresse, forme juridique, SIREN, numéro et statut ORIAS, type (indépendant, membre de groupement, vente à distance, mandataire), statut par entité, groupement, délégation commerciale, préférence de déclinaison du bordereau |
| `DistributorGroup` | Groupement | code, libellé, distributeur tête de groupement |
| `ProductionCode` | Code de production | code, libellé, distributeur, statut, codes externes par système source |
| `BankAccount` | Coordonnées bancaires | IBAN, BIC, titulaire, dates de validité, porteur (code de production, distributeur ou groupement) |
| `PayeeRule` | Règle de versement | périmètre (code de production, distributeur, gamme), liste de parts (bénéficiaire, pourcentage, somme = 100 %), périodicité de paiement (mensuelle, trimestrielle, semestrielle, annuelle), dates de validité |
| `QualityIndicator` | Indicateur de qualité d'un distributeur | type, période de mesure, valeur, gamme, source |
| `ExternalCode` | Correspondance d'identifiant | objet interne, système source, code externe |

Statuts d'un distributeur et d'un code de production : `CANDIDATE` (en cours de codification), `ACTIVE` (confirmé), `SUSPENDED`, `TERMINATED` (partenariat clos). Seul `ACTIVE` autorise le paiement ; les trois autres bloquent les paiements sans arrêter le calcul.

Types d'indicateur de qualité : taux de réclamations, taux de sans-suite, taux de radiation sur production nouvelle, taux de radiation du portefeuille, taux d'impayés, ratio prestations sur cotisations (P/C), note de solvabilité. Toutes les valeurs de taux sont stockées en fraction décimale (0,15 pour 15 %) et saisies en pourcentage dans tous les écrans.

Règle de versement applicable : la plus précise l'emporte, dans l'ordre code de production et gamme, code de production, distributeur et gamme, distributeur. À défaut de règle, le bénéficiaire est le code de production s'il porte des coordonnées bancaires, sinon le distributeur.

Définition des indicateurs, reprise des avenants 2026 :

| Indicateur | Formule | Mesure |
| --- | --- | --- |
| Taux d'impayés | Adhérents en état d'impayé à la date, divisés par les adhérents en portefeuille | À la date du calcul |
| Taux de sans-suite | Adhésions classées sans suite ou refusées, divisées par les adhésions nouvelles | Mensuelle, sur 12 mois glissants |
| Taux de radiation sur production nouvelle | Radiations des adhésions ayant pris effet dans l'année, divisées par les adhésions nouvelles | Mensuelle, sur l'année civile |
| Taux de radiation du portefeuille | Radiations connues dans l'année parmi les contrats en vigueur au 1er janvier, divisées par le portefeuille au 1er janvier | Annuelle |
| Taux de réclamations | Réclamants pour défaut du distributeur, divisés par les adhésions nouvelles | Mensuelle, sur 12 mois glissants |
| P/C | Prestations versées, divisées par les encaissements hors rémunérations et taxes | Annuelle |

**Comment ça marche : qui est payé.** Une affaire est toujours rattachée à un code de production. Le calcul se fait à ce niveau ; c'est la règle de versement qui décide ensuite sur quel compte bancaire l'argent arrive.

**Exemple.** Le cabinet P13897 a deux codes de production, 61723 (Lyon) et 66117 (Grenoble), et appartient au groupement G012. Une commission de 30 € naît sur une affaire du code 61723. Selon la règle de versement en vigueur :

| Règle de versement | Qui reçoit |
| --- | --- |
| Aucune ; le code 61723 a ses propres coordonnées bancaires | 30 € sur le compte du code 61723 |
| Aucune ; seul le cabinet a des coordonnées bancaires | 30 € sur le compte du cabinet P13897 |
| 100 % au groupement | 30 € sur le compte de G012, qui redistribue lui-même |
| 90 % au cabinet, 10 % au groupement | 27 € au cabinet, 3 € au groupement |

Dans les quatre cas, la ligne garde le code 61723 comme origine : les restitutions montrent toujours au cabinet ce que ses affaires ont produit.

**Limites.** La hiérarchie a trois niveaux fixes ; un groupement de groupements n'est pas prévu. Un distributeur commun à deux entités a ses propres lignes `distributor_entity` dans chaque entité, une par période de statut : son statut est propre à chacune : il peut être suspendu chez l'une et actif chez l'autre. Son immatriculation ORIAS, elle, est unique : une radiation le suspend partout.

### 5.2 Contrats

| Objet | Rôle | Attributs clés |
| --- | --- | --- |
| `Policyholder` | Assuré ou entreprise souscriptrice, réduit au nécessaire | identifiant externe, nom, prénom, date de naissance, type (personne physique, morale) |
| `Contract` | Adhésion | numéro, système source, produit, souscripteur, dates de signature, d'enregistrement, d'effet et de fin, motif de fin, statut, périodicité, mode de vente, contrat collectif parent |
| `Coverage` | Garantie | option de produit, date d'effet, date de fin, motif de fin, statut, prime annualisée hors taxes, type de tarif (réduit, standard, majoré), collège (cadres, non-cadres), critères libres datés (durée de prêt, capital, taux de frais, protocole) |
| `RemunerationChoice` | Choix de rémunération du distributeur sur la garantie | mode (`LINEAR`, `UPFRONT`), taux linéaire ou couple apport et suivi, escompté (oui, non), dates de validité |
| `CoverageAssignment` | Affectation de la garantie à un code de production, historisée | code de production, rôle (apporteur principal, co-courtier), part en pourcentage, dates de validité, origine (souscription, cession, ordre de remplacement) |
| `PortfolioTransfer` | Cession de portefeuille | cédant, repreneur, date d'effet, périmètre, sort de la créance d'escompte (remboursée, reprise par le repreneur), statut (à traiter, traitée) |
| `ReplacementOrder` | Ordre de remplacement | contrat, ancien et nouveau code de production, date de réception, date d'effet |

Statuts d'une adhésion et d'une garantie : `PENDING` (enregistrée, pas encore en vigueur), `IN_FORCE`, `SUSPENDED` (versements suspendus, mise en réduction), `TERMINATED`, `WITHDRAWN` (renonciation), `NOT_PURSUED` (sans suite ou refusée), `LITIGATION` (contentieux).

**Pourquoi la garantie et non le contrat.** Un même contrat peut porter plusieurs garanties, souscrites à des dates différentes, avec des modes de rémunération différents et, après une cession, des distributeurs différents. La première année d'un précompté, l'ancienneté pour la reprise et l'escompte se comptent donc garantie par garantie.

**Exemple.** Un adhérent souscrit Santé Select le 1er mars en précompté 30 + 10, puis ajoute une garantie Pack Hospi le 1er septembre en précompté 40 + 10. En novembre, sa cotisation commissionne l'apport des deux garanties. En avril suivant, seule la seconde est encore dans sa première année.

**Comment ça marche : l'affectation historisée.** L'affectation dit, pour chaque jour, quel code de production tient la garantie. Le moteur ne lit jamais « le distributeur actuel » : il lit l'affectation à la date de l'événement.

**Exemple.** Une garantie est tenue par le cabinet A jusqu'au 30 juin, puis cédée au cabinet B au 1er juillet. La prime de juin commissionne A, celle de juillet commissionne B. Si le contrat est résilié en septembre, la reprise d'apport est débitée à chacun pour ce qu'il a lui-même perçu.

**Limites.** L'application applique la date d'effet de cession ou d'ordre de remplacement qu'elle reçoit ; elle ne recalcule pas les règles de prise d'effet (30 jours de préavis en santé, 1er janvier pour les autres contrats), qui restent dans les outils de gestion. Une cession rétroactive reçue après validation d'une période ne modifie pas cette période : elle se traite par ajustement.

### 5.3 Événements métier

Tout ce qui entre dans le calcul est un `BusinessEvent`. Un événement n'est jamais modifié : une correction arrive sous la forme d'un événement d'annulation lié à l'événement d'origine.

| Attribut | Contenu |
| --- | --- |
| `type` | Voir la liste ci-dessous |
| `coverageId`, `contractId` | Rattachement ; la garantie est obligatoire pour les événements financiers |
| `eventDate` | Date d'effet métier (encaissement, résiliation, versement) |
| `receivedDate` | Date de connaissance par l'application |
| `periodFrom`, `periodTo` | Période de cotisation couverte |
| `grossAmount`, `taxAmount`, `feeAmount`, `basisAmount` | Montant TTC, taxes et contributions, frais, assiette hors taxes |
| `fundCode`, `managementProfile` | Support financier et profil de gestion (épargne) |
| `sourceSystem`, `sourceKey` | Clé d'idempotence : un même couple n'est intégré qu'une fois |
| `reversesEventId` | Événement annulé |
| `unpaidRank`, `recoveredAfterLitigation` | Rang de l'impayé ; prime recouvrée après une procédure précontentieuse ou contentieuse, exclue de l'assiette |
| `precomputedBasis`, `precomputedRate`, `precomputedAmount` | Assiette, taux et montant fournis par l'assureur quand la commission arrive déjà calculée |
| `beneficiaryCode`, `label` | Bénéficiaire visé par une commission déjà calculée ; libellé repris sur le bordereau |
| `processingStatus` | `PENDING`, `ON_HOLD` (donnée ou règle manquante), `PROCESSED`, `NOT_COMMISSIONABLE` |

Types d'événement :

- Primes : `PREMIUM_ISSUED`, `PREMIUM_COLLECTED`, `PREMIUM_CANCELLED` (annulation d'encaissement, rejet de prélèvement), `PREMIUM_EXEMPTED`.
- Vie du contrat : `SUBSCRIPTION`, `ENDORSEMENT` (avenant changeant la prime), `TERMINATION`, `WITHDRAWAL`, `NOT_PURSUED`, `DEATH`, `UNPAID_NOTICE` (avec rang de l'impayé), `LITIGATION_OPENED`, `REINSTATEMENT`.
- Épargne : `INITIAL_PAYMENT`, `FREE_PAYMENT`, `SCHEDULED_PAYMENT`, `INBOUND_TRANSFER`, `ARBITRAGE`, `OUTSTANDING_FEE` (frais prélevés sur encours), `OUTSTANDING_BALANCE` (encours moyen de la période), `PLAN_SUSPENDED`, `PLAN_CHANGED`, `SURRENDER` (rachat total ou partiel), `OUTBOUND_TRANSFER`.
- Emprunteur : `EARLY_REPAYMENT` (rachat anticipé total du prêt), `FILE_FEE_COLLECTED` (frais de dossier encaissés, montant porté par `gross_amount`), PARTIAL\_EARLY\_REPAYMENT (rachat partiel, sans effet sur les commissions, hypothèse H-47).
- Commissions externes : `INSURER_COMMISSION` (commission reçue d'un assureur), `EXTERNAL_COMMISSION` (commission distributeur déjà calculée par un tiers).

**Comment ça marche : la vie d'un événement.**

1. Il arrive par un import et reçoit le statut `PENDING`.
2. Si une donnée manque (code de production inconnu, règle absente, taux hors grille), il passe `ON_HOLD` avec son motif.
3. Au calcul de sa période, il produit ses lignes et passe `PROCESSED`, ou `NOT_COMMISSIONABLE` s'il ne donne droit à rien.
4. Il n'est plus jamais modifié. Une erreur se corrige par un événement d'annulation, suivi si besoin d'un nouvel événement juste.

**Deux dates, deux usages.** `eventDate` dit quand le fait s'est produit ; elle sert à choisir la règle, l'affectation et à mesurer les anciennetés. `receivedDate` dit quand l'application l'a su ; elle sert à choisir la période de traitement.

**Exemple.** Une résiliation prend effet le 15 mars mais n'est transmise que le 10 mai. Elle est traitée dans la période de mai. L'ancienneté de la garantie est mesurée au 15 mars : si la garantie avait pris effet le 1er avril de l'année précédente, elle a 348 jours et la reprise est de 100 %, bien que l'information arrive après le 365e jour.

**Idempotence.** Le couple système source et clé source identifie un événement. Si CLEVA renvoie deux fois l'encaissement `ENC-000123`, le second est rejeté comme doublon et compté parmi les rejets du journal d'import.

**Limites.** Tout repose sur la capacité des sources à fournir une clé stable. Un fichier d'assureur sans identifiant de mouvement demande une clé composée dans le mapping (contrat, type, date, montant), avec un risque de faux doublon si deux mouvements identiques existent le même jour.

### 5.4 Règles

| Objet | Rôle | Attributs clés |
| --- | --- | --- |
| `Agreement` | Accord contractuel qui fonde une règle | type (convention de partenariat, avenant RCD, avenant RCP, convention d'escompte, protocole assureur, mandat), parties, dates, statut de signature, référence du document |
| `CommissionPlan` | Ensemble cohérent de règles | code, libellé, entité, sens (`INBOUND`, `OUTBOUND`), millésime |
| `Rule` | Règle de rémunération | code, libellé, famille de calcul (section 6), plan |
| `RuleVersion` | Version datée d'une règle | validité, statut (`DRAFT`, `APPROVED`, `ACTIVE`, `RETIRED`), périmètre (types d'événement déclencheurs compris), assiette, nature de ligne produite, paramètres de formule, politique de reprise, échéancier de versement, priorité, groupe d'exclusion, auteur, approbateur |
| `Scale` | Barème | mode (`MARGINAL`, `WHOLE`), paliers (borne basse, borne haute, taux ou montant) |
| `Condition` | Condition de versement | indicateur ou compteur, opérateur, seuil, période de mesure |
| `Enrollment` | Inscription d'un distributeur à une règle réservée | distributeur ou groupement, règle, accord, dates, statut |
| `AdvanceAgreement` | Convention d'escompte | distributeur, validité (12 mois au plus), produits couverts, délai d'anticipation en jours, plafond d'adhésions, rang d'impayé déclenchant la reprise, statut (`VALID`, `SUSPENDED`, `CLOSED`) |

Le périmètre d'une version de règle est une conjonction de filtres sur : entité, gamme, produit, option, type de tarif, collège, assureur, type d'événement, mode de rémunération choisi, type de distributeur, distributeur ou groupement nommé, inscription requise, mode de vente, critères libres de la garantie, âge du souscripteur, ancienneté de la garantie.

**Comment ça marche : de l'accord à la version.** Quatre objets s'emboîtent. L'accord est le document signé qui fonde la règle. Le plan regroupe des règles cohérentes, par exemple « Guide 2026, santé individuelle ». La règle nomme une mécanique, par exemple « Santé Select précompté ». La version porte tout ce qui se calcule : dates, périmètre, paramètres. Quand le barème change, on ne modifie rien : on ajoute une version.

**Exemple.** Voici la version 2026 de la règle Santé Select précompté, telle que l'application la stocke :

```json
{
  "rule": "SANTE_SELECT_UPFRONT",
  "family": "UPFRONT",
  "versionNo": 1,
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31",
  "versionAnchor": "EVENT_DATE",
  "exclusionGroup": "BASE",
  "priority": 10,
  "scope": {
    "entity": "ALPTIS_ASSURANCES",
    "product": ["SANTE_SELECT"],
    "eventType": ["PREMIUM_COLLECTED", "PREMIUM_CANCELLED"],
    "remunerationMode": "UPFRONT"
  },
  "parameters": {
    "allowedRates": [
      {"acquisition": 0.30, "servicing": 0.10},
      {"acquisition": 0.20, "servicing": 0.10}
    ],
    "firstYearDays": 365
  },
  "clawbackPolicy": {
    "type": "AGE_STEPS",
    "natures": ["ACQUISITION"],
    "triggers": ["TERMINATION", "WITHDRAWAL"],
    "steps": [
      {"maxDays": 365, "rate": 1.0},
      {"maxDays": 730, "rate": 0.5}
    ]
  }
}
```

L'administrateur ne voit jamais ce texte : il remplit un formulaire propre à la famille. Un critère absent du périmètre signifie « tous » ; un critère à plusieurs valeurs signifie « l'une d'elles ». La politique de reprise d'une règle ne décrit que les reprises propres à cette règle ; le sans-suite (`NOT_PURSUED`) est une règle générale du moteur, hors politique : il reprend 100 % des lignes `LINEAR`, `ACQUISITION`, `SERVICING` et `FEE` acquises sur l'adhésion, apurées comprises, quelle que soit la règle ; l'escompte est régularisé par sa rupture (section 6.4) et la RCD par la sortie du cumul (section 6.7) (section 6.3).

**Limites.** Le périmètre est une conjonction : il ne sait pas dire « produit A ou distributeur B ». Ce cas demande deux règles. Il ne sait pas non plus exprimer une exclusion (« tous les produits sauf ») : on liste les produits inclus.

### 5.5 Résultats

| Objet | Rôle | Attributs clés |
| --- | --- | --- |
| `Period` | Mois de traitement d'une entité | entité, mois, état (section 7), dates de chaque transition |
| `CalculationRun` | Exécution d'un calcul | période, type (prévisionnel, réel, simulation), statut, horodatage, auteur, empreinte du paramétrage, paramètres (simulation), compteurs |
| `CommissionLine` | Ligne de commission, immuable | nature, sens, garantie, événement source, version de règle, assiette, taux, montant, bénéficiaire, code de production d'origine, mois de rattachement, exercice, statut, ligne d'origine en cas de correction, motif, exécution, position d'escompte, bordereau, ordre de paiement, blocage et sa date de levée |
| `AdvancePosition` | Suivi de l'escompte d'une garantie | convention, montants avancés par nature (apport, suivi, linéaire), montants apurés, régularisation, date de paiement, statut |
| `Adjustment` | Ajustement manuel | type, montant, bénéficiaire, garantie éventuelle, motif, pièce jointe, contrepassation automatique, échéancier de remboursement, second compte pour un transfert, auteur, approbateur, statut |
| `ControlResult` | Résultat d'un contrôle | contrôle, période, objet concerné, gravité, valeur constatée, décision, auteur de la décision |
| `DistributorAccount` | Compte distributeur | entité, bénéficiaire, solde, blocage manuel, son motif et sa date de levée |
| `AccountEntry` | Écriture du compte | compte, date, type de mouvement (paiement, rejet de paiement, solde d'ouverture, remboursement reçu, abandon de créance), montant, ordre de paiement |
| `PaymentBatch` | Lot de paiement | entité, période, référence, type (cycle, cycle épargne retraite, hors cycle, chèques), date d'exécution, total, statut |
| `PaymentOrder` | Ordre de paiement | lot, bénéficiaire, IBAN, montant, mode (virement, chèque), bordereau, statut (à valider, validé, émis, réglé, rejeté, annulé) |
| `Statement` | Bordereau | bénéficiaire, période, niveau (code de production, distributeur, groupement), type de lot, entités couvertes, solde reporté, total, version, statut, fichiers PDF et tableur |
| `AccountingEntry` | Écriture comptable auxiliaire | événement comptable, comptes débit et crédit (rôles), montant, journal, date, exercice, mois, ligne de commission (ou ordre de paiement, ou position d'escompte), extraction |
| `CutoffExtract` | Extraction mensuelle | entité, mois, date d'arrêté, fichier, statut |
| `AuditEvent` | Trace d'audit, inaltérable | horodatage, utilisateur, action, objet, valeurs avant et après |

Natures d'une ligne de commission : `LINEAR`, `ACQUISITION`, `SERVICING`, `ADVANCE`, `ADVANCE_ADJUSTMENT`, `CLAWBACK`, `RCD`, `RCP`, `COMPLEMENT` (rémunération complémentaire retraite), `OUTSTANDING` (sur encours), `ARBITRAGE`, `RETROCESSION`, `FEE` (frais de dossier, honoraires), `EXTERNAL`, `MANUAL`, `TRANSFER` (compensation entre comptes), `INSURER` (commission reçue). L'apurement d'un escompte n'est pas une nature : c'est le statut `OFFSET` d'une ligne acquise.

Statuts d'une ligne : `FORECAST` (ligne prévisionnelle, calculée sur prime émise ou à la souscription selon le produit ; c'est une ligne distincte, jamais payée et jamais transformée en ligne acquise), `EARNED` (acquise, calculée sur prime encaissée), `PAYABLE` (validée et portée sur un bordereau), `PAID` (réglée), `OFFSET` (imputée sur un escompte, donc non payée), `CANCELLED` (annulée par une ligne inverse avant la validation métier, section 10.1).

**Nature et statut : deux axes indépendants.** La nature dit ce qu'est la ligne (un apport, une reprise, une RCD) et ne change jamais. Le statut dit où elle en est (acquise, payable, payée) et progresse. Une reprise de −30 € suit le même parcours qu'une commission : elle est acquise, portée sur un bordereau, puis soldée avec le paiement.

**Exemple.** L'encaissement de 100 € du 5 avril, sur une garantie Santé Select précomptée 30 + 10 de 96 jours (effet au 30 décembre 2025), produit deux lignes. Voici l'explication stockée avec la ligne d'apport :

```json
{
  "event": {"type": "PREMIUM_COLLECTED", "date": "2026-04-05", "basis": 100.00},
  "ruleVersion": "SANTE_SELECT_UPFRONT v1",
  "coverageAgeDays": 96,
  "firstYear": true,
  "rate": 0.30,
  "amount": 30.00,
  "payee": {"payeeType": "DISTRIBUTOR", "payeeCode": "P13897", "originProductionCode": "61723", "share": 1.0}
}
```

C'est ce contenu que l'écran affiche en clair quand un gestionnaire répond à un courtier : « 30 € d'apport, soit 30 % de votre encaissement de 100 € du 5 avril, garantie dans sa première année ».

**Limites.** L'explication décrit le calcul, pas la donnée d'entrée : si la prime reçue de l'outil de gestion est fausse, la ligne est fausse et son explication cohérente. Détecter cela relève des contrôles (section 8.6).

### 5.6 Autres énumérations

| Objet et attribut | Valeurs |
| --- | --- |
| `Rule.family` | `LINEAR`, `UPFRONT`, `ADVANCE`, `TIERED` (barème sur cumul), `PORTFOLIO` (annuelle sous conditions), `SHARE` (quote-part d'une autre commission), `OUTSTANDING`, `FIXED`, `EXTERNAL` |
| `Scale.tier.fromFirstUnit` | Vrai si le taux du palier s'applique dès le premier euro une fois le palier atteint |
| `CalculationRun.type` | `FORECAST`, `ACTUAL`, `SIMULATION` |
| `AdvancePosition.status` | `PENDING` (éligible, pas encore versé), `ACTIVE` (versé, en cours d'apurement ou, pour l'épargne retraite, en attente du cinquième anniversaire), `SETTLED`, `CANCELLED` |
| `Enrollment.status` | `PENDING_SIGNATURE`, `ACTIVE`, `ENDED` |
| `Adjustment.type` | `EXCEPTIONAL_ADVANCE`, `MANUAL_CLAWBACK`, `CORRECTION`, `EXCEPTIONAL_COMMISSION`, `ACCOUNT_TRANSFER` |
| `Adjustment.status` | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `APPLIED` |
| `ControlResult.severity` | `INFO`, `WARNING`, `BLOCKING` |
| `ControlResult.decision` | `OPEN`, `FIXED`, `ACCEPTED`, `DEFERRED` |
| `PaymentBatch.status` | `DRAFT`, `VALIDATED`, `ISSUED`, `SETTLED` |
| `PaymentOrder.status` | `TO_VALIDATE`, `VALIDATED`, `ISSUED`, `SETTLED`, `REJECTED`, `CANCELLED` |
| `Agreement.signatureStatus` | `NOT_SIGNED`, `SIGNED` |

Valeurs de code des autres attributs, échangés avec les systèmes voisins ou internes. Les sources ne donnent que des libellés ; ces codes sont proposés (hypothèse H-51).

| Objet et attribut | Valeurs |
| --- | --- |
| `Insurer.role` | `RISK_CARRIER`, `DELEGATED_MANAGER` |
| `ProductRange.target` | `INDIVIDUAL` (particulier), `PROFESSIONAL` (professionnel), `COMPANY` (entreprise) |
| `Product.managementMode` | `DIRECT`, `DELEGATED` |
| `ProductOption.optionType` | `BASE` (niveau ou formule), `RIDER` (renfort), `PACK`, `FUND` (support financier) |
| `Distributor.type` | `INDEPENDENT`, `GROUP_MEMBER`, `REMOTE_SALES`, `AGENT` |
| `Distributor.oriasStatus` | `REGISTERED`, `STRUCK_OFF` |
| `Distributor.statementGrouping` | `NONE`, `BY_INSURER`, `BY_PRODUCT` |
| `PayeeRule.paymentFrequency` | `MONTHLY`, `QUARTERLY`, `HALF_YEARLY`, `YEARLY` |
| `PayeeRuleShare.payeeType`, `DistributorAccount.payeeType` | `PRODUCTION_CODE`, `DISTRIBUTOR`, `GROUP` |
| `Agreement.type` | `PARTNERSHIP`, `RCD_AMENDMENT`, `RCP_AMENDMENT`, `ADVANCE_AGREEMENT`, `INSURER_PROTOCOL`, `MANDATE` |
| `QualityIndicator.type` | `COMPLAINT_RATE`, `NOT_PURSUED_RATE`, `NEW_BUSINESS_LAPSE_RATE`, `PORTFOLIO_LAPSE_RATE`, `UNPAID_RATE`, `LOSS_RATIO`, `SOLVENCY_SCORE` |
| `Policyholder.personType` | `NATURAL`, `LEGAL` |
| `Contract.periodicity` | `MONTHLY`, `QUARTERLY`, `HALF_YEARLY`, `YEARLY`, `SINGLE` |
| `Contract.salesChannel` | `PAPER`, `ALPTIS_CONNECT`, `REMOTE_E_SIGNATURE`, `OTHER` |
| `Contract.endReason`, `Coverage.endReason` | `TERMINATION`, `WITHDRAWAL`, `NOT_PURSUED`, `DEATH`, `EARLY_REPAYMENT`, `SURRENDER`, `OUTBOUND_TRANSFER` |
| `Coverage.tariffType` | `REDUCED`, `STANDARD`, `INCREASED` |
| `Coverage.staffCategory` | `EXECUTIVE`, `NON_EXECUTIVE` ; vide quand la garantie n'a pas de collège |
| `CoverageAssignment.role` | `MAIN`, `CO_BROKER` |
| `CoverageAssignment.origin` | `SUBSCRIPTION`, `TRANSFER`, `REPLACEMENT_ORDER` |
| `PortfolioTransfer.advanceDebtHandling` | `REPAID_BY_TRANSFEROR`, `TAKEN_OVER` |
| `PortfolioTransfer.status` | `PENDING`, `PROCESSED` |
| `RuleVersion.basis` | `COLLECTED_PREMIUM`, `ISSUED_PREMIUM`, `ANNUALISED_PREMIUM`, `OUTSTANDING_BALANCE`, `SOURCE_COMMISSION`, `FIXED_AMOUNT`, `NET_SCHEDULED_PAYMENTS` (versements programmés annualisés, nets de frais) |
| `Condition.operator` | `LT`, `LE`, `GT`, `GE`, `EQ` |
| `CalculationRun.status` | `RUNNING`, `COMPLETED`, `FAILED` |
| `ImportBatch.status` | `RECEIVED`, `LOADED`, `PARTIALLY_LOADED`, `REFUSED` |
| `ImportRejection.status` | `OPEN`, `CORRECTED`, `DISCARDED` |
| `ExportBatch.status` | `GENERATED`, `DELIVERED`, `FAILED` |
| `Claim.status` | `OPEN`, `IN_PROGRESS`, `CLOSED` |
| `PaymentOrder.paymentMode` | `TRANSFER`, `CHEQUE` |
| `Statement.level` | `PRODUCTION_CODE`, `DISTRIBUTOR`, `GROUP` |
| `Statement.status` | `DRAFT`, `PUBLISHED`, `CORRECTED` |
| `CutoffExtract.status` | `GENERATED`, `DELIVERED` |
| `Employee.jobCode` | `CAC`, `RB`, `DR`, `RR`, `DNV` (postes de la grille 2026) |
| `EmployeeMeasure.type` | `ABSENCE`, `PRODUCTION`, `ACTIVITY`, `MARGIN`, `SPECIFICATIONS_RECEIVED` |
| `VariableResult.component` | `MONTHLY_INDIVIDUAL`, `QUARTERLY_QUALITATIVE`, `ANNUAL_COLLECTIVE`, `ANNUAL_ADJUSTMENT`, `MANUAL_ADJUSTMENT` |
| `VariableResult.status` | `CALCULATED`, `VALIDATED`, `EXPORTED` |

Une version de règle `APPROVED` devient `ACTIVE` automatiquement à sa date de début et `RETIRED` à sa date de fin. Le bénéficiaire n'est pas un objet propre : c'est le porteur du compte, c'est-à-dire un code de production, un distributeur ou un groupement. Un blocage n'est pas un statut de ligne : c'est un attribut du compte ou de la ligne, avec son motif.

### 5.7 Modèle de données relationnel

Le modèle physique compte 68 tables PostgreSQL, organisées en six domaines ; `commission_line` en est le centre, reliée à l'événement qui la déclenche, à la version de règle qui la calcule, au compte qui la porte et au bordereau qui la justifie.

#### Diagrammes de classes UML

Quatre diagrammes de classes couvrent le modèle. Chaque classe correspond à une table, de même nom sauf Condition (table rule\_condition) ; seuls les attributs principaux sont montrés. Un attribut typé par une autre classe (par exemple `coverage : Coverage`) est une association vers une classe d'un autre diagramme.

&#91;embedded content: diagramme de classes UML · distributeurs et versement\]

&#91;embedded content: diagramme de classes UML · contrats, garanties et événements\]

&#91;embedded content: diagramme de classes UML · règles de rémunération\]

&#91;embedded content: diagramme de classes UML · calcul, comptes, paiement et comptabilité\]

Les tables ci-dessous donnent le détail complet, colonne par colonne.

**Conventions.**

- Noms en anglais, en minuscules avec tirets bas ; une table par objet de la section 5.
- Clé primaire `id bigint` générée, sauf mention ; clé étrangère nommée `<table>_id`.
- Toute table métier porte `entity_id`, clé étrangère vers `legal_entity`, directement ou par sa table parente (une garantie par son contrat, une version de règle par son plan, un ordre par son lot), sauf les référentiels partagés entre entités.
- Montants en `numeric(14,2)`, taux en `numeric(9,6)`, dates en `date`, horodatages en `timestamptz`.
- Énumérations stockées en `varchar(30)` avec contrainte de valeurs ; ce sont celles des sections 3 (rôles), 5.1 à 5.6, 7.1 (états de la période), 8.2 (codes motif de rejet), 8.6 (familles de contrôle : `COMPLETENESS`, `DATA_QUALITY`, `CALCULATION`, `ADVANCE`, `CLAWBACK`, `SUPPLEMENT`, `ACCOUNT`, `PAYMENT`, `ACCOUNTING`, `EXTERNAL_FLOW`), 8.14 (types d'objectif : `INDIVIDUAL_QUANTITATIVE`, `QUALITATIVE`, `COLLECTIVE_QUANTITATIVE`), 9.11 (types de document) et 10.2 (événements comptables, rôles de compte et variantes), complétées par les valeurs données entre parenthèses dans les tableaux ci-dessous. Motifs de mise en attente (`hold_reason`) : `UNKNOWN_REFERENCE`, `NO_RULE`, `RATE_OUT_OF_GRID`, `CONCURRENT_RULES`, `EXTERNAL_MISMATCH`, `OUT_OF_BOUNDS`, `UNMATCHED_NEGATIVE`. Motifs d'exclusion du paiement : `ACCOUNT_BLOCKED`, `PAYEE_INACTIVE`, `NON_POSITIVE_BALANCE`, `BELOW_THRESHOLD`, `NO_BANK_DETAILS`, `FREQUENCY`. Motifs de clôture d'une position d'escompte : `SETTLED_BY_OFFSET`, `SETTLED_AT_TERM`, `WRITE_OFF`, le code de l'événement de rupture, ou `REPLACEMENT_ORDER`, `PORTFOLIO_TRANSFER`, `AGREEMENT_CLOSED`. Le code d'un compteur de condition est un libellé libre défini par la règle.
- Historisation par `valid_from` et `valid_to` (nul = en vigueur) ; deux lignes d'un même objet ne se chevauchent pas.
- Chaque table porte `created_at` et `created_by`, ainsi que la date de dernière modification updated\_at, non répétés ci-dessous.
- `business_event`, `commission_line`, `accounting_entry` et `audit_event` sont en ajout seul une fois validées, et partitionnées par mois.

#### Référentiels

| Table | Colonnes | Clés et contraintes |
| --- | --- | --- |
| `legal_entity` | `code varchar(20)`, `name`, `debtor_iban varchar(34)`, `debtor_bic varchar(11)`, `min_payment_amount`, `advance_settlement_days int` (360), advance\_settlement\_tolerance, `payment_calendar jsonb` | Unicité de `code` |
| `entity_parameter` | `entity_id`, `param_key`, `param_value`, `valid_from` | Unicité (`entity_id`, `param_key`, `valid_from`) |
| `insurer` | `code`, `name`, `role` | Unicité de `code` |
| `product_range` | `code`, `label`, `target` | Unicité de `code` |
| `product` | `entity_id`, `code`, `label`, `range_id`, `insurer_id`, `management_mode`, `reference_lag_months smallint` (0), `forecast_basis` (`ISSUED_PREMIUMS`, `ANNUALISED_AT_SUBSCRIPTION`, `NONE`), `commissionable boolean`, `marketed_from`, `marketed_to` | Unicité (`entity_id`, `code`) |
| `product_option` | `product_id`, `code`, `label`, `option_type` | Unicité (`product_id`, `code`) |
| `distributor_group` | `code`, `label`, `head_distributor_id` | Unicité de `code` |
| `distributor` | `partner_code`, `legal_name`, `legal_form`, `siren char(9)`, `address_line1`, `address_line2`, `postal_code`, `city`, `orias_number`, `orias_status`, `distributor_type`, `group_id`, `sales_delegation`, `commissionable boolean`, `advance_excluded boolean`, `statement_grouping` (`NONE`, `BY_INSURER`, `BY_PRODUCT`) | Unicité de `partner_code` |
| `distributor_entity` | `distributor_id`, `entity_id`, `status`, `valid_from`, `valid_to` | Unicité (`distributor_id`, `entity_id`, `valid_from`) : une ligne par entité et par période de statut, ce qui conserve l'historique des statuts |
| `production_code` | `entity_id`, `distributor_id`, `code`, `label`, `status` | Unicité (`entity_id`, `code`) : un code de production désigne un seul distributeur dans une entité |
| `external_code` | `object_type`, `object_id`, `source_system`, `external_code` | Unicité (`object_type`, `source_system`, `external_code`) |
| `bank_account` | `entity_id`, `holder_type`, `holder_id`, `iban`, `bic`, `holder_name`, `valid_from`, `valid_to` | `holder_type` : `PRODUCTION_CODE`, `DISTRIBUTOR`, `GROUP` |
| `payee_rule` | `entity_id`, `production_code_id`, `distributor_id`, `range_id`, `payment_frequency`, `valid_from`, `valid_to` | Au moins un de `production_code_id` et `distributor_id` renseigné |
| `payee_rule_share` | `payee_rule_id`, `payee_type`, `payee_id`, `share numeric(7,6)` | Somme des parts d'une règle = 1 |
| `quality_indicator` | `entity_id`, `distributor_id`, `indicator_type`, `range_id`, `period_from`, `period_to`, `value numeric(12,6)`, `source` | Unicité (`entity_id`, `distributor_id`, `indicator_type`, `range_id`, `period_to`) |
| `portfolio_transfer` | `entity_id`, `from_distributor_id`, `to_distributor_id`, `effect_date`, `scope jsonb`, `advance_debt_handling`, `status` |  |
| `replacement_order` | `contract_id`, `from_production_code_id`, `to_production_code_id`, `received_date`, `effect_date` |  |

#### Contrats et événements

| Table | Colonnes | Clés et contraintes |
| --- | --- | --- |
| `policyholder` | `entity_id`, `source_system`, `external_id`, `last_name`, `first_name`, `birth_date`, `person_type` | Unicité (`entity_id`, `source_system`, `external_id`) |
| `contract` | `entity_id`, `source_system`, `contract_number`, `product_id`, `policyholder_id`, `parent_contract_id`, `signature_date`, `registration_date`, `effect_date`, `end_date`, `end_reason`, `status`, `periodicity`, `sales_channel`, `new_business boolean` | Unicité (`entity_id`, `source_system`, `contract_number`) |
| `coverage` | `contract_id`, `coverage_code`, `option_id`, `effect_date`, `end_date`, `end_reason`, `status`, `annual_premium`, `tariff_type`, `staff_category`, `single_premium boolean`, `commissionable boolean` | Unicité (`contract_id`, `coverage_code`) |
| `coverage_criterion` | `coverage_id`, `criterion_key`, `criterion_value`, `valid_from`, `valid_to` | Index (`coverage_id`, `criterion_key`) |
| `remuneration_choice` | `coverage_id`, `mode`, `linear_rate`, `acquisition_rate`, `servicing_rate`, `advance_requested boolean`, `valid_from`, `valid_to` | Taux cohérents avec `mode` |
| `coverage_assignment` | `coverage_id`, `production_code_id`, `role`, `share numeric(7,6)`, `origin`, `valid_from`, `valid_to` | Somme des parts en vigueur d'une garantie = 1 |
| `import_mapping` | `entity_id`, `flow_code`, `source_system`, `version_no`, `definition jsonb`, `valid_from`, `valid_to` | Unicité (`entity_id`, `flow_code`, `source_system`, `version_no`) |
| `import_batch` | `entity_id`, `flow_code`, `mapping_id`, `source_system`, `file_name`, `file_hash char(64)`, `received_at`, `imported_by`, `lines_read`, `lines_filtered`, `lines_loaded`, `lines_rejected`, `lines_on_hold`, `status` | Unicité (`entity_id`, `source_system`, `file_hash`) : refus du réimport ; le système source vient du répertoire de dépôt, ou d'un paramètre obligatoire pour un téléversement à l'écran ou un dépôt par API |
| `export_batch` | entity\_id, flow\_code, file\_name, file\_hash char(64), generated\_at, generated\_by, lines\_written, total\_amount, status | Journal des fichiers émis, planifiés ou à la demande |
| `import_rejection` | `import_batch_id`, `line_number`, `raw_line text`, `reason_code`, `status`, `resolved_at`, `resolved_by` |  |
| `business_event` | `entity_id`, `source_system`, `source_key`, `event_type`, `contract_id`, `coverage_id`, `event_date`, `received_date`, `period_from`, `period_to`, `gross_amount`, `tax_amount`, `fee_amount`, `basis_amount`, `fund_code`, `management_profile`, `unpaid_rank smallint`, `recovered_after_litigation boolean`, `reverses_event_id`, `precomputed_basis`, `precomputed_rate`, `precomputed_amount`, `beneficiary_code`, `label`, `processing_status`, `hold_reason`, `eligible_period_id`, `import_batch_id` | Unicité (`entity_id`, `source_system`, `source_key`) ; index (`coverage_id`, `event_date`) et (`processing_status`, `eligible_period_id`) |

#### Règles

| Table | Colonnes | Clés et contraintes |
| --- | --- | --- |
| `agreement` | `entity_id`, `agreement_type`, `distributor_id`, `group_id`, `insurer_id`, `reference`, `valid_from`, `valid_to`, `signature_status`, `signed_at`, `document_ref` |  |
| `commission_plan` | `entity_id`, `code`, `label`, `direction`, `vintage smallint` | Unicité (`entity_id`, `code`) |
| `rule` | `plan_id`, `code`, `label`, `family` | Unicité (`plan_id`, `code`) |
| `rule_version` | `rule_id`, `version_no`, `valid_from`, `valid_to`, `status`, `version_anchor` (`EVENT_DATE`, `COVERAGE_EFFECT_DATE`), `scope jsonb`, `trigger_event_types varchar[]`, `basis`, `output_nature`, `parameters jsonb`, `clawback_policy jsonb`, `payment_schedule jsonb`, `priority int`, `exclusion_group`, `agreement_id`, `approved_by`, `approved_at` | Unicité (`rule_id`, `version_no`) ; `output_nature` nul pour la famille `UPFRONT`, dont les deux natures sont fixées par la famille ; `approved_by` différent de `created_by` ; dès le statut `APPROVED`, seuls `status` et `valid_to` changent encore : la fin de validité est avancée à l'approbation de la version suivante. La colonne des types d'événement déclencheurs recopie ceux du périmètre, pour l'indexation |
| `scale` | `rule_version_id`, `mode`, `measure` (`AMOUNT`, `COUNT`), `output` (`RATE`, `AMOUNT`) |  |
| `scale_tier` | `scale_id`, `lower_bound`, `upper_bound`, `rate`, `amount`, `from_first_unit boolean` | Paliers d'un barème sans chevauchement |
| `rule_condition` | `rule_version_id`, `indicator_type`, `counter_code`, `operator`, `threshold numeric(14,6)`, `measurement_period`, `on_failure` (`FORFEIT`, `DEFER`) |  |
| `enrollment` | `rule_id`, `distributor_id`, `group_id`, `agreement_id`, `valid_from`, `valid_to`, `status` | Un seul de `distributor_id` et `group_id` renseigné |
| `advance_agreement` | `entity_id`, `distributor_id`, `agreement_id`, `valid_from`, `valid_to`, `anticipation_days int`, `max_contracts int`, `unpaid_rank_threshold smallint` (2), `split jsonb`, `status` | `valid_to` au plus 12 mois après `valid_from` |
| `advance_agreement_product` | `advance_agreement_id`, `product_id` | Clé primaire sur les deux colonnes |

#### Calcul, contrôles et ajustements

| Table | Colonnes | Clés et contraintes |
| --- | --- | --- |
| `period` | `entity_id`, `period_month date`, `state`, `data_cutoff_date` | Unicité (`entity_id`, `period_month`) |
| `period_transition` | `period_id`, `from_state`, `to_state`, `occurred_at`, `user_id`, `reason` |  |
| `calculation_run` | `period_id`, `run_type`, `started_at`, `finished_at`, `launched_by`, `app_version`, `config_hash char(64)`, `status`, `parameters jsonb`, `counters jsonb` |  |
| `commission_line` | `entity_id`, `period_id`, `run_id`, `nature`, `direction`, `status`, `contract_id`, `coverage_id`, `event_id`, `rule_version_id`, `basis_amount`, `rate`, `amount`, `account_id`, `origin_production_code_id`, `origin_distributor_id`, `attachment_month date`, `premium_fiscal_year smallint`, `origin_line_id`, `reason`, `explanation jsonb`, `advance_position_id`, `statement_id`, `payment_order_id`, `blocked boolean`, `block_reason`, blocked\_until, `earned_at`, `payable_at`, `paid_at` | Index (`account_id`, `status`), (`coverage_id`, `nature`), (`event_id`), (`statement_id`) ; seuls le statut, le blocage, le bordereau, l'ordre et leurs dates sont modifiables |
| `advance_position` | `advance_agreement_id`, `coverage_id`, `status`, `paid_at`, `settlement_due_date`, `advanced_acquisition`, `advanced_servicing`, `advanced_linear`, `offset_acquisition`, `offset_servicing`, `offset_linear`, `adjustment_amount`, `closed_at`, `close_reason` | Unicité de `coverage_id` ; `advance_agreement_id` nul pour l'emprunteur et l'épargne retraite |
| `production_counter` | `entity_id`, `rule_id`, `payee_type`, `payee_id`, `enrollment_id`, `counter_year smallint`, `counter_month smallint`, `cumulative_basis`, `cumulative_entitlement` | Unicité (`rule_id`, `payee_type`, `payee_id`, `counter_year`, `counter_month`) ; `enrollment_id` nul pour une règle sans inscription (complément retraite) ; `payee_type` et `payee_id` désignent ici le titulaire du cumul, distributeur ou groupement, non le bénéficiaire du règlement |
| `surcommission_allocation` | `commission_line_id`, `contract_id`, `annualised_premium`, `allocated_amount` | Somme des montants = montant de la ligne |
| `adjustment` | `entity_id`, `period_id`, `adjustment_type`, `amount`, `account_id`, `coverage_id`, `corrected_line_id`, `reason`, `attachment_ref`, `auto_reverse boolean`, repayment\_schedule jsonb, counterpart\_account\_id, `status`, `approved_by`, `approved_at`, `resulting_line_id` | `approved_by` différent de `created_by` |
| `claim` | `entity_id`, `distributor_id`, `statement_id`, `received_at`, `subject`, `status`, `resolution`, `closed_at` |  |
| `claim_line` | `claim_id`, `commission_line_id` | Clé primaire sur les deux colonnes |
| `control_definition` | `entity_id`, `code`, `label`, `family`, `severity`, `parameters jsonb`, `active boolean`, `valid_from`, `valid_to` | Unicité (`entity_id`, `code`, `valid_from`) |
| `control_result` | `period_id`, `control_id`, `object_type`, `object_id`, `severity`, `observed_value`, `expected_value`, `decision`, `decided_by`, `decided_at`, `justification` | Index (`period_id`, `decision`) |

#### Comptes, paiements, documents, comptabilité

| Table | Colonnes | Clés et contraintes |
| --- | --- | --- |
| `distributor_account` | `entity_id`, `payee_type`, `payee_id`, `blocked boolean`, `block_reason`, `blocked_until` | Unicité (`entity_id`, `payee_type`, `payee_id`) ; le solde est calculé, jamais stocké |
| `account_entry` | `account_id`, `period_id`, `entry_date`, `entry_type` (`PAYMENT`, `PAYMENT_REJECTION`, `OPENING_BALANCE`, `REPAYMENT_RECEIVED`, `WRITE_OFF`), `amount`, `payment_order_id` | Mouvements du compte autres que les lignes de commission |
| `payment_batch` | `entity_id`, `period_id`, `batch_reference, batch_type` (`CYCLE`, SAVINGS\_CYCLE, `OFF_CYCLE`, `CHEQUE`), `execution_date`, `total_amount`, `status`, `validated_by`, `validated_at`, `sepa_file_ref`, `sepa_file_hash char(64)` |  |
| `payment_order` | `batch_id`, `account_id`, `statement_id`, `amount`, `payment_mode`, `iban`, `bic`, `creditor_name`, `end_to_end_id`, `status`, `rejection_reason`, `settled_at` | Unicité de `end_to_end_id` ; `amount` égal au net du bordereau, ou au sous-total de l'entité pour un bordereau multi-entités |
| `payment_exclusion` | `batch_id`, `account_id`, `reason`, `amount` |  |
| `statement` | `entity_id`, `period_id`, `account_id`, `batch_type`, `statement_number`, `version_no`, `level`, `carried_balance`, `period_total`, `net_amount`, `status` (`DRAFT`, `PUBLISHED`, `CORRECTED`), `replaces_statement_id`, `pdf_ref`, `pdf_hash char(64)`, `sheet_ref`, `published_at` | Unicité (`statement_number`, `version_no`) ; `entity_id`, la période et le compte nuls pour un bordereau multi-entités, rattaché alors à ses lignes de commission et, s'il y a paiement, à ses ordres, un par entité |
| `document_template` | `entity_id`, `document_type`, `version_no`, `content text`, `valid_from` |  |
| `account_mapping` | `entity_id`, `account_role`, `account_number`, `journal`, `analytic_axes jsonb` | Unicité (`entity_id`, `account_role`) |
| `accounting_scheme` | `entity_id`, `accounting_event`, `nature`, `variant`, `debit_role`, `credit_role` | Unicité (`entity_id`, `accounting_event`, `nature`, `variant`) ; `variant` vaut `STANDARD`, `NEGATIVE`, `NO_FORECAST` ou `SAVINGS` (section 10.2) |
| `accounting_entry` | `entity_id`, `period_id`, `accounting_event`, `journal`, `entry_date`, `debit_account`, `credit_account`, `amount`, `fiscal_year smallint`, `accounting_month date`, `premium_fiscal_year smallint`, `commission_line_id`, `payment_order_id`, `advance_position_id`, `cutoff_extract_id` | Index (`period_id`, `accounting_event`) ; `cutoff_extract_id` renseigné une seule fois |
| `cutoff_extract` | `entity_id`, `period_id`, `cutoff_date`, `file_ref`, `file_hash char(64)`, `generated_at`, `status` | Unicité de `period_id` |

#### Transverse et forces de vente

| Table | Colonnes | Clés et contraintes |
| --- | --- | --- |
| `role_mapping` | `directory_group`, `role`, `entity_id` | Unicité (`directory_group`, `role`, `entity_id`) ; aucun compte utilisateur local |
| `audit_event` | `occurred_at`, `user_id`, `entity_id`, `action`, `object_type`, `object_id`, `before_value jsonb`, `after_value jsonb`, `previous_hash char(64)`, `hash char(64)` | Ajout seul ; chaînage par empreinte |
| `employee` | `entity_id`, `external_id`, `last_name`, `first_name`, `job_code`, `team`, `region`, `manager_id`, `valid_from`, `valid_to` | Lot 9 |
| `employee_measure` | `employee_id`, `measure_type`, `dimension`, `period_from`, `period_to`, `value` | Absences, production, activité, marge, cahiers des charges reçus |
| `sales_objective` | `entity_id`, `employee_id`, `job_code`, `objective_type`, `period_from`, `period_to`, `target_value`, `version_no` |  |
| `variable_plan` | `entity_id`, `job_code`, `component`, `envelope_amount`, `instalment_amount`, `parameters jsonb`, `valid_from`, `valid_to` |  |
| `variable_result` | `period_id`, `employee_id`, `plan_id`, `component`, `achievement_rate`, `amount`, `adjustment_reason`, `status`, `validated_by`, `validated_at`, `explanation jsonb` |  |

Le solde d'un compte se lit par une vue : somme des `commission_line` du compte de sens `OUTBOUND`, hors statuts `FORECAST` et `OFFSET`, plus la somme des `account_entry`, les paiements y étant négatifs.

## 6. Règles de rémunération

Le moteur ne connaît aucun produit : il applique des familles de calcul paramétrées à des événements. Cette section définit les principes communs, puis chaque famille avec sa formule, ses paramètres et sa politique de reprise. Les taux cités sont ceux du Guide de rémunération 2026, des avenants RCD et RCP 2026 et du protocole UMR ; ils sont des données de paramétrage, pas du code.

### 6.1 Principes communs

| N° | Principe |
| --- | --- |
| P1 | Assiette par défaut : prime effectivement encaissée, hors taxes et contributions de toute nature. |
| P2 | Sont exclues de l'assiette : les primes recouvrées après une procédure précontentieuse ou contentieuse, les cotisations d'association, les droits d'entrée, les primes exonérées. |
| P3 | Période de référence : la période M porte les événements reçus jusqu'au dernier jour du mois M ; elle est calculée et payée en M+1. Un décalage en mois, paramètre du produit, retarde l'entrée d'une prime dans le calcul : 1 mois pour Sopretis (paiement en M+2), 2 mois pour Alptis Dynamique Emprunteur, Iriade, CNP, MNCAP et Maestro (paiement en M+3). En attendant, l'événement reste au statut PENDING. |
| P4 | Chaque prime émise produit une ligne `FORECAST` ; chaque prime encaissée produit une ligne `EARNED`. Seules les lignes `EARNED` alimentent le compte distributeur et le paiement. Deux exceptions pour le prévisionnel, selon un paramètre du produit : provision constituée en une fois à la souscription sur la prime annualisée, ou aucun prévisionnel (section 10.1). |
| P5 | La version de règle appliquée est celle en vigueur à la date d'effet de l'événement. Une règle peut retenir à la place la version en vigueur à la date d'effet de la garantie, pour les conditions « acquises à la souscription ». |
| P6 | Une modification de paramétrage ne recalcule jamais une période validée. Elle s'applique aux événements à venir. |
| P7 | Sélection des règles : toutes les versions actives dont le périmètre couvre l'événement sont candidates. Dans un même groupe d'exclusion, seule la priorité la plus haute s'applique. Les règles de groupes différents se cumulent. |
| P8 | Si aucune règle de base ne couvre un événement commissionnable, l'événement passe `ON_HOLD` avec une anomalie ; il sera recalculé dès que le paramétrage ou la donnée manquante arrive. Une règle de base est une règle du groupe d'exclusion réservé `BASE`. |
| P9 | Le taux choisi par le distributeur est porté par la garantie. La règle contrôle qu'il appartient à la grille autorisée ; sinon l'événement passe `ON_HOLD`. |
| P10 | Le bénéficiaire est déterminé par l'affectation de la garantie à la date de l'événement, puis par la règle de versement du code de production. |
| P11 | Une annulation d'événement produit la ligne inverse de la ligne d'origine, avec la même version de règle, le même taux et le même bénéficiaire. |
| P12 | Si le choix de rémunération de la garantie change au cours de la période couverte par une prime, la prime est répartie au prorata des jours entre les deux taux. Un changement de version de règle, lui, s'applique par date d'événement, sans prorata (P5). |
| P13 | Arrondi au centime, demi s'éloignant de zéro, ligne par ligne. En cas de partage entre bénéficiaires, le dernier reçoit le reliquat. |
| P14 | Le montant, l'assiette, le taux et la règle d'une ligne ne changent jamais. Seul son statut progresse. Toute correction est une nouvelle ligne liée à la première. |
| P15 | Le calcul continue pour un distributeur suspendu ou radié ; seules ses lignes sont bloquées au paiement. |
| P16 | Un produit, une garantie ou un distributeur peut être marqué non commissionnable ; ses événements passent `NOT_COMMISSIONABLE` sans anomalie. |
| P17 | Toute règle `OUTBOUND` peut avoir sa jumelle `INBOUND` (commission reçue de l'assureur). La marge de l'entité est la différence, par garantie et par période. |

**Comment ça marche.** Pour chaque événement de la période, le moteur déroule toujours les six mêmes étapes, dans cet ordre :

1. **Qualification.** L'événement est-il commissionnable (P2, P16) ? Sa période est-elle arrivée, compte tenu du décalage du produit (P3) ? Sinon il attend ou sort du calcul.
2. **Sélection.** Le moteur rassemble toutes les versions de règles actives à la date de l'événement dont le périmètre couvre l'événement : entité, produit, type d'événement, mode de rémunération, distributeur, etc. (P5, P7).
3. **Arbitrage.** Dans chaque groupe d'exclusion, il ne garde que la version de priorité la plus haute. Les survivantes de groupes différents s'appliquent toutes (détail en section 6.11).
4. **Contrôle du taux.** Le taux porté par la garantie doit appartenir à la grille de la règle retenue (P9).
5. **Calcul.** La famille de la règle transforme l'assiette en une ou plusieurs lignes, arrondies au centime (P13).
6. **Bénéficiaire et explication.** Chaque ligne est attribuée au bénéficiaire désigné par l'affectation et la règle de versement (P10), puis enregistrée avec tout ce qui a servi à la calculer.

**Exemple.** Le 15 mars 2026, CLEVA transmet un encaissement de 100 € hors taxes sur une garantie Santé Select en mode précompté 30 + 10, à effet du 1er janvier 2026. Étape 1 : la prime est commissionnable et Santé Select n'a pas de décalage, elle entre donc dans la période de mars. Étape 2 : une seule règle couvre l'événement, le barème précompté Santé Select du Guide (groupe `BASE`, priorité 10). Étape 3 : pas de concurrente, elle est retenue. Étape 4 : le couple 30 + 10 figure dans la grille. Étape 5 : la garantie est dans sa première année, la règle produit une ligne `ACQUISITION` de 30,00 € et une ligne `SERVICING` de 10,00 €. Étape 6 : les deux lignes vont au compte du code de production de la garantie. Elles seront payées début avril. La RCD n'intervient pas ici : elle ne se déclenche pas sur un encaissement mais une fois par mois, sur le cumul des adhésions nouvelles (section 6.7).

**Exemple de décalage (P3).** Une prime Sopretis encaissée en mars 2026 a un décalage d'un mois : elle reste `PENDING` pendant la période de mars, entre dans le calcul de la période d'avril et se paie début mai.

**Trois partis pris à garder en tête.**

- *Pas de formule libre.* L'administrateur choisit une famille et remplit ses paramètres ; il n'écrit jamais de formule. Ce que le métier peut exprimer est donc borné par les familles existantes, mais tout calcul reste testable et explicable.
- *Tout est daté.* Règles, taux, affectations et hiérarchie ont des dates de validité. La question n'est jamais « quel est le taux ? » mais « quel était le taux à la date de l'événement ? ».
- *Rien ne se réécrit.* Une ligne validée ne change plus (P14). Une erreur se corrige par une ligne supplémentaire, visible sur le bordereau suivant.

**Limites.** Le moteur ne devine rien : un événement qu'aucune règle de base ne couvre n'est ni payé à zéro ni ignoré, il attend en `ON_HOLD` jusqu'à ce que quelqu'un paramètre la règle (P8). Un mode de calcul qui n'entre dans aucune famille n'est pas un paramétrage : c'est un développement (section 12).

### 6.2 Commissions de base

**Linéaire** (`LINEAR`). Une ligne par prime encaissée :

```latex
C = A \times t
```

avec A l'assiette et t le taux linéaire choisi.

**Précomptée** (`UPFRONT`). Deux lignes par prime encaissée pendant la première année de la garantie, une seule ensuite :

```latex
C_{apport} = A \times t_a \quad \text{si la période couverte tombe dans les 365 premiers jours}
\qquad
C_{suivi} = A \times t_s \quad \text{toujours}
```

La première année court sur 365 jours à partir de la date d'effet de la garantie. Une prime à cheval sur la limite est répartie au prorata des jours.

Paramètres de la famille : grille de taux autorisés (liste ou intervalle avec pas), couples apport et suivi autorisés, taux distinct par collège possible, politique de reprise.

**Exemple en linéaire.** Garantie à 15 %, cotisation de 100 € hors taxes par mois : chaque encaissement donne une ligne de 15,00 €, tant que le contrat vit. Rien n'est versé d'avance, rien n'est repris en cas de résiliation.

**Exemple en précompté.** Même cotisation, couple 30 + 10 :

| Moment | Apport | Suivi | Total par encaissement |
| --- | --- | --- | --- |
| Chaque mois de la 1re année | 30,00 € | 10,00 € | 40,00 € |
| Chaque mois à partir de la 2e année | — | 10,00 € | 10,00 € |

Le précompté paie donc davantage la première année (480 € contre 180 €) et moins ensuite (120 € par an contre 180 €). Le linéaire rattrape le précompté au bout de six ans : 1 080 € des deux côtés. C'est pour cela que l'apport, et lui seul, est repris si le contrat s'arrête tôt (section 6.3).

**Exemple à la frontière de la première année.** Garantie à effet du 1er mars 2026, couple 30 + 10. La première année s'achève le 28 février 2027. Une prime trimestrielle de 300 € couvre la période du 1er février au 30 avril 2027, soit 89 jours dont 28 dans la première année. Apport : 300 × 28 / 89 × 30 % = 28,31 €. Suivi : 300 × 10 % = 30,00 €.

**Exemple de changement de taux en cours de période (P12).** Le distributeur fait passer une garantie de 15 % à 18 % le 16 juin. La prime de juin, 100 €, couvre 30 jours : 15 jours à 15 % (7,50 €) et 15 jours à 18 % (9,00 €), soit 16,50 € en deux lignes.

**Limites.** Le prorata suppose que l'événement porte sa période de cotisation. Si la source ne la fournit pas, la prime est rattachée en entier à la date de l'événement, sans prorata (hypothèse H-45). Le passage d'une garantie du linéaire au précompté en cours de vie n'est pas décrit par les sources : la première année reste comptée depuis la date d'effet de la garantie, si bien qu'un passage après 365 jours n'ouvre droit à aucun apport (hypothèse H-52).

### 6.3 Reprise de commission

Une reprise récupère une commission déjà acquise quand la garantie prend fin trop tôt. Elle est calculée dans la période où l'événement de fin est reçu.

| Événement | Reprise par défaut |
| --- | --- |
| `TERMINATION` ou `WITHDRAWAL` dans les 365 jours suivant la date d'effet | 100 % de la commission d'apport acquise sur la garantie |
| `TERMINATION` ou `WITHDRAWAL` entre le 366e et le 730e jour | 50 % de la commission d'apport acquise |
| Au-delà de 730 jours | Aucune |
| `NOT_PURSUED` | 100 % des lignes `LINEAR`, `ACQUISITION`, `SERVICING` et `FEE` acquises sur l'adhésion, apurées comprises, quelle que soit la règle |
| `DEATH` | Aucune |
| `PREMIUM_CANCELLED` | Pas de reprise : la ligne inverse du principe P11 suffit |

La politique de reprise est un paramètre de la version de règle. Trois formes existent :

1. `AGE_STEPS` : une liste de seuils en jours et de pourcentages, comme ci-dessus.
2. `PRO_RATA` : sur une durée D en mois, reprise égale à la commission acquise multipliée par (D − n) / D, n étant le nombre de mois écoulés.
3. `NONE`.

Chaque forme précise les natures reprises (apport seul, apport et suivi, toutes) et les événements déclencheurs. Le périmètre de la règle permet une politique propre à un distributeur, à un groupement ou à un millésime.

Règles de garde :

- La reprise porte sur les lignes réellement acquises, nettes des annulations et des reprises antérieures. Elle ne peut jamais dépasser ce qui a été acquis.
- La ligne de reprise est débitée au bénéficiaire qui a perçu la commission, même si la garantie a changé de distributeur depuis.
- En cas de partage entre un groupement et un distributeur, la reprise suit le même partage.
- Une ligne acquise mais imputée sur un escompte compte comme perçue.

L'ancienneté se mesure de la date d'effet de la garantie à la date d'effet de l'événement de fin, et non à sa date de réception. Si la résiliation est annulée (`REINSTATEMENT`), la reprise est annulée par sa ligne inverse.

**Comment ça marche.** À la réception d'un événement de fin :

1. Le moteur mesure l'ancienneté de la garantie : date d'effet de l'événement moins date d'effet de la garantie, en jours. Les bornes sont incluses : 365 jours d'ancienneté donnent encore 100 %, 730 jours encore 50 %.
2. Il lit dans la politique de reprise de la règle le pourcentage qui correspond à cette ancienneté.
3. Il additionne les lignes des natures visées (par défaut l'apport) réellement acquises sur la garantie, annulations et reprises antérieures déduites.
4. Il crée une ligne `CLAWBACK` négative par bénéficiaire, égale à ce total multiplié par le pourcentage.
5. La ligne entre au compte du distributeur dans cette même période et vient en déduction de son prochain paiement.

**Exemple.** Garantie en 30 + 10, cotisation de 100 € par mois. Huit encaissements ont été commissionnés, dont un annulé ensuite pour impayé. Apport net acquis : 7 × 30 = 210 €. Suivi net acquis : 70 €.

| Événement | Ancienneté | Reprise | Ce que garde le distributeur |
| --- | --- | --- | --- |
| Résiliation à effet du 200e jour | 365 jours au plus | 100 % de 210 = −210,00 € | 70 € de suivi |
| Résiliation à effet du 400e jour | Entre 366 et 730 jours | 50 % de l'apport acquis | La moitié de l'apport et tout le suivi |
| Résiliation à effet du 800e jour | Plus de 730 jours | Aucune | Tout |
| Adhésion classée sans suite | Sans objet | 100 % de tout : −280,00 € | Rien |

Si la résiliation du 200e jour est annulée le mois suivant, une ligne de +210,00 € neutralise la reprise.

**Limites.**

- *Résiliation connue tard.* L'ancienneté se mesure à la date d'effet, pas à la date de réception : une résiliation à effet du 300e jour reçue deux ans plus tard déclenche bien une reprise de 100 %.
- *Prime encaissée après la résiliation.* Un encaissement tardif portant sur une période antérieure à la fin est commissionné normalement ; la reprise est alors complétée du même pourcentage dans la même période (hypothèse H-46).
- *Distributeur parti.* La reprise est débitée même si le distributeur n'a plus de portefeuille ; son compte devient débiteur et relève des règles de la section 10.5.
- *Linéaire.* Aucune reprise sur une commission linéaire, hors « sans suite » (hypothèse H-05).

### 6.4 Escompte

L'escompte verse d'avance les commissions des 12 premiers mois, puis les récupère sur les commissions réellement acquises. Il s'applique à une garantie quel que soit son mode, linéaire ou précompté.

**Éligibilité d'une garantie.** Toutes les conditions suivantes sont requises :

1. Le distributeur a une convention d'escompte au statut `VALID` et n'est pas exclu de l'escompte.
2. Le produit figure dans la convention et l'adhésion est individuelle.
3. La date d'enregistrement de l'adhésion est comprise entre le début de la convention et sa fin plus 5 jours.
4. La date d'effet de la garantie est comprise entre le début de la convention et sa fin plus le délai d'anticipation.
5. La garantie n'est ni à prime unique ni une garantie de tiers payant (critère de la garantie, fourni dans le pivot), et n'a donné lieu à aucune commission dans une période antérieure.
6. Le distributeur n'a pas changé depuis l'enregistrement.
7. Le plafond d'adhésions de la convention n'est pas atteint (100 pour un distributeur nouveau ou sans portefeuille).
8. Le distributeur a présenté la garantie à l'escompte (attribut « escompté » de son choix de rémunération).

**Versement.** L'escompte est payé dans la première période dont le mois atteint le mois de la date d'effet diminuée du délai d'anticipation. Le délai est un nombre de jours libre ; les valeurs en usage sont 0 (dit « paiement à la date d'effet » : l'escompte entre dans la période du mois d'effet et se paie au début du mois suivant), 120, 180, 240, 270, 300 et 360. La convention peut fractionner le versement : une part à cette date, le solde un nombre de mois donné après la date d'effet. L'écart Δ se calcule alors sur l'escompte total dû, versé ou non ; à la rupture, la part non encore versée est annulée et Δ se calcule sur le seul escompte versé.

**Montant.** Avec PA la prime annualisée hors taxes :

```latex
E = PA \times (t_a + t_s) \ \text{en précompté} \qquad E = PA \times t \ \text{en linéaire}
```

Une ligne `ADVANCE` est créée par nature (apport, suivi, linéaire). La nature avancée figure dans `explanation` de la ligne et dans la ventilation de la position d'escompte (`advanced_acquisition`, `advanced_servicing`, `advanced_linear`). Si la source ne fournit pas la prime annualisée, elle est estimée à partir des primes émises : assiette émise multipliée par 360 et divisée par le nombre de jours couverts, comptés à raison de 30 jours par mois.

**Apurement.** Tant que l'escompte est en cours, chaque ligne acquise sur la garantie dont la période couverte débute avant le premier anniversaire de la date d'effet passe au statut `OFFSET` au lieu d'être payée, et son montant s'ajoute au cumul apuré. Une ligne dont la période couverte débute à partir de cet anniversaire est payée normalement, même si l'escompte est encore en cours. Les apurements n'apparaissent jamais dans un paiement.

**Solde.** L'écart se calcule ainsi :

```latex
\Delta = \text{cumul apuré} - \text{escompte versé}
```

| Situation | Effet |
| --- | --- |
| Δ atteint ou dépasse zéro, à tout moment | Escompte `SETTLED`. Si Δ > 0, une ligne `ADVANCE_ADJUSTMENT` de ce montant est créée et payée : c'est le complément d'escompte |
| Δ encore négatif 360 jours après la plus tardive des dates de paiement et d'effet | Escompte `SETTLED`. Une ligne `ADVANCE_ADJUSTMENT` de montant Δ est créée : c'est la reprise d'escompte indu. Exception : sous la tolérance de solde, aucune ligne n'est créée et le reliquat passe en charge |
| Événement de rupture avant le solde | Escompte `CANCELLED`. Une ligne `ADVANCE_ADJUSTMENT` de montant Δ est créée immédiatement |
| Aucun des trois | L'escompte reste `ACTIVE` |

Événements de rupture : résiliation, renonciation, sans suite, contentieux, impayé de rang supérieur ou égal au seuil de la convention (2e par défaut, 3e si la convention le prévoit), changement de distributeur par ordre de remplacement, cession de portefeuille dont la créance est remboursée par le cédant, rachat anticipé d'un prêt, clôture de la convention. Le décès n'est pas un événement de rupture : l'escompte reste en cours et se solde au 360e jour.

Après le solde, les commissions de la garantie sont payées normalement. La reprise d'apport de la section 6.3 s'applique en plus, sur l'apport acquis.

Une annulation d'encaissement qui porte sur une ligne apurée diminue d'autant le cumul apuré : sa ligne inverse prend elle aussi le statut `OFFSET`, hors solde du compte, et passe l'écriture `ADVANCE_OFFSETTING` inverse.

**Exemple.** Prime annualisée 1 000 € payée par semestre, apport 30 %, suivi 10 %. Escompte versé : 400 € (300 d'apport, 100 de suivi). Résiliation au bout de 6 mois : 200 € ont été apurés (150 d'apport, 50 de suivi). Reprise d'escompte indu : 200 €. Reprise d'apport à 100 % : 150 €. Total repris : 350 € ; le distributeur conserve 50 € de suivi.

**Vie de la convention.**

- Une convention dure 12 mois au plus et ne se reconduit pas tacitement.
- `SUSPENDED` arrête les nouveaux escomptes sans toucher aux escomptes en cours.
- `CLOSED` annule les escomptes non encore versés et rompt les escomptes en cours, qui passent au statut annulé avec régularisation immédiate.
- Une convention arrivée à échéance et sans escompte en cours est clôturée automatiquement.
- La convention affiche en permanence : nombre d'adhésions escomptées, total versé, total apuré, régularisations positives et négatives, créance restante.
- Lors d'une cession de portefeuille, la créance d'escompte est soit remboursée par le cédant, soit transférée au compte du repreneur. Remboursée (`REPAID_BY_TRANSFEROR`) : la cession vaut événement de rupture et la ligne `ADVANCE_ADJUSTMENT` est créée sur le compte du cédant. Transférée (`TAKEN_OVER`) : l'escompte reste en cours, les apurements se poursuivent sur les commissions du repreneur et la régularisation finale est portée à son compte ; aucune écriture n'est passée à la cession, la créance restant au compte « Avances aux distributeurs ».

**Comment ça marche, mois par mois.** Garantie en 30 + 10, cotisation de 100 € par mois, prime annualisée de 1 200 €, effet au 1er janvier 2026, convention avec paiement à la date d'effet.

| Période | Ce qui se passe | Versé au distributeur | Cumul apuré | Écart Δ |
| --- | --- | --- | --- | --- |
| Janvier | Escompte : 1 200 × 40 % = 480 € (360 d'apport, 120 de suivi). L'encaissement de janvier donne 40 € acquis, imputés sur l'avance | 480 € | 40 € | −440 € |
| Février à novembre | Chaque encaissement donne 40 € acquis, imputés | 0 € | 440 € fin novembre | −40 € |
| Décembre | 12e encaissement : 40 € acquis, imputés. Δ atteint zéro : escompte soldé | 0 € | 480 € | 0 € |
| Janvier 2027 et suivants | La garantie est en 2e année : 10 € de suivi par encaissement | 10 € par mois | — | — |

Sur deux ans, le distributeur touche exactement ce qu'il aurait touché sans escompte ; seul le calendrier change. L'escompte est une avance de trésorerie, pas une rémunération de plus.

**Exemple de délai d'anticipation.** Garantie à effet du 1er juin 2026, délai de 120 jours : la date de référence est le 1er février 2026. L'escompte est calculé dans la période de février et payé début mars, trois mois avant le premier encaissement.

**Pourquoi un solde à 360 jours.** Si le 12e encaissement tarde, l'écart est repris au 360e jour (ici −40 €), puis la commission de l'encaissement tardif, arrivée après le solde, est payée normalement (+40 €). Le résultat est neutre pour le distributeur ; la règle évite seulement de laisser une avance ouverte indéfiniment.

**Piège des arrondis.** Prime annualisée de 1 000 € payée par mois : 83,33 € par encaissement. L'escompte versé est de 400,00 €. Chaque encaissement donne 25,00 € d'apport et 8,33 € de suivi, soit 399,96 € en douze mois. Sans précaution, l'application reprendrait 0,04 € au 360e jour et l'afficherait sur le bordereau. Une tolérance de solde, paramètre de l'entité fixé à 1 € par défaut, traite ce cas : si l'écart est négatif et inférieur à la tolérance en valeur absolue (un écart positif reste versé en complément d'escompte, quel que soit son montant), l'escompte est soldé sans ligne de régularisation et le reliquat passe en charge par l'événement comptable ADVANCE\_WRITE\_OFF (hypothèse H-40).

**Limites.**

- *La prime change en cours d'année.* L'escompte versé n'est jamais recalculé. Une hausse fait apurer plus vite et donne un complément d'escompte (cas CR-24) ; une baisse laisse un déficit, repris au 360e jour (cas CR-28).
- *Deux reprises sur une résiliation.* L'exemple plus haut le montre : la reprise d'escompte indu récupère ce qui n'a pas été apuré, la reprise d'apport récupère ce qui l'a été. Elles ne font pas double emploi.
- *Créance irrécouvrable.* L'application constate la dette au compte du distributeur ; elle ne gère ni mise en demeure ni recouvrement.

### 6.5 Emprunteur

Les produits emprunteur sont gérés par un gestionnaire délégué qui calcule aujourd'hui les commissions. L'application les traite en deux temps.

1. Tant que la gestion des commissions reste déléguée, les commissions arrivent déjà calculées (section 6.10) et sont seulement portées au compte et au bordereau.
2. À la reprise en main, les règles suivantes s'appliquent. Elles se paramètrent avec les familles existantes : `LINEAR` ou `UPFRONT` pour les taux, le type de tarif et la durée du prêt étant des critères de périmètre ; `ADVANCE` sans convention pour l'avance de première année ; `FIXED` pour les frais de dossier. Le rachat anticipé est un traitement du moteur déclenché par l'événement `EARLY_REPAYMENT`, hors politique de reprise, comme le sans-suite ; sa durée de 36 mois est un paramètre de la règle.

| Règle | Contenu |
| --- | --- |
| Taux | Selon le type de tarif (réduit, standard, majoré) et la durée du prêt, critère de la garantie |
| Précompté | Ouvert seulement au-delà d'une durée de prêt (10 ans, ou 6 ans selon le produit). Toujours escompté, de droit et sans convention : l'avance est versée à la première échéance et les conditions d'éligibilité de la section 6.4 ne s'appliquent pas. Le taux de première année se décompose en apport et suivi : 40 % = 30 + 10 ; 50 % = 35 + 15 |
| Frais de dossier | Montant fixe de 0 à 1 000 € saisi à la souscription (un montant hors de ces bornes met l'événement en attente) ; ligne `FEE` reversée au distributeur |
| Rachat anticipé dans les 36 premiers mois (`EARLY_REPAYMENT`) | Reprise de l'escompte versé et du linéaire éventuel au-delà de la première année, puis recalcul de toutes les commissions au taux linéaire du même type de tarif (hypothèse H-18). La régularisation est la différence entre ce recalcul et ce qui a été versé, net de la régularisation d'escompte éventuelle ; elle est portée par une ligne `CLAWBACK`. Si l'avance de première année est encore en cours, elle est d'abord rompue selon la section 6.4 |
| Paiement | Toujours par virement |

**Exemple de précompté emprunteur.** Prêt de 15 ans au tarif standard, prime de 1 000 € par an, précompté 40 % puis 10 %. À la première échéance, l'avance de première année est versée : 400 € (300 d'apport, 100 de suivi). Les encaissements de la première année sont imputés sur cette avance. À partir de la deuxième année, chaque encaissement est payé à 10 %.

**Exemple de rachat anticipé.** Le même prêt est remboursé par anticipation au 24e mois. Le distributeur a perçu 400 € la première année et 100 € la seconde, soit 500 €. Le recalcul au taux linéaire standard donne 2 000 × 15 % = 300 €. Régularisation : 300 − 500 = −200,00 €, inscrite à son compte. S'il avait été remboursé au 40e mois, il n'y aurait rien eu : la règle ne joue que pendant les 36 premiers mois.

**Exemple de frais de dossier.** Le distributeur saisit 300 € de frais à la souscription : une ligne `FEE` de 300,00 € lui est reversée à l'encaissement des frais, sans taux ni politique de reprise (le sans-suite la reprend, section 6.3).

**Limites.** Tant que le gestionnaire délégué calcule, l'application ne peut pas expliquer ses montants : elle affiche l'assiette, le taux et le montant reçus, rien de plus. Le remboursement anticipé partiel n'est pas traité par les sources : il ne déclenche aucune régularisation (hypothèse H-47).

### 6.6 Épargne retraite (PER)

Les commissions de l'épargne portent sur des versements et des encours, pas sur des primes. Deux flux coexistent : ce que l'assureur verse à l'entité, et ce que l'entité verse au distributeur.

**Commission versée au distributeur (Guide 2026).**

| Élément | Mode linéaire | Mode escompté |
| --- | --- | --- |
| Versement initial et versements libres | Taux de frais appliqué au dossier moins 0,5 point, soit 0 à 3,5 % (frais plafonnés à 4 %) | Identique |
| Versements programmés | Taux de frais moins 0,5 point | 8 % du versement programmé annualisé en année 1 ; rien des années 2 à 5 ; 1,20 % de chaque versement à partir de l'année 6 |
| Encours en gestion libre, fonds en euros | 0,30 % par an | 0,05 % par an |
| Encours en gestion libre, unités de compte | Barème de rétrocessions par support | Barème de rétrocessions par support |
| Encours en gestion libre, SCPI | 0,50 % par an | 0,20 % par an |
| Encours en fonds profilés et horizon, en euros | 0,46 % par an | 0,10 % par an |
| Encours en fonds profilés et horizon, en unités de compte | 0,46 % par an | 0,20 % par an |
| Arbitrages | 0,30 % du montant arbitré, à compter du 2e arbitrage de l'année civile (hypothèse H-17) | 0,30 % du montant arbitré, à compter du 2e arbitrage de l'année civile (hypothèse H-17) |

Conditions de l'escompte : le souscripteur a moins de 55 ans dans l'année de souscription et met en place des versements programmés à la souscription. Le taux de frais est alors imposé (1,95 % dans l'exemple du Guide). L'escompte est dû dans la période qui suit le premier versement programmé encaissé, délai de renonciation échu ; les années du contrat se comptent à partir de ce premier versement.

Exemples du Guide : versement initial de 6 000 € à 2,5 % de frais, commission 6 000 × 2,0 % = 120 €. Versements programmés de 300 € par mois à 3,5 % de frais : 9 € par mois en linéaire ; 300 × 12 × 8 % = 288 € en escompté.

**Rappel de l'escompte.** Chaque trimestre, pour tout contrat escompté de moins de 5 ans qui a connu une suspension non régularisée dans le trimestre, un arrêt des versements programmés, un rachat total ou partiel ou un transfert sortant :

```latex
R = E - \sum_{i} V_i \times t_{lin}
```

avec E l'escompte versé, V les versements programmés encaissés et le taux linéaire du contrat, soit son taux de frais moins 0,5 point. Le contrat quitte définitivement le mode escompté et repasse en linéaire. Exemple : 100 € par mois, escompte de 8 % soit 96 € ; 25 versements encaissés à 1,45 % (frais de 1,95 %) soit 36,25 € ; rappel de 59,75 €. La même formule vaut pour la commission reçue de l'assureur, avec ses propres taux ; le modèle UMR donne : escompte de 120 €, 25 versements à 1,95 % soit 48,75 €, rappel de 71,25 €.

**Réajustement annuel.** À chaque anniversaire du premier versement programmé encaissé, pendant les cinq premières années et dans la période du mois anniversaire, si les versements programmés encaissés sur 12 mois diffèrent de la base annualisée initiale, l'écart multiplié par le taux d'escompte donne un supplément d'escompte (hausse) ou une reprise (baisse). Un seuil de tolérance en pourcentage est paramétrable.

**Natures et écritures.** L'escompte retraite est une ligne `ADVANCE`, suivie par une position d'escompte sans convention, dont le montant est porté par `advanced_linear`. Il n'est pas apuré : les versements programmés des années 1 à 5 ne donnent aucune ligne (la règle d'escompte retraite les couvre et les marque traités, de sorte qu'ils ne passent pas en attente au titre de P8 ; la règle linéaire du produit les écarte par le critère de garantie « escompte en cours » (`PRECOMPTE_ACTIF`, section 9.4)), et le délai de 360 jours de la section 6.4 ne s'applique pas. La position est soldée (`SETTLED`) au cinquième anniversaire du premier versement, sans régularisation, ou rompue (`CANCELLED`) par un rappel. En comptabilité, cet escompte est une charge de commission dès son versement (variante `SAVINGS` du schéma, section 10.2) et ne passe pas par le compte « Avances aux distributeurs ». Le rappel de l'escompte et la reprise du réajustement annuel sont des lignes `CLAWBACK` ; le supplément d'escompte du réajustement annuel est une ligne `ADVANCE` de même variante.

**Familles de calcul utilisées.** L'épargne retraite ne demande aucune famille propre. Versements initiaux, libres et programmés en mode linéaire : famille `LINEAR`, le taux (frais du dossier moins 0,5 point) étant porté par la garantie. Versements programmés à partir de l'année 6 d'un contrat escompté : famille `LINEAR` à 1,20 %, dont le périmètre porte sur l'ancienneté de la garantie, comptée ici à partir du premier versement programmé encaissé, et dont le taux est fixé par la règle. Escompte retraite, rappel et réajustement annuel : famille `ADVANCE`, sans convention, au taux de 8 %. Encours : famille `OUTSTANDING`. Arbitrages : famille `LINEAR` sur l'événement d'arbitrage, avec le paramètre « rang minimal dans l'année civile » fixé à 2. Rémunération complémentaire retraite : famille `TIERED` en mode global, calculée à la clôture de l'année ; son rappel est la politique de reprise de cette règle. Commissions reçues de l'assureur : la ligne `INSURER` est chargée telle que reçue ; le montant attendu, calculé par les règles jumelles de sens `INBOUND` (P17, CAL-13), ne crée pas de ligne et ne donne aucune écriture : il est conservé avec le résultat du contrôle « commission reçue différente de l'attendu », et c'est lui que vérifient les cas CR-33, CR-34 et CR-36. Chargement par la famille `EXTERNAL`.

**Paramètres que ces usages ajoutent aux familles.** Ils valent pour toutes les règles, pas seulement pour l'épargne retraite et l'emprunteur.

| Famille | Paramètre | Rôle |
| --- | --- | --- |
| Toutes | `output_nature` | Nature de la ligne produite. `LINEAR` : `LINEAR` ou `ARBITRAGE`. `UPFRONT` : `ACQUISITION` et `SERVICING`. `ADVANCE` : `ADVANCE`. `TIERED` : `RCD` ou `COMPLEMENT`. `PORTFOLIO` : `RCP`. `SHARE` et `OUTSTANDING` : `RETROCESSION` ou `OUTSTANDING`. `FIXED` : `FEE`. `EXTERNAL` : `EXTERNAL` ou `INSURER` |
| `LINEAR` | `rateSource` | `COVERAGE` : taux porté par la garantie et contrôlé contre la grille (P9, étape 4 du calcul). `RULE` : taux fixé par la règle, sans contrôle de grille (1,20 % à partir de l'année 6, 0,30 % sur arbitrage, 2 % d'une opération de rentrée). Le paramètre `seniorityOrigin` (`EFFECT_DATE`, `FIRST_SCHEDULED_PAYMENT`) fixe l'origine de l'ancienneté utilisée par le périmètre |
| `LINEAR` | `minRankInCalendarYear` | Rang minimal de l'événement dans l'année civile pour le même contrat ; 2 pour les arbitrages |
| `ADVANCE` | `agreementRequired` | Vrai en santé et prévoyance (section 6.4) ; faux pour l'emprunteur et l'épargne retraite, dont les conditions d'éligibilité et le seuil d'impayé de la convention ne s'appliquent pas |
| `ADVANCE` | `rate`, `offsetting`, `settlementRule` | Taux propre (8 % en épargne retraite ; sinon le taux de la garantie). Apurement : vrai, sauf épargne retraite. Solde : `DAYS_360` ou `FIFTH_ANNIVERSARY` |
| `ADVANCE` | `recall`, `annualReadjustment`, `readjustmentTolerance` | Rappel trimestriel et réajustement annuel de l'épargne retraite, avec son seuil de tolérance (H-15) |
| `TIERED` | `calculationFrequency` | `MONTHLY` pour la RCD, `ANNUAL` pour le complément retraite. L'assiette `NET_SCHEDULED_PAYMENTS` vaut le versement programmé annualisé multiplié par (1 − taux de frais) |
| `UPFRONT` | `earlyRepaymentMonths`, `recalculationRate` | Emprunteur : durée pendant laquelle le rachat anticipé déclenche le recalcul (36 mois) et taux linéaire de ce recalcul |
| `FIXED` | — | Frais de dossier : déclenchée par l'événement `FILE_FEE_COLLECTED`, dont le montant est l'assiette ; les autres montants fixes par l'événement de leur périmètre |

**Rémunération complémentaire retraite** (`COMPLEMENT`). Calculée à la clôture de l'année sur la production d'affaires nouvelles du distributeur en versements programmés annualisés nets de frais, versée en mars N+1. Le taux du palier atteint s'applique à toute la production ; la borne basse de chaque palier est incluse. Ne comptent que les contrats dont le souscripteur a moins de 55 ans dans l'année de souscription et qui ont des versements programmés.

| Versements programmés annualisés du distributeur | Taux |
| --- | --- |
| Moins de 10 000 € | 10 % |
| De 10 000 à 14 000 € | 12 % |
| De 14 000 à 20 000 € | 15 % |
| De 20 000 à 50 000 € | 20 % |
| À partir de 50 000 € | 25 % |

Exemple du Guide : 292,50 € nets par mois, soit 292,50 × 12 × 10 % = 351 €. Un rappel calculé une fois par an, avec le complément de l'année (période de février), porté par une ligne `CLAWBACK`, reprend la totalité de la rémunération complémentaire versée au titre d'un contrat (hypothèse H-16) dès qu'il connaît, dans ses 5 premières années, une suspension, une diminution, un arrêt des versements programmés, un rachat ou un transfert sortant.

**Commission reçue de l'assureur (protocole UMR).**

| Élément | Règle |
| --- | --- |
| Versements programmés en commissionnement dérogatoire | 10 % en année 1, calculé sur 12 mois glissants après le délai de renonciation et le premier encaissement ; 0 % des années 2 à 5 ; 1,5 % à partir de l'année 6 |
| Encours, gestion libre | 0,09 % sur fonds en euros ; 0,245 % sur unités de compte ; 0,275 % sur produits structurés, SCPI et SCI |
| Encours, gestion à horizon et gestion pilotée | 0,12 % sur fonds en euros ; 0,30 % sur unités de compte |
| Rétrocessions | 15 % des rétrocessions sur produits structurés, OPCVM, SCPI et SCI |
| Sur-commission | 28 % des versements programmés des 12 mois suivant le premier encaissement, pour les adhésions de l'année dont le souscripteur a moins de 55 ans ; plafond de 50 000 € par contrat et par an ; calculée au 31 décembre, versée avant le 31 mars |
| Acquisition définitive | Après 5 ans de versements programmés ininterrompus |
| Paiement | Trimestriel, dans les 4 mois qui suivent la fin du trimestre |

Le partage des frais sur encours et sur arbitrage entre l'assureur et l'entité arrive ligne à ligne dans le fichier de l'assureur. La commission du distributeur sur encours se calcule alors de l'une des deux façons paramétrables : taux annuel appliqué à l'encours moyen de la période fourni par l'assureur, au prorata du nombre de mois de la période sur 12 ; ou quote-part de la commission reçue par l'entité. La fréquence est celle du fichier de l'assureur.

**Comment lire cette section.** Sur un contrat d'épargne retraite, l'entité est au milieu : l'assureur la rémunère selon le protocole, et elle rémunère le distributeur selon le Guide. Ce sont deux règles distinctes, de sens opposés, calculées sur les mêmes versements. Leur différence est la marge de l'entité.

**Exemple suivi sur un contrat.** Souscripteur de 40 ans, versements programmés de 300 € par mois, mode escompté, frais de 1,95 %.

| Élément | Reçu de l'assureur | Versé au distributeur | Marge |
| --- | --- | --- | --- |
| Année 1, versements programmés | 3 600 × 10 % = 360,00 € | 3 600 × 8 % = 288,00 € | 72,00 € |
| Sur-commission et complément retraite, en mars N+1 | 3 600 × 28 % = 1 008,00 € | 294,15 × 12 × 10 % = 352,98 € | 655,02 € |
| Années 2 à 5, versements programmés | 0 € | 0 € | 0 € |
| À partir de l'année 6, par versement de 300 € | 1,5 % = 4,50 € | 1,20 % = 3,60 € | 0,90 € |

Le complément se calcule sur le versement net de frais : 300 × (1 − 1,95 %) = 294,15 € par mois. Les commissions sur encours s'ajoutent des deux côtés tout au long du contrat.

**Exemple de commission sur encours.** Encours moyen de 20 000 € sur le fonds en euros, gestion libre, contrat en mode linéaire, fichier trimestriel de l'assureur : 20 000 × 0,30 % × 3 / 12 = 15,00 € pour le trimestre.

**Le rappel en clair.** L'escompte paie d'avance cinq années de commission. Si le contrat cesse d'être alimenté avant cinq ans, le rappel ramène le distributeur à ce qu'il aurait gagné en linéaire : il rend l'avance, moins la commission linéaire des versements réellement encaissés. Le même raisonnement vaut entre l'assureur et l'entité.

**Limites.**

- Le barème des rétrocessions par support n'est pas dans les sources : il doit être chargé au paramétrage, support par support (hypothèse H-54).
- La sur-commission de l'assureur est calculée au 31 décembre alors que les douze mois du contrat ne sont pas écoulés : le premier calcul est provisoire, complété l'année suivante (hypothèse H-35).
- Le texte du protocole sur les cas de rappel est incomplet (hypothèse H-14) ; la formule appliquée est celle du Guide et du modèle de fichier.
- Un versement programmé qui augmente ou diminue ne modifie pas l'escompte dans l'année : l'écart est traité une fois par an par le réajustement.

### 6.7 Rémunération complémentaire de développement (RCD)

La RCD rémunère le volume d'adhésions nouvelles d'un distributeur inscrit, par paliers, chaque mois.

| Paramètre | Valeur dans les avenants 2026 |
| --- | --- |
| Activation | Inscription du distributeur et avenant signé ; sans signature enregistrée, la règle ne produit rien |
| Période | Année civile ; l'avenant prend fin au 31 décembre |
| Assiette | Primes annualisées hors taxes des adhésions nouvelles acceptées depuis le 1er janvier, cumulées |
| Produits santé | Tous les produits individuels sauf Solution Santé Collective, CCN Santé et Luminéis |
| Produits prévoyance | Tous sauf Solution Prévoyance Collective et la gamme emprunteur |
| Modes de vente éligibles | Papier, Alptis Connect ou Espace Pro, signature électronique déportée |
| Barème santé | 20 000 à 45 000 € : 3 % ; 45 001 à 75 000 € : 4 % ; 75 001 à 100 000 € : 5 % ; à partir de 100 001 € : 5 % dès le premier euro |
| Barème prévoyance | 10 000 à 30 000 € : 5 % ; 30 001 à 50 000 € : 6 % ; 50 001 à 100 000 € : 8 % ; à partir de 100 001 € : 10 % |
| Mode du barème | `MARGINAL` : chaque tranche de production est rémunérée à son taux. Un palier peut être marqué « dès le premier euro » |
| Conditions de versement | Premier palier atteint ; taux d'impayés inférieur à 5 % à la date du calcul |
| Condition d'octroi | Taux de réclamations inférieur à 5 % sur 12 mois glissants |
| Versement | Mensuel |

Calcul mensuel :

```latex
RCD_M = B(P_M) - B(P_{M-1})
```

avec P la production cumulée depuis le 1er janvier à la fin du mois et B le barème appliqué à ce cumul. Si une condition de versement n'est pas remplie le mois M, la ligne du mois est à zéro et porte le motif ; le comportement est paramétrable (`FORFEIT` par défaut, `DEFER` pour un rattrapage le mois suivant).

La RCD d'un mois est répartie entre les adhésions du mois au prorata de leur prime annualisée ; cette répartition sert à la reprise.

Reprise : si l'adhésion est résiliée, rachetée ou mise en réduction dans ses 12 premiers mois, 100 % de sa part de RCD est reprise ; entre le 13e et le 24e mois, 50 %. Le décès de l'adhérent ne déclenche pas de reprise.

Le modèle accepte aussi les variantes décrites par le métier : barème `WHOLE` (le taux du palier atteint s'applique dès le premier euro), montant forfaitaire en euros par palier, palier exprimé en nombre de contrats, reprise au prorata temporis.

Précisions de calcul :

- La RCD se calcule au niveau de l'inscription : le distributeur, ou le groupement si c'est lui qui est inscrit. Elle est versée selon la règle de versement.
- Une adhésion classée sans suite ou ayant fait l'objet d'une renonciation sort de la production cumulée. Si le cumul baisse, la formule donne un montant négatif, inscrit tel quel.
- « Résiliée » correspond à `TERMINATION`, « rachetée » à `SURRENDER`, « mise en réduction » à `PLAN_SUSPENDED`.
- Un taux de réclamations de 5 % ou plus ne suspend pas le calcul (hypothèse H-56) : il produit une alerte de contrôle, l'avenant autorisant alors Alptis à le résilier. Il en va de même des autres seuils de l'avenant : sans-suite 12 %, radiation du portefeuille 20 %, radiation sur production nouvelle 12 %. Le taux d'impayés fait exception : à 5 % ou plus, il annule en outre la ligne du mois, au titre des conditions de versement.

**Comment ça marche.** Chaque mois, le moteur additionne les primes annualisées de toutes les adhésions nouvelles du distributeur depuis le 1er janvier, applique le barème à ce cumul pour obtenir le droit total de l'année à cette date, puis verse la différence avec le droit du mois précédent. Le barème santé se lit ainsi :

| Production cumulée P | Droit cumulé B(P) |
| --- | --- |
| Moins de 20 000 € | 0 € |
| De 20 000 à 45 000 € | 3 % de la part au-dessus de 20 000 € |
| De 45 001 à 75 000 € | 750 € + 4 % de la part au-dessus de 45 000 € |
| De 75 001 à 100 000 € | 1 950 € + 5 % de la part au-dessus de 75 000 € |
| À partir de 100 001 € | 5 % de toute la production |

**Exemple sur une année.** Distributeur inscrit à la RCD santé, avenant signé.

| Mois | Cumul P | Droit B(P) | Ligne du mois | Remarque |
| --- | --- | --- | --- | --- |
| Janvier | 15 000 € | 0 € | 0 € | Premier palier non atteint |
| Février | 30 000 € | 300 € | 300,00 € | 10 000 × 3 % |
| Mars | 60 000 € | 1 350 € | 1 050,00 € | 750 + 15 000 × 4 %, moins 300 |
| Avril | 70 000 € | 1 750 € | 0 € | Taux d'impayés de 6 % : les 400 € du mois sont perdus |
| Mai | 110 000 € | 5 500 € | 3 750,00 € | 5 500 − 1 750 |

Fin mai, le distributeur a reçu 5 100 € pour un droit de 5 500 € : l'écart est le mois d'avril, perdu. Avec le comportement `DEFER`, les 400 € auraient été versés en mai si le taux d'impayés était repassé sous 5 %.

**Exemple de répartition et de reprise.** Les 300 € de février sont répartis entre les deux adhésions du mois : A (prime annualisée 5 000 €) porte 100 €, B (10 000 €) porte 200 €. Si B est résiliée au 8e mois, 200 € sont repris ; au 15e mois, 100 €.

**Exemple de mois négatif.** Le cumul est de 60 000 € fin mars. En avril, une adhésion de 10 000 € est classée sans suite et aucune adhésion nouvelle n'arrive : le cumul retombe à 50 000 €, le droit à 950 €, et la ligne d'avril vaut 950 − 1 350 = −400,00 €.

**Limites.**

- *Saut au dernier palier.* À 100 000 € le droit est de 3 200 € ; à 100 001 € il passe à 5 000 €. L'adhésion qui fait franchir le seuil déclenche 1 800 € d'un coup, portés par les seules adhésions de ce mois-là : leur résiliation précoce coûte donc plus cher au distributeur que celle des adhésions des autres mois.
- *Trois lectures possibles de l'avenant.* Pour 60 000 €, on obtient 1 350 €, 1 950 € ou 2 400 € selon la lecture (hypothèses H-09 et H-38). La première est retenue ; les deux autres s'obtiennent par paramétrage.
- *Mois perdu.* Avec `FORFEIT`, la somme des lignes de l'année peut être inférieure au droit cumulé. C'est voulu, et le bordereau porte le motif.
- *Cumul avec la RCP.* RCD et RCP ne s'excluent pas : elles ne naissent pas des mêmes événements et se cumulent. Interdire le cumul demanderait une condition supplémentaire sur l'une des deux, pas un groupe d'exclusion (section 6.11).

### 6.8 Rémunération complémentaire de portefeuille (RCP)

La RCP est une surcommission annuelle sur les primes encaissées, soumise à des conditions de qualité et de volume. Elle ne fait l'objet d'aucune reprise.

| Paramètre | Avenant santé 2026 | Avenant prévoyance 2026 |
| --- | --- | --- |
| Assiette | Primes encaissées sur l'exercice, hors taxes et hors part de l'association souscriptrice | Identique |
| Produits exclus | Solution Santé Collective, CCN Santé, Luminéis | Solution Prévoyance Collective, CCN Prévoyance, gamme emprunteur |
| Taux | 4 % | Selon le nombre d'adhésions nouvelles de l'année : 5 à 9, 1 % ; 10 à 19, 2 % ; 20 à 39, 3 % ; 40 et plus, 4 % |
| Conditions de qualité | P/C inférieur ou égal à 95 % ; taux de radiation inférieur ou égal à 20 % | Taux de radiation inférieur ou égal à 15 % |
| Conditions de volume | Portefeuille santé au 31 décembre supérieur à 100 adhésions ; plus de 30 adhésions nouvelles dans l'année | Portefeuille prévoyance au 31 décembre d'au moins 40 adhésions ; prime annualisée du portefeuille au moins égale à celle de l'année précédente |
| Calcul | Période de janvier N+1, calculée début février | Période de janvier N+1, calculée début février |
| Versement | Avant le 15 février N+1 | Avant le 15 février N+1 |

Toutes les conditions sont cumulatives : si l'une manque, la RCP est nulle et la ligne à zéro porte le détail des conditions et des valeurs constatées. Le calcul se fait distributeur par distributeur, y compris pour les membres d'un groupement, même si le paiement va au groupement.

Seules comptent les adhésions réalisées sur papier, par Alptis Connect ou l'Espace Pro, ou par signature électronique déportée. Un avenant RCD ou RCP prend fin de plein droit à la cessation du partenariat, à la perte de l'immatriculation ORIAS ou à la cession du portefeuille ; l'inscription passe alors `ENDED`.

**Comment ça marche.** Dans la période de janvier N+1, calculée début février, pour chaque distributeur inscrit dont l'avenant est signé :

1. Le moteur réunit les indicateurs de l'année N arrêtés au 31 décembre : P/C, taux de radiation, taille du portefeuille, nombre d'adhésions nouvelles, prime annualisée du portefeuille.
2. Il additionne les primes encaissées de l'exercice sur les produits éligibles.
3. Il évalue chaque condition et garde le résultat, vrai ou faux, avec la valeur constatée.
4. Si toutes sont vraies, il applique le taux ; sinon le montant est nul.
5. Il crée une ligne `RCP` unique, payée avec cette période, donc début février, avant l'échéance du 15.

**Exemple en santé.** 200 000 € de primes encaissées en 2026.

| Condition | Seuil | Constat | Résultat |
| --- | --- | --- | --- |
| P/C | 95 % au plus | 82 % | Remplie |
| Taux de radiation | 20 % au plus | 14 % | Remplie |
| Portefeuille au 31 décembre | Plus de 100 adhésions | 140 | Remplie |
| Adhésions nouvelles de l'année | Plus de 30 | 36 | Remplie |

RCP : 200 000 × 4 % = 8 000,00 €. Avec 28 adhésions nouvelles au lieu de 36, la quatrième condition tombe et la RCP est de 0 € ; le distributeur lit sur son bordereau la condition manquée et la valeur constatée.

**Exemple en prévoyance.** 50 000 € encaissés, 12 adhésions nouvelles, conditions remplies : le palier « 10 à 19 » donne 2 %, soit 1 000,00 €.

**Limites.**

- *Effet de seuil.* La RCP est en tout ou rien : dans l'exemple, la 31e adhésion nouvelle vaut 8 000 €. Les contestations sont prévisibles ; l'explication stockée sur la ligne est faite pour y répondre.
- *Indicateur absent.* Un indicateur non reçu ne vaut pas condition manquée. Le contrôle « indicateur de condition manquant » bloque la validation de la période jusqu'à sa réception (hypothèse H-48) ; par exception à la règle générale de la section 7.3, cette anomalie ne peut pas être acceptée avec justification.
- *Indicateurs externes.* Le P/C vient du CRM ou de l'actuariat ; l'application le reçoit et ne peut pas le justifier.
- *Pas de reprise.* Une RCP versée reste acquise, même si le portefeuille fond l'année suivante.

### 6.9 Partage de la rémunération

| Cas | Règle |
| --- | --- |
| Versement au niveau voulu | La règle de versement désigne le bénéficiaire : code de production, distributeur ou groupement |
| Partage groupement et membre | La règle de versement porte plusieurs parts, par exemple 90 % au distributeur et 10 % au groupement. Chaque part donne une ligne de commission |
| Co-courtage | Plusieurs affectations simultanées sur la garantie, avec leur pourcentage. Chaque co-courtier reçoit sa part de la commission ; la somme des parts vaut 100 % |
| Rétrocession à un mandataire | La commission du mandataire est un pourcentage de la commission reçue par l'entité : 60 % sur les affaires nouvelles santé, prévoyance et IARD chez Promantis Conseil. Le pourcentage peut dépendre de l'année de vie du contrat |
| Rétrocession sur supports financiers | Taux annuel appliqué à l'encours moyen d'un support, ou montant par part souscrite, selon un barème par support |

Dans tous les cas, la ligne conserve le code de production d'origine : les restitutions montrent à chaque membre son propre montant, pas celui du groupement.

**Comment ça marche.** Le partage se fait en deux temps. Le co-courtage répartit d'abord la commission entre les distributeurs affectés à la garantie. La règle de versement de chacun répartit ensuite sa part entre lui et son groupement. Chaque part finale est une ligne de commission à part entière, avec son bénéficiaire.

**Exemple.** Commission de 100 € sur une garantie en co-courtage : courtier A 60 %, courtier B 40 %. A appartient à un groupement, avec une règle de versement 90 % et 10 % ; B est indépendant.

| Bénéficiaire | Calcul | Ligne |
| --- | --- | --- |
| Courtier A | 100 × 60 % × 90 % | 54,00 € |
| Groupement de A | 100 × 60 % × 10 % | 6,00 € |
| Courtier B | 100 × 40 % | 40,00 € |

Une reprise ultérieure de 100 € se répartit de la même façon : −54, −6 et −40.

**Exemple d'arrondi.** 10,00 € partagés en trois parts égales donnent 3,33 €, 3,33 € et 3,34 € : le dernier bénéficiaire reçoit le reliquat (P13), la somme reste exacte.

**Exemple de rétrocession.** L'assureur verse 100 € à Promantis Conseil sur une affaire nouvelle de santé. Le mandataire reçoit 60 % de cette commission reçue, soit 60,00 €. La rétrocession ne se calcule pas sur la prime : sans commission reçue, pas de rétrocession.

**Limites.** Les parts sont celles en vigueur à la date de l'événement ; un changement de répartition ne recalcule rien. Une garantie dont les parts de co-courtage ne totalisent pas 100 % est mise en anomalie à l'import. Le partage ne connaît que trois niveaux (hypothèse H-19) : un réseau à quatre étages ne se paramètre pas.

### 6.10 Commissions déjà calculées

Quand un tiers calcule la commission, l'événement `EXTERNAL_COMMISSION` porte l'assiette, le taux et le montant. L'application crée une ligne `EXTERNAL` à l'identique, sans recalcul, après avoir contrôlé que le montant égale l'assiette multipliée par le taux à un centime près. La ligne suit ensuite le cycle normal : compte, bordereau, paiement, comptabilité.

Quand un assureur verse une commission à l'entité, l'événement `INSURER_COMMISSION` crée une ligne `INSURER` de sens `INBOUND`. Elle sert d'assiette aux rétrocessions, au calcul de la marge et au rapprochement avec le paiement reçu de l'assureur. Une ligne `INBOUND` naît `EARNED` ; son écriture `INSURER_RECEIVABLE` est passée à la validation métier de la période ; elle passe `PAID` au rapprochement du règlement de l'assureur (FIN-08) et n'entre dans aucun bordereau ni paiement.

**Exemple de commission calculée par un tiers.** Le gestionnaire délégué envoie : assiette 500,00 €, taux 3,5 %, montant 17,50 €. Le contrôle passe (500 × 3,5 % = 17,50) et une ligne `EXTERNAL` de 17,50 € est créée. S'il avait envoyé 17,60 €, l'écart dépasserait un centime : l'événement passerait `ON_HOLD` avec une anomalie, et l'opérateur aurait à choisir entre accepter le montant du tiers avec une justification ou demander un fichier corrigé (hypothèse H-49).

**Exemple de commission reçue.** Le fichier UMR de juillet 2026 donne 451,54 € de lignes `INSURER`. Elles n'entrent dans aucun paiement : elles servent à calculer ce qui est dû aux distributeurs, à mesurer la marge et à vérifier que l'assureur a bien versé cette somme.

**Limites.** L'application ne sait rien du calcul d'un tiers : pas de règle, pas de reprise calculée, pas d'explication au-delà des trois chiffres reçus. Une reprise décidée par le tiers arrive comme une commission externe négative et suit le même chemin.

### 6.11 Opérations temporaires et priorités

Une opération commerciale est une version de règle à durée limitée, avec un périmètre précis (produit, période de souscription, combinaison de contrats, code promotionnel, parrainage) et une priorité supérieure à la règle de base du même groupe d'exclusion. Elle remplace alors la règle de base ; placée dans un autre groupe, elle s'y ajoute.

À l'approbation d'une version, l'application signale les conflits : deux versions actives de même priorité dans le même groupe d'exclusion dont les périmètres se recouvrent sur une même période. L'approbation est refusée tant que le conflit existe.

**Comment ça marche.** Le groupe d'exclusion répond à une seule question : parmi les règles qui couvrent cet événement, lesquelles se remplacent et lesquelles s'ajoutent ? Pour un événement donné :

1. Le moteur liste les versions candidates (section 6.1, étape 2).
2. Il les range par groupe d'exclusion.
3. Dans chaque groupe, il garde la priorité la plus haute et écarte les autres.
4. Il applique toutes les versions gardées, une par groupe.

Deux règles d'un même groupe ne s'appliquent donc jamais ensemble ; deux règles de groupes différents s'appliquent toujours ensemble. Le nom du groupe est libre, sauf `BASE`, réservé aux règles de base (P8) : `BASE` pour les commissions de base, `BONUS_RENTREE` pour une prime ponctuelle, etc.

**Exemple.** Trois règles couvrent un encaissement Santé Select de 100 € en septembre 2026 pour le courtier X.

| Règle | Groupe | Priorité | Sort | Ligne |
| --- | --- | --- | --- | --- |
| Barème du Guide, linéaire 15 % | `BASE` | 10 | Écartée | — |
| Accord dérogatoire du courtier X, linéaire 18 % | `BASE` | 30 | Gardée | 18,00 € |
| Opération de rentrée, 2 % en plus | `BONUS_RENTREE` | 10 | Gardée | 2,00 € |

Le courtier X reçoit 20,00 € ; un autre courtier recevrait 15,00 + 2,00 = 17,00 €. Les taux de 15 % et de 18 % sont ceux que portent les garanties (P9) : chaque règle les admet dans sa grille, l'accord dérogatoire ouvrant le taux de 18 % au seul courtier X.

**Convention de priorité proposée.** Plus une règle est spécifique, plus sa priorité est haute. Cette convention n'est pas imposée par le moteur ; elle évite que chaque administrateur invente son échelle.

| Niveau | Périmètre type | Priorité |
| --- | --- | --- |
| Barème général | Produit | 10 |
| Opération commerciale | Produit et période de souscription | 20 |
| Accord dérogatoire | Produit et distributeur nommé | 30 |

**Quand deux règles du même groupe ont la même priorité.** Le moteur ne tire jamais au sort et n'additionne pas. Le cas est traité à deux moments :

- *À l'approbation.* Deux versions de même groupe et de même priorité dont les périmètres et les dates se recouvrent : l'approbation de la seconde est refusée. L'administrateur change la priorité, le groupe, les dates ou le périmètre.
- *Au calcul.* Si le cas se présente malgré tout, l'événement passe `ON_HOLD` avec l'anomalie « règles concurrentes », sans aucune ligne, jusqu'à correction du paramétrage (hypothèse H-41).

Deux périmètres se recouvrent sauf s'il existe au moins un critère sur lequel ils portent des valeurs sans rien de commun. « Santé Select, courtier X » et « Santé Select, courtier Y » ne se recouvrent pas. « Santé Select » et « courtier X » se recouvrent : un encaissement Santé Select du courtier X entre dans les deux (hypothèse H-42).

**Limites.**

- *L'exclusion est déclarée, pas déduite.* Deux règles qui devraient s'exclure mais placées par erreur dans deux groupes se cumulent sans alerte. La simulation avant approbation (exigence PAR-13) est le garde-fou.
- *Tout ou rien.* La règle gagnante remplace entièrement la perdante. « Le meilleur des deux montants » ou « le barème plus la moitié du bonus » ne s'expriment pas.
- *Même événement seulement.* L'exclusion joue entre règles candidates sur un même événement. Elle ne peut pas faire s'exclure une RCD mensuelle et une RCP annuelle : il faut une condition sur l'une des deux.

### 6.12 Paramétrage initial : barème du Guide 2026

Ce tableau est le jeu de données initial des règles de base d'Alptis Assurances. La reprise d'apport est de 100 % la première année et de 50 % la deuxième pour tous les modes précomptés, sauf l'emprunteur, régi par la section 6.5.

| Produit | Segment | Linéaire | Précompté (apport + suivi) |
| --- | --- | --- | --- |
| Santé Pro+ | Niveaux 1 à 6 et surcomplémentaire | 0 à 20 % par pas de 5 | 20 + 10, ou 25 + 15 |
| Santé Select | Niveaux 1 à 8 et surcomplémentaire | 0 à 20 % par pas de 1 | 30 + 10, ou 20 + 10 |
| Santé Équilibre | Niveaux 1 à 5 | 15 ou 18 % | 20 + 10, ou 30 + 10 |
| Santé Protect | Socle et renforts | 15 ou 18 % | — |
| Luminéis | Socle ou packs simultanés | 15 % | 20 + 10 |
| Luminéis | Packs souscrits après coup | 10 % | — |
| Santé Frontaliers Suisses | Niveaux 1 à 6 | 15 ou 20 % | 20 + 10 |
| Solution Professions Indépendantes | — | 0 à 20 % par pas de 5 | 20 + 10, ou 30 + 10 |
| Prévoyance Pro+ | — | 0 à 20 % par pas de 5 | 20 + 10, ou 30 + 10 |
| Frais Généraux+ | — | 0 à 20 % par pas de 5 | 20 + 10, ou 30 + 10 |
| Homme Clé, capitaux inférieurs à 500 K€ | Moins de 40 ans, ou plus de 40 ans au tarif standard | 18 % | 40 + 10 |
| Homme Clé, capitaux inférieurs à 500 K€ | Plus de 40 ans au tarif réduit | 14 % | 20 + 10 |
| Homme Clé, capitaux d'au moins 500 K€ | Tarif standard | 18 % | 40 + 10 |
| Homme Clé, capitaux d'au moins 500 K€ | Tarif réduit | 14 % | 20 + 10 |
| Garantie Dépendance | — | — | 10 + 10, ou 55 + 5 |
| Prévoyance des Frontaliers | — | — | 40 + 10 |
| Décès Plus | — | 18 % | 40 + 10 |
| Pack Hospi | — | — | 40 + 10 |
| Iriade, MNCAP Emprunteur Pro, Maestro, CNP Access Protect | Prêts de 10 ans au plus | 5 % réduit, 15 % standard, 20 % majoré | — |
| Iriade, MNCAP Emprunteur Pro, Maestro, CNP Access Protect | Prêts de plus de 10 ans | 5 % réduit, 15 % standard, 20 % majoré | Standard : 40 % la 1re année puis 10 % ; majoré : 50 % puis 15 % |
| Alptis Dynamique Emprunteur | Prêts de 6 ans au plus | 5 % réduit, 15 % standard, 20 % majoré | — |
| Alptis Dynamique Emprunteur | Prêts de plus de 6 ans | 5 % réduit, 15 % standard, 20 % majoré | Standard : 40 % la 1re année puis 10 % ; majoré : 50 % puis 15 % |
| Sopretis | Prêts de 6 ans au plus | 0 à 28 % par pas de 1 | — |
| Sopretis | Prêts de plus de 6 ans et de moins de 11 ans | 0 à 28 % par pas de 1 | 18 à 48 % la 1re année, puis 0 à 23 % |
| Sopretis | Prêts de 11 ans et plus | 0 à 28 % par pas de 1 | 18 à 48 % la 1re année, puis 1 à 24 % |
| Solution Santé Collective | Niveau ANI+ à niveau 11 | 0 à 15 % par pas de 1 | 8 + 5, ou 13 + 7 |
| Solution Santé Collective | Renforts individuels | 15 % | — |
| CCN Syntec | Conventionnel | 5 % | — |
| CCN Syntec | Amélioré et renforcé | 7 % | — |
| CCN Syntec | Packs individuels | 15 % | — |
| Solution Prévoyance Collective | Niveaux 1 à 11 | 0 à 15 % par pas de 1 | 13 + 7, ou 20 + 10 |

En collectif, le taux peut différer par collège, par exemple 7 % pour les cadres et 5 % pour les non-cadres.

## 7. Cycle de traitement

Chaque entité traite un mois dans une `Period` qui avance par états ; le paiement des commissions du mois M doit être effectif entre le 5 et le 10 du mois M+1. Aujourd'hui ce cycle tient sur neuf jours ouvrés, rythmé par des tickets entre services ; l'application le porte de bout en bout.

### 7.1 États d'une période

| État | Signification | Qui fait avancer |
| --- | --- | --- |
| `OPEN` | Les imports alimentent la période | Automatique, le 1er du mois |
| `LOADED` | Données gelées à la date d'arrêté des données | `OPERATOR` |
| `CALCULATED` | Calcul exécuté, lignes consultables | `BUSINESS_ADMIN` |
| `CONTROLLED` | Contrôles exécutés, anomalies bloquantes traitées | `OPERATOR` |
| `BUSINESS_VALIDATED` | Montants validés par le métier, lignes figées, sauf refus de la Finance | `BUSINESS_VALIDATOR` |
| `PAYMENT_PREPARED` | Lot de paiement et bordereaux provisoires constitués | Automatique |
| `FINANCE_VALIDATED` | Rapprochement validé, fichier de virement émis | `FINANCE_CONTROLLER` |
| `PAID` | Paiements exécutés ; bordereaux publiés le lendemain | `FINANCE_CONTROLLER` |
| `CLOSED` | Extraction comptable produite ; plus rien ne change | `FINANCE_CONTROLLER` |

La période M s'ouvre le 1er du mois M, quel que soit l'état de la période précédente : en fonctionnement normal, deux périodes sont ouvertes à la fois par entité, celle qui collecte et celle qui se calcule ; un retard de clôture peut en laisser une troisième, payée et en attente de clôture. Une période ne peut être calculée que si la précédente est au moins `BUSINESS_VALIDATED`.

Retours arrière autorisés : de `CALCULATED` ou `CONTROLLED` vers `LOADED` ou `OPEN` (recalcul, rechargement) ; de `PAYMENT_PREPARED` vers `CONTROLLED` sur refus de la Finance, avec motif. Ce second retour repasse les lignes de `PAYABLE` à `EARNED`, contre-passe toutes les écritures générées à la validation, supprime les bordereaux provisoires et les lots au statut `DRAFT`, et rouvre le recalcul, qui recalcule aussi les positions d'escompte. Aucun retour après `FINANCE_VALIDATED`. Quand la période comporte plusieurs lots (cycle, épargne retraite, chèques), elle atteint `FINANCE_VALIDATED` à la validation du lot de cycle ; chaque autre lot est validé séparément par le contrôleur Finance avant son émission, ses bordereaux sont publiés le lendemain de son exécution (avec ceux des bénéficiaires sans ordre, pour le lot de cycle), et la période reste `FINANCE_VALIDATED` jusqu'à l'exécution du dernier lot, où elle passe `PAID`.

**Plusieurs lots dans une période.** Le compte reste unique par entité et par bénéficiaire. Les lignes des gammes d'épargne retraite vont au bordereau et à l'ordre du lot épargne retraite ; toutes les autres vont au lot de cycle, qui porte aussi le solde antérieur reporté, la compensation entre comptes et le seuil minimal. Un net négatif de l'un des deux lots est imputé sur l'ordre de l'autre lot de la même période, puis reporté : l'imputation figure en une ligne sur le bordereau du lot qui paie et les lignes imputées sont rattachées à son ordre, de sorte que l'ordre reste égal au net du bordereau (PAY-05) et qu'aucun paiement n'est émis tant que le solde du compte n'est pas positif (CPT-03). Les bénéficiaires sans coordonnées bancaires sont exclus de ces lots et réglés par le lot de chèques du trimestre. Le bordereau porte le type de son lot (`batch_type`). Si la Finance refuse un lot autre que le lot de cycle, ce lot revient à `DRAFT`, ses ordres sont annulés et ses lignes restent `PAYABLE` ; la correction passe par des ajustements et la période ne change pas d'état. Un lot hors cycle est rattaché à la période de l'entité à l'état `OPEN`, suit la même validation et ne modifie l'état d'aucune période. La période passe `PAID` quand tous les ordres de ses lots sont réglés ou rejetés.

### 7.2 Déroulé mensuel

1. **Collecte.** Les flux et fichiers arrivent tout au long du mois. Chaque import est contrôlé, transformé vers le pivot et journalisé ; les rejets attendent correction.
2. **Gel des données.** L'opérateur fixe la date d'arrêté des données, par défaut le dernier jour du mois de la période. Les événements reçus après cette date partent sur la période suivante.
3. **Calcul prévisionnel.** Lignes `FORECAST` sur les primes émises ou à la souscription, selon le paramètre du produit.
4. Calcul réel. Les traitements 1 à 6 se déroulent garantie par garantie, dans l'ordre chronologique des événements de chaque garantie ; les traitements 7 à 10 viennent ensuite, distributeur par distributeur (section 12.3). La liste donne leur ordre de dépendance :
   1. annulations d'événements déjà commissionnés ;
   2. commissions de base sur les encaissements ;
   3. commissions déjà calculées par des tiers ;
   4. nouveaux escomptes ;
   5. apurements et soldes d'escompte ;
   6. reprises ;
   7. rémunérations complémentaires dues ce mois (RCD chaque mois, RCP dans la période de janvier, complément retraite dans celle de février pour un versement en mars, rappels et rétrocessions en fin de trimestre) ;
   8. ajustements manuels approuvés ;
   9. compensation entre les comptes d'un même distributeur ;
   10. report des soldes débiteurs et proposition de paiement.
5. **Contrôles automatiques.** Chaque contrôle produit des résultats classés information, avertissement ou bloquant.
6. **Contrôles manuels.** L'opérateur déroule la liste de contrôle de la période et statue sur chaque anomalie : corrigée, acceptée avec justification, reportée.
7. **Validation métier.** Le valideur approuve les totaux. Les lignes acquises et non imputées sur un escompte passent `PAYABLE` et sont rattachées à un bordereau.
8. **Préparation du paiement.** Un ordre par bénéficiaire payable et par lot ; les exclusions sont listées avec leur motif.
9. **Contrôle Finance.** La comptabilité rapproche le total du lot avec ses propres états, compare au mois précédent et au même mois de l'année précédente, puis valide. Le fichier SEPA est émis.
10. **Paiement.** À l'enregistrement du résultat bancaire (flux IN-08) ou, pour un chèque, à la saisie de son émission par le contrôleur Finance, les ordres passent « réglé » et les lignes `PAID`. Les rejets bancaires recréditent le compte du distributeur.
11. **Publication.** Les bordereaux définitifs et le détail des commissions sont mis à disposition des extranets, de la GED et du CRM.
12. **Clôture.** L'extraction comptable du mois est générée et transmise ; la période est verrouillée.

Un recalcul avant validation métier remplace les lignes du périmètre recalculé : toute la période, ou seulement un distributeur, un produit ou une règle. Entre la validation métier et la validation Finance, seul un refus de la Finance rouvre le recalcul (section 7.1). Après la validation Finance, toute correction est une ligne nouvelle sur la période suivante.

### 7.3 Calendrier cible

Les jalons sont paramétrés par entité, en jours ouvrés du mois M+1. Valeurs par défaut proposées (hypothèse H-55), plus serrées que les neuf jours du calendrier actuel d'Alptis Assurances :

| Jour ouvré | Jalon |
| --- | --- |
| 1 | Gel des données, calcul prévisionnel et calcul réel |
| 2 | Contrôles automatiques et manuels |
| 3 | Validation métier, préparation du paiement |
| 4 | Contrôle Finance et émission du fichier de virement |
| 5 à 7 | Exécution des virements |
| Lendemain du paiement | Publication des bordereaux |
| Dernier jour ouvré | Clôture et extraction comptable |

Certains mois, les jours fériés repoussent les jours ouvrés 5 à 7 au-delà du 10 (jusqu'au 12 en janvier 2026, au 13 en mai 2026). L'entité avance alors ses jalons dans le paramétrage du calendrier, quitte à en regrouper deux le même jour, pour que l'exécution des virements reste dans la fenêtre du 5 au 10.

L'épargne retraite suit un calendrier distinct : virements entre le 20 et le 25, à la fréquence fixée par la règle de versement du bénéficiaire (`PayeeRule`), choisie pour suivre la périodicité des versements. Ces virements partent dans un lot de paiement distinct de la même période, daté du 20 au 25, avec un bordereau propre à l'épargne retraite ; la période n'est payée que lorsque tous ses lots sont exécutés (hypothèse H-57).

**Exemple : la période de septembre 2026 chez Alptis Assurances.**

| Date | Ce qui se passe | État de la période |
| --- | --- | --- |
| Du 1er au 30 septembre | Les fichiers de CLEVA arrivent chaque nuit ; les rejets sont corrigés au fil de l'eau | `OPEN` |
| Jeudi 1er octobre | L'opérateur gèle les données au 30 septembre, puis l'administrateur métier lance le calcul. La période d'octobre s'ouvre en parallèle et reçoit les fichiers suivants | `LOADED` puis `CALCULATED` |
| Vendredi 2 octobre | Contrôles automatiques, puis revue des anomalies par l'opérateur | `CONTROLLED` |
| Lundi 5 octobre | Le valideur métier approuve les totaux ; le lot de paiement et les bordereaux provisoires sont constitués | `BUSINESS_VALIDATED` puis `PAYMENT_PREPARED` |
| Mardi 6 octobre | La Finance rapproche et valide ; le fichier SEPA part à la banque | `FINANCE_VALIDATED` |
| Du 7 au 9 octobre | Les virements sont exécutés ; le lendemain, les bordereaux définitifs sont publiés. Si la période comporte un lot épargne retraite, il part entre le 20 et le 25 et la période n'atteint cet état qu'après | `PAID` |
| Vendredi 30 octobre | Extraction comptable et verrouillage | `CLOSED` |

**Quand ça se passe mal.**

| Situation | Ce que fait l'application |
| --- | --- |
| Un fichier attendu manque le 1er octobre | Le contrôle de complétude le signale. L'opérateur attend le fichier, ou gèle sans lui : ses événements partiront alors sur la période d'octobre |
| Des événements sont restés `ON_HOLD` | Ils ne bloquent pas la période. Ils sont listés, et calculés dans la première période ouverte après leur déblocage |
| Un contrôle bloquant échoue | La période ne peut pas passer `CONTROLLED` tant que l'anomalie n'est ni corrigée ni acceptée avec justification |
| Une erreur de paramétrage est vue le 2 octobre | Retour à `LOADED`, correction par une nouvelle version approuvée qui remplace la version erronée, recalcul complet : les lignes de la période sont remplacées |
| La Finance refuse le lot le 6 octobre | Retour à `CONTROLLED` avec motif ; les lignes redeviennent acquises, le recalcul est rouvert |
| Une erreur est vue après la validation Finance | Plus de retour arrière : ajustement sur la période d'octobre et, si besoin, paiement hors cycle |
| Un virement est rejeté par la banque | Le compte est recrédité, une anomalie est ouverte ; le paiement repart au cycle suivant ou hors cycle après correction de l'IBAN |

**Limites.** Le calendrier est un objectif suivi par des relances (exigence WKF-03) ; l'application ne valide rien à la place des acteurs. Une entité qui prend du retard ne gêne pas les autres, mais elle ne peut pas calculer la période suivante tant que la précédente n'a pas reçu sa validation métier.

### 7.4 Traitements hors cycle mensuel

| Traitement | Fréquence | Contenu |
| --- | --- | --- |
| Paiement hors cycle | À la demande | Lot de paiement isolé, par exemple pour libérer les commissions d'un distributeur réinscrit à l'ORIAS sans attendre le mois suivant |
| Chèques | Trimestrielle | Pour les bénéficiaires sans coordonnées bancaires : liste de chèques à émettre avant le 15 du mois suivant le trimestre (le 25 pour l'épargne retraite) |
| Rappels d'escompte retraite, rétrocessions sur supports | Trimestrielle | Sections 6.6 et 6.9 |
| RCP | Annuelle, période de janvier | Section 6.8 |
| Complément retraite, sur-commission assureur | Annuelle, période de février, versement en mars | Section 6.6 |
| Attestations fiscales | Annuelle, janvier | Récapitulatif des commissions versées par bénéficiaire sur l'année civile |
| Suspension ORIAS | Quotidienne | Un distributeur radié de l'ORIAS passe `SUSPENDED` ; ses paiements sont bloqués jusqu'à sa réinscription |
| Simulation | À la demande | Calcul sur une copie du paramétrage, sans effet sur les comptes |

## 8. Exigences fonctionnelles

Les exigences sont numérotées par module. La colonne « Prio » vaut M (indispensable) ou S (souhaitable) ; la colonne « RFI » renvoie aux exigences EF, NF et SO du questionnaire d'Alptis.

### 8.1 Référentiels (REF)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| REF-01 | Gérer entités, assureurs, gammes, produits et options, avec dates de validité. | M | EF-01 |
| REF-02 | Recevoir le référentiel distributeurs de chaque CRM et le synchroniser. L'application n'en est pas maître : elle ne crée ni ne modifie un distributeur, sauf ses attributs propres (règle de versement, blocage, préférence de déclinaison du bordereau). | M | EF-01 |
| REF-03 | Porter la hiérarchie groupement, distributeur, code de production, et l'historiser. | M | EF-24 |
| REF-04 | Tenir la correspondance des codes externes de chaque système source. Un code inconnu met l'événement en attente, sans le rejeter. | M | EF-01 |
| REF-05 | Recevoir les coordonnées bancaires des outils de gestion, avec dates de validité et contrôle de structure de l'IBAN. Signaler tout changement intervenu depuis le dernier paiement, pour vérification avant le paiement suivant. | M | EF-01 |
| REF-06 | Gérer les règles de versement : bénéficiaire et parts, par code de production, distributeur et gamme. | M | EF-24 |
| REF-07 | Recevoir les indicateurs de qualité par distributeur et par période. Calculer en interne ceux dont les données sont présentes : nombre d'adhésions nouvelles, taux d'impayés, taux de radiation. | M | EF-09 |
| REF-08 | Appliquer à leur date d'effet les changements de statut d'un distributeur ; bloquer ou débloquer ses paiements en conséquence. | M | — |
| REF-09 | Appliquer les cessions de portefeuille et les ordres de remplacement : réaffecter les garanties à la date d'effet reçue ; les primes postérieures sont commissionnées au repreneur. | M | — |
| REF-10 | Offrir une fiche distributeur : identité, hiérarchie, statut, accords, inscriptions, comptes, indicateurs, historique. | M | EF-53 |
| REF-11 | Permettre qu'un distributeur soit commun à plusieurs entités, ou propre à une seule. | M | EF-121 |

### 8.2 Intégration des données (ING)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| ING-01 | Recevoir les données par fichier (CSV, Excel, XML, JSON) et par API REST. | M | EF-01 à 05, NF-10 |
| ING-02 | Transformer chaque source vers le format pivot par un mapping paramétrable : correspondance de colonnes, valeurs par défaut, tables de transcodage, expressions simples. Intégrer le fichier d'un nouveau partenaire ne demande aucun développement. Les mappings sont paramétrés par l'administrateur métier. | M | EF-04 |
| ING-03 | Contrôler à l'import : structure, types, champs obligatoires, doublons, cohérence des montants, dates dans le futur, montants nuls ou anormaux. | M | EF-29 |
| ING-04 | Rejeter ligne par ligne sans bloquer le fichier. Une erreur de structure, de type, de champ obligatoire, de montant (absent, non numérique ou nul ; une ligne négative n'est pas rejetée, elle suit la règle de rapprochement de la section 9.3), de date dans le futur ou un doublon rejette la ligne ; l'écart entre une commission externe et son assiette multipliée par son taux n'est pas un rejet mais une anomalie (section 6.10) ; un code inconnu des référentiels ou une règle absente ne la rejette pas : l'événement est intégré en attente. Chaque rejet porte un code motif (`STRUCTURE`, `TYPE`, `MISSING_FIELD`, `AMOUNT`, `FUTURE_DATE`, `DUPLICATE`) ; il se corrige à l'écran ou par réimport et repart automatiquement. | M | EF-29 |
| ING-05 | Journaliser chaque import : fichier, empreinte, date, auteur, lignes lues, filtrées par le mapping, intégrées, rejetées, en attente (lues = filtrées + intégrées + rejetées + en attente). Refuser un fichier déjà intégré. | M | NF-16 |
| ING-06 | Relancer automatiquement les événements en attente quand la donnée ou la règle manquante arrive. | M | — |
| ING-07 | Permettre la saisie manuelle d'événements et de données complémentaires, tracée. | M | EF-06 |
| ING-08 | Permettre l'import en masse de données complémentaires. | M | EF-07 |
| ING-09 | Intégrer des commissions déjà calculées et des commissions reçues d'assureurs. | M | EF-04, EF-05 |
| ING-10 | Suivre les flux attendus par période : reçu, en retard, manquant. | M | EF-30 |
| ING-11 | Reprendre l'historique à la bascule d'un périmètre : lignes acquises des 24 derniers mois (pour l'épargne retraite, 5 ans de versements programmés encaissés et les positions d'escompte en cours, durée des rappels), escomptes en cours, soldes des comptes, cumuls annuels des surcommissions. | M | SO-21 |

**En pratique.** Un fichier d'encaissements de 1 000 lignes arrive (chiffres illustratifs). 990 lignes sont intégrées. 6 sont rejetées : 2 primes nulles, 1 date dans le futur, 3 doublons. 4 sont intégrées en attente, leur code de production étant inconnu. Le journal d'import affiche ces compteurs, le cinquième (lignes filtrées par le mapping) étant ici à zéro. L'opérateur corrige à l'écran la date erronée, qui repart seule. Le lendemain, Sésame envoie le nouveau code de production : les 4 événements en attente sont calculés sans intervention.

La différence entre rejet et attente tient à l'origine du défaut : une ligne rejetée est fausse en elle-même et doit être corrigée ; une ligne en attente est juste, mais l'application n'a pas encore ce qu'il faut pour la traiter.

**Limites.** Le mapping paramétrable couvre les fichiers tabulaires ordinaires. Un fichier dont la structure change d'une ligne à l'autre, ou qui demande de rapprocher plusieurs fichiers entre eux, sort du mapping et demande un développement.

### 8.3 Paramétrage des règles (PAR)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| PAR-01 | Créer plans, règles, versions, barèmes et conditions par des écrans, sans développement. | M | EF-08 à 12 |
| PAR-02 | Offrir les familles de calcul de la section 6 : linéaire, précomptée, escompte, barème sur cumul, portefeuille sous conditions, quote-part d'une autre commission, encours, montant fixe, commission externe. | M | EF-15 à 22 |
| PAR-03 | Versionner chaque règle par dates de validité ; une version n'est jamais écrasée. | M | EF-13 |
| PAR-04 | Faire suivre à une version le cycle `DRAFT`, `APPROVED`, `ACTIVE`, `RETIRED` ; l'approbation revient à un second administrateur. | M | EF-12 |
| PAR-05 | Gérer priorités et groupes d'exclusion ; détecter les conflits à l'approbation. | M | EF-14 |
| PAR-06 | Gérer des barèmes à paliers, en mode marginal ou global, en taux ou en montant. | M | EF-09 |
| PAR-07 | Conditionner un versement à des indicateurs de qualité et à des compteurs. | M | EF-09 |
| PAR-08 | Paramétrer la politique de reprise de chaque version. | M | EF-11 |
| PAR-09 | Saisir et suivre les conventions d'escompte. | M | EF-09 |
| PAR-10 | Gérer les inscriptions aux règles réservées, à l'unité ou en masse ; n'activer une inscription que si l'accord est enregistré comme signé. | M | EF-09 |
| PAR-11 | Générer l'avenant à partir du paramétrage et l'envoyer en signature électronique. | S | — |
| PAR-12 | Dupliquer un plan pour la reconduction annuelle. | M | EF-12 |
| PAR-13 | Simuler une version en projet sur une période passée et comparer le résultat au réel. Quand la version retire de sa grille un taux que portent des garanties, la simulation accepte une table de substitution (ancien taux ou couple, nouveau taux ou couple) ; sans elle, ces événements ressortent en attente, comptés à part, comme le ferait le calcul réel (P9). Une simulation est un calcul de type simulation dont les paramètres portent les versions simulées, celles qu'elles remplacent et la table de substitution : une version simulée est prise quels que soient son statut et ses dates de validité, la version qu'elle remplace est retirée des candidates, et les lignes sont écrites dans un espace séparé de même structure, sans effet sur les comptes ni les écritures. | M | EF-12 |
| PAR-14 | Saisir tous les taux en pourcentage et alerter sur une valeur hors bornes de vraisemblance. | M | — |
| PAR-15 | Exporter et importer le paramétrage, pour la promotion entre environnements et la réversibilité. | M | NF-07 |

**En pratique : la reconduction annuelle.** En novembre 2026, l'administrateur métier duplique le plan 2026 en plan 2027 : toutes les versions sont copiées en `DRAFT`, valables du 1er janvier au 31 décembre 2027. Il modifie les taux qui changent, puis simule le plan 2027 sur les données d'octobre 2026 et compare au réel : l'écart doit se limiter aux taux modifiés (si un taux porté par des garanties disparaît de la grille, il renseigne la table de substitution de PAR-13). Un second administrateur approuve. Les versions passent `ACTIVE` d'elles-mêmes au 1er janvier. Rien de ce qui a été calculé en 2026 n'est touché.

**Limites.** Le paramétrage sans développement s'arrête aux familles existantes. La séparation des tâches suppose au moins deux administrateurs par entité.

### 8.4 Calcul (CAL)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| CAL-01 | Exécuter le calcul prévisionnel et le calcul réel par entité et par période, selon la section 7. | M | EF-15, EF-62 |
| CAL-02 | Calculer les commissions de base linéaires et précomptées. | M | EF-15 |
| CAL-03 | Calculer escomptes, apurements et régularisations. | M | EF-16, EF-23 |
| CAL-04 | Calculer les reprises. | M | EF-17 |
| CAL-05 | Calculer RCD, RCP et rémunérations complémentaires, avec leurs conditions. | M | EF-16 |
| CAL-06 | Calculer les commissions de l'épargne : versements, encours, arbitrages, rappels. | M | EF-22 |
| CAL-07 | Calculer co-courtage, partages hiérarchiques et rétrocessions. | M | EF-18, EF-24 |
| CAL-08 | Calculer frais de dossier, honoraires et montants fixes. | M | EF-19 |
| CAL-09 | Appliquer les règles temporaires et dérogatoires. | M | EF-20 |
| CAL-10 | Rattacher chaque ligne à son événement, sa version de règle, son assiette et son taux, et afficher l'explication du calcul en clair. | M | EF-25, EF-125 |
| CAL-11 | Recalculer à l'identique une période close, à partir des données et des versions historisées. | M | EF-25 |
| CAL-12 | Permettre un recalcul partiel avant validation : un distributeur, un produit, une règle. | M | — |
| CAL-13 | Calculer la commission reçue attendue de chaque assureur et la marge. | M | — |
| CAL-14 | Gérer une avance exceptionnelle remboursée selon un échéancier. | M | EF-23 |

### 8.5 Ajustements et réclamations (REG)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| REG-01 | Saisir un ajustement : avance exceptionnelle, reprise manuelle, correction d'erreur, commission exceptionnelle, transfert entre comptes. | M | EF-26 |
| REG-02 | Exiger un motif, accepter une pièce jointe, lier l'ajustement à la ligne corrigée. | M | EF-124 |
| REG-03 | Soumettre tout ajustement à l'approbation d'un second utilisateur, opérateur ou administrateur métier ; au-delà d'un seuil paramétrable, à celle du valideur métier. | M | EF-26 |
| REG-04 | Importer des ajustements en masse. | M | EF-27 |
| REG-05 | Proposer l'ajustement à contrepassation automatique : une avance en M, neutralisée par une reprise en M+1. | M | EF-26 |
| REG-06 | Enregistrer la réclamation d'un distributeur, la lier aux lignes contestées et suivre son traitement jusqu'à la régularisation. | M | — |

**En pratique : une réclamation.** Un courtier signale qu'une garantie est commissionnée à 15 % au lieu des 18 % convenus, depuis trois mois, sur une cotisation de 100 €. L'opérateur enregistre la réclamation et la lie aux trois lignes de 15,00 €. Le taux est corrigé dans l'outil de gestion, pour l'avenir. Pour le passé, l'opérateur saisit un ajustement « correction d'erreur » de 3 × 3,00 = +9,00 €, avec le motif et le courriel du courtier en pièce jointe. Un second utilisateur l'approuve. La ligne `MANUAL` de 9,00 € figure sur le bordereau suivant, dans la rubrique des ajustements, et la réclamation est close.

**En pratique : l'avance contrepassée.** Une avance exceptionnelle de 1 000 € est accordée en mars ; l'option de contrepassation crée d'office la reprise de −1 000 € sur avril.

**Limites.** L'ajustement corrige un montant, jamais une ligne existante ni une donnée source : si le taux n'est pas corrigé dans l'outil de gestion, l'erreur se reproduit le mois suivant.

### 8.6 Contrôles (CTL)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| CTL-01 | Offrir un catalogue de contrôles paramétrables par le métier : seuil, gravité, périmètre, activation. | M | EF-29 à 36 |
| CTL-02 | Livrer les contrôles listés ci-dessous. | M | EF-30 à 33 |
| CTL-03 | Proposer une liste de contrôle par période, à cocher. | S | EF-37 |
| CTL-04 | Versionner les contrôles. | S | EF-38 |
| CTL-05 | Tracer la décision prise sur chaque anomalie : auteur, date, justification. | M | EF-39 |
| CTL-06 | Exporter les données nécessaires aux contrôles de niveau 2 et 3. | M | EF-34, EF-35 |

Contrôles livrés :

| Famille | Contrôles |
| --- | --- |
| Complétude | Tous les flux attendus sont reçus. Le nombre d'événements par source ne s'écarte pas du mois précédent au-delà du seuil. |
| Qualité des données | Prime nulle, négative ou anormale. Date dans le futur. Garantie sans distributeur. Distributeur absent du référentiel ou non actif. IBAN manquant. Choix de rémunération hors grille. |
| Calcul | Événement sans règle. Taux hors barème. Total par nature comparé au mois précédent et au même mois de l'année précédente. Variation par distributeur au-delà du seuil. Taux moyen d'un distributeur en rupture avec son historique. Revue des 20 premiers distributeurs. |
| Escompte | Distributeur nouvellement conventionné sans escompte calculé. Convention sans adhésion. Apurement présent dans un paiement. Créance au-delà d'un plafond. |
| Reprises | Reprise supérieure à l'acquis. Résiliation précoce sans reprise. |
| Surcommissions | Versement sans accord signé. Indicateur de condition manquant. Seuil de qualité franchi autorisant la résiliation de l'avenant. |
| Comptes | Solde débiteur. Débit plus ancien que n mois. Compensation possible non faite. |
| Paiement | Total du lot égal à la somme des bordereaux payés (sous-totaux de l'entité pour les bordereaux multi-entités) et à la somme des lignes rattachées à ses ordres, soldes antérieurs reportés compris. Ordre sans bordereau. Bénéficiaire non actif. |
| Comptabilité | Écritures équilibrées. Somme des écritures rattachées à des lignes égale à la somme de ces lignes ; écritures de paiement égales aux ordres ; reliquats d'escompte égaux aux écarts soldés sous la tolérance. |
| Flux externes | Commission externe différente de l'assiette multipliée par le taux. Commission reçue d'un assureur différente de l'attendu. |

**En pratique.** Le contrôle « variation par distributeur » est réglé à 30 %, en avertissement. Un courtier passe de 2 000 € en août à 3 100 € en septembre, soit +55 % : le contrôle le signale. L'opérateur ouvre le détail, constate douze adhésions nouvelles, et accepte l'anomalie avec la justification « production nouvelle ». Son nom, la date et le texte sont conservés.

Les trois gravités n'ont pas le même effet : une information s'affiche ; un avertissement demande une décision tracée mais n'empêche rien ; un contrôle bloquant interdit la transition suivante tant qu'il n'est pas levé. L'égalité entre le total du lot et la somme des lignes rattachées à ses ordres, soldes reportés compris, est toujours bloquante.

**Limites.** Un contrôle compare, il ne juge pas : un seuil trop bas noie l'opérateur, un seuil trop haut laisse passer. Les seuils se règlent sur les premiers mois de production.

### 8.7 Pilotage du cycle (WKF)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| WKF-01 | Porter le cycle de la période et ses transitions (section 7), avec contrôle des rôles. | M | EF-28 |
| WKF-02 | Exiger un second niveau de validation selon des règles paramétrables : total, écart avec le mois précédent, présence d'ajustements. | S | EF-40, EF-41 |
| WKF-03 | Notifier par courriel les acteurs de chaque transition et relancer en cas de retard sur le calendrier. | M | EF-28 |
| WKF-04 | Afficher l'avancement de toutes les périodes ouvertes, par entité. | M | EF-52 |

### 8.8 Compte distributeur (CPT)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| CPT-01 | Tenir un compte par entité et par bénéficiaire : lignes au débit et au crédit, statut de chacune, solde. | M | EF-58 |
| CPT-02 | Offrir une vue par distributeur qui regroupe les comptes de ses codes de production, et une vue par groupement qui regroupe ses membres. | M | EF-24 |
| CPT-03 | Reporter un solde débiteur de période en période ; n'émettre aucun paiement tant que le solde n'est pas positif. | M | EF-59 |
| CPT-04 | Compenser automatiquement les comptes débiteurs et créditeurs d'un même distributeur dans une même entité, par des lignes `TRANSFER`. Jamais entre deux sociétés de courtage distinctes. | M | EF-59 |
| CPT-05 | Bloquer manuellement un compte ou une ligne, avec motif et date de levée. | M | EF-58 |
| CPT-06 | Donner à tout moment le statut de chaque commission d'un distributeur : prévisionnelle, acquise, payable, payée, imputée sur un escompte, annulée, avec le motif de blocage éventuel. | M | EF-58 |
| CPT-07 | Lister les distributeurs débiteurs sans portefeuille actif, pour demande de remboursement ; enregistrer un échéancier d'apurement, porté par un ajustement à échéancier (exigence CAL-14). | S | EF-59 |
| CPT-08 | Suivre la créance d'escompte par convention et par distributeur. | M | EF-59 |

### 8.9 Paiement (PAY)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| PAY-01 | Constituer un lot de paiement de cycle par entité et par période, plus un lot épargne retraite quand la période en comporte (hypothèse H-57), avec un ordre par bénéficiaire et par lot. Le lot de chèques du trimestre s'y ajoute (PAY-04). | M | EF-61 |
| PAY-02 | Exclure du lot, avec motif : compte bloqué, bénéficiaire non actif, solde nul ou négatif, montant sous le seuil minimal de l'entité, coordonnées bancaires absentes. Les sommes restent au compte. Respecter aussi la périodicité de paiement du bénéficiaire. | M | EF-59 |
| PAY-03 | Produire le fichier de virements SEPA (ISO 20022, pain.001). | M | EF-61 |
| PAY-04 | Produire la liste des règlements par chèque. | M | EF-61 |
| PAY-05 | Garantir l'alignement entre bordereau et ordre : même bénéficiaire, même montant (ou sous-total de l'entité pour un bordereau multi-entités), référence du bordereau dans le libellé du virement. | M | EF-44 |
| PAY-06 | Enregistrer le résultat bancaire ; un rejet recrédite le compte et crée une anomalie. | M | EF-61 |
| PAY-07 | Constituer un lot hors cycle. | M | — |
| PAY-08 | Pour un distributeur commun à plusieurs entités, émettre un ordre par entité débitrice. | M | EF-122 |

**En pratique.** À la validation métier, 1 250 bénéficiaires ont un compte mouvementé (chiffres illustratifs). Le lot contient 1 180 ordres. Les 70 autres sont exclus, chacun avec son motif : 31 soldes débiteurs, 22 montants sous le seuil, 9 sans coordonnées bancaires, 5 distributeurs suspendus à l'ORIAS, 3 comptes bloqués à la main. Tous reçoivent un bordereau ; les sommes non payées restent à leur compte et repartent d'elles-mêmes quand le motif tombe.

**Limites.** L'application produit le fichier de virements ; elle ne se connecte pas à la banque. Le résultat d'exécution revient par fichier ou par saisie (flux IN-08).

### 8.10 Bordereaux et documents (BRD)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| BRD-01 | Produire un bordereau par bénéficiaire et par période (plus un bordereau propre à l'épargne retraite quand elle est payée dans un lot distinct), en PDF et en tableur, dès qu'il existe une ligne ou un solde reporté, qu'un paiement soit émis ou non. | M | EF-42 |
| BRD-02 | Respecter le contenu décrit en section 9.5. | M | EF-42 |
| BRD-03 | Produire le bordereau au niveau du bénéficiaire : code de production, distributeur ou groupement. | M | EF-43 |
| BRD-04 | Produire un bordereau par entité, ou un bordereau unique couvrant plusieurs entités avec un sous-total par entité. Décliner par assureur ou par produit selon la préférence enregistrée du distributeur (aucune déclinaison, par assureur ou par produit). | M | EF-44 |
| BRD-05 | Laisser l'administrateur métier modifier les modèles : logo, mentions, colonnes, ordre. | M | EF-45 |
| BRD-06 | Archiver le PDF publié et ne jamais le modifier. Une correction donne un bordereau rectificatif. | M | NF-17 |
| BRD-07 | Reporter un solde débiteur antérieur en une seule ligne, sans répéter son détail. | M | EF-59 |
| BRD-08 | Produire l'attestation fiscale annuelle par bénéficiaire. | M | EF-47 |
| BRD-09 | Produire la synthèse des escomptes par convention : versé, apuré, créance, détail par adhérent. | M | EF-47 |
| BRD-10 | Mettre bordereaux, attestations et détail des lignes à disposition des extranets, de la GED et du CRM, dès la publication. | M | EF-46, EF-57 |
| BRD-11 | Pour un membre payé par son groupement, produire un relevé informatif de sa propre part. | M | EF-43 |
| BRD-12 | Exporter les données du bordereau vers un outil d'éditique externe. | S | EF-42 |

**En pratique : le bordereau rectificatif.** Une erreur est découverte sur un bordereau déjà publié. Le PDF d'origine reste en GED, intact. L'application publie un second document, de même numéro en version 2, marqué « rectificatif », qui renvoie au premier. Si le montant change, l'écart est une ligne d'ajustement sur la période suivante. Un exemple de bordereau figure en section 9.5.

**Limites.** La modification des modèles par l'administrateur porte sur le logo, les mentions, les colonnes et leur ordre. Une mise en page nouvelle relève d'un développement ou de l'éditique externe (exigence BRD-12).

### 8.11 Comptabilité (FIN)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| FIN-01 | Générer une écriture auxiliaire pour chaque événement comptable d'une ligne, d'un ordre de paiement ou d'une position d'escompte (section 10). | M | EF-60 |
| FIN-02 | Paramétrer le schéma comptable par entité : comptes, journaux, axes analytiques. | M | EF-60 |
| FIN-03 | Produire l'extraction mensuelle après cut-off. Pas de compte rendu d'événement au fil de l'eau. | M | EF-60 |
| FIN-04 | Tenir les deux visions : à l'émission (prévisionnel) et à l'encaissement (réel). | M | EF-62 |
| FIN-05 | Rattacher chaque écriture à un exercice et à un mois. | M | EF-60 |
| FIN-06 | Rapprocher commissions acquises et commissions payées, par bénéficiaire et au global. | M | EF-60 |
| FIN-07 | Relier flux de trésorerie, bordereau, ligne de commission, assiette, taux, règle et écriture. | M | NF-17 |
| FIN-08 | Rapprocher les commissions reçues des assureurs avec l'attendu et avec leurs avis de paiement. | M | — |
| FIN-09 | Produire l'état de cut-off de pilotage (section 10.4). | M | EF-62 |

### 8.12 Restitutions (REP)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| REP-01 | Offrir des tableaux de bord : commissions par mois, nature, gamme, entité ; reprises ; escomptes versés, apurés et créances ; comptes débiteurs ; premiers distributeurs ; anomalies. | M | EF-52 |
| REP-02 | Répondre à la question « combien a été payé, à qui, quand et pourquoi » pour tout montant. | M | EF-53 |
| REP-03 | Filtrer par produit, distributeur et hiérarchie, délégation commerciale, nature, période. | M | EF-53 |
| REP-04 | Exporter toute liste en CSV et en tableur, à la maille de la ligne. | M | EF-54 |
| REP-05 | Alimenter la plateforme Data chaque jour. | M | EF-55, NF-06 |
| REP-06 | Exposer une API de lecture pour le CRM, les outils de gestion et les extranets. | M | EF-56 |
| REP-07 | Offrir une vision consolidée groupe aux utilisateurs habilités. | M | EF-122 |
| REP-08 | Restituer la marge par produit, assureur et distributeur. | M | — |
| REP-09 | Mesurer les indicateurs de fonctionnement : règles actives, durée du cycle, part des lignes ajustées à la main, montants repris. | S | — |

### 8.13 Administration et transversal (ADM)

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| ADM-01 | Fonctionner en instance unique multi-entités, avec cloisonnement des données par entité et par rôle. | M | EF-121 |
| ADM-02 | Laisser chaque entité administrer son paramétrage métier ; centraliser l'administration technique. | M | EF-121 |
| ADM-03 | Proposer une interface et une documentation entièrement en français. | M | EF-123 |
| ADM-04 | Historiser toute modification manuelle : qui, quand, avant, après. | M | EF-124 |
| ADM-05 | Paramétrer par entité : calendrier, seuil minimal de paiement, seuils de validation, tolérances des contrôles. | M | — |
| ADM-06 | Purger ou anonymiser les données personnelles à l'échéance des durées de conservation. | M | NF-14 |

### 8.14 Rémunération variable des forces de vente (FDV)

Ce module est le dernier lot. Il réutilise le socle (périodes, règles versionnées, barèmes, conditions, contrôles, ajustements, audit) et ajoute ses propres référentiels. Les montants cités viennent de la grille 2026 d'Alptis Assurances ; ce sont des paramètres.

| N° | Exigence | Prio | RFI |
| --- | --- | --- | --- |
| FDV-01 | Gérer les collaborateurs : poste, équipe, région, responsable, dates ; alimentation par le SI RH. | M | EF-63 |
| FDV-02 | Recevoir les données de mesure : absences hors congés et RTT, production par marché, activité commerciale du CRM (appels argumentés, rendez-vous, devis, nouveaux distributeurs producteurs), marge, cahiers des charges reçus. | M | EF-64 à 67 |
| FDV-03 | Gérer des objectifs individuels et collectifs, quantitatifs et qualitatifs, par poste, par personne et par période, versionnés. | M | EF-70 |
| FDV-04 | Gérer des plans de variable par poste : enveloppes, critères, barèmes, règles d'absence. | M | EF-71 |
| FDV-05 | Variable mensuel quantitatif individuel : si l'objectif du mois est atteint, verser la mensualité du poste (dans la règle de référence, 70 % de l'enveloppe annuelle répartis sur 12 mois). Non versé à partir de 10 jours ouvrés d'absence dans le mois. | M | EF-72 |
| FDV-06 | Régularisation annuelle de l'individuel en janvier N+1 : enveloppe multipliée par le taux d'atteinte annuel, moins les versements mensuels. | M | EF-76 |
| FDV-07 | Variable trimestriel qualitatif : un montant par critère atteint (productivité, efficacité, développement de nouveaux producteurs), plus un complément si l'objectif de développement est dépassé d'au moins 2. Non versé à partir de 6 semaines d'absence dans le trimestre. | M | EF-73 |
| FDV-08 | Variable annuel quantitatif collectif : enveloppe versée en janvier N+1 si l'objectif annuel est atteint, régularisée en juin ; proratisée à partir d'un mois d'absence. | M | EF-74, EF-78 |
| FDV-09 | Variable des animateurs et responsables : paliers de croissance de la production (15, 25, 35 %) et nombre de critères qualitatifs atteints (3, 5). | M | EF-77, EF-85, EF-91 |
| FDV-10 | Variable de la vente directe : pourcentage de la marge brute des affaires nouvelles avec prime de dépassement ; barème sur la valeur du portefeuille ; versement de 70 % en janvier et régularisation en juin. | M | EF-79 à 82 |
| FDV-11 | Pondérer la production par marché (en 2026 : particulier 0,7 ; emprunteur 1 ; professionnel 1,3 ; collectif 0,65). | M | EF-72 |
| FDV-12 | Réduire le variable quand un contrat est résilié avant une durée paramétrée. | M | EF-76, EF-83 |
| FDV-13 | Faire valider les montants par le responsable, autoriser un ajustement motivé (prime accordée malgré un objectif manqué, correction). | M | EF-94, EF-110 |
| FDV-14 | Produire chaque mois le fichier de synthèse des variables à payer par personne et l'export vers la paie. | M | EF-112, EF-119 |
| FDV-15 | Permettre à chaque commercial de consulter son variable, ses objectifs et son historique. | M | EF-115, EF-120 |
| FDV-16 | Offrir un simulateur de variable et des alertes d'atteinte de seuil. | S | EF-96, EF-97 |

Grille 2026 des enveloppes annuelles, par poste du courtage grossiste :

| Enveloppe | CAC | RB | DR | RR | DNV |
| --- | --- | --- | --- | --- | --- |
| Quantitatif individuel, à 100 % de l'objectif | 2 450 € | 2 450 € | 7 700 € | 12 000 € | 16 000 € |
| Versement mensuel correspondant | 146 € | 146 € | 458 € | 700 € | 933 € |
| Qualitatif | 2 000 € | 2 000 € | 3 000 € | 5 000 € | 6 000 € |
| Complément nouveaux producteurs | 500 € | 500 € | 800 € | 1 200 € | 1 600 € |
| Quantitatif collectif, à 100 % de l'objectif | 550 € | 550 € | 2 500 € | 4 800 € | 6 000 € |

**En pratique : l'année d'un CAC.** Enveloppe quantitative individuelle de 2 450 €, mensualité de 146 €.

| Étape | Calcul | Montant |
| --- | --- | --- |
| Mensualités : objectif du mois atteint 9 fois sur 12 | 9 × 146 | 1 314,00 € |
| Droit annuel : taux d'atteinte de 95 % sur l'année | 2 450 × 95 % | 2 327,50 € |
| Régularisation de janvier N+1 | 2 327,50 − 1 314,00 | 1 013,50 € |

Un mois manqué n'est donc pas perdu si l'année est bonne : la régularisation le rattrape.

**Exemple de pondération.** 10 000 € de production sur le marché des particuliers et 5 000 € sur celui des professionnels comptent pour 10 000 × 0,7 + 5 000 × 1,3 = 13 500 € face à l'objectif.

**Limites.** Les sources masquent plusieurs montants et la règle des 70 % ne redonne pas les mensualités publiées (hypothèse H-21). Le cas d'une régularisation négative, quand les mensualités versées dépassent le droit annuel, n'est pas tranché par les sources : le module calcule l'écart et le soumet à la validation du responsable (exigence FDV-13, hypothèse H-53). Précisions retenues : un plan de variable est versionné par ses dates de validité, sans statut ; une prime accordée malgré un objectif manqué est un résultat de composante `MANUAL_ADJUSTMENT` portant son motif ; le taux d'atteinte annuel est la production pondérée cumulée rapportée à l'objectif cumulé, plafonné à 100 % ; dans l'export paie, le mois du nom de fichier est la période calculée et `pay_period` le mois de paie qui suit ; l'export est lancé par le contrôleur Finance une fois tous les résultats du mois validés ; l'administrateur métier gère les plans et les objectifs et lance le calcul avec la période ; une mensualité non versée pour absence n'est pas rattrapée par la régularisation annuelle, l'enveloppe étant réduite de la mensualité du poste par mois non versé. Ce module est spécifié moins finement que le reste et demandera un atelier avec la direction commerciale avant sa construction.

## 9. Interfaces et flux

L'application reçoit des référentiels, des contrats et des événements, et restitue des paiements, des écritures, des bordereaux et des données. Toute source est ramenée à un format pivot unique ; aucune règle de calcul ne dépend du système d'origine.

&#91;embedded content: flux autour de l'application · 4 sources, 4 étapes internes, 6 destinataires\]

À gauche les systèmes qui alimentent l'application, au centre ses quatre étapes internes, à droite ceux qui reçoivent ses résultats.

### 9.1 Inventaire des flux

| N° | Sens | Système | Contenu | Format et transport | Fréquence |
| --- | --- | --- | --- | --- | --- |
| IN-01 | Entrant | Sésame (Alptis Assurances) | Distributeurs, groupements, codes de production, statuts, accords, indicateurs de qualité, cessions de portefeuille, ordres de remplacement | API REST ou fichier pivot | Quotidienne |
| IN-02 | Entrant | Salesforce (UNEP) | Idem | API REST ou fichier pivot | Quotidienne |
| IN-03 | Entrant | CLEVA | Contrats, garanties, choix de rémunération, émissions, encaissements, annulations, événements de vie, coordonnées bancaires | Fichiers pivot via UseItFlow | Quotidienne |
| IN-04 | Entrant | RSI | Idem, périmètre individuel, jusqu'à son décommissionnement | Fichiers pivot via UseItFlow | Quotidienne |
| IN-05 | Entrant | Kelia (UNEP) | Contrats, versements, encours, coordonnées bancaires | Fichiers pivot | Mensuelle |
| IN-06 | Entrant | Assureurs et gestionnaires délégués | Fichiers de production, d'encaissement, de commissions ; un mapping par fichier | Excel ou CSV, dépôt ou téléversement | Mensuelle, trimestrielle ou annuelle selon le fichier |
| IN-07 | Entrant | Référentiel produits | Gammes, produits, options, assureurs | Fichier pivot | À chaque évolution |
| IN-08 | Entrant | SI Finance | Résultat d'exécution des virements, rejets | Fichier ou saisie | À chaque lot |
| IN-09 | Entrant | SI RH, CRM | Collaborateurs, absences, production, activité commerciale, marge, cahiers des charges reçus (module forces de vente) | Fichiers | Mensuelle |
| OUT-01 | Sortant | SI Finance, trésorerie | Ordres de virement | SEPA pain.001 ; liste des chèques en CSV ; dépôt de fichier | À chaque lot |
| OUT-02 | Sortant | SI Finance, comptabilité | Extraction des écritures après cut-off | CSV | Mensuelle |
| OUT-03 | Sortant | GED, éditique | Bordereaux et attestations, avec métadonnées d'indexation | PDF, tableur, JSON | À chaque publication |
| OUT-04 | Sortant | Alptis Connect, K4U, CRM | Comptes, lignes de commission, bordereaux, conventions d'escompte | API REST en lecture | À la demande |
| OUT-05 | Sortant | Plateforme Data | Lignes, événements commissionnés, écritures de compte, paiements, règles | Fichiers incrémentaux | Quotidienne |
| OUT-06 | Sortant | Paie | Variables à payer par collaborateur (module forces de vente) | Tableur ou CSV | Mensuelle |
| OUT-07 | Sortant | Supervision | État de santé et métriques des traitements | HTTP | Continue |

### 9.2 Format pivot : garanties

Une ligne par garantie. Les dates, le statut et le motif de fin sont ceux de la garantie ; ceux de l'adhésion en sont déduits (première date d'effet ; fin quand toutes ses garanties sont finies, avec le motif de la dernière). Un astérisque marque un champ obligatoire. L'envoi d'une garantie déjà connue met à jour ses attributs ; l'ancien état est tracé au journal d'audit, et les critères, le choix de rémunération et l'affectation, qui sont datés, sont historisés à la date `updated_at`.

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \* | Texte | Entité |
| `source_system` \* | Texte | Système émetteur |
| `contract_number` \* | Texte | Numéro de l'adhésion dans le système source |
| `coverage_code` \* | Texte | Identifiant de la garantie dans l'adhésion |
| `product_code` \*, `option_code` | Texte | Produit et option |
| `policyholder_id` \*, `policyholder_name`, `policyholder_birth_date` | Texte, date | Souscripteur |
| `collective_contract_number` | Texte | Contrat collectif parent |
| `signature_date`, `registration_date` \*, `effect_date` \*, `end_date` | Date | Dates de vie |
| `status` \*, `end_reason` | Code | Statut de la section 5.2 et motif de fin |
| `periodicity`, `sales_channel` | Code | Périodicité de paiement, mode de vente |
| `annual_premium` | Décimal | Prime annualisée hors taxes |
| `tariff_type`, `staff_category` | Code | Type de tarif, collège |
| `remuneration_mode` \* | Code | `LINEAR` ou `UPFRONT` |
| `linear_rate`, `acquisition_rate`, `servicing_rate` | Décimal | Taux choisis, en fraction |
| `advance_requested` | Booléen | Garantie présentée à l'escompte |
| `production_code` \* | Texte | Code de production de l'apporteur principal |
| `co_brokers` | Liste | Codes de production et parts des co-courtiers |
| `criteria` | Liste clé = valeur | Durée du prêt, capital, taux de frais, protocole, etc. |
| `updated_at` \* | Horodatage | Date de la dernière modification dans la source |

Cinq champs s'ajoutent : `new_business` (vrai pour une adhésion nouvelle au sens du glossaire, faux pour un remplacement ou une modification), `single_premium` et `commissionable` (faux pour une garantie non rémunérée, principe P16), ainsi que `policyholder_first_name` et `policyholder_person_type` (personne physique ou morale), facultatifs ; `policyholder_name` porte le nom ou la raison sociale. Les autres objets (distributeurs et hiérarchie, produits, coordonnées bancaires, indicateurs de qualité, accords et signatures, cessions et ordres de remplacement, résultats de paiement, collaborateurs et mesures) suivent le même principe : un fichier pivot par objet, décrit en section 9.10. Les conventions d'escompte sont saisies à l'écran.

### 9.3 Format pivot : événements

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \*, `source_system` \*, `source_key` \* | Texte | La clé source est unique par système ; elle garantit l'idempotence |
| `event_type` \* | Code | Type de la section 5.3 |
| `contract_number` \*, `coverage_code` | Texte | Rattachement |
| `event_date` \* | Date | Date d'effet de l'événement |
| `period_from`, `period_to` | Date | Période de cotisation couverte |
| `gross_amount`, `tax_amount`, `fee_amount`, `basis_amount` | Décimal | Montant TTC, taxes et contributions, frais, assiette hors taxes |
| `fund_code`, `management_profile` | Texte | Support et profil de gestion |
| `unpaid_rank` | Entier | Rang de l'impayé |
| `recovered_after_litigation` | Booléen | Prime recouvrée après une procédure précontentieuse ou contentieuse ; elle est exclue de l'assiette |
| `reverses_source_key` | Texte | Clé de l'événement annulé |
| `precomputed_basis`, `precomputed_rate`, `precomputed_amount` | Décimal | Renseignés pour une commission déjà calculée |
| `beneficiary_code` | Texte | Code de production ou distributeur visé, pour une commission déjà calculée |
| `label` | Texte | Libellé libre repris sur le bordereau |

Règles d'intégration :

- Sans assiette explicite, l'assiette vaut `gross_amount` moins `tax_amount` moins `fee_amount`. Les cotisations d'association et les droits d'entrée sont portés dans `fee_amount`.
- Un indicateur `recovered_after_litigation` exclut la prime de l'assiette.
- Une ligne négative sans clé d'annulation est rapprochée de l'événement de même contrat, même type et montant opposé ; à défaut, elle est mise en attente. Exception : un événement `EXTERNAL_COMMISSION` ou `INSURER_COMMISSION` négatif (reprise ou rappel décidé par le tiers) est intégré tel quel, sans rapprochement.

**Exemple.** Un encaissement de mars, puis son annulation en avril après rejet du prélèvement. Seules les colonnes utiles sont montrées.

| Champ | Ligne 1 | Ligne 2 |
| --- | --- | --- |
| `source_key` | ENC-000123 | ANN-000045 |
| `event_type` | PREMIUM\_COLLECTED | PREMIUM\_CANCELLED |
| `contract_number` | AD-004512 | AD-004512 |
| `event_date` | 2026-03-05 | 2026-04-12 |
| `period_from`, `period_to` | 2026-03-01, 2026-03-31 | 2026-03-01, 2026-03-31 |
| `gross_amount` | 113.27 | 113.27 |
| `tax_amount` | 13.27 | 13.27 |
| `reverses_source_key` | (vide) | ENC-000123 |

Première ligne : l'assiette n'est pas fournie, elle vaut 113,27 − 13,27 = 100,00 € ; en linéaire à 15 %, une ligne de 15,00 € naît sur la période de mars. Seconde ligne : elle désigne l'événement annulé par sa clé ; une ligne inverse de −15,00 € naît sur la période d'avril, avec la même règle et le même bénéficiaire, même si le taux ou le distributeur ont changé entre-temps.

**Deux dates, deux rôles.** `event_date` choisit la version de règle et mesure les anciennetés. La date de réception choisit la période de calcul. Un encaissement daté du 28 mars mais reçu le 3 avril, après le gel, est calculé dans la période d'avril avec les règles en vigueur le 28 mars.

**Idempotence.** Si la ligne `ENC-000123` est renvoyée dans un autre fichier, elle est reconnue par sa clé et rejetée comme doublon : aucune commission en double.

**Limites.** Tout repose sur la qualité de `source_key` : une source qui régénère ses clés à chaque extraction crée des doublons que l'application ne peut pas voir. La stabilité des clés est à exiger de chaque source et à vérifier en recette.

### 9.4 Exemple de mapping : fichiers UMR

| Colonne UMR | Cible pivot |
| --- | --- |
| `NUMERO_CONTRAT`, `NUMERO_ADHERENT`, `NOM_ADHERENT`, `DATE_NAISSANCE`, `DATE_EFFET_ADHESION`, `STATUT_CONTRAT` | Garantie et souscripteur |
| `CODE_POINT_VENTE`, de la forme `300516_P75942-72717` | Code partenaire (`P75942`) et code de production (`72717`) |
| `CODE_MOUVEMENT` | `source_key` |
| `TYPE_MOUVEMENT` | Type d'événement : versement initial, libre, programmé, frais de gestion administrative ; la date du mouvement (`DATE_MOUVEMENT`) alimente `event_date` |
| `BASE_CALCUL`, `TAUX_COMMISSION`, `MONTANT_COMMISSION` | Commission reçue de l'assureur ; assiette de la commission du distributeur. Portées par les champs `precomputed_basis`, `precomputed_rate` et `precomputed_amount` d'un second événement `INSURER_COMMISSION`, créé à côté du versement avec la même clé source suivie de `-COM`, qui donne une ligne `INSURER` |
| `NOM_MUTUELLE` | Distingue la part de l'entité et la part de l'assureur ; seule la part de l'entité est intégrée |
| `SUPPORT`, `PROFIL` | Support et profil de gestion |
| `ADH_PRECOMPTE`, `PRECOMPTE_ACTIF`, `DATE_SORTIE_PRECOMPTE` | Le premier alimente `advance_requested` ; les deux autres sont des critères de la garantie : escompte en cours, date de sortie |
| `DATE_EFFET_ARRET_PLAN` | Événement `PLAN_SUSPENDED` |
| `IDC_RACHAT_ANTICIPATION`, `IDC_TRANSFERT_SORTANT` | Événements `SURRENDER` et `OUTBOUND_TRANSFER` |

### 9.5 Contenu d'un bordereau

1. **En-tête** : logo et mentions légales de l'entité ; nom, adresse et code du bénéficiaire ; code de production ou secteur commercial ; période ; date et référence du paiement ; numéro et version du bordereau.
2. **Corps**, par entité puis par gamme, une ligne par commission : numéro de contrat, nom de l'adhérent, garantie, nature de la commission, période d'encaissement, montant encaissé TTC, assiette hors taxes, taux, montant.
3. **Rubriques distinctes** : commissions ; reprises, avec leur motif ; escomptes versés ; régularisations d'escompte ; rémunérations complémentaires ; ajustements, avec leur motif.
4. **Rubrique d'information** : apurements du mois et créance d'escompte restante. Ces montants ne sont pas payés.
5. **Pied** : total de la période ; solde antérieur reporté, en une ligne ; net payé, ou solde débiteur reporté au mois suivant.

Pour l'épargne, le corps présente par contrat : versements initiaux et transferts entrants, versements libres, versements programmés (primes brutes et chargements), commissions sur encours et sur arbitrages. La version tableur reprend les mêmes lignes à plat, une colonne par donnée.

**Exemple de bordereau** (données fictives). Cabinet P13897, code de production 61723, période de septembre 2026, paiement du 7 octobre 2026.

| Rubrique | Contrat | Nature | Assiette | Taux | Montant |
| --- | --- | --- | --- | --- | --- |
| Commissions | AD-004512, Santé Select | Apport | 100,00 € | 30 % | 30,00 € |
| Commissions | AD-004512, Santé Select | Suivi | 100,00 € | 10 % | 10,00 € |
| Commissions | AD-003377, Prévoyance Pro+ | Linéaire | 200,00 € | 15 % | 30,00 € |
| Reprises | AD-002981, Santé Select | Reprise d'apport, résiliation au 200e jour | 210,00 € | 100 % | −210,00 € |
| Rémunérations complémentaires | — | RCD santé, septembre | — | — | 300,00 € |
| **Total de la période** |  |  |  |  | **160,00 €** |
| Solde antérieur reporté |  |  |  |  | −50,00 € |
| **Net payé** |  |  |  |  | **110,00 €** |

Pour information, hors paiement : apurements d'escompte du mois 40,00 € ; créance d'escompte restante 120,00 €.

Le virement reçu par le cabinet est de 110,00 € et son libellé porte le numéro du bordereau. Si le total avait été négatif, le bordereau aurait été produit quand même, avec la mention du solde débiteur reporté et sans virement.

### 9.6 Fichier de virements

Format ISO 20022 pain.001.001.03. Un bloc de paiement par lot, débité du compte de l'entité à la date d'exécution demandée. Une transaction par ordre : identifiant de bout en bout égal au numéro du bordereau (avec un suffixe en cas de réémission ou de bordereau multi-entités, section 9.11), nom et IBAN du bénéficiaire, montant en euros, libellé « COM » suivi du code de l'entité, du mois et du numéro de bordereau.

### 9.7 Extraction comptable

Un fichier CSV par entité et par mois, produit à la clôture. Colonnes : entité, exercice, mois comptable, journal, date d'écriture, événement comptable, compte au débit, compte au crédit, montant, gamme, produit, assureur, code du bénéficiaire, code du distributeur, numéro de bordereau, lot de paiement, exercice de rattachement de la prime ; au niveau de détail le plus fin s'ajoutent l'identifiant de la ligne, de l'ordre de paiement ou de la position d'escompte. Le niveau de détail est paramétrable : une écriture par ligne de commission, ou un cumul par bénéficiaire, produit et événement comptable.

### 9.8 API

API REST en JSON, décrite en OpenAPI, versionnée dans le chemin, exposée derrière la passerelle d'API d'Alptis et authentifiée par jeton OAuth 2.

| Ressource | Usage |
| --- | --- |
| `GET /api/v1/distributors/{code}/accounts` | Soldes et statut de paiement d'un distributeur |
| `GET /api/v1/distributors/{code}/commission-lines` | Lignes de commission, filtrables par période, nature, statut |
| `GET /api/v1/distributors/{code}/statements` | Liste et téléchargement des bordereaux |
| `GET /api/v1/distributors/{code}/advance-agreements` | Conventions d'escompte et créances |
| `POST /api/v1/imports/{flow}` | Dépôt d'un fichier ou d'un lot d'événements au format pivot |
| `GET /api/v1/imports/{id}` | Résultat d'un import : compteurs et rejets |
| `GET /api/v1/periods` | État des périodes |

Toute ressource de distributeur accepte le paramètre `entity`. Sans lui, la réponse couvre les entités visibles de l'appelant, séparément.

### 9.9 Imports, exports et flux : vue d'ensemble des formats

Les sections 9.1 à 9.8 décrivent les flux un par un. Les sections 9.9 à 9.12 en donnent la référence : pour chaque fichier reçu ou émis, sa structure, son nom et ses champs, avec un exemple pour les principaux. C'est le document à remettre aux équipes des systèmes voisins. Le flux OUT-07 (supervision) n'est pas un fichier et n'y figure pas.

| Flux | Objet échangé | Structure | Nom du fichier | Détail |
| --- | --- | --- | --- | --- |
| IN-01, IN-02 | Distributeurs, groupements, codes de production, accords, indicateurs, cessions de portefeuille, ordres de remplacement | CSV pivot ou JSON par API | `<entité>_distributors_<date>.csv` et suivants | 9.10 |
| IN-03 à IN-06 | Garanties | CSV pivot, ou fichier libre avec mapping | `<entité>_coverages_<date>.csv` | 9.2, 9.4 |
| IN-03 à IN-06 | Événements | CSV pivot, ou fichier libre avec mapping | `<entité>_events_<date>.csv` | 9.3, 9.4 |
| IN-03, IN-04, IN-05 | Coordonnées bancaires | CSV pivot | `<entité>_bank_accounts_<date>.csv` | 9.10 |
| IN-07 | Gammes, produits, options et assureurs | CSV pivot | `<entité>_products_<date>.csv` et suivants | 9.10 |
| IN-08 | Résultat des virements | CSV | `<entité>_payment_results_<date>.csv` | 9.10 |
| IN-09 | Collaborateurs et mesures | CSV | `<entité>_employees_<date>.csv` et suivant | 9.10 |
| OUT-01 | Ordres de virement ; liste des chèques | XML pain.001.001.03 ; CSV | `<entité>_SEPA_<mois>_L<lot>.xml`, `<entité>_CHQ_<mois>.csv` | 9.6, 9.11 |
| OUT-02 | Écritures comptables | CSV | `<entité>_ECR_<mois>.csv` | 9.7, 9.11 |
| OUT-03 | Bordereaux, attestations et autres documents | PDF, tableur, JSON de métadonnées | `<entité>_<type>_<numéro>_v<version>.pdf` | 9.5, 9.11 |
| OUT-04 | Comptes, lignes, bordereaux, conventions d'escompte | JSON par API | — | 9.8 |
| OUT-05 | Données pour la plateforme Data | CSV incrémentaux | `<entité>_DATA_<objet>_<date>.csv` | 9.11 |
| OUT-06 | Variables à payer | CSV, ou tableur de mêmes colonnes | `<entité>_VAR_<mois>.csv` | 9.11 |

**Conventions communes à tous les fichiers CSV.**

| Point | Convention |
| --- | --- |
| Encodage | UTF-8, sans marque d'ordre d'octets |
| Séparateur | Point-virgule ; un champ qui en contient un est entouré de guillemets doubles |
| En-tête | Première ligne obligatoire, noms de champs en minuscules ; l'ordre des colonnes est libre |
| Nombres | Point décimal, sans séparateur de milliers ; montants à 2 décimales |
| Taux | En fraction, jusqu'à 6 décimales : 15 % s'écrit `0.15` |
| Dates | `AAAA-MM-JJ` ; horodatages `AAAA-MM-JJTHH:MM:SS`, heure de Paris |
| Mois | `AAAA-MM` dans un champ ; `AAAAMM` dans un nom de fichier ou un identifiant de lot |
| Booléens | `true` ou `false` |
| Listes | Éléments séparés par une barre verticale ; une liste de couples s'écrit `clé=valeur\|clé=valeur` |
| Valeur absente | Champ vide ; jamais `NULL` ni zéro |
| Dans les noms de fichier | `<date>` = `AAAAMMJJ`, `<mois>` = `AAAAMM`, `<entité>` = code de l'entité, `<objet>` = nom du fichier tel que le donnent les sections 9.10 et 9.11 (`distributors`, `distributor_groups`, `production_codes`, `coverages`, `events`, etc.). Chaque système source dépose dans son propre répertoire : un fichier reçu est identifié par son flux, son système source et son nom, de sorte que deux sources peuvent envoyer le même nom le même jour |
| Fichier vide | Un fichier attendu sans donnée est envoyé avec sa seule ligne d'en-tête, pour ne pas être signalé manquant |
| Empreinte | Calculée sur le nom et le contenu du fichier : deux fichiers vides de dates différentes ne sont pas des doublons |
| Pivot en JSON | Par l'API d'import, le pivot s'envoie aussi en JSON avec les mêmes noms de champs. Excel et XML ne sont acceptés qu'à travers un mapping (section 9.4) |

Ces conventions ne viennent pas des sources ; elles sont proposées pour que tous les fichiers se lisent de la même façon (hypothèse H-50).

**Cycle de vie d'un fichier reçu.** Dépôt par UseItFlow, téléversement à l'écran ou appel d'API ; calcul de l'empreinte et refus si le fichier a déjà été intégré ; contrôle ligne par ligne ; intégration des lignes conformes, rejet ou mise en attente des autres ; inscription au journal d'import, sous un code de flux égal au nom d'objet du fichier (coverages, events, distributors, etc.). Le fichier d'origine est conservé tel quel.

**Cycle de vie d'un fichier émis.** Génération par le traitement qui en a la charge ; calcul de l'empreinte ; dépôt dans le répertoire d'échange ou mise à disposition par API ; inscription au journal des exports avec le nombre de lignes et le total. Un fichier émis n'est jamais régénéré : il se retélécharge à l'identique, et une correction donne un nouveau fichier.

### 9.10 Formats des fichiers entrants autres que les garanties et les événements

Les garanties et les événements sont décrits en 9.2 et 9.3. Les fichiers ci-dessous suivent le même principe. Leur structure est dérivée du modèle de données de la section 5.7, où chaque champ a sa colonne ; un code métier (`partner_code`, `range_code`) y devient l'identifiant interne correspondant. Les sources ne donnent pas ces structures (hypothèse H-51). Un astérisque marque un champ obligatoire. Dans tous ces fichiers, un code de distributeur est celui du CRM de l'entité émettrice ; quand le fichier n'a pas de champ de système source, ce CRM se déduit du code de l'entité.

**Distributeurs** (`distributors`). Une ligne par distributeur et par entité.

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \*, `source_system` \* | Texte | Entité et CRM émetteur |
| `partner_code` \* | Texte | Code du distributeur dans le CRM émetteur ; un distributeur commun à plusieurs entités est rapproché par la table de correspondance des codes (hypothèse H-31) |
| `legal_name` \*, `legal_form`, `siren` | Texte | Raison sociale, forme juridique, SIREN |
| `distributor_type` \* | Code | Indépendant, membre de groupement, vente à distance, mandataire |
| `status` \*, `valid_from` \*, `valid_to` | Code, date | Statut dans l'entité (`CANDIDATE`, `ACTIVE`, `SUSPENDED`, `TERMINATED`) et sa période d'effet |
| `orias_number`, `orias_status` | Texte, code | Immatriculation ORIAS et son état |
| `group_code` | Texte | Groupement de rattachement |
| `sales_delegation` | Texte | Délégation commerciale |
| `commissionable`, `advance_excluded` | Booléen | Distributeur rémunéré ; exclu de l'escompte |
| `updated_at` \* | Horodatage | Dernière modification dans la source |

S'y ajoute l'adresse postale du distributeur (`address_line1`, `address_line2`, `postal_code`, `city`), reprise sur le bordereau et sur la liste des chèques. La préférence de déclinaison du bordereau (`statement_grouping`) n'est pas reçue : elle se saisit dans l'application.

**Groupements** (`distributor_groups`).

| Champ | Type | Contenu |
| --- | --- | --- |
| `group_code` \*, `label` \* | Texte | Code et libellé du groupement |
| `head_partner_code` | Texte | Distributeur tête de groupement |

**Codes de production** (`production_codes`).

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \*, `production_code` \* | Texte | Entité et code |
| `partner_code` \* | Texte | Distributeur porteur du code |
| `label` | Texte | Libellé, par exemple l'agence |
| `status` \* | Code | Mêmes valeurs que pour un distributeur |

**Coordonnées bancaires** (`bank_accounts`), fournies par l'outil de gestion de chaque entité.

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \* | Texte | Entité |
| `holder_type` \*, `holder_code` \* | Code, texte | Niveau du titulaire (`PRODUCTION_CODE`, `DISTRIBUTOR`, `GROUP`) et son code |
| `iban` \*, `bic` | Texte | Structure de l'IBAN contrôlée à l'import |
| `holder_name` \* | Texte | Titulaire du compte, repris dans le virement |
| `valid_from` \*, `valid_to` | Date | Validité |

**Gammes, produits et options** (`product_ranges`, `products`, `product_options`).

| Fichier | Champ | Type | Contenu |
| --- | --- | --- | --- |
| `product_ranges` | `range_code` \*, `label` \*, `target` | Texte | Gamme et clientèle visée (code de la section 5.6) |
| `products` | `entity_code` \*, `product_code` \*, `label` \* | Texte | Entité, produit, libellé |
| `products` | `range_code` \*, `insurer_code` | Texte | Gamme et assureur porteur du risque |
| `products` | `management_mode`, `forecast_basis` | Code | Mode de gestion ; base du prévisionnel (`ISSUED_PREMIUMS`, `ANNUALISED_AT_SUBSCRIPTION`, `NONE`) |
| `products` | `reference_lag_months` | Entier | Décalage du principe P3 : 0, 1 ou 2 mois |
| `products` | `commissionable` | Booléen | Produit ouvrant droit à commission |
| `products` | `marketed_from` \*, `marketed_to` | Date | Période de commercialisation |
| `product_options` | `product_code` \*, `option_code` \*, `label` \*, `option_type` | Texte, code | Option d'un produit |

Les assureurs et gestionnaires délégués arrivent par le même flux, dans un fichier `insurers` : `insurer_code` \*, `name` \*, `role` \*.

**Indicateurs de qualité** (`quality_indicators`).

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \*, `partner_code` \* | Texte | Entité et distributeur |
| `indicator_type` \* | Code | L'un des sept types de la section 5.1 : taux de réclamations, de sans-suite, de radiation sur production nouvelle, de radiation du portefeuille, d'impayés, P/C, note de solvabilité |
| `range_code` | Texte | Gamme mesurée (santé, prévoyance) ; vide si l'indicateur est global |
| `period_from` \*, `period_to` \* | Date | Période de mesure |
| `value` \* | Décimal | Valeur en fraction : 82 % s'écrit `0.82` |
| `source` | Texte | Service ou système qui a produit la mesure |

**Accords et signatures** (`agreements`). Les conventions d'escompte ne passent pas par ce fichier : elles sont saisies à l'écran avec leurs paramètres (exigence PAR-09).

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \*, `agreement_type` \*, `reference` \* | Texte, code | Convention de partenariat, avenant RCD ou RCP, protocole assureur, mandat |
| `partner_code`, `group_code`, `insurer_code` | Texte | Partie signataire : distributeur, groupement ou assureur |
| `valid_from` \*, `valid_to` | Date | Validité |
| `signature_status` \*, `signed_at` | Code, date | SIGNED ou NOT\_SIGNED, et date de signature |
| `document_ref` | Texte | Référence du document en GED |

**Cessions de portefeuille et ordres de remplacement** (`portfolio_transfers`, `replacement_orders`), reçus du CRM.

| Fichier | Champ | Type | Contenu |
| --- | --- | --- | --- |
| `portfolio_transfers` | `entity_code` \*, `from_partner_code` \*, `to_partner_code` \* | Texte | Entité, cédant, repreneur |
| `portfolio_transfers` | `effect_date` \*, `scope` | Date, liste | Date d'effet ; périmètre cédé : liste de codes de production, vide pour tout le portefeuille |
| `portfolio_transfers` | `advance_debt_handling` \* | Code | `REPAID_BY_TRANSFEROR` ou `TAKEN_OVER` |
| `replacement_orders` | `entity_code` \*, `source_system` \*, `contract_number` \* | Texte | Contrat visé |
| `replacement_orders` | `from_production_code` \*, `to_production_code` \* | Texte | Ancien et nouveau code de production |
| `replacement_orders` | `received_date` \*, `effect_date` \* | Date | Réception et effet |

**Résultat des virements** (`payment_results`), renvoyé par le SI Finance après chaque lot.

| Champ | Type | Contenu |
| --- | --- | --- |
| `entity_code` \*, `batch_reference` \* | Texte | Entité et référence du lot |
| `end_to_end_id` \* | Texte | Identifiant de l'ordre |
| `status` \* | Code | `SETTLED` ou `REJECTED` |
| `settled_at` | Date | Date d'exécution constatée |
| `rejection_reason` | Texte | Motif bancaire du rejet |

```csv
entity_code;batch_reference;end_to_end_id;status;settled_at;rejection_reason
ALPTIS_ASSURANCES;202609-L001;2026-09-004512;SETTLED;2026-10-07;
ALPTIS_ASSURANCES;202609-L001;2026-09-004520;REJECTED;;Compte clôturé
```

La seconde ligne recrédite le compte du distributeur et ouvre une anomalie (exigence PAY-06).

**Collaborateurs et mesures** (`employees`, `employee_measures`), pour le module forces de vente.

| Fichier | Champ | Type | Contenu |
| --- | --- | --- | --- |
| `employees` | `entity_code` \*, `external_id` \* | Texte | Entité et matricule du SI RH |
| `employees` | `last_name` \*, `first_name` \* | Texte | Identité |
| `employees` | `job_code` \*, `team`, `region`, `manager_external_id` | Texte | Poste, équipe, région, responsable |
| `employees` | `valid_from` \*, `valid_to` | Date | Période d'affectation au poste |
| `employee_measures` | `external_id` \*, `measure_type` \*, `dimension` | Texte, code | Collaborateur et nature de la mesure : absence, production par marché, activité commerciale, marge, cahiers des charges reçus ; la dimension précise le marché (gamme) ou le type d'activité (appel, rendez-vous, devis, nouveau producteur) |
| `employee_measures` | `period_from` \*, `period_to` \*, `value` \* | Date, décimal | Période et valeur |

**Imports en masse déposés par les utilisateurs.** Quatre fichiers se téléversent à l'écran, aux mêmes conventions. L'entité est celle de la session de l'utilisateur qui téléverse ; le code du bénéficiaire est celui qui figure sur son bordereau (code de partenaire, de groupement ou de production).

| Fichier | Exigence | Champs |
| --- | --- | --- |
| `adjustments` | REG-04 | `adjustment_type` \*, `payee_code` \*, `amount` \*, `reason` \*, `source_system`, `contract_number`, `coverage_code`, `counterpart_payee_code` (second compte d'un transfert), `auto_reverse` |
| `enrollments` | PAR-10 | `plan_code` \*, `rule_code` \*, `partner_code` ou `group_code` (l'un des deux) \*, `valid_from` \*, `valid_to`, `agreement_reference` |
| Données complémentaires | ING-08 | Format pivot des garanties ou des événements |
| Reprise de l'historique | ING-11 | `opening_lines` (lignes acquises des 24 derniers mois, pour l'épargne retraite, 5 ans de versements programmés encaissés, repris comme événements), `opening_advances` (escomptes en cours), `opening_balances` (soldes des comptes), `opening_counters` (cumuls annuels des surcommissions) ; structure à arrêter avec l'équipe du système repris, lot par lot |

### 9.11 Formats des fichiers sortants

**Fichier de virements (OUT-01).** Éléments renseignés dans le message pain.001.001.03 :

| Élément | Niveau | Contenu |
| --- | --- | --- |
| `GrpHdr/MsgId`, `CreDtTm` | En-tête | Référence du lot et horodatage de création |
| `GrpHdr/NbOfTxs`, `CtrlSum` | En-tête | Nombre d'ordres et total du lot |
| `PmtInf/PmtMtd` | Bloc | `TRF` |
| `PmtInf/ReqdExctnDt` | Bloc | Date d'exécution demandée |
| `PmtInf/Dbtr`, `DbtrAcct`, `DbtrAgt` | Bloc | Nom, IBAN et BIC du compte de l'entité |
| `CdtTrfTxInf/PmtId/EndToEndId` | Transaction | Numéro du bordereau ; puis `-E` et le rang de l'entité pour un bordereau multi-entités ; puis `-R` et le rang de réémission après un rejet. Exemple : `2026-09-004512-E2-R1`. Toujours 35 caractères au plus |
| `CdtTrfTxInf/Amt/InstdAmt` | Transaction | Net payé, en euros |
| `CdtTrfTxInf/Cdtr/Nm`, `CdtrAcct` | Transaction | Titulaire et IBAN du bénéficiaire |
| `CdtTrfTxInf/RmtInf/Ustrd` | Transaction | « COM », code de l'entité, mois, numéro de bordereau |

```xml
<CdtTrfTxInf>
  <PmtId><EndToEndId>2026-09-004512</EndToEndId></PmtId>
  <Amt><InstdAmt Ccy="EUR">110.00</InstdAmt></Amt>
  <Cdtr><Nm>CABINET P13897</Nm></Cdtr>
  <CdtrAcct><Id><IBAN>FR76...4471</IBAN></Id></CdtrAcct>
  <RmtInf>
    <Ustrd>COM ALPTIS_ASSURANCES 2026-09 2026-09-004512</Ustrd>
  </RmtInf>
</CdtTrfTxInf>
```

**Liste des chèques (OUT-01, même destinataire).** Un fichier par trimestre, nommé du dernier mois du trimestre (et un second pour l'épargne retraite, de même nom suivi de `_PER`). L'adresse est celle du distributeur titulaire du compte ; pour un groupement ou un code de production, celle du distributeur qui porte ses coordonnées de règlement. Un chèque n'a pas de retour bancaire : le contrôleur Finance saisit son émission, qui règle l'ordre. Fichier établi, pour les bénéficiaires sans coordonnées bancaires : `entity_code` \*, `payee_code` \*, `creditor_name` \*, `address_line1` \*, `address_line2`, `postal_code` \*, `city` \*, `amount` \*, `statement_number` \*.

**Extraction comptable (OUT-02).** Les colonnes de la section 9.7, avec leur nom technique :

| Colonne | Type | Contenu |
| --- | --- | --- |
| `entity_code` \* | Texte | Entité |
| `fiscal_year` \*, `accounting_month` \* | Entier, mois | Exercice et mois comptable |
| `journal` \*, `entry_date` \* | Texte, date | Journal et date d'écriture |
| `accounting_event` \* | Code | Événement comptable de la section 10.2 |
| `debit_account` \*, `credit_account` \* | Texte | Comptes, selon le schéma de l'entité |
| `amount` \* | Décimal | Montant, toujours positif |
| `range_code`, `product_code`, `insurer_code` | Texte | Axes analytiques : gamme, produit, assureur |
| `payee_code`, `origin_partner_code` | Texte | Bénéficiaire et distributeur d'origine ; vides pour une commission reçue d'un assureur |
| `statement_number`, `batch_reference` | Texte | Bordereau et lot de paiement |
| `premium_fiscal_year` | Entier | Exercice de rattachement de la prime |

Au niveau de détail le plus fin, trois colonnes s'ajoutent pour relier chaque écriture à son origine : `line_id` (ligne de commission), `end_to_end_id` (ordre de paiement) et `advance_reference` (position d'escompte d'un reliquat passé en charge, désignée par le système source, le numéro de contrat et le code de garantie). Extrait, limité à six colonnes :

```csv
accounting_month;accounting_event;debit_account;credit_account;amount;payee_code
2026-09;EARNING;[PROVISION];[ACQUIS];30.00;P13897
2026-09;PAYABLE_RECOGNITION;[ACQUIS];[DISTRIBUTEUR];30.00;P13897
```

Les comptes entre crochets attendent le plan de comptes (hypothèse H-22).

**Documents pour la GED (OUT-03).** Chaque PDF est accompagné d'un fichier JSON de métadonnées. Le champ `document_type` vaut `STATEMENT` (bordereau), `CORRECTED_STATEMENT` (rectificatif), `MEMBER_STATEMENT` (relevé informatif d'un membre de groupement), `TAX_CERTIFICATE` (attestation fiscale) ou `ADVANCE_SUMMARY` (synthèse des escomptes) ; il donne aussi le `<type>` du nom de fichier. Pour un bordereau couvrant plusieurs entités, `entity_code` vaut `MULTI`.

```json
{
  "document_type": "STATEMENT",
  "statement_number": "2026-09-004512",
  "version": 1,
  "entity_code": "ALPTIS_ASSURANCES",
  "payee_code": "P13897",
  "period": "2026-09",
  "net_amount": 110.00,
  "published_at": "2026-10-08T09:00:00",
  "sha256": "<empreinte du PDF>"
}
```

Le numéro d'un bordereau se compose du mois de la période (`AAAA-MM`) et d'un numéro d'ordre à six chiffres ; il s'écrit tel quel dans les noms de fichier, par exception à la convention des mois. Pour une attestation fiscale ou une synthèse des escomptes, `<numéro>` est le code du bénéficiaire suivi de l'année. Pour une éditique externe (exigence BRD-12), le même JSON est enrichi des lignes du corps du bordereau.

**Export vers la plateforme Data (OUT-05).** Chaque nuit, un fichier par objet, contenant les lignes créées ou modifiées depuis l'export précédent. Chaque ligne porte `updated_at` ; la plateforme reconstitue l'état complet en appliquant les fichiers dans l'ordre.

| Fichier | Table d'origine | Clé | Contenu |
| --- | --- | --- | --- |
| `commission_lines` | `commission_line` | Identifiant de ligne | Nature, statut, assiette, taux, montant, bénéficiaire, règle et version, événement, bordereau |
| `business_events` | `business_event` | Système et clé source | Type, dates, montants, statut de traitement |
| `account_entries` | `account_entry` | Identifiant du mouvement | Mouvements de compte hors commissions : type (paiement, rejet, solde d'ouverture, remboursement reçu, abandon de créance), date, montant, ordre de paiement |
| `payment_orders` | `payment_order`, `payment_batch` | Identifiant d'ordre | Référence du lot, bénéficiaire, montant, date d'exécution, statut |
| `rule_versions` | `rule_version` | Règle et numéro de version | Famille (reprise de la table `rule`), validité, périmètre, paramètres, statut |

Les colonnes de chaque fichier sont celles de sa table d'origine en section 5.7, les identifiants internes étant remplacés par les codes métier. Les écritures comptables ne partent pas par ce flux : elles suivent l'extraction OUT-02.

**Export vers la paie (OUT-06).** Un fichier par mois, produit après la validation du responsable.

| Colonne | Type | Contenu |
| --- | --- | --- |
| `external_id` \* | Texte | Matricule du SI RH |
| `job_code` \* | Code | Poste |
| `pay_period` \* | Mois | Mois de paie visé |
| `component` \* | Code | Mensuel individuel, qualitatif, collectif, régularisation, ajustement |
| `amount` \* | Décimal | Montant brut à verser |
| `adjustment_reason` | Texte | Motif d'un ajustement accordé par le responsable |
| `validated_by` \*, `validated_at` \* | Texte, horodatage | Trace de la validation |

Les sources demandent les exports Data et paie sans en donner la structure : celle-ci est proposée (hypothèse H-51).

### 9.12 Exports à la demande

En plus des flux planifiés, tout utilisateur exporte ce qu'il voit, dans la limite de ses droits (exigences REP-04, CTL-06 et PAR-15).

| Export | Format | Maille |
| --- | --- | --- |
| Toute liste affichée, avec ses filtres | CSV ou tableur | Une ligne par élément de la liste |
| Lignes de commission d'une période | CSV ou tableur | Une ligne par commission |
| Comptes et soldes des distributeurs | CSV ou tableur | Un compte par ligne |
| Anomalies et décisions | CSV ou tableur | Une anomalie par ligne |
| Données des contrôles de niveau 2 et 3 | CSV | Selon le contrôle |
| État de cut-off | Tableur | Par garantie, distributeur, produit ou entité |
| Paramétrage des règles | JSON | Plan complet, pour la promotion entre environnements et la réversibilité |

Un export à la demande suit les conventions de la section 9.9 et respecte le cloisonnement par entité. Comme tout fichier émis, il est inscrit au journal des exports (table `export_batch`) avec son auteur.

**Limites.** Aucun échange en temps réel n'est prévu : les outils de gestion déposent des fichiers, et le SI Finance reçoit ses écritures une fois par mois. Les formats propriétaires des assureurs ne sont pas figés ici : chacun passe par un mapping (section 9.4), à refaire si l'assureur change son fichier.

## 10. Compte distributeur et comptabilité

L'application tient une comptabilité auxiliaire des commissions : les changements de statut d'une ligne produisent des écritures, et l'ensemble part au SI Finance une fois par mois, après cut-off. Le compte distributeur en est la vue métier, consultable à tout moment.

### 10.1 Cycle de vie d'une commission

| Statut | Entrée dans le statut | Sortie possible |
| --- | --- | --- |
| `FORECAST` | Prime émise, ou souscription pour un produit provisionné sur la prime annualisée | Aucune : la ligne reste inchangée. Une résiliation, un impayé ou un avenant ajoute une ligne prévisionnelle de régularisation |
| `EARNED` | Prime encaissée, ou événement donnant droit (escompte dû, RCD du mois, reprise) | `PAYABLE`, `OFFSET`, `CANCELLED` |
| `PAYABLE` | Validation métier de la période ; la ligne figure sur un bordereau | `PAID`, ou reste au compte si le paiement est exclu ; retour au statut acquis si la Finance refuse le lot |
| `PAID` | Virement exécuté ou chèque émis | Retour à `PAYABLE` sur rejet bancaire |
| `OFFSET` | Ligne imputée sur un escompte | — |
| `CANCELLED` | Ligne acquise dont l'événement est annulé avant la validation métier : elle sort du paiement, comme sa ligne inverse. Une ligne déjà payable ou payée garde son statut ; sa ligne inverse suit alors le parcours normal et vient en déduction du paiement suivant | — |

La provision prévisionnelle se constitue selon un paramètre du produit : sur chaque prime émise, ou en une fois à la souscription sur la prime annualisée des 12 premiers mois, ou pas du tout (produit sans prévisionnel).

&#91;embedded content: cycle de vie d'une ligne de commission · 6 statuts\]

La ligne prévisionnelle vit à part et n'est jamais payée. Une ligne acquise suit le chemin de gauche à droite ; les deux sorties du bas la retirent du paiement sans la supprimer.

### 10.2 Schéma des écritures

Les comptes sont désignés par leur rôle ; la correspondance avec le plan de comptes de chaque entité est une table de paramétrage. Codes des rôles : `FORECAST_EXPENSE` (charge de commission prévisionnelle), `PROVISION` (provision pour commissions à payer), `EARNED_COMMISSIONS` (commissions acquises), `DISTRIBUTOR_PAYABLE` (distributeur, commissions à payer), `BANK` (banque), `DISTRIBUTOR_ADVANCES` (avances aux distributeurs), `COMMISSION_EXPENSE` (charge de commission), `CLAWBACK_INCOME` (reprises de commissions), `INSURER_RECEIVABLE` (assureur, commissions à recevoir), `COMMISSION_INCOME` (produits de commissions).

| Événement comptable | Déclencheur | Débit | Crédit |
| --- | --- | --- | --- |
| `FORECAST_RECOGNITION` | Ligne `FORECAST` créée | Charge de commission prévisionnelle | Provision pour commissions à payer |
| `FORECAST_ADJUSTMENT` | Résiliation, impayé, avenant | Provision pour commissions à payer si le prévisionnel baisse ; charge prévisionnelle s'il augmente | Le compte opposé : charge prévisionnelle si le prévisionnel baisse ; provision s'il augmente |
| `EARNING` | Ligne `EARNED` sur encaissement | Provision pour commissions à payer | Commissions acquises |
| `PAYABLE_RECOGNITION` | Passage à `PAYABLE` | Commissions acquises | Distributeur, commissions à payer |
| `PAYMENT` | Passage à `PAID` | Distributeur, commissions à payer | Banque |
| `PAYMENT_REJECTION` | Rejet bancaire | Banque | Distributeur, commissions à payer |
| `ADVANCE_GRANTED` | Escompte validé | Avances aux distributeurs | Distributeur, commissions à payer |
| `ADVANCE_OFFSETTING` | Apurement | Commissions acquises | Avances aux distributeurs |
| `ADVANCE_SETTLEMENT` | Régularisation négative (escompte indu) | Distributeur, commissions à payer | Avances aux distributeurs |
| `ADVANCE_SETTLEMENT` | Régularisation positive (complément d'escompte) | Avances aux distributeurs | Distributeur, commissions à payer |
| ADVANCE\_WRITE\_OFF | Reliquat d'escompte sous la tolérance de solde (hypothèse H-40) | Charge de commission | Avances aux distributeurs |
| `CLAWBACK` | Reprise | Distributeur, commissions à payer | Reprises de commissions |
| `SUPPLEMENT` | RCD, RCP, complément, frais, ajustement ; écriture inversée si le montant est négatif | Charge de commission | Distributeur, commissions à payer |
| `ACCOUNT_TRANSFER` | Compensation entre comptes | Distributeur, compte créditeur | Distributeur, compte débiteur |
| `INSURER_RECEIVABLE` | Commission reçue d'un assureur | Assureur, commissions à recevoir | Produits de commissions |

Chaque écriture porte l'identifiant de la ligne de commission (ou, pour un paiement, celui de l'ordre, et pour un reliquat d'escompte, celui de la position d'escompte), donc l'événement, l'assiette, le taux et la version de règle qui l'expliquent.

Règles d'application du schéma :

- `EARNING` puis `PAYABLE_RECOGNITION` valent pour les commissions nées d'un flux : natures `LINEAR`, `ACQUISITION`, `SERVICING`, `OUTSTANDING`, `ARBITRAGE`, `RETROCESSION`, `EXTERNAL`.
- Les autres natures sont inscrites directement au compte du distributeur par leur propre événement comptable , passé quand la ligne devient payable à la validation métier ; elles ne donnent ni acquisition ni constatation de dette séparées. Un refus de la Finance contre-passe ces écritures comme les autres.
- Pour un produit sans prévisionnel, `EARNING` débite la charge de commission au lieu de la provision (variante `NO_FORECAST`).
- Une ligne inverse passe l'écriture inverse de sa ligne d'origine.

**Variantes du schéma.** Chaque ligne du schéma a une variante. `STANDARD` est le cas général. `NEGATIVE` s'applique quand le montant de la ligne est négatif (baisse du prévisionnel, escompte indu, supplément négatif), pour les événements décrits sur deux sens (`FORECAST_ADJUSTMENT`, `ADVANCE_SETTLEMENT`, `SUPPLEMENT`). `NO_FORECAST` est l'`EARNING` d'un produit sans prévisionnel. `SAVINGS` est l'`ADVANCE_GRANTED` de l'escompte retraite : débit « Charge de commission », crédit « Distributeur, commissions à payer », sans apurement.

**Hors de l'application.** Le règlement d'un assureur est comptabilisé par le SI Finance, qui solde le compte « Assureur, commissions à recevoir » ; l'application se limite au rapprochement (FIN-08). Le solde d'ouverture d'un compte repris et l'abandon d'un solde débiteur irrécouvrable ne donnent aucune écriture dans l'application : le remboursement reçu d'un distributeur et l'abandon de créance sont saisis par le contrôleur Finance comme mouvements du compte (`REPAYMENT_RECEIVED`, `WRITE_OFF`) et comptabilisés au SI Finance, de même que la créance des escomptes repris à la bascule. Une avance exceptionnelle (CAL-14, REG-05) est un ajustement : elle suit le schéma `SUPPLEMENT`, le compte « Avances aux distributeurs » étant réservé à l'escompte.

**Moment des écritures.** Toutes les écritures d'une période sont générées à sa validation métier, y compris le prévisionnel, l'acquisition, l'apurement et le reliquat d'escompte ; aucune n'existe avant, et un recalcul n'a donc rien à contre-passer. Seules les écritures de paiement et de rejet bancaire sont passées plus tard, à l'enregistrement du résultat. Une commission externe négative passe l'écriture inversée de son schéma, le montant de l'extraction restant positif. Dans les exemples ci-dessous, la colonne « Moment » donne le fait générateur de chaque écriture, non la date à laquelle elle est générée.

**Exemple : la vie comptable d'une commission linéaire.** Cotisation de 100 € par mois, taux de 15 %, provision à la souscription.

| Moment | Événement comptable | Débit | Crédit | Montant |
| --- | --- | --- | --- | --- |
| Souscription | `FORECAST_RECOGNITION` | Charge prévisionnelle | Provision | 180,00 € |
| 1er encaissement | `EARNING` | Provision | Commissions acquises | 15,00 € |
| Validation métier | `PAYABLE_RECOGNITION` | Commissions acquises | Distributeur | 15,00 € |
| Virement exécuté | `PAYMENT` | Distributeur | Banque | 15,00 € |

Après ces quatre écritures, la provision restante est de 165 €, le compte du distributeur est soldé et la banque a décaissé 15 €. Si cet encaissement est annulé le mois suivant, la ligne inverse passe, à la validation métier de sa période, les écritures inverses `EARNING` et `PAYABLE_RECOGNITION` de 15 € ; le distributeur doit alors 15 €, déduits de son prochain paiement.

**Exemple : la vie comptable d'un escompte.** Escompte de 480 €, apuré par douze commissions de 40 €.

| Moment | Événement comptable | Débit | Crédit | Montant |
| --- | --- | --- | --- | --- |
| Escompte validé | `ADVANCE_GRANTED` | Avances aux distributeurs | Distributeur | 480,00 € |
| Virement exécuté | `PAYMENT` | Distributeur | Banque | 480,00 € |
| Chaque encaissement | `EARNING` | Provision | Commissions acquises | 40,00 € |
| Chaque encaissement | `ADVANCE_OFFSETTING` | Commissions acquises | Avances aux distributeurs | 40,00 € |

Le compte d'avances porte 480 € après le versement et revient à zéro au douzième apurement : à tout moment, son solde est la créance d'escompte. L'escompte ne crée aucune charge par lui-même : la charge de commission suit le même rythme qu'en l'absence d'escompte.

**Limites.** Ce schéma est une proposition construite sans plan de comptes (hypothèse H-22) : aucun numéro de compte, aucun journal. Il doit être validé par la direction financière avant tout développement du module comptable. Le schéma étant une table de paramétrage, changer un compte ne demande pas de code ; changer le nombre ou l'ordre des événements comptables en demande.

### 10.3 Rattachement et cut-off

- Le mois comptable d'une écriture est celui de la période dans laquelle l'événement comptable est constaté, sauf pour l'écriture de paiement, qui porte le mois de la date d'exécution du virement. La date d'écriture est le dernier jour du mois comptable, ou la date d'exécution pour un paiement.
- L'exercice de rattachement de la prime (année du début de la période de cotisation) est conservé comme attribut de l'écriture.
- La clôture d'une période fige ses écritures et produit l'extraction. Une écriture n'est transmise qu'une fois ; l'extraction qui la contient est mémorisée sur l'écriture.
- Une correction postérieure à la clôture produit des écritures dans la période ouverte suivante.

**Exemple.** Une prime de septembre est encaissée le 12 septembre. Les écritures d'acquisition et de validation portent le mois comptable de septembre, bien que la validation métier ait lieu le 5 octobre : elles appartiennent à la période de septembre. L'écriture de paiement porte le mois de la date d'exécution du virement, octobre : la banque n'est mouvementée qu'à ce moment (hypothèse H-43). Fin septembre, la comptabilité montre donc une dette de 15 € envers le distributeur, éteinte en octobre.

**Limites.** Entre la validation métier et la clôture de la période, les écritures de septembre existent dans l'application mais ne sont pas encore au SI Finance. Une clôture comptable du groupe plus précoce que la clôture de la période demanderait d'avancer celle-ci dans le calendrier.

### 10.4 État de cut-off de pilotage

Cet état se lit par garantie, distributeur, produit ou entité, à une date d'arrêté.

| Colonne | Définition |
| --- | --- |
| Prévisionnel | Cumul des lignes `FORECAST`, régularisations comprises |
| Acquis cumulé | Cumul des lignes acquises de commission |
| Régularisations cumulées | Cumul des reprises, régularisations d'escompte et ajustements |
| Payé cumulé | Cumul des paiements |
| Solde à payer | Acquis cumulé + régularisations cumulées − payé cumulé |
| Reste à acquérir | Prévisionnel − acquis cumulé |

Exemple : cotisation de 100 € par mois, taux de 15 %, provision à la souscription de 180 €.

| Situation | Prévisionnel | Acquis | Régularisations | Payé | Solde à payer | Reste à acquérir |
| --- | --- | --- | --- | --- | --- | --- |
| Après le 1er encaissement | 180 | 15 | 0 | 0 | 15 | 165 |
| Après 6 encaissements, 5 payés | 180 | 90 | 0 | 75 | 15 | 90 |
| Résiliation au 6e mois, tout payé | 90 | 90 | 0 | 90 | 0 | 0 |

Un avenant qui porte la cotisation annuelle de 1 200 à 1 500 € relève le prévisionnel de 180 à 225 € : une régularisation de +45 €. Un impayé de 100 € annule 15 € d'acquis par la ligne inverse.

### 10.5 Règles du compte distributeur

1. Il existe un compte par entité et par bénéficiaire. Les lignes y sont inscrites dès qu'elles sont acquises.
2. Le solde du compte est la somme de toutes ses lignes, positives et négatives, hors lignes prévisionnelles et hors lignes imputées sur un escompte, augmentée du solde d'ouverture repris à la bascule , des remboursements reçus et des abandons de créance, et diminuée des paiements.
3. Une reprise est imputée immédiatement sur les commissions à devoir du même compte.
4. Si le solde est positif et atteint le seuil minimal de paiement de l'entité, il est proposé au paiement en totalité.
5. Si le solde est négatif, aucun paiement n'est émis. Le débit est reporté et absorbe les commissions des périodes suivantes jusqu'à extinction. Les lignes restent payables au compte ; elles sont toutes soldées par le premier paiement qui suit. Un bordereau est produit chaque mois, payé ou non.
6. Avant le report, les comptes débiteurs d'un distributeur sont compensés avec ses comptes créditeurs de la même entité (exigence CPT-04).
7. Un distributeur débiteur sans portefeuille actif est signalé pour demande de remboursement.
8. L'application ne reprend jamais plus que ce qu'elle a versé sur une garantie.

Motifs d'exclusion d'un paiement, affichés sur le compte : distributeur non actif, radiation ORIAS, blocage manuel, solde débiteur, montant sous le seuil, coordonnées bancaires absentes ou invalides, périodicité de paiement non échue.

Exemple : un compte présente −1 000 € fin janvier après une reprise. En février, 600 € de commissions ramènent le solde à −400 €, sans paiement ; le bordereau de février montre le détail de février et une ligne « solde antérieur −1 000 € ». En mars, 700 € de commissions portent le solde à +300 €, payés.

## 11. Exigences non fonctionnelles

Les seuils viennent du questionnaire RFI et du document d'exigences techniques du RFP ; ils s'appliquent tels quels à une solution développée et hébergée par Alptis.

| N° | Exigence | Cible | Source |
| --- | --- | --- | --- |
| NFR-01 | Disponibilité sur la plage de service, hors maintenance planifiée | 99,9 % | NF-02 |
| NFR-02 | Reprise après sinistre | RTO et RPO de 24 h au plus ; sauvegarde quotidienne conservée 30 jours | RFP |
| NFR-03 | Temps de réponse des écrans, opérations courantes | Moins de 2 secondes sur le poste | NF-04 |
| NFR-04 | Durée du calcul mensuel complet | Moins de 4 heures pour 20 000 bénéficiaires | RFP |
| NFR-05 | Volumétrie | 9 600 distributeurs rémunérés ; 5 millions d'événements par an ; 10 ans d'historique | RFP |
| NFR-06 | Utilisateurs | 25 au démarrage, 100 à terme | NF-03 |
| NFR-07 | Localisation | Hébergement, sauvegardes et traitements en France ou dans l'Union européenne, sans exception | NF-15 |
| NFR-08 | Authentification | SSO OpenID Connect ou SAML 2.0 ; authentification multifacteur portée par le SSO ; TLS 1.2 au minimum | NF-11 |
| NFR-09 | Habilitations | Rôles, cloisonnement par entité, séparation des tâches ; droits issus des groupes de l'annuaire | NF-12, NF-13 |
| NFR-10 | Chiffrement | En transit et au repos | RFP |
| NFR-11 | Piste d'audit technique | Toutes les actions des utilisateurs et des traitements ; non modifiable ; au moins 12 mois en ligne | NF-16 |
| NFR-12 | Piste d'audit financière | Chaîne complète du virement à la règle (exigence FIN-07) | NF-17 |
| NFR-13 | Conservation | Lignes, écritures, bordereaux et versions de règles conservés 10 ans ; données personnelles purgées ou anonymisées au-delà | NF-18 |
| NFR-14 | Protection des données personnelles | Minimisation : du souscripteur, seuls l'identifiant, le nom, le prénom, la date de naissance et le type de personne sont reçus ; aucune donnée de santé | NF-14 |
| NFR-15 | Environnements | Production ; recette, rafraîchissable depuis la production avec anonymisation, qui sert aussi à la formation et aux essais de règles ; développement | RFP |
| NFR-16 | Supervision | Point de contrôle de santé et métriques des traitements, intégrés à Centreon | NF-05 |
| NFR-17 | Réversibilité | Export complet, en format ouvert, des données, règles, référentiels et historiques | NF-07 |
| NFR-18 | Exactitude numérique | Arithmétique décimale exacte ; montants à 2 décimales ; taux à 6 décimales ; aucun nombre flottant | — |
| NFR-19 | Idempotence | Tout import et tout traitement est relançable sans créer de doublon | — |
| NFR-20 | Reproductibilité | Chaque exécution enregistre la version de l'application et l'empreinte du paramétrage utilisé | EF-25 |
| NFR-21 | Navigateurs et accessibilité | Versions courantes de Chrome, Edge et Firefox ; conformité RGAA visée | RFP |
| NFR-22 | Sécurité du développement | Revue de code, analyse statique, suivi des vulnérabilités des dépendances, secrets hors du code, test d'intrusion avant chaque mise en service majeure | RFP |
| NFR-23 | Langue | Interface, messages, documents et documentation en français | EF-123 |

**Ce que ces chiffres veulent dire.**

- *Performance (NFR-04, NFR-05).* 5 millions d'événements par an font environ 420 000 par mois. Les calculer en moins de 4 heures revient à traiter une trentaine d'événements par seconde, ce qui laisse le temps de relancer un calcul complet dans la journée si un paramétrage est corrigé.
- *Reprise après sinistre (NFR-02).* Un RPO de 24 heures signifie qu'on peut perdre au pire une journée de données. Comme les imports sont relançables sans doublon, cette journée se reconstitue en redéposant les fichiers.
- *Exactitude numérique (NFR-18).* En nombres flottants, 0,1 + 0,2 ne fait pas exactement 0,3 ; sur des millions de lignes, les totaux dérivent de quelques centimes et le lot ne tombe plus sur la somme des bordereaux. L'arithmétique décimale supprime ce risque.
- *Idempotence (NFR-19).* Un traitement interrompu à mi-course se relance tel quel : ce qui est déjà fait n'est pas refait, rien n'est compté deux fois.
- *Reproductibilité (NFR-20).* Trois ans plus tard, face à un auditeur ou à un courtier, on peut rejouer le calcul d'un mois clos et retrouver les mêmes lignes au centime.

**Limites.** Ces seuils viennent d'un appel d'offres rédigé pour un éditeur. La disponibilité de 99,9 % suppose une infrastructure redondée que la DSSI doit confirmer pour une application interne ; un outil de back-office utilisé en heures ouvrées peut s'accommoder d'une cible moindre.

## 12. Architecture technique

L'application est un monolithe modulaire, en instance unique pour toutes les entités, adossé à une base relationnelle ; les règles sont des données, les familles de calcul sont du code. Le socle proposé est Java 21, Quarkus, PostgreSQL ; c'est une hypothèse à confirmer avec la DSSI (section 15).

### 12.1 Composants

| Composant | Responsabilité |
| --- | --- |
| Référentiels | Entités, produits, distributeurs, hiérarchie, coordonnées bancaires, règles de versement, indicateurs |
| Intégration | Réception des fichiers et appels d'API, mappings, contrôles, rejets, journal d'import, zone de transit |
| Contrats et événements | Adhésions, garanties, affectations historisées, événements métier |
| Règles | Accords, plans, règles, versions, barèmes, conditions, inscriptions, conventions d'escompte, détection de conflits |
| Moteur de calcul | Sélection des règles, familles de calcul, production des lignes, escomptes, reprises, simulation |
| Cycle et contrôles | Périodes, contrôles, ajustements, réclamations, validations, notifications |
| Comptes et paiements | Comptes distributeur, compensation, lots, ordres SEPA, écritures, extractions |
| Documents | Modèles, bordereaux, attestations, archivage |
| Restitutions | Tableaux de bord, recherches, exports, API de lecture, export Data |
| Sécurité et audit | SSO, rôles, cloisonnement, piste d'audit |

### 12.2 Choix structurants

1. **Cloisonnement.** Toute table métier porte l'identifiant de l'entité, directement ou par sa table parente. Le filtre par entité est appliqué dans la couche d'accès aux données, pas dans les écrans, et vérifié par des tests automatiques.
2. **Données en ajout seul.** Événements, lignes de commission, écritures et traces d'audit ne sont jamais modifiés ni supprimés après validation. Les grandes tables sont partitionnées par mois.
3. **Règles sans langage de script.** Une famille de calcul est une classe ; une version de règle est un jeu de paramètres typés et un périmètre structuré. L'utilisateur métier ne rédige aucune formule libre : le calcul reste déterministe, testable et explicable.
4. **Calcul par lots.** Le calcul d'une période est découpé en unités de travail : la garantie pour les commissions, puis le distributeur pour les cumuls et les comptes. Chaque unité est une transaction, relançable seule, exécutable en parallèle (section 12.3).
5. **Explication native.** Chaque ligne stocke les entrées de son calcul : événement, version de règle, assiette, taux, palier, conditions évaluées et leurs valeurs.
6. **Simulation isolée.** Une simulation écrit dans un espace séparé des lignes réelles et ne touche ni aux comptes ni aux écritures.
7. **Documents figés.** Un bordereau publié est un fichier archivé avec son empreinte ; il n'est jamais régénéré à partir des données.
8. **Intégration par fichiers d'abord.** Les échanges avec les outils de gestion passent par des fichiers pivot déposés par UseItFlow ; l'API sert les consommateurs en lecture et les imports unitaires.
9. **Audit inaltérable.** La table d'audit est en ajout seul, et chaque trace porte l'empreinte de la précédente.
10. **Interface.** Application web monopage en Vue.js pour les écrans de gestion, servie par l'application et alimentée par son API REST ; aucun accès direct des distributeurs.

**Comment se déroule une exécution de calcul.**

1. L'application verrouille la période et enregistre l'exécution : auteur, version du logiciel, empreinte du paramétrage.
2. Elle découpe le travail en unités, une par garantie ayant des événements ou une échéance dans la période.
3. Chaque unité s'exécute dans sa propre transaction : elle supprime les lignes non validées qu'elle avait produites pour cette période, relit ses événements dans l'ordre chronologique, applique les règles, écrit les lignes. Les unités tournent en parallèle.
4. Une unité en échec n'arrête pas les autres ; elle est relancée seule après correction.
5. Les traitements qui portent sur un distributeur entier viennent ensuite, distributeur par distributeur : surcommissions, puis ajustements approuvés, compensation entre comptes, report des soldes et proposition de paiement.
6. L'exécution est close avec ses compteurs : événements lus, lignes créées, événements en attente, anomalies.

**Paramétrage ou développement ?** La frontière découle du troisième choix structurant.

| Besoin | Nature du travail |
| --- | --- |
| Nouveau produit, nouveau taux, nouveau barème, nouveau palier | Paramétrage |
| Opération commerciale, accord dérogatoire pour un distributeur | Paramétrage |
| Nouvelle convention d'escompte, nouvelle politique de reprise parmi les trois formes | Paramétrage |
| Fichier d'un nouveau partenaire | Paramétrage (mapping) |
| Nouveau contrôle sur un seuil ou une variation | Paramétrage |
| Mode de calcul hors des familles existantes | Développement d'une famille : une classe, son formulaire, ses tests |
| Nouvel événement comptable, nouveau format d'échange | Développement |

**Limites.** Le monolithe en instance unique est un choix de simplicité : une panne touche toutes les entités à la fois. Le socle technique reste une hypothèse (H-30) ; aucune règle de cette spécification n'en dépend.

### 12.3 Moteur de calcul : fonctionnement et décisions

Cette section décrit comment le moteur exécute les règles de la section 6. Les sections 6 et 7 disent ce qui doit être calculé et dans quel ordre de dépendance ; celle-ci dit comment, et pourquoi. Chaque décision est suivie de sa motivation et de la solution écartée. Aucune ne vient des sources : ce sont des choix de conception, à réviser si un prototype les contredit.

**Vue d'ensemble.** Le moteur reçoit les événements d'une période gelée, les versions de règles approuvées et l'état validé à la fin de la période précédente. Il produit des lignes de commission, de nouvelles positions d'escompte, de nouveaux cumuls et la liste des événements mis en attente. Il ne produit ni écriture comptable, ni bordereau, ni ordre de paiement : ceux-ci naissent plus tard, à la validation métier, à partir des lignes. Le calcul prévisionnel (étape 3 de la section 7.2) passe par le même moteur, au temps 1, sur les primes émises.

| Temps | Unité de travail | Ce qui s'y calcule | Exécution |
| --- | --- | --- | --- |
| 0. Plafonds d'escompte | La convention d'escompte | Éligibilité des garanties nouvelles quand la convention a un plafond d'adhésions | Séquentielle par convention |
| 1. Garanties | La garantie | Commissions de base, commissions externes, escompte, apurement, reprises, annulations, partage entre bénéficiaires, échéances de la garantie | En parallèle |
| 2. Cumuls | Le titulaire du cumul (distributeur ou groupement) | RCD, RCP, complément retraite et leurs conditions | En parallèle, après le temps 1 |
| 3. Comptes | Le distributeur | Ajustements approuvés, compensation entre comptes, report des soldes, proposition de paiement | En parallèle, après le temps 2 |

**D1. Une famille est une classe, une règle est une donnée.** *Décision.* Chaque famille de calcul est une classe qui déclare le schéma de ses paramètres ; une version de règle est une ligne de `rule_version` dont les colonnes communes portent validité, priorité et groupe d'exclusion, et dont `parameters` porte le reste, validé contre ce schéma à la saisie comme à l'import. *Motivation.* La frontière entre paramétrage et développement reste nette (tableau de la section 12.2), un paramètre mal formé est refusé avant tout calcul, et le schéma sert aussi à construire le formulaire de la famille. Une mécanique nouvelle s'ajoute par une classe, sans toucher au cœur. *Écarté.* Un langage de formules saisi par le métier : il rend le calcul impossible à expliquer et à tester (choix structurant 3).

**D2. Une règle propose, le moteur applique.** *Décision.* Une famille ne crée aucune ligne et ne modifie aucun état. Elle reçoit l'événement, la version de règle et une vue en lecture de l'état, et rend une proposition : nature, assiette, taux, montant, explication, et les variations d'état qu'elle demande (ouvrir une position d'escompte, ajouter au cumul). Le moteur décide ensuite de ce qu'il en fait. *Motivation.* Quatre décisions se prennent après le calcul et ne relèvent pas de la règle : l'arbitrage entre règles concurrentes (P7), le partage entre bénéficiaires (section 6.9), le sort de la ligne, payable ou apurée (section 6.4), et la destination du résultat, réel ou simulation. Une famille sans effet de bord se teste aussi sans base de données. *Écarté.* Des règles qui écrivent leurs lignes : il faudrait défaire celles que l'arbitrage écarte et dupliquer le code pour la simulation.

**D3. Trois sortes de déclencheurs.** *Décision.* Une règle se déclenche sur un événement reçu (encaissement, annulation, résiliation, versement), sur une échéance (solde d'un escompte au 360e jour, cinquième anniversaire, rappel trimestriel, réajustement annuel) ou sur un cumul (RCD du mois, RCP et complément de l'année). *Motivation.* Les deux dernières sortes doivent s'exécuter même si aucun événement n'est arrivé : un escompte non apuré se solde au 360e jour sur une garantie silencieuse. *Écarté.* Un moteur purement événementiel, qui obligerait à fabriquer de faux événements d'horloge dans les flux.

**D4. L'unité du premier temps est la garantie, non le distributeur.** *Décision.* Tous les événements d'une même garantie sont traités ensemble, par une seule unité de travail, quel que soit le nombre de distributeurs qu'elle rémunère. Un événement sans garantie connue (commission externe globale) forme une unité à lui seul. *Motivation.* C'est la garantie qui porte l'état dont les règles ont besoin : ancienneté, position d'escompte, lignes à inverser (section 5.2). Le distributeur n'intervient qu'au partage. En co-courtage, ou quand une cession tombe en cours de période, une garantie rémunère deux distributeurs : découpée par distributeur, elle serait traitée deux fois et sa position d'escompte mise à jour deux fois, ou traitée une fois par un lot qui écrirait chez un autre. *Écarté.* Le lot par distributeur pour tout le calcul, plus simple et calqué sur le paiement, mais sans réponse pour la garantie partagée. Il reste l'unité des temps 2 et 3.

**D5. Ordre de traitement dans une garantie.** *Décision.* L'unité déroule, dans l'ordre : l'ouverture d'un escompte si la garantie y devient éligible ; puis ses événements triés par date d'effet, date de réception et clé source ; puis ses échéances. Une annulation est traitée à sa place chronologique, donc après l'événement qu'elle annule. *Motivation.* Le sort d'une commission dépend de ce que les précédentes ont laissé : le troisième encaissement n'est payé que si les deux premiers ont soldé l'avance. Un tri complet et stable rend le résultat identique d'une exécution à l'autre (NFR-20). *Écarté.* Le traitement par nature sur toute la période (toutes les annulations, puis toutes les commissions, puis tous les apurements) : il donne le même total mais peut solder un escompte au mauvais moment.

**D6. Enchaînement pour un événement.** *Décision.* Le moteur applique les six étapes de la section 6.1 en les répartissant ainsi : qualification, sélection des versions candidates, arbitrage par groupe d'exclusion et priorité, contrôle du taux ; appel de la famille de chaque version gardée, qui rend sa proposition ; partage entre bénéficiaires et arrondi avec reliquat au dernier (P13) ; décision du statut de chaque ligne ; intégration à l'état de travail de l'unité. *Motivation.* L'arbitrage précède le calcul pour ne pas calculer ce qui sera écarté ; le partage suit le calcul parce qu'il découpe un montant déjà arrondi ; le statut vient en dernier parce qu'il dépend de la position d'escompte du moment. *Écarté.* Calculer toutes les candidates puis choisir : coûteux, et trompeur dans l'explication stockée avec la ligne.

**D7. État de travail en mémoire, une transaction par unité.** *Décision.* Pendant son déroulé, l'unité tient en mémoire les lignes proposées et l'état modifié. La base n'est touchée qu'à la fin, en une transaction : lignes, positions, statut des événements. Deux décisions se succèdent donc : au fil des événements, le sort de chaque proposition ; à la fin, l'application du résultat de l'unité, qui est enregistré, envoyé en simulation ou abandonné. *Motivation.* Une garantie est entièrement calculée ou pas du tout ; une erreur n'en laisse aucune à moitié traitée, et l'unité fautive est signalée sans arrêter les autres. La simulation réutilise le même déroulé en changeant seulement la destination de l'écriture (choix structurant 6). *Écarté.* L'écriture au fil des événements, qui impose de savoir défaire en cas d'échec au milieu d'une garantie.

**D8. L'état de départ se reconstruit à partir des lignes validées.** *Décision.* Au début d'une unité, le cumul apuré d'un escompte est la somme de ses lignes `OFFSET` des périodes validées, et le cumul de production d'un titulaire est celui de la dernière période validée. Les colonnes de cumul de `advance_position` et de `production_counter` sont une matérialisation, réécrite à chaque calcul. *Motivation.* Un calcul peut être relancé dix fois avant la validation (NFR-19) et la Finance peut refuser un lot après validation (section 7.1) : un cumul mis à jour en place serait alors compté plusieurs fois. Repartir de ce qui est validé rend la relance sûre sans mécanisme d'annulation. *Écarté.* Le cumul incrémenté en place, plus rapide mais faux dès le deuxième lancement.

**D9. Le plafond d'une convention se décide avant les garanties.** *Décision.* Quand une convention plafonne le nombre d'adhésions escomptées, les garanties candidates de la période sont classées par date d'enregistrement et admises jusqu'au plafond, dans un temps 0 séquentiel par convention. Le temps 1 lit cette décision. *Motivation.* C'est la seule condition d'éligibilité (section 6.4, condition 7) qui dépend d'autres garanties : deux unités en parallèle pourraient chacune se croire la centième. *Écarté.* Un verrou sur la convention pendant le temps 1, qui sérialiserait toutes les garanties d'un gros distributeur.

**D10. Les cumuls viennent après les garanties, les comptes après les cumuls.** *Décision.* Le temps 2 calcule les règles sur cumul par titulaire d'inscription, distributeur ou groupement. Le temps 3 traite les comptes par distributeur. Aucun ne démarre avant la fin du précédent pour l'entité. *Motivation.* La RCD de février lit la production que les garanties viennent de produire et ce qui a déjà été versé en janvier ; la compensation et le report lisent toutes les lignes du distributeur, RCD comprise. Le solde d'un compte étant la somme de ses lignes (section 10.5), le temps 1 peut écrire des lignes sur plusieurs comptes sans conflit. *Écarté.* Mettre à jour les cumuls au fil des garanties, ce qui créerait un point de contention par distributeur.

**D11. Versions figées et paramétrage chargé une fois.** *Décision.* Au lancement, le moteur charge les versions de règles applicables à la période, en calcule l'empreinte et l'enregistre avec l'exécution. Chaque ligne garde l'identifiant de la version qui l'a produite. *Motivation.* Les versions approuvées ne changent plus (section 5.7) : rejouer une période close redonne les mêmes lignes au centime (CAL-11, cas CR-69). Charger une fois évite aussi une lecture en base par événement. *Écarté.* Relire la règle à chaque événement, lent et exposé à une approbation survenue en cours de calcul.

**D12. Chaque famille sait se défaire.** *Décision.* Pour une annulation, le moteur retrouve les lignes nées de l'événement annulé et demande à la famille d'origine la proposition inverse, calculée avec la version d'origine et non avec la version en vigueur. *Motivation.* Une ligne inverse doit être l'exact opposé de sa ligne d'origine, même si le barème a changé entre-temps (section 10.1) ; elle doit aussi défaire les variations d'état, par exemple diminuer le cumul apuré (section 6.4). *Écarté.* Recalculer l'annulation comme un événement négatif ordinaire : l'écart de taux resterait au compte du distributeur.

**D13. Relance et recalcul partiel.** *Décision.* Une unité relancée supprime d'abord les lignes non validées qu'elle avait produites pour la période, puis recalcule. Un recalcul partiel (CAL-12) désigne un ensemble de garanties par distributeur, produit ou règle, puis rejoue les temps 2 et 3 pour les distributeurs touchés. *Motivation.* Le même mécanisme sert à la reprise après échec, à la correction d'un paramétrage et au retour arrière sur refus de la Finance. *Écarté.* Un recalcul différentiel qui ne retoucherait que les lignes modifiées : plus rapide, mais il introduit un second chemin de calcul à maintenir et à prouver.

**D14. L'explication fait partie de la proposition.** *Décision.* La famille rend, avec le montant, les valeurs qu'elle a lues et les conditions qu'elle a évaluées ; le moteur y ajoute l'arbitrage (règles écartées et pourquoi) et le partage. Le tout est stocké avec la ligne. *Motivation.* L'explication affichée à l'écran (CAL-10) est alors ce que le calcul a réellement fait, et non une reconstitution après coup qui pourrait diverger. *Écarté.* Reconstituer l'explication à la demande en rejouant la règle.

**D15. Parallélisme.** *Décision.* Les unités d'un même temps sont indépendantes et s'exécutent en parallèle. Elles peuvent être validées par paquets ; un paquet en échec est rejoué unité par unité pour isoler la fautive. *Motivation.* 420 000 événements en moins de quatre heures (NFR-04, NFR-05) demandent une trentaine d'événements par seconde : le découpage par garantie donne des unités petites et nombreuses, faciles à répartir. *Écarté.* Un traitement séquentiel de la période, plus simple mais qui ne laisse pas le temps de relancer un calcul complet dans la journée.

**Exemple déroulé : une garantie escomptée.** Escompte de 480 €, dont 400 € déjà apurés dans les périodes validées. Trois encaissements arrivent dans la période, chacun donnant 40 € de commission (30 d'apport, 10 de suivi).

| Étape | Ce que fait le moteur | État de travail après l'étape |
| --- | --- | --- |
| Départ | Reconstruit le cumul apuré : 400 € (D8) | Reste à apurer : 80 € |
| 1er encaissement | La famille propose 30 € et 10 €. Il reste de l'avance : les deux lignes sont apurées | Reste à apurer : 40 € |
| 2e encaissement | Même proposition, même décision. L'écart atteint zéro : l'escompte est soldé | Reste à apurer : 0 € ; escompte `SETTLED` |
| 3e encaissement | Même proposition. L'escompte est soldé : les deux lignes sont acquises et seront payables | Deux lignes `EARNED` de 30 € et 10 € |
| Fin de l'unité | Écrit en une transaction six lignes et la position d'escompte (D7) | — |

La famille a rendu trois fois la même proposition ; c'est le moteur qui a décidé trois fois différemment, à partir de l'état laissé par l'étape précédente. En simulation, la dernière étape écrit dans l'espace séparé et rien d'autre ne change.

**Limites.** Ces décisions n'ont été éprouvées par aucun prototype. Le débit du temps 1 dépend du coût de reconstruction de l'état de départ (D8), à mesurer sur un jeu réel avant de figer la taille des paquets (D15). Le temps 0 suppose que le plafond se juge sur la date d'enregistrement, ce que les sources ne disent pas. La règle de RCD répartit son montant entre les adhésions du mois (section 6.7) : cette répartition se fait au temps 2, et la reprise de RCD d'une garantie résiliée, au temps 1, lit donc des répartitions de périodes antérieures seulement.

## 13. Cas de recette de référence

Ces cas sont les tests d'acceptation du moteur ; chacun doit être automatisé et rejoué à chaque livraison. Les chiffres viennent des exemples des sources ; quand la source ne donne pas d'exemple, le cas applique la formule de la section 6.

**Comment lire un cas.** Chaque ligne se lit en trois temps : une situation de départ (les données), un événement ou un traitement (le cas), et ce que l'application doit avoir produit (le résultat attendu). Un cas est réussi quand les lignes créées ont exactement la nature, le montant au centime et le statut annoncés, et qu'aucune autre ligne n'est apparue.

**Exemple déroulé : le cas CR-04.** Garantie en 30 + 10 à effet du 1er janvier. Six encaissements mensuels de 100 € sont injectés : le test vérifie douze lignes, six d'apport à 30,00 € et six de suivi à 10,00 €, soit 180 € et 60 €. Une résiliation à effet du 180e jour est injectée : le test vérifie une seule ligne nouvelle, une reprise de −180,00 € au même bénéficiaire, et vérifie que les douze lignes d'origine n'ont pas changé. Le distributeur conserve 60 €.

**À quoi servent ces cas.** Ils sont la définition vérifiable de « le moteur calcule juste ». Ils servent trois fois : au développeur, qui les automatise avant d'écrire une famille de calcul ; à la recette métier, qui les rejoue à l'écran ; à la maintenance, où leur exécution à chaque livraison garantit qu'une évolution n'a rien cassé. Les sections 13.1 à 13.5 les rangent par thème ; la section 13.6 ajoute ceux des fonctions restantes.

**Limites.** Ces cas couvrent les règles, pas les volumes ni les données réelles : seul le cas CR-38 rejoue un fichier véritable. Ils ne remplacent pas la marche en double décrite en section 14. Les cas qui reposent sur une hypothèse de la section 15 (par exemple CR-41 sur la lecture du barème RCD) changent de résultat attendu si Alptis tranche autrement.

### 13.1 Commissions de base et reprises

| N° | Cas | Données | Résultat attendu |
| --- | --- | --- | --- |
| CR-01 | Linéaire | Santé Select, linéaire 15 %, encaissement de 100 € hors taxes | Une ligne `LINEAR` de 15,00 € |
| CR-02 | Précompté, année 1 | Apport 30 %, suivi 10 %, prime annuelle de 1 000 € encaissée en une fois | `ACQUISITION` 300,00 € et `SERVICING` 100,00 € |
| CR-03 | Précompté, année 2 | Même garantie, prime de la 2e année | `SERVICING` 100,00 €, aucune ligne d'apport |
| CR-04 | Reprise en 1re année | Apport 30 %, suivi 10 %, 6 encaissements mensuels de 100 €, résiliation au 180e jour | Acquis : apport 180,00 €, suivi 60,00 €. Reprise : −180,00 € |
| CR-05 | Reprise en 2e année | Exemple 2 du Guide : apport acquis 300 €, résiliation au 18e mois | Reprise : −150,00 € |
| CR-06 | Pas de reprise après 2 ans | Résiliation à effet 731 jours après la date d'effet de la garantie | Aucune ligne de reprise |
| CR-07 | Sans suite | Adhésion classée sans suite après 2 encaissements commissionnés | Reprise de 100 % de toutes les lignes acquises |
| CR-08 | Décès | Décès de l'adhérent au 5e mois, mode précompté | Aucune reprise |
| CR-09 | Annulation d'encaissement | L'encaissement du cas CR-01 est annulé | Ligne inverse de −15,00 €, même règle, même bénéficiaire |
| CR-10 | Changement de taux | Luminéis, linéaire 15 % ; nouvelle version du barème à 12 % applicable au 1er du mois M+3 ; l'outil de gestion passe les garanties à 12 % à la même date | Les encaissements antérieurs restent à 15 % ; les suivants sont à 12 % ; aucune ligne recalculée |
| CR-11 | Taux hors grille | Garantie Santé Pro+ au taux linéaire de 12 % | Événement `ON_HOLD`, anomalie « taux hors grille » |
| CR-12 | Collèges | Santé collective, 7 % cadres et 5 % non-cadres, 1 000 € encaissés sur chaque collège | 70,00 € et 50,00 € |

### 13.2 Escompte

| N° | Cas | Données | Résultat attendu |
| --- | --- | --- | --- |
| CR-20 | Versement | Convention valide, paiement à la date d'effet, prime annualisée 1 000 €, apport 30 %, suivi 10 % | `ADVANCE` 300,00 € et 100,00 € dans la période du mois d'effet |
| CR-21 | Apurement | Encaissements des mois suivants | Lignes acquises au statut `OFFSET` ; aucun paiement ; créance diminuée d'autant |
| CR-22 | Résiliation à 6 mois | 200 € apurés (150 d'apport, 50 de suivi) | Escompte `CANCELLED` ; régularisation −200,00 € ; reprise d'apport −150,00 € ; le distributeur conserve 50,00 € |
| CR-23 | Solde par apurement | 400 € apurés au 10e mois | Escompte `SETTLED` à ce moment, aucune régularisation ; les commissions suivantes sont payées |
| CR-24 | Solde avec complément d'escompte | 380 € apurés, puis une ligne acquise de 50 € après une hausse de cotisation | Escompte `SETTLED` ; régularisation +30,00 €, payable |
| CR-25 | Hors convention | Adhésion enregistrée le 21 décembre, effet au 1er février, convention finissant le 31 décembre, paiement à la date d'effet | Pas d'escompte |
| CR-26 | Second impayé | Impayé de rang 2, convention au seuil 2 | Escompte `CANCELLED`, régularisation immédiate |
| CR-27 | Plafond | 101e adhésion d'une convention plafonnée à 100 | Pas d'escompte ; commissions payées sur encaissement |
| CR-28 | Déficit à 360 jours | 370 € apurés au 360e jour, contrat toujours en vigueur | Escompte `SETTLED` ; reprise d'escompte indu de −30,00 € |

### 13.3 Épargne retraite

| N° | Cas | Données | Résultat attendu |
| --- | --- | --- | --- |
| CR-30 | Versement initial | 6 000 €, frais de 2,5 % | 120,00 € |
| CR-31 | Versement programmé, linéaire | 300 € par mois, frais de 3,5 % | 9,00 € par versement |
| CR-32 | Versement programmé, escompté | 300 € par mois, souscripteur de moins de 55 ans | 288,00 € en année 1 ; rien des années 2 à 5. Variante à 100 € par mois et 1,95 % de frais, plan suspendu après 25 versements : escompte de 96,00 €, rappel de −59,75 € |
| CR-33 | Rappel | Commission reçue de l'assureur : escompte de 120 €, 25 versements de 100 € à 1,95 %, suspension du plan | Rappel de −71,25 € ; contrat repassé en linéaire |
| CR-34 | Rappel | Commission reçue de l'assureur : escompte de 240 €, 21 versements de 200 € à 1,95 % | Rappel de −158,10 € |
| CR-35 | Complément retraite | Production annualisée inférieure à 10 000 € ; contrats à 292,50 € et 294,15 € nets par mois | 351,00 € et 352,98 €, soit 703,98 € |
| CR-36 | Sur-commission assureur | Versements programmés de 300 € par mois sur 12 mois | 1 008,00 € reçus par l'entité ; plafond de 50 000 € par contrat |
| CR-37 | Part de l'entité | Ligne UMR de frais de gestion : base 0,43 €, part Alptis 0,24 €, part UMR 0,19 € | Seule la ligne de 0,24 € est intégrée |
| CR-38 | Fichier réel | Fichier de production UMR de juillet 2026 | Commissions reçues par l'entité égales au « montant de commission dû » du bordereau UMR : 451,54 € (449,08 sur versements, 2,46 sur frais de gestion). Même contrôle sur juin 2026 : 95,19 € |

### 13.4 Surcommissions et partages

| N° | Cas | Données | Résultat attendu |
| --- | --- | --- | --- |
| CR-40 | RCD santé, sous le premier palier | Production cumulée de 15 000 € | 0 € |
| CR-41 | RCD santé, deux paliers | Cumul de 30 000 € fin janvier, 60 000 € fin février | Janvier : 300,00 €. Février : 1 350,00 − 300,00 = 1 050,00 € |
| CR-42 | RCD santé, palier au premier euro | Cumul de 120 000 € | Droit cumulé de 6 000,00 € |
| CR-43 | RCD, condition non remplie | Taux d'impayés de 6 % en mars | Ligne de mars à 0 €, motif enregistré |
| CR-44 | RCD sans avenant signé | Inscription présente, accord non signé | Aucune ligne ; anomalie de contrôle |
| CR-45 | Reprise de RCD | Adhésion portant 40 € de RCD, résiliée au 8e mois, puis variante au 15e mois | −40,00 € ; variante −20,00 € |
| CR-46 | RCP santé | 200 000 € de primes encaissées, conditions remplies | 8 000,00 € |
| CR-47 | RCP santé, condition non remplie | P/C de 96 % | 0 €, détail des conditions |
| CR-48 | RCP prévoyance | 12 adhésions nouvelles, 50 000 € encaissés | 2 %, soit 1 000,00 € |
| CR-49 | Partage avec un groupement | Commission de 30 €, parts 90 % et 10 % | 27,00 € au distributeur, 3,00 € au groupement |
| CR-50 | Rétrocession à un mandataire | Commission reçue de 100 €, taux de 60 % | 60,00 € |
| CR-51 | Commission externe | Ligne reçue : assiette 500 €, taux 3,5 %, montant 17,50 € | Ligne `EXTERNAL` de 17,50 €, sans recalcul |

### 13.5 Comptes, paiement, comptabilité, robustesse

| N° | Cas | Données | Résultat attendu |
| --- | --- | --- | --- |
| CR-60 | Compte débiteur | Exemple de la section 10.5, le solde de départ étant injecté | Pas de paiement en janvier ni en février ; 300,00 € en mars ; solde reporté en une ligne |
| CR-61 | Compensation | Distributeur avec un code à −200 € et un code à +500 € | Deux lignes `TRANSFER` ; paiement de 300,00 € sur le code créditeur |
| CR-62 | Distributeur suspendu | Radiation ORIAS le 24 février, réinscription le 24 mars | Commissions calculées, paiement de début mars bloqué, libéré par un lot hors cycle après le 24 mars |
| CR-63 | Seuil de paiement | Solde positif inférieur au seuil | Pas d'ordre ; solde conservé |
| CR-64 | Alignement | Tout lot | Pour chaque ordre, un bordereau de même montant (ou de même sous-total d'entité, variante rejouée au lot 8) ; total du lot égal à la somme des lignes rattachées à ses ordres, soldes antérieurs reportés compris |
| CR-65 | Cycle comptable | Exemple de la section 10.2 | Écritures de 180, puis 15 par encaissement, équilibrées |
| CR-66 | Résiliation, vision comptable | Résiliation au 6e mois | Régularisation du prévisionnel de −90 € |
| CR-67 | Données erronées | Fichier contenant une prime nulle, une ligne sans numéro de contrat, une prime datée dans le futur, un distributeur inconnu | Les trois premières lignes sont rejetées avec leur motif ; la quatrième est intégrée en attente ; le reste du fichier est intégré |
| CR-68 | Réimport | Même fichier déposé deux fois | Second dépôt refusé ; aucun événement en double |
| CR-69 | Reproductibilité | Simulation d'une période close avec son paramétrage | Lignes identiques, au centime |
| CR-70 | Cloisonnement | Utilisateur UNEP | Aucune donnée d'Alptis Assurances visible, par écran, export ou API |
| CR-71 | Séparation des tâches | Un administrateur approuve sa propre version de règle | Refus |
| CR-72 | Performance | Jeu de 20 000 bénéficiaires et 420 000 événements | Calcul complet en moins de 4 heures |

### 13.6 Cas complémentaires

Ces cas couvrent les fonctions que les tableaux précédents ne testaient pas. Chacun reprend un exemple chiffré du document ou, à défaut, applique la règle citée.

| N° | Cas | Données | Résultat attendu |
| --- | --- | --- | --- |
| CR-73 | Prorata à la frontière de la première année | Exemple de la section 6.2 : effet au 1er mars 2026, couple 30 + 10, prime trimestrielle de 300 € couvrant du 1er février au 30 avril 2027 | Apport 28,31 € ; suivi 30,00 € |
| CR-74 | Changement de taux en cours de période (P12) | Garantie passée de 15 % à 18 % le 16 juin ; prime de juin de 100 € | Deux lignes : 7,50 € et 9,00 € |
| CR-75 | Groupes d'exclusion et priorités | Exemple de la section 6.11 : trois règles sur un encaissement de 100 € | Courtier X : 18,00 € et 2,00 €, la règle de priorité 10 du groupe `BASE` étant écartée. Autre courtier : 15,00 € et 2,00 € |
| CR-76 | Co-courtage et groupement | Commission de 100 € ; courtier A 60 %, courtier B 40 % ; A versé à 90 % et 10 % à son groupement | 54,00 €, 6,00 € et 40,00 €. Reprise ultérieure de 100 € : −54,00 €, −6,00 € et −40,00 € |
| CR-77 | Frais de dossier | 300 € de frais saisis à la souscription et encaissés (exemple de la section 6.5, transposé à un produit du lot) | Ligne `FEE` de 300,00 €, sans taux ni politique de reprise |
| CR-78 | Tolérance de solde | Escompte de 400,00 € ; 399,96 € apurés au 360e jour ; tolérance de 1 € | Escompte `SETTLED` sans ligne de régularisation ; écriture `ADVANCE_WRITE_OFF` de 0,04 € |
| CR-79 | Délai d'anticipation | Garantie à effet du 1er juin 2026, délai de 120 jours | Escompte calculé dans la période de février 2026 |
| CR-80 | Cession avec escompte en cours | Escompte de 480 €, 200 € apurés, garantie cédée | Créance transférée : escompte toujours `ACTIVE`, apurements suivants sur les commissions du repreneur. Variante, créance remboursée par le cédant : escompte `CANCELLED`, régularisation de −280,00 € au compte du cédant |
| CR-81 | Rachat anticipé d'un prêt | Exemple de la section 6.5 : remboursement au 24e mois, 500 € perçus, recalcul linéaire de 300 € | Ligne `CLAWBACK` de −200,00 €. Variante au 40e mois : aucune ligne |
| CR-82 | Commission sur encours | Encours moyen de 20 000 € en fonds en euros, gestion libre, mode linéaire, fichier trimestriel | Ligne `OUTSTANDING` de 15,00 € |
| CR-83 | Arbitrage | Deux arbitrages de 10 000 € dans la même année civile | Rien sur le premier ; ligne `ARBITRAGE` de 30,00 € sur le second |
| CR-84 | Réajustement annuel | Base annualisée de 3 600 €, escompte à 8 % ; 4 200 € encaissés sur 12 mois, puis variante à 3 000 € | Supplément d'escompte de 48,00 € (ligne `ADVANCE`) ; variante : reprise de −48,00 € |
| CR-85 | Rejet bancaire | Ordre de 110,00 € rejeté dans le fichier de résultat des virements | Lignes revenues à `PAYABLE` ; écriture `PAYMENT_REJECTION` de 110,00 € ; montant représenté au lot suivant |
| CR-86 | Lot de l'épargne retraite | Période portant des commissions d'épargne retraite | Lot distinct daté du 20 au 25, bordereau propre ; la période ne passe `PAID` qu'une fois tous ses lots exécutés |
| CR-87 | Variable, pondération | 10 000 € de production sur les particuliers, 5 000 € sur les professionnels | 13 500 € retenus face à l'objectif |
| CR-88 | Variable, absence | Objectif mensuel atteint, 10 jours ouvrés d'absence dans le mois | Mensualité non versée |

## 14. Lotissement

La construction suit le plan de déploiement de septembre 2026 : un socle commun livré avec le périmètre le plus simple, puis un périmètre nouveau par trimestre à partir de janvier 2027. Chaque lot livre des fonctions du moteur et met en service un périmètre métier.

| Lot | Périmètre mis en service | Fonctions livrées | Prérequis et sortie |
| --- | --- | --- | --- |
| 0. Socle | Aucun | SSO, entités, rôles, audit, référentiels, format pivot, mappings, rejets, périodes | Environnements disponibles |
| 1. Tronc commun et PER individuel UMR | Commissions PER d'Alptis Assurances | Familles linéaire, externe, quote-part, encours, barème sur cumul ; escompte et rappel retraite ; commissions reçues et marge ; règles de versement ; compte distributeur ; ajustements ; contrôles essentiels et cycle de validation ; bordereau ; lot SEPA et liste de chèques ; extraction comptable ; restitution vers Alptis Connect ; export Data ; premiers tableaux de bord ; calcul prévisionnel ; simulation ; mécanisme de reprise d'historique, réutilisé à chaque lot | SI Finance en production et stable. Recette : cas CR-01 et CR-09 transposés à un produit du lot, CR-30 à CR-38, CR-49 à CR-51, CR-60, CR-63 à CR-69, CR-71, CR-72 et CR-82 à CR-86 |
| 2. IARD Pro | Lancement de l'IARD professionnel | Mappings assureurs supplémentaires ; montants fixes et frais | Fichiers des assureurs spécifiés. Recette : cas CR-77 |
| 3. Santé et prévoyance collectives | Flux CLEVA | Famille précomptée ; reprises ; collèges ; co-courtage ; opérations temporaires ; catalogue de contrôles complet ; compensation entre comptes ; suspension ORIAS et paiement hors cycle ; attestations fiscales ; réclamations | Flux CLEVA vers l'application sécurisés. Recette : cas CR-01 à CR-12 (transposés aux produits du lot), CR-61, CR-62 et CR-73 à CR-76. Sortie : arrêt du module commissions de CLEVA |
| 4. UNEP | Seconde entité | Cloisonnement multi-entités en conditions réelles ; règles d'assurance vie ; flux Kelia ; restitution vers K4U | Recette métier UNEP, cas CR-70 |
| 5. Santé et prévoyance individuelles | Flux RSI, puis CLEVA lot 2 | Escompte complet ; reprise de l'historique RSI ; cessions et ordres de remplacement | Calé sur le lot 2 de CLEVA (2028). Recette : cas CR-20 à CR-28 et CR-78 à CR-80. Sortie : arrêt des commissions de base dans RSI ; les surcommissions y restent jusqu'au lot 6 |
| 6. Surcommissions | RCD, RCP | Famille portefeuille sous conditions ; conditions de qualité ; inscriptions et accords ; répartition par adhésion et reprise ; génération des avenants | Indicateurs de qualité livrés par Sésame. Recette : cas CR-40 à CR-48. Sortie : décommissionnement d'Incentive |
| 7. Insured Services et Promantis Conseil | Autres entités | Mappings et règles propres ; paramétrage de la rétrocession Promantis (mécanique livrée au lot 1) | — |
| 8. Bordereau unique et emprunteur | Tous distributeurs ; reprise de la gestion déléguée de l'emprunteur | Bordereau multi-entités ; règles emprunteur ; rachat anticipé (jusque-là, les commissions emprunteur calculées par le gestionnaire délégué entrent par la famille externe ; leur lot de mise en service reste à fixer avec Alptis) | Étude du bordereau unique prévue en 2027. Recette : cas CR-81 et variante du cas CR-64 |
| 9. Forces de vente | Variable des commerciaux | Module de la section 8.14 | Non prioritaire pour le groupe. Recette : cas CR-87 et CR-88 |

Principes repris du plan : commencer par un périmètre prudent, à faible montant et peu de distributeurs ; déployer le collectif avant l'individuel pour éprouver les flux CLEVA ; traiter l'UNEP en premier parmi les entités, son risque opérationnel étant le plus élevé ; réviser le plan 2028 au deuxième trimestre 2027.

**Comment se passe la bascule d'un périmètre.** Mettre un lot en service, c'est faire passer un périmètre de l'ancien calcul au nouveau sans qu'aucun distributeur ne voie de rupture. La séquence est la même à chaque lot :

1. **Paramétrage** des règles du périmètre et passage des cas de recette correspondants.
2. **Reprise de l'historique** (exigence ING-11) : lignes acquises des 24 derniers mois, escomptes en cours, soldes des comptes, cumuls annuels ; pour l'épargne retraite, cinq ans de versements programmés encaissés et les positions d'escompte en cours, durée des rappels. Sans elle, ni reprise, ni apurement, ni RCD ne peuvent être calculés juste dès le premier mois.
3. **Marche en double** : pendant deux périodes par défaut, l'ancien système calcule et paie, la nouvelle application calcule à blanc sur les mêmes données. Les totaux sont comparés par distributeur et par nature ; chaque écart est expliqué : erreur de paramétrage, donnée manquante, ou anomalie de l'ancien système (hypothèse H-44).
4. **Bascule** : à la période suivante, la nouvelle application paie ; l'ancien calcul est arrêté sur ce périmètre.

**Exemple de reprise.** Un courtier arrive avec un solde débiteur de 350 € et un escompte en cours de 480 € versé, 200 € apurés. À la bascule, son compte s'ouvre à −350 € et la position d'escompte à 480 versés, 200 apurés. Le mois suivant, sa commission de 40 € sur cette garantie s'impute sur l'avance (240 apurés), et ses autres commissions comblent d'abord les 350 €.

**Limites.** Pendant la transition, un même distributeur peut être payé par deux systèmes selon ses produits, et recevoir deux bordereaux ; la compensation entre ses comptes ne joue qu'à l'intérieur de la nouvelle application. Le plan dépend de projets que cette spécification ne maîtrise pas : SI Finance, lot 2 de CLEVA, flux Sésame. La qualité de l'historique RSI à reprendre n'a pas été examinée.

## 15. Hypothèses, incohérences et points ouverts

Les sources sont des documents d'étude, souvent en brouillon ; 39 points y sont contradictoires, ambigus ou absents (H-01 à H-39), et 18 comportements, formats ou choix nécessaires au fonctionnement n'y sont pas abordés du tout (H-40 à H-57). Pour chacun, cette spécification retient un choix explicite, toujours paramétrable quand c'est possible, afin que la construction ne soit pas bloquée. Aucun de ces choix n'a été validé par Alptis.

**Comment utiliser cette section.** Chaque ligne est une question à poser à Alptis. La colonne « Choix retenu » dit ce que l'application fera tant que personne n'a répondu. La plupart des choix sont des valeurs de paramètres : une réponse différente se règle sans développement. Cinq d'entre eux engagent la structure ou les montants et sont à trancher avant la construction du lot concerné :

| Hypothèse | Pourquoi elle pèse |
| --- | --- |
| H-09, lecture du barème RCD | Le montant versé varie de 1 350 à 2 400 € pour la même production |
| H-12, assiette de la RCP | Tout le portefeuille ou les seules affaires nouvelles : le montant change d'un ordre de grandeur |
| H-13, deux flux PER | Fonde le calcul de la marge et le modèle à deux sens |
| H-22, schéma comptable | Tout le module comptable en dépend |
| H-37, production du format pivot | Décide qui porte le travail d'extraction, et donc le planning |

### 15.1 Règles métier

| N° | Sujet | Constat dans les sources | Choix retenu |
| --- | --- | --- | --- |
| H-01 | Vocabulaire | « Précompte » désigne le mode apport et suivi dans le Guide, l'avance dans RSI (« convention de précompte ») et l'avance sur versements programmés chez UMR | Un seul sens par terme (section 4) : précompté = apport et suivi ; escompte = avance |
| H-02 | Exemple 1 du Guide | Résiliation « au bout de 12 mois » avec un apport payé de 150 €, alors que 30 % de 1 000 € font 300 € ; l'exemple 2 donne bien 300 € | La reprise s'applique à l'apport réellement acquis ; l'exemple 1 n'est pas repris en recette |
| H-03 | Borne de la 1re année | Le Guide dit « 365 jours suivant la date d'effet » ; les cas d'usage évoquent aussi un prorata n/12 ou n/24 | Seuils en jours par défaut ; prorata disponible comme politique alternative |
| H-04 | Première année du précompté | Le Guide parle d'« année N » ; RSI calcule les taux par exercice civil | 365 jours glissants depuis la date d'effet de la garantie |
| H-05 | Reprise sur le linéaire | Note interne : la reprise s'appliquerait à tout type de commission, « à confirmer » | Aucune reprise sur le linéaire, sauf sans suite ; modifiable par la politique de reprise |
| H-06 | Délai de solde de l'escompte | 360 jours dans RSI ; non écrit dans le Guide | 360 jours, paramètre de l'entité |
| H-07 | Rang d'impayé déclenchant la reprise d'escompte | 2e impayé dans le support de formation ; 2e ou 3e selon une annexe du distributeur dans RSI | Paramètre de la convention, 2 par défaut |
| H-08 | Délais d'anticipation de l'escompte | 4 et 6 mois dans le support de formation ; 4, 6, 8, 9, 10 et 12 mois dans RSI | Nombre de jours libre sur la convention |
| H-09 | Lecture du barème RCD | L'avenant rémunère chaque adhésion « au taux du palier dans lequel elle se trouve » ; l'expression de besoin de 2025 applique le premier taux dès le premier euro une fois le palier atteint | Lecture de l'avenant : rien sous le premier seuil. Avec 60 000 €, 1 350 € et non 1 950 €. Basculable par un indicateur du barème |
| H-10 | Condition mensuelle de la RCD | L'audit interne relève que la période de mesure du taux n'est pas précisée ; l'avenant dit « aucun versement au titre du mois concerné » | Mesure à la date du calcul ; le mois est perdu (`FORFEIT`), rattrapage possible par paramètre |
| H-11 | Reprise de la RCD | L'avenant RCD parle de reprise de « la commission d'apport » | Compris comme la reprise de la part de RCD de l'adhésion |
| H-12 | Assiette de la RCP | L'avenant la réserve aux « affaires nouvelles » ; le support de formation et l'expression de besoin parlent des primes encaissées de tout le portefeuille | Tout le portefeuille éligible ; restriction aux adhésions de l'année disponible par paramètre |
| H-13 | Taux PER | 8 % et 1,20 % dans le Guide ; 10 % et 1,5 % dans le protocole UMR | Deux flux distincts : le Guide régit la commission versée, le protocole la commission reçue |
| H-14 | Protocole UMR, article 4 | Le texte des cas de rappel comporte des blancs | Règle de rappel reprise du Guide et du modèle de fichier UMR |
| H-15 | Réajustement annuel PER | « Significativement différent », sans seuil | Seuil de tolérance paramétrable, 0 % par défaut |
| H-16 | Rappel du complément retraite | Montant du rappel non précisé | Totalité du complément versé au titre du contrat |
| H-17 | Arbitrages | « À partir du 2e arbitrage », sans période | 2e arbitrage de l'année civile ; assiette = montant arbitré |
| H-18 | Rachat anticipé emprunteur | Recalcul « au taux linéaire choisi initialement », pour un contrat précompté | Taux linéaire du même type de tarif |
| H-19 | Hiérarchie des distributeurs | L'annexe qui la décrit est vide | Trois niveaux : groupement, distributeur, code de production |
| H-20 | Cession de portefeuille | Les reprises postérieures à une cession ne sont pas traitées | Débitées au bénéficiaire qui a perçu la commission |
| H-21 | Variable des commerciaux | Mensualités 2026 de 146 € et 458 €, alors que 70 % de l'enveloppe sur 12 mois donnent 143 € et 449 € ; plusieurs montants masqués | Mensualité saisie par poste, non déduite |

### 15.2 Comptabilité et paiement

| N° | Sujet | Constat dans les sources | Choix retenu |
| --- | --- | --- | --- |
| H-22 | Plan de comptes | Aucun compte, journal ni axe n'est fourni ; les libellés d'écriture sont approximatifs | Comptes désignés par rôle ; correspondance paramétrée par entité ; schéma de la section 10.2 à valider par la DAF |
| H-23 | État de cut-off | L'exemple fourni a 5 colonnes et 4 valeurs ; le « solde à payer » n'est pas défini | Définitions de la section 10.4, avec deux soldes distincts |
| H-24 | Escompte et surcommissions en comptabilité | Non décrits | Escompte porté en avance, sauf l'escompte retraite, en charge dès son versement ; surcommission en charge à la validation métier |
| H-25 | Outil comptable | Sage FRP 1000, Sage 3000 ou Cegid selon les documents | Extraction CSV neutre |
| H-26 | Seuil minimal de paiement | Il existe dans RSI ; sa valeur n'est pas donnée | Paramètre de l'entité |
| H-27 | Chèques | Prévus par le Guide en l'absence de coordonnées bancaires | Liste de chèques conservée ; à supprimer si Alptis impose le virement |
| H-28 | TVA et fiscalité | Seul le mandat Promantis dit la rémunération exonérée de TVA ; le contenu de l'attestation fiscale n'est pas décrit | Aucune TVA calculée ; attestation = récapitulatif annuel des sommes versées |
| H-29 | Nombre de paiements pour un distributeur commun | Question posée et laissée ouverte | Un ordre par entité débitrice ; bordereau unique possible |

### 15.3 Système d'information et projet

| N° | Sujet | Constat dans les sources | Choix retenu |
| --- | --- | --- | --- |
| H-30 | Socle technique | Aucune contrainte de technologie pour un développement interne | Java, Quarkus, PostgreSQL, à confirmer |
| H-31 | Référentiel distributeurs groupe | Il n'existe pas ; les CRM diffèrent par entité | Enregistrement unique par distributeur, statut par entité, codes de chaque CRM en table de correspondance ; distributeur commun déclaré par l'administrateur dans la table de correspondance des codes |
| H-32 | Éditique et GED | Solution interne ou intégrée non tranchée ; GED non désignée | PDF produits par l'application et déposés avec leurs métadonnées ; export vers une éditique externe en option |
| H-33 | Volumes | 7 000, 8 000 ou 9 600 distributeurs rémunérés ; 8 ou 10 M€ escomptés ; 3 ou 4 M€ repris, selon les documents | Dimensionnement sur les valeurs hautes |

Deux comportements de RSI ne sont volontairement pas repris : le garde-fou des 15 jours sur le calcul des taux, dont le sens diffère entre les schémas, et le coefficient correcteur entre assiettes TTC et hors taxes, devenu sans objet.

### 15.4 Autres points

| N° | Sujet | Constat dans les sources | Choix retenu |
| --- | --- | --- | --- |
| H-34 | Impayé en première année | Le support de formation reprend « 100 % de l'apport et le suivi au prorata » sur résiliation ou impayé ; le Guide ne cite que la résiliation et la renonciation | Un impayé annule la commission de la prime impayée et rompt l'escompte à partir du rang fixé par la convention (H-07) ; la reprise d'apport attend la résiliation |
| H-35 | Assiette de la sur-commission UMR | Le protocole cite à la fois les 12 mois suivant le premier encaissement et les versements de l'année de référence | Les 12 mois suivant le premier encaissement ; calcul provisoire au 31 décembre, complété l'année suivante |
| H-36 | Durée de conservation | « Selon la réglementation », sans durée | 10 ans |
| H-37 | Production du format pivot | Rien ne dit qui extrait les données de CLEVA, RSI et Kelia | Extractions au format pivot à la charge des outils de gestion ; à défaut, un mapping par source |
| H-38 | RCD, troisième lecture | L'avenant dit aussi que le taux « sera appliqué sur le montant total des primes annualisées », soit 2 400 € pour 60 000 € | Non retenue ; obtenue par le mode global du barème |
| H-39 | Adhésion nouvelle | Aucun flux ne distingue vente additionnelle, remplacement et modification | Indicateur fourni par la source dans le format pivot |

### 15.5 Choix de fonctionnement non couverts par les sources

Ces points n'apparaissent dans aucun document du dossier. Ils ont été ajoutés pour que chaque mécanisme ait un comportement défini dans tous les cas.

| N° | Sujet | Constat | Choix retenu |
| --- | --- | --- | --- |
| H-40 | Tolérance de solde de l'escompte | Les arrondis laissent des écarts de quelques centimes entre l'escompte versé et le cumul apuré | Tolérance par entité, 1 € par défaut ; pour un écart négatif en deçà, escompte soldé sans régularisation, reliquat passé en charge ; un écart positif reste versé (section 6.4) |
| H-41 | Règles concurrentes au calcul | Rien ne dit quoi faire si deux règles du même groupe ont la même priorité | Événement `ON_HOLD` avec anomalie, aucune ligne (section 6.11) |
| H-42 | Recouvrement de deux périmètres | Notion nécessaire à la détection des conflits, non définie | Deux périmètres se recouvrent sauf si un même critère y porte des valeurs disjointes (section 6.11) |
| H-43 | Mois comptable du paiement | Le paiement d'une période est exécuté le mois suivant | L'écriture de paiement porte le mois de la date d'exécution ; les autres, celui de la période (section 10.3) |
| H-44 | Marche en double | Durée et critère de sortie non définis | Deux périodes par périmètre, bascule quand tous les écarts sont expliqués (section 14) |
| H-45 | Prime sans période de cotisation | Les prorata supposent une période couverte | Rattachement entier à la date de l'événement, sans prorata (section 6.2) |
| H-46 | Prime encaissée après la résiliation | Cas non décrit | Commissionnée, puis reprise complétée du même pourcentage (section 6.3) |
| H-47 | Remboursement anticipé partiel d'un prêt | Seul le rachat total est décrit | Aucune régularisation (section 6.5) |
| H-48 | Indicateur de qualité absent au calcul de la RCP | Cas non décrit | Contrôle bloquant jusqu'à réception ; jamais assimilé à une condition manquée (section 6.8) |
| H-49 | Commission externe incohérente | Le contrôle est prévu, pas sa conséquence | Événement `ON_HOLD` ; l'opérateur accepte avec justification ou demande une correction (section 6.10) |
| H-50 | Conventions des fichiers d'échange | Encodage, séparateur, format des dates et des nombres, nommage : rien n'est fixé | Conventions uniques de la section 9.9, à confirmer avec la DSSI |
| H-51 | Structure des fichiers non décrits | Les fichiers de référentiels, de cessions, de collaborateurs et d'imports en masse, le résultat des virements, la liste des chèques, les métadonnées de GED, les exports Data et paie, les noms de colonnes de l'extraction comptable et les valeurs de code ne sont pas décrits | Structures dérivées du modèle de données (sections 5.6, 9.10 et 9.11), à valider avec chaque système voisin |
| H-52 | Changement de mode en cours de vie | Le passage d'une garantie du linéaire au précompté après la souscription n'est pas décrit | Première année toujours comptée depuis la date d'effet : aucun apport après 365 jours (section 6.2) |
| H-53 | Régularisation négative du variable | Le cas où les mensualités versées dépassent le droit annuel d'un commercial n'est pas tranché | Écart calculé et soumis à la décision du responsable (section 8.14) |
| H-54 | Barème des rétrocessions par support | Le Guide renvoie à un barème par support financier qui n'est pas dans le dossier | Barème chargé au paramétrage, support par support (section 6.6) |
| H-55 | Calendrier cible du cycle | Le calendrier actuel tient sur neuf jours ouvrés ; aucun calendrier cible n'est donné | Jalons en sept jours ouvrés, paramétrés par entité (section 7.3) |
| H-56 | Taux de réclamations et RCD | L'avenant en fait une condition d'octroi et un motif de résiliation, sans dire s'il suspend le versement | Alerte de contrôle sans suspension du calcul ; la résiliation de l'avenant reste une décision d'Alptis (section 6.7) |
| H-57 | Paiement de l'épargne retraite | Virements entre le 20 et le 25, alors que le cycle de la période paie entre le 5 et le 10 ; l'articulation n'est pas décrite | Lot de paiement distinct dans la même période, daté du 20 au 25, avec son propre bordereau ; chaque lot est validé séparément par la Finance et la période est payée quand tous ses lots sont exécutés (sections 7.1 et 7.3) |

## 16. Sources exploitées

La spécification s'appuie sur le dossier « Remuneration » transmis le 6 octobre 2026 (132 fichiers). Les réponses commerciales des éditeurs n'ont servi qu'à relever ce qu'Alptis y précise de son besoin.

| Document | Usage dans cette spécification |
| --- | --- |
| Guide de la rémunération courtiers 2026 (version 06/26) | Modes de commission, barèmes, reprises, escompte, règles retraite (section 6) |
| Avenants RCD santé et prévoyance, RCP santé et prévoyance (mars 2026) | Sections 6.7 et 6.8 |
| Protocole de rémunération Alptis et UMR 2026 | Commission reçue de l'assureur (section 6.6) |
| Modèles et fichiers UMR : bordereaux linéaires de juin et juillet 2026, modèle précompte, modèle surcommission, rétrocessions, fichier d'intégration RSI | Formats, mapping et cas de recette (sections 9.4 et 13.3) |
| Documentation du calcul des commissions dans RSI : 20 schémas et le déroulé des batchs | Mécanique de l'escompte, statuts, cycle mensuel (sections 6.4 et 7) |
| Procédure de contrôle et de validation des commissions mensuelles (2022) | Contrôles, compensations, calendrier (sections 7 et 8.6) |
| Support « Pilotage et gestion courtiers » (2025) | Escompte, cessions, ordres de remplacement, ORIAS (sections 5, 6.4 et 7.4) |
| Expression de besoins « Outil de gestion des commissions » V0 | Hiérarchie, surcommissions, bordereau, compte débiteur |
| Cahier des charges RFP v1 et cas d'usage (septembre 2026) | Périmètre, volumes, profils, cas d'usage |
| Questionnaire RFI (EF-01 à EF-126, NF-01 à NF-18, exigences de service SO) | Exigences des sections 8 et 11 |
| Exigences techniques, sécurité et conformité du RFP | Section 11 |
| Note « Comptabilité » et « Vision comptable » (août 2026) | Section 10 |
| Échanges de questions complémentaires avec les éditeurs (août et septembre 2026) | Multi-entités, bordereaux, flux, volumes |
| Compilation d'audit interne sur les surcommissions (2023) | Exigences de contrôle et d'habilitation |
| Convention de mandat et Guide de rémunération Promantis Conseil | Rétrocession à un mandataire (section 6.9) |
| Synthèse de la rémunération des commerciaux 2026 et annexe du RFI | Module forces de vente (section 8.14) |
| COPIL n°1 et point d'alignement du 24/09/2026, plan de déploiement v1, schéma SI du 15 juillet | Contexte, architecture, lotissement (sections 2, 9, 12 et 14) |
