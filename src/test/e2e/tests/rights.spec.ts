import { test, expect } from '@playwright/test';
import { resetDb } from '../support/helpers';

// Niveau 4 — droits (TESTS-E2E section 5). A table, held here in the test code after specification
// section 3, gives for each action the roles entitled to it. One generated test walks the table: for
// each role-action couple the action is offered and succeeds (not forbidden) for an entitled role, or
// is refused by the server (HTTP 403) for a role that lacks it. The server's @RolesAllowed is the thing
// under test; a bogus id or an entity path makes the authorization fire before any business logic, so
// the check is independent of the data state.
//
// @req:SEC-01

/** The nine application roles (section 3), each with a dev user that carries exactly those roles. */
const USERS: { user: string; roles: string[] }[] = [
  { user: 'operator-alptis', roles: ['OPERATOR'] },
  { user: 'admin-alptis', roles: ['BUSINESS_ADMIN', 'OPERATOR'] },
  { user: 'validator-alptis', roles: ['BUSINESS_VALIDATOR'] },
  { user: 'finance-alptis', roles: ['FINANCE_CONTROLLER'] },
  { user: 'viewer-alptis', roles: ['VIEWER'] },
  { user: 'auditor', roles: ['AUDITOR'] },
  { user: 'it-admin', roles: ['IT_ADMIN'] },
  { user: 'manager-alptis', roles: ['SALES_MANAGER'] },
  { user: 'rep-alptis', roles: ['SALES_REP'] },
];

const E = 'ALPTIS_ASSURANCES';
const M = '2026-09-01';

/** The rights table: for each action, the roles the specification (section 3) entitles to it. */
const ACTIONS: { name: string; path: string; roles: string[]; method?: string }[] = [
  // Read actions with a role restriction (section 3): consulting these lists is reserved.
  { name: 'read-role-mappings', path: '/api/ui/v1/role-mappings', method: 'get', roles: ['IT_ADMIN', 'AUDITOR'] },
  { name: 'read-mappings', path: '/api/ui/v1/mappings', method: 'get', roles: ['BUSINESS_ADMIN', 'OPERATOR', 'AUDITOR'] },
  { name: 'read-audit', path: '/api/ui/v1/audit', method: 'get', roles: ['AUDITOR', 'IT_ADMIN', 'BUSINESS_ADMIN', 'OPERATOR', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER'] },
  { name: 'purge', path: '/api/ui/v1/admin/purge', roles: ['IT_ADMIN'] },
  { name: 'run-calc', path: `/api/ui/v1/calculations/${E}/${M}/run`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'run-controls', path: `/api/ui/v1/controls/${E}/${M}/run`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'approve-adjustment', path: '/api/ui/v1/adjustments/999999/approve', roles: ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR'] },
  { name: 'validate-lot', path: '/api/ui/v1/payment/lots/999999/validate', roles: ['FINANCE_CONTROLLER'] },
  { name: 'business-validate', path: `/api/ui/v1/validation/${E}/${M}/validate`, roles: ['BUSINESS_VALIDATOR'] },
  { name: 'validate-variable', path: '/api/ui/v1/fdv/results/999999/validate', roles: ['SALES_MANAGER', 'BUSINESS_ADMIN'] },
  { name: 'run-fdv', path: `/api/ui/v1/fdv/${E}/${M}/run`, roles: ['BUSINESS_ADMIN'] },
  { name: 'publish-statement', path: '/api/ui/v1/documents/statements/999999/publish', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'extract-accounting', path: `/api/ui/v1/accounting/${E}/${M}/extract`, roles: ['OPERATOR', 'BUSINESS_ADMIN', 'FINANCE_CONTROLLER'] },
  { name: 'compensate-accounts', path: `/api/ui/v1/accounts/${E}/${M}/distributor/P99999/compensate`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'unblock-account', path: '/api/ui/v1/accounts/999999/unblock', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'prepare-lot', path: '/api/ui/v1/payment/lots/999999/prepare', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'off-cycle-lot', path: `/api/ui/v1/payment/${E}/${M}/off-cycle/P99999`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'correct-rejection', path: '/api/ui/v1/imports/rejections/999999/correct', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'approve-rule', path: '/api/ui/v1/plans/P99/rules/R99/versions/1/approve', roles: ['BUSINESS_ADMIN'] },
  { name: 'annual-regularization', path: `/api/ui/v1/fdv/${E}/2026/annual-regularization`, roles: ['BUSINESS_ADMIN'] },
  { name: 'pay-export-fdv', path: `/api/ui/v1/fdv/${E}/${M}/pay-export`, roles: ['FINANCE_CONTROLLER'] },
  { name: 'rectify-statement', path: '/api/ui/v1/documents/statements/999999/rectify', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'set-role-mapping', path: '/api/ui/v1/role-mappings', roles: ['IT_ADMIN'] },
  { name: 'set-mapping', path: '/api/ui/v1/mappings', roles: ['BUSINESS_ADMIN'] },
  { name: 'close-development', path: `/api/ui/v1/calculations/${E}/${M}/close-development`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'close-portfolio', path: `/api/ui/v1/calculations/${E}/${M}/close-portfolio`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'close-annual', path: `/api/ui/v1/calculations/${E}/${M}/close-annual`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'recalc', path: `/api/ui/v1/calculations/${E}/${M}/recalc?type=DISTRIBUTOR&value=P99999`, roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'quarterly-qualitative', path: `/api/ui/v1/fdv/${E}/${M}/quarterly-qualitative`, roles: ['BUSINESS_ADMIN'] },
  { name: 'annual-collective', path: `/api/ui/v1/fdv/${E}/${M}/annual-collective`, roles: ['BUSINESS_ADMIN'] },
  { name: 'manager-variable', path: `/api/ui/v1/fdv/${E}/${M}/manager-variable`, roles: ['BUSINESS_ADMIN'] },
  { name: 'direct-sales-variable', path: `/api/ui/v1/fdv/${E}/${M}/direct-sales-variable`, roles: ['BUSINESS_ADMIN'] },
  { name: 'termination-reduction', path: `/api/ui/v1/fdv/${E}/${M}/termination-reduction`, roles: ['BUSINESS_ADMIN'] },
  { name: 'save-fdv-plan', path: `/api/ui/v1/fdv/${E}/plans`, roles: ['BUSINESS_ADMIN'] },
  { name: 'create-collaborator', path: `/api/ui/v1/fdv/${E}/collaborators`, roles: ['BUSINESS_ADMIN'] },
  { name: 'cheque-emission', path: '/api/ui/v1/payment/lots/999999/cheque-emission', roles: ['FINANCE_CONTROLLER'] },
  { name: 'order-result', path: '/api/ui/v1/payment/orders/999999/result', roles: ['FINANCE_CONTROLLER'] },
  { name: 'block-account', path: '/api/ui/v1/accounts/999999/block', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'block-line', path: '/api/ui/v1/accounts/lines/999999/block', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'unblock-line', path: '/api/ui/v1/accounts/lines/999999/unblock', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
  { name: 'discard-rejection', path: '/api/ui/v1/imports/rejections/999999/discard', roles: ['OPERATOR', 'BUSINESS_ADMIN'] },
];

const basic = (user: string): string => 'Basic ' + Buffer.from(`${user}:${user}`).toString('base64');

test('chaque couple rôle-action du tableau des droits est offert au rôle habilité et refusé aux autres @req:SEC-01', async ({
  request,
}) => {
  await resetDb(request);
  for (const action of ACTIONS) {
    for (const u of USERS) {
      const method = action.method ?? 'POST';
      const res = await request.fetch(action.path, {
        method,
        headers: { Authorization: basic(u.user), 'Content-Type': 'application/json' },
        ...(method.toUpperCase() === 'GET' ? {} : { data: '{}' }),
      });
      const entitled = u.roles.some((r) => action.roles.includes(r));
      const label = `${u.user} → ${action.name} (statut ${res.status()})`;
      if (entitled) {
        // Offered and carried out: anything but a forbidden (a bogus id may yield 404/400, not 403).
        expect(res.status(), label).not.toBe(403);
      } else {
        // Absent from the role: refused by the server.
        expect(res.status(), label).toBe(403);
      }
    }
  }
});
