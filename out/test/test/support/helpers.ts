import { APIRequestContext, expect } from '@playwright/test';

/** Dev credentials for the end-to-end users, matching application.properties. */
export const USERS = {
  adminAlptis: { username: 'admin-alptis', password: 'admin-alptis' },
  admin2Alptis: { username: 'admin2-alptis', password: 'admin2-alptis' },
  operatorAlptis: { username: 'operator-alptis', password: 'operator-alptis' },
  validatorAlptis: { username: 'validator-alptis', password: 'validator-alptis' },
  financeAlptis: { username: 'finance-alptis', password: 'finance-alptis' },
  viewerAlptis: { username: 'viewer-alptis', password: 'viewer-alptis' },
  operatorUnep: { username: 'operator-unep', password: 'operator-unep' },
  itAdmin: { username: 'it-admin', password: 'it-admin' },
  auditor: { username: 'auditor', password: 'auditor' },
  managerAlptis: { username: 'manager-alptis', password: 'manager-alptis' },
  salesRep: { username: 'rep-alptis', password: 'rep-alptis' },
};

/** Resets the database to its seeded baseline through the dev-only test endpoint. */
export async function resetDb(request: APIRequestContext): Promise<void> {
  const response = await request.post('/api/test/reset');
  expect(response.ok()).toBeTruthy();
}

/** Resets then loads the full demonstration dataset through the dev-only test endpoints. */
export async function loadDemo(request: APIRequestContext): Promise<void> {
  const reset = await request.post('/api/test/reset');
  expect(reset.ok()).toBeTruthy();
  const demo = await request.post('/api/test/demo');
  expect(demo.ok()).toBeTruthy();
}

/** The month of the seeded cycle scene. */
export const DEMO_MONTH = '2026-09-01';

/** Plants the deterministic cycle scene (period, distributor, accounts) for the cycle screens. */
export async function seedCycle(request: APIRequestContext): Promise<void> {
  const response = await request.post(
    `/api/test/cycle-demo?entity=ALPTIS_ASSURANCES&month=${DEMO_MONTH}`,
  );
  expect(response.ok()).toBeTruthy();
}

/** Plants the deterministic sales-force scene (collaborator, plan, objective, measure). */
export async function seedFdv(request: APIRequestContext): Promise<void> {
  const response = await request.post(
    `/api/test/fdv-demo?entity=ALPTIS_ASSURANCES&month=${DEMO_MONTH}`,
  );
  expect(response.ok()).toBeTruthy();
}
