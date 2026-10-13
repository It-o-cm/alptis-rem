import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the external-code correspondence screen (REF-04): internal object to its
// identifier in each source system.
//
// @screen:external-codes @req:REF-04

const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

async function importFile(page: Page, flow: string, content: string) {
  const response = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OPERATOR_AUTH },
    data: { entityCode: 'ALPTIS_ASSURANCES', flowCode: flow, sourceSystem: 'SESAME', fileName: `${flow}.csv`, content },
  });
  expect(response.ok()).toBeTruthy();
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Codes externes — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les correspondances de codes externes @screen:external-codes @req:REF-04', async ({ page }) => {
    await importFile(page, 'distributors',
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
      + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n');
    await importFile(page, 'external_codes',
      'object_type;internal_code;source_system;external_code\n'
      + 'DISTRIBUTOR;P13897;CLEVA;CLV-13897\n');

    await page.goto('/codes-externes');
    await expect(page.getByTestId('page-title')).toHaveText('Codes externes');
    const row = page.getByTestId('code-row-0');
    await expect(row).toContainText('DISTRIBUTOR');
    await expect(row).toContainText('P13897');
    await expect(row).toContainText('CLV-13897');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:external-codes', async ({ page }) => {
    await page.goto('/codes-externes');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Codes externes — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:external-codes', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/codes-externes');
    await expect(page.getByRole('link', { name: 'Codes externes' })).toHaveCount(0);
    await context.close();
  });
});
