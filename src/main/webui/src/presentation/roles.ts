// The nine application roles of specification section 3, with their French labels as
// shown in the page header. Access rules themselves are enforced by the server.

export type Role =
  | 'BUSINESS_ADMIN'
  | 'OPERATOR'
  | 'BUSINESS_VALIDATOR'
  | 'FINANCE_CONTROLLER'
  | 'VIEWER'
  | 'AUDITOR'
  | 'IT_ADMIN'
  | 'SALES_MANAGER'
  | 'SALES_REP';

const ROLE_LABELS: Record<Role, string> = {
  BUSINESS_ADMIN: 'administrateur métier',
  OPERATOR: 'gestionnaire',
  BUSINESS_VALIDATOR: 'valideur métier',
  FINANCE_CONTROLLER: 'contrôleur Finance',
  VIEWER: 'consultation',
  AUDITOR: 'audit',
  IT_ADMIN: 'administrateur technique',
  SALES_MANAGER: 'responsable commercial',
  SALES_REP: 'commercial',
};

/** All role codes, in the order of specification section 3. */
export const ROLES: Role[] = Object.keys(ROLE_LABELS) as Role[];

/**
 * Returns the French label for a role code, or the raw code if it is not a known role.
 */
export function roleLabel(role: string): string {
  return ROLE_LABELS[role as Role] ?? role;
}
