import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the exports: producing an on-demand export, its inscription in the journal and
// its identical re-download (REP-04, section 9.11/9.12).
//
// @screen:exports @req:REP-04 @req:REP-05 @req:CTL-06

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur produit un export, le journal l’inscrit et il se télécharge @screen:exports @req:REP-04 @path:FLX-01 @role-action:OPERATOR-produce-export', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/exports');
  // ADM-03: the entity and period are chosen from lists shown by their French labels, never typed.
  await expect(page.getByTestId('exports-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('exports-period').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
  // FLX-01: each of the section 9.12 on-demand exports is produced and journalled.
  for (const object of ['COMMISSION_LINES', 'ACCOUNTS', 'CONTROL_RESULTS']) {
    await page.getByTestId('exports-object').selectOption(object);
    await page.getByTestId('exports-produce').click();
    await expect(page.getByTestId('exports-message')).toContainText('produit');
  }
  // The journal records each export with its author, line count and total.
  const row = page.locator('[data-testid^="export-row-"]').first();
  await expect(row).toBeVisible();
  await expect(row).toContainText('operator-alptis');
  await expect(row).toContainText('€');
  // The file is downloaded and read.
  await page.locator('[data-testid^="export-download-"]').first().click();
  await expect(page.getByTestId('export-content')).not.toBeEmpty();
  await operator.close();
});

test('l’opérateur exporte les données des contrôles de niveau 2 et 3 @screen:exports @req:CTL-06 @role-action:OPERATOR-produce-export', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/exports');
  await page.getByTestId('exports-object').selectOption('CONTROL_RESULTS');
  await page.getByTestId('exports-produce').click();
  await expect(page.getByTestId('exports-message')).toContainText('produit');
  await page.locator('[data-testid^="export-download-"]').first().click();
  await expect(page.getByTestId('export-content')).toContainText('control_code;severity;object_type');
  await operator.close();
});

test('l’opérateur produit un export Data incrémental depuis une date @screen:exports @req:REP-05 @role-action:OPERATOR-produce-export', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/exports');
  await page.getByTestId('exports-object').selectOption('COMMISSION_LINES');
  await page.getByTestId('exports-data').check();
  await page.getByTestId('exports-since').fill('2026-09-01');
  await page.getByTestId('exports-produce').click();
  await expect(page.getByTestId('exports-message')).toContainText('produit');
  // The journal shows the incremental Data flow for that window.
  await expect(page.locator('[data-testid^="export-row-"]', { hasText: 'INCR:2026-09-01' }).first()).toBeVisible();
  await operator.close();
});

test.describe('Exports — accessibilité', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:exports', async ({ page }) => {
    await page.goto('/exports');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
