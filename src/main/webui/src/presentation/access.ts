import type { Role } from './roles';

// Which roles may see each screen. Mirrors the server's @RolesAllowed so the interface can hide
// what a user may not do; the server stays the authority. A screen absent from the map is open to
// every authenticated role (used by the step-0 placeholder screens).

const SCREEN_ACCESS: Record<string, readonly Role[]> = {
  adminEntities: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  adminRoleMappings: ['IT_ADMIN', 'AUDITOR'],
  adminAudit: ['AUDITOR', 'IT_ADMIN', 'BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER'],
  imports: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  mapping: ['BUSINESS_ADMIN', 'OPERATOR', 'AUDITOR'],
  distributorSearch: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  groupings: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  contractSearch: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  products: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  qualityIndicators: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  bankAccounts: ['OPERATOR', 'BUSINESS_ADMIN', 'FINANCE_CONTROLLER', 'AUDITOR'],
  transfers: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'AUDITOR'],
  manualEvent: ['OPERATOR', 'BUSINESS_ADMIN'],
  events: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  heldEvents: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  calculations: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  claims: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  validation: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  payeeRules: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  externalCodes: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR', 'IT_ADMIN'],
  rules: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  families: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  enrollments: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  advance: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  discounts: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  periods: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  controls: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  adjustments: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  distributor: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  payment: ['OPERATOR', 'BUSINESS_ADMIN', 'FINANCE_CONTROLLER', 'AUDITOR'],
  statements: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  accounting: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  reconciliation: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  cutoff: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  flows: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  exports: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  formats: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  simulation: ['BUSINESS_ADMIN', 'OPERATOR', 'AUDITOR'],
  paramTransfer: ['BUSINESS_ADMIN', 'OPERATOR', 'AUDITOR'],
  reprise: ['OPERATOR', 'BUSINESS_ADMIN', 'AUDITOR'],
  dashboards: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  commissionSearch: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  groupView: ['AUDITOR', 'IT_ADMIN', 'BUSINESS_ADMIN', 'FINANCE_CONTROLLER', 'VIEWER', 'BUSINESS_VALIDATOR', 'OPERATOR'],
  margin: ['BUSINESS_ADMIN', 'FINANCE_CONTROLLER', 'AUDITOR'],
  indicators: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'VIEWER', 'AUDITOR'],
  supervision: ['IT_ADMIN', 'AUDITOR'],
  purge: ['IT_ADMIN'],
  fdvPlan: ['BUSINESS_ADMIN', 'SALES_MANAGER', 'AUDITOR'],
  fdvValidation: ['SALES_MANAGER', 'BUSINESS_ADMIN', 'FINANCE_CONTROLLER', 'AUDITOR'],
  fdvCommercial: ['SALES_REP', 'SALES_MANAGER', 'BUSINESS_ADMIN', 'AUDITOR'],
};

/**
 * Tells whether a user holding the given roles may see a screen. Screens with no explicit rule are
 * open to any authenticated user.
 */
export function canAccess(screenName: string, roles: readonly string[]): boolean {
  const allowed = SCREEN_ACCESS[screenName];
  if (!allowed) {
    return true;
  }
  return roles.some((role) => (allowed as readonly string[]).includes(role));
}

/**
 * Tells whether a user holding the given roles may edit entity parameters (business administrator).
 */
export function canEditEntityParameters(roles: readonly string[]): boolean {
  return roles.includes('BUSINESS_ADMIN');
}

/**
 * Tells whether a user holding the given roles may manage role mappings (technical administrator).
 */
export function canManageRoleMappings(roles: readonly string[]): boolean {
  return roles.includes('IT_ADMIN');
}

/**
 * Tells whether a user holding the given roles may upload files and resolve rejections.
 */
export function canOperateImports(roles: readonly string[]): boolean {
  return roles.includes('OPERATOR') || roles.includes('BUSINESS_ADMIN');
}

/**
 * Tells whether a user holding the given roles may save a file mapping (business administrator).
 */
export function canEditMapping(roles: readonly string[]): boolean {
  return roles.includes('BUSINESS_ADMIN');
}
