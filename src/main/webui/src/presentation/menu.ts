// Menu tailoring by role (ERGONOMIE §2.2). The access rules (who may see a
// screen) stay in access.ts and on the server; this module only decides the
// menu's presentation: which rubrics open by default for a role and, inside a
// rubric, which screens come first. Both derive from the role missions of
// specification section 3. Pure functions, covered at 100%.

import type { Role } from './roles';

/**
 * Work rubrics of each role: the navigation sections where the role acts in its
 * daily work (specification section 3). Headings match those of NAV_SECTIONS.
 * - BUSINESS_ADMIN configures rules, runs calculations and keeps the referentials.
 * - OPERATOR follows imports, handles rejects, controls and the distributor accounts.
 * - BUSINESS_VALIDATOR validates the month's amounts.
 * - FINANCE_CONTROLLER validates payment, runs the accounting extraction and closes.
 * - VIEWER consults accounts, statements and dashboards.
 * - AUDITOR reads everything, chiefly the audit trail and the restitutions.
 * - IT_ADMIN manages role mappings and supervises the treatments and flows.
 * - SALES_MANAGER and SALES_REP work in the sales-force module.
 */
const WORK_SECTIONS: Record<Role, readonly string[]> = {
  BUSINESS_ADMIN: ['Cycle mensuel', 'Règles', 'Référentiels', 'Flux'],
  OPERATOR: ['Cycle mensuel', 'Distributeurs', 'Flux'],
  BUSINESS_VALIDATOR: ['Cycle mensuel'],
  FINANCE_CONTROLLER: ['Cycle mensuel', 'Comptabilité'],
  VIEWER: ['Restitutions', 'Distributeurs'],
  AUDITOR: ['Administration', 'Restitutions'],
  IT_ADMIN: ['Administration', 'Flux'],
  SALES_MANAGER: ['Forces de vente'],
  SALES_REP: ['Forces de vente'],
};

/**
 * Roles that act on a screen — as opposed to merely consulting it — derived
 * from the role missions of specification section 3. A screen absent from this
 * map is consult-only for everyone. Used to order the entries inside a rubric.
 */
const SCREEN_ACTORS: Record<string, readonly Role[]> = {
  // Cycle mensuel
  periods: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER'],
  imports: ['OPERATOR', 'BUSINESS_ADMIN'],
  manualEvent: ['OPERATOR', 'BUSINESS_ADMIN'],
  calculations: ['OPERATOR', 'BUSINESS_ADMIN'],
  controls: ['OPERATOR', 'BUSINESS_ADMIN'],
  adjustments: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR'],
  claims: ['OPERATOR', 'BUSINESS_ADMIN'],
  payment: ['OPERATOR', 'FINANCE_CONTROLLER'],
  statements: ['OPERATOR', 'BUSINESS_ADMIN'],
  // Distributeurs
  transfers: ['OPERATOR', 'BUSINESS_ADMIN'],
  distributor: ['OPERATOR', 'BUSINESS_ADMIN'],
  advance: ['BUSINESS_ADMIN', 'OPERATOR'],
  // Règles
  rules: ['BUSINESS_ADMIN'],
  enrollments: ['BUSINESS_ADMIN'],
  simulation: ['BUSINESS_ADMIN', 'OPERATOR'],
  paramTransfer: ['BUSINESS_ADMIN', 'OPERATOR'],
  // Flux
  exports: ['OPERATOR', 'BUSINESS_ADMIN'],
  mapping: ['BUSINESS_ADMIN', 'OPERATOR'],
  reprise: ['OPERATOR', 'BUSINESS_ADMIN'],
  // Référentiels
  products: ['BUSINESS_ADMIN'],
  bankAccounts: ['OPERATOR', 'BUSINESS_ADMIN'],
  payeeRules: ['BUSINESS_ADMIN', 'OPERATOR'],
  externalCodes: ['BUSINESS_ADMIN', 'OPERATOR'],
  // Comptabilité
  accounting: ['OPERATOR', 'FINANCE_CONTROLLER'],
  cutoff: ['FINANCE_CONTROLLER', 'OPERATOR'],
  // Administration
  adminEntities: ['BUSINESS_ADMIN'],
  adminRoleMappings: ['IT_ADMIN'],
  supervision: ['IT_ADMIN'],
  purge: ['IT_ADMIN'],
  // Forces de vente
  fdvPlan: ['BUSINESS_ADMIN', 'SALES_MANAGER'],
  fdvValidation: ['SALES_MANAGER', 'FINANCE_CONTROLLER', 'BUSINESS_ADMIN'],
};

/**
 * The set of navigation rubrics to open by default for a user, as the union of
 * the work rubrics of each of their roles (ERGONOMIE §2.2). Order follows the
 * first appearance across the roles.
 * @param roles the user's role codes
 */
export function workSectionsForRoles(roles: readonly string[]): string[] {
  const sections = new Set<string>();
  for (const role of roles) {
    const work = WORK_SECTIONS[role as Role];
    if (work) {
      for (const heading of work) {
        sections.add(heading);
      }
    }
  }
  return [...sections];
}

/**
 * Tells whether a user acts on a screen (rather than only consulting it).
 * @param screenName route name of the screen
 * @param roles the user's role codes
 */
export function actsOn(screenName: string, roles: readonly string[]): boolean {
  const actors = SCREEN_ACTORS[screenName];
  if (!actors) {
    return false;
  }
  return roles.some((role) => (actors as readonly string[]).includes(role));
}

/**
 * Orders a rubric's entries so the screens where the user acts come before the
 * ones they only consult (ERGONOMIE §2.2), keeping the original order within
 * each group.
 * @param items the rubric's entries (any object carrying a route `name`)
 * @param roles the user's role codes
 */
export function orderItemsForRoles<T extends { name: string }>(
  items: readonly T[],
  roles: readonly string[],
): T[] {
  const acting = items.filter((item) => actsOn(item.name, roles));
  const consulting = items.filter((item) => !actsOn(item.name, roles));
  return [...acting, ...consulting];
}
