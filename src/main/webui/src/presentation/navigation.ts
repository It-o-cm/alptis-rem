// Side-navigation layout, reproducing the sections and entries of the mock-up
// (docs/maquette/Main.dc.html). Later steps add one entry per list of the
// section 4 inventory; this is the common frame delivered at step 0.

/** A single navigation entry leading to a page. */
export interface NavItem {
  /** Route name, unique across the application. */
  readonly name: string;
  /** Route path. */
  readonly path: string;
  /** French label shown in the navigation and used as the page title. */
  readonly label: string;
}

/** A labelled group of navigation entries. */
export interface NavSection {
  /** French section heading, as in the mock-up. */
  readonly heading: string;
  /** Entries of the section. */
  readonly items: readonly NavItem[];
}

/** The navigation sections, in the order of the mock-up. */
export const NAV_SECTIONS: readonly NavSection[] = [
  {
    heading: 'Cycle mensuel',
    items: [
      { name: 'periods', path: '/', label: 'Périodes' },
      { name: 'imports', path: '/imports', label: 'Imports' },
      { name: 'manualEvent', path: '/saisie-evenement', label: 'Saisie manuelle' },
      { name: 'events', path: '/evenements', label: 'Recherche d’événements' },
      { name: 'heldEvents', path: '/evenements-en-attente', label: 'Événements en attente' },
      { name: 'calculations', path: '/calculs', label: 'Exécutions de calcul' },
      { name: 'controls', path: '/controles', label: 'Contrôles' },
      { name: 'adjustments', path: '/ajustements', label: 'Ajustements' },
      { name: 'claims', path: '/reclamations', label: 'Réclamations' },
      { name: 'validation', path: '/validation', label: 'Validation métier' },
      { name: 'payment', path: '/paiement', label: 'Paiement' },
      { name: 'statements', path: '/bordereaux', label: 'Bordereaux' },
    ],
  },
  {
    heading: 'Distributeurs',
    items: [
      { name: 'distributorSearch', path: '/distributeurs', label: 'Recherche distributeurs' },
      { name: 'groupings', path: '/groupements', label: 'Groupements' },
      { name: 'contractSearch', path: '/contrats', label: 'Recherche contrats' },
      { name: 'transfers', path: '/cessions', label: 'Cessions et ordres' },
      { name: 'distributor', path: '/distributeur', label: 'Fiche et compte' },
      { name: 'advance', path: '/escompte', label: 'Escompte' },
    ],
  },
  {
    heading: 'Règles',
    items: [
      { name: 'rules', path: '/regles', label: 'Paramétrage' },
      { name: 'enrollments', path: '/inscriptions', label: 'Accords et inscriptions' },
      { name: 'simulation', path: '/simulation', label: 'Simulation' },
      { name: 'families', path: '/familles', label: 'Familles de calcul' },
      { name: 'paramTransfer', path: '/parametrage-transfert', label: 'Export / import du paramétrage' },
    ],
  },
  {
    heading: 'Flux',
    items: [
      { name: 'flows', path: '/flux', label: 'Catalogue des flux' },
      { name: 'exports', path: '/exports', label: 'Exports' },
      { name: 'formats', path: '/formats', label: "Formats d'échange" },
      { name: 'mapping', path: '/mapping', label: 'Mapping de fichier' },
      { name: 'reprise', path: '/reprise', label: "Reprise d'historique" },
    ],
  },
  {
    heading: 'Référentiels',
    items: [
      { name: 'products', path: '/produits', label: 'Produits' },
      { name: 'qualityIndicators', path: '/indicateurs', label: 'Indicateurs qualité' },
      { name: 'bankAccounts', path: '/coordonnees-bancaires', label: 'Coordonnées bancaires' },
      { name: 'payeeRules', path: '/regles-versement', label: 'Règles de versement' },
      { name: 'externalCodes', path: '/codes-externes', label: 'Codes externes' },
    ],
  },
  {
    heading: 'Comptabilité',
    items: [
      { name: 'accounting', path: '/comptabilite', label: 'Schéma et écritures' },
      { name: 'cutoff', path: '/cutoff', label: 'État de cut-off' },
    ],
  },
  {
    heading: 'Restitutions',
    items: [
      { name: 'dashboards', path: '/tableaux-de-bord', label: 'Tableaux de bord' },
      { name: 'commissionSearch', path: '/recherche-commissions', label: 'Recherche de commissions' },
      { name: 'groupView', path: '/vision-groupe', label: 'Vision consolidée groupe' },
      { name: 'margin', path: '/marge', label: 'Marge' },
      { name: 'indicators', path: '/indicateurs-fonctionnement', label: 'Indicateurs de fonctionnement' },
    ],
  },
  {
    heading: 'Administration',
    items: [
      { name: 'adminEntities', path: '/entites', label: 'Entités et paramètres' },
      { name: 'adminRoleMappings', path: '/correspondances-roles', label: 'Groupes et rôles' },
      { name: 'adminAudit', path: '/audit', label: "Piste d'audit" },
      { name: 'supervision', path: '/supervision', label: 'Supervision' },
      { name: 'purge', path: '/purge', label: 'Purge et anonymisation' },
    ],
  },
  {
    heading: 'Forces de vente',
    items: [
      { name: 'fdvPlan', path: '/fdv/plan', label: 'Plan de variable' },
      { name: 'fdvValidation', path: '/fdv/validation', label: 'Validation mensuelle' },
      { name: 'fdvCommercial', path: '/fdv/commercial', label: 'Mon variable' },
    ],
  },
];

/** Flattens all navigation entries across sections, preserving order. */
export function allNavItems(): NavItem[] {
  return NAV_SECTIONS.flatMap((section) => [...section.items]);
}

/**
 * Heading of the navigation rubric a route belongs to, or an empty string when
 * the route is unknown. Used as the common eyebrow above every page title.
 * @param routeName the current route name
 */
export function sectionHeadingOf(routeName: string | undefined): string {
  if (!routeName) {
    return '';
  }
  const match = NAV_SECTIONS.find((section) => section.items.some((item) => item.name === routeName));
  return match ? match.heading : '';
}
