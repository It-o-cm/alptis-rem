import type { Role } from './roles';

// Which roles may see each screen. Mirrors the server's @RolesAllowed so the interface can hide
// what a user may not do; the server stays the authority. A screen absent from the map is open to
// every authenticated role (used by the step-0 placeholder screens).

const SCREEN_ACCESS: Record<string, readonly Role[]> = {
  adminEntities: ['BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER', 'AUDITOR'],
  adminRoleMappings: ['IT_ADMIN', 'AUDITOR'],
  adminAudit: ['AUDITOR', 'IT_ADMIN', 'BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER'],
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
