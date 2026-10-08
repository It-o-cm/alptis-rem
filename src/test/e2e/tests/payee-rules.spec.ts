import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the payment-rules consultation screen (REF-06): beneficiary, shares and
// periodicity by perimeter.
//
// @screen:payee-rules @req:REF-06

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

test.describe('Règles de versement — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les règles de versement et leurs parts @screen:payee-rules @req:REF-06', async ({ page }) => {
    await importFile(page, 'distributor_groups', 'group_code;label\nG1;Réseau Martin\n');
    await importFile(page, 'distributors',
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
      + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n');
    await importFile(page, 'payee_rules',
      'entity_code;production_code;partner_code;range_code;payment_frequency;valid_from;valid_to;shares\n'
      + 'ALPTIS_ASSURANCES;;P13897;;MONTHLY;2026-01-01;;DISTRIBUTOR:P13897:0.9|GROUP:G1:0.1\n');

    await page.goto('/regles-versement');
    await expect(page.getByTestId('page-title')).toHaveText('Règles de versement');
    const row = page.getByTestId('rule-row-0');
    await expect(row).toContainText('P13897');
    await expect(row).toContainText('MONTHLY');
    await expect(row.getByTestId('rule-shares')).toContainText('90 %');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:payee-rules', async ({ page }) => {
    await page.goto('/regles-versement');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Règles de versement — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:payee-rules', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/regles-versement');
    await expect(page.getByRole('link', { name: 'Règles de versement' })).toHaveCount(0);
    await context.close();
  });
});
