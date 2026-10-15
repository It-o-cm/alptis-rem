import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen test for the agreements-and-enrollments screen (PAR-10): a signed agreement activates
// an enrollment to a reserved rule.
//
// @screen:enrollments @req:PAR-10

const ADMIN_AUTH =
  'Basic ' + Buffer.from(`${USERS.adminAlptis.username}:${USERS.adminAlptis.password}`).toString('base64');
const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

async function setup(page: Page) {
  const distributors = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OPERATOR_AUTH },
    data: {
      entityCode: 'ALPTIS_ASSURANCES', flowCode: 'distributors', sourceSystem: 'SESAME', fileName: 'd.csv',
      content: 'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
        + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n',
    },
  });
  expect(distributors.ok()).toBeTruthy();
  const plan = await page.request.post('/api/ui/v1/plans', {
    headers: { Authorization: ADMIN_AUTH },
    data: { entityCode: 'ALPTIS_ASSURANCES', code: 'RESP', label: 'Réservé', direction: 'INBOUND', vintage: 2026 },
  });
  expect(plan.ok()).toBeTruthy();
  const rule = await page.request.post('/api/ui/v1/plans/RESP/rules', {
    headers: { Authorization: ADMIN_AUTH },
    data: { entityCode: 'ALPTIS_ASSURANCES', code: 'RCD', label: 'RCD', family: 'PORTFOLIO' },
  });
  expect(rule.ok()).toBeTruthy();
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Accords et inscriptions', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('un accord signé active l’inscription à une règle réservée @screen:enrollments @req:PAR-10',
    async ({ page }) => {
    await setup(page);
    await page.goto('/inscriptions');
    await expect(page.getByTestId('page-title')).toHaveText('Accords et inscriptions');

    // ADM-03: the distributor is picked from a list by its name, the reference is a new code typed.
    await page.getByTestId('ag-ref').fill('AGR-1');
    await expect(page.getByTestId('ag-partner').locator('option', { hasText: 'Cabinet Martin (P13897)' })).toHaveCount(1);
    await page.getByTestId('ag-partner').selectOption('P13897');
    await page.getByTestId('ag-from').fill('2026-01-01');
    await page.getByTestId('ag-create').click();
    await expect(page.getByTestId('agreement-row-AGR-1')).toContainText('SIGNED');

    // ADM-03: plan, rule, distributor and agreement are all picked from lists, never typed.
    await expect(page.getByTestId('en-plan').locator('option', { hasText: 'Réservé (RESP)' })).toHaveCount(1);
    await page.getByTestId('en-plan').selectOption('RESP');
    await expect(page.getByTestId('en-rule').locator('option', { hasText: 'RCD (RCD)' })).toHaveCount(1);
    await page.getByTestId('en-rule').selectOption('RCD');
    await page.getByTestId('en-partner').selectOption('P13897');
    await page.getByTestId('en-ref').selectOption('AGR-1');
    await page.getByTestId('en-from').fill('2026-01-01');
    await page.getByTestId('en-enroll').click();
    await expect(page.getByTestId('enrollment-row-0')).toContainText('ACTIVE');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:enrollments', async ({ page }) => {
    await page.goto('/inscriptions');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});
