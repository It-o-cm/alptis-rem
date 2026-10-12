// Every router screen of the application, with its path and an end-to-end user
// who may access it (ERGONOMIE §5). The alignment test iterates this manifest to
// measure every screen; the UX build guard fails when a router screen is missing
// here, so this list stays in step with src/main/webui/src/router. Default user
// is the auditor (reads almost everything); two screens the auditor cannot reach
// use a role that can.

import { USERS } from './helpers';

/** A router screen covered by the alignment test. */
export interface Screen {
  /** Route name, matching the navigation entry. */
  readonly name: string;
  /** Route path. */
  readonly path: string;
  /** Key of the end-to-end user used to open it. */
  readonly user: keyof typeof USERS;
}

/** All router screens, in navigation order. */
export const SCREENS: readonly Screen[] = [
  { name: 'periods', path: '/', user: 'auditor' },
  { name: 'imports', path: '/imports', user: 'auditor' },
  { name: 'manualEvent', path: '/saisie-evenement', user: 'adminAlptis' },
  { name: 'events', path: '/evenements', user: 'auditor' },
  { name: 'heldEvents', path: '/evenements-en-attente', user: 'auditor' },
  { name: 'calculations', path: '/calculs', user: 'auditor' },
  { name: 'controls', path: '/controles', user: 'auditor' },
  { name: 'adjustments', path: '/ajustements', user: 'auditor' },
  { name: 'payment', path: '/paiement', user: 'auditor' },
  { name: 'statements', path: '/bordereaux', user: 'auditor' },
  { name: 'distributorSearch', path: '/distributeurs', user: 'auditor' },
  { name: 'contractSearch', path: '/contrats', user: 'auditor' },
  { name: 'transfers', path: '/cessions', user: 'auditor' },
  { name: 'distributor', path: '/distributeur', user: 'auditor' },
  { name: 'advance', path: '/escompte', user: 'auditor' },
  { name: 'rules', path: '/regles', user: 'auditor' },
  { name: 'enrollments', path: '/inscriptions', user: 'auditor' },
  { name: 'simulation', path: '/simulation', user: 'auditor' },
  { name: 'families', path: '/familles', user: 'auditor' },
  { name: 'paramTransfer', path: '/parametrage-transfert', user: 'auditor' },
  { name: 'flows', path: '/flux', user: 'auditor' },
  { name: 'exports', path: '/exports', user: 'auditor' },
  { name: 'formats', path: '/formats', user: 'auditor' },
  { name: 'mapping', path: '/mapping', user: 'auditor' },
  { name: 'reprise', path: '/reprise', user: 'auditor' },
  { name: 'products', path: '/produits', user: 'auditor' },
  { name: 'qualityIndicators', path: '/indicateurs', user: 'auditor' },
  { name: 'bankAccounts', path: '/coordonnees-bancaires', user: 'auditor' },
  { name: 'payeeRules', path: '/regles-versement', user: 'auditor' },
  { name: 'externalCodes', path: '/codes-externes', user: 'auditor' },
  { name: 'accounting', path: '/comptabilite', user: 'auditor' },
  { name: 'cutoff', path: '/cutoff', user: 'auditor' },
  { name: 'dashboards', path: '/tableaux-de-bord', user: 'auditor' },
  { name: 'commissionSearch', path: '/recherche-commissions', user: 'auditor' },
  { name: 'groupView', path: '/vision-groupe', user: 'auditor' },
  { name: 'margin', path: '/marge', user: 'auditor' },
  { name: 'indicators', path: '/indicateurs-fonctionnement', user: 'auditor' },
  { name: 'adminEntities', path: '/entites', user: 'auditor' },
  { name: 'adminRoleMappings', path: '/correspondances-roles', user: 'auditor' },
  { name: 'adminAudit', path: '/audit', user: 'auditor' },
  { name: 'supervision', path: '/supervision', user: 'auditor' },
  { name: 'purge', path: '/purge', user: 'itAdmin' },
  { name: 'fdvPlan', path: '/fdv/plan', user: 'auditor' },
  { name: 'fdvValidation', path: '/fdv/validation', user: 'auditor' },
  { name: 'fdvCommercial', path: '/fdv/commercial', user: 'auditor' },
];
