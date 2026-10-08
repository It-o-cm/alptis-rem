import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the portfolio-transfer and replacement-order screen (REF-09).
//
// @screen:transfers @req:REF-09

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

test.describe('Cessions — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les cessions de portefeuille @screen:transfers @req:REF-09', async ({ page }) => {
    await importFile(page, 'distributors',
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
      + 'ALPTIS_ASSURANCES;SESAME;P1;Cédant;INDEPENDENT;ACTIVE;2026-01-01\n'
      + 'ALPTIS_ASSURANCES;SESAME;P2;Repreneur;INDEPENDENT;ACTIVE;2026-01-01\n');
    await importFile(page, 'portfolio_transfers',
      'entity_code;from_partner_code;to_partner_code;effect_date;advance_debt_handling\n'
      + 'ALPTIS_ASSURANCES;P1;P2;2026-07-01;TAKEN_OVER\n');

    await page.goto('/cessions');
    await expect(page.getByTestId('page-title')).toHaveText('Cessions et ordres de remplacement');
    const row = page.getByTestId('transfer-row').first();
    await expect(row).toContainText('P1');
    await expect(row).toContainText('P2');
    await expect(row).toContainText('TAKEN_OVER');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:transfers', async ({ page }) => {
    await page.goto('/cessions');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Cessions — accès', () => {
  test('un consultant n’a pas accès à l’écran @screen:transfers', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
    const page = await context.newPage();
    await page.goto('/cessions');
    await expect(page.getByRole('link', { name: 'Cessions et ordres' })).toHaveCount(0);
    await context.close();
  });
});
