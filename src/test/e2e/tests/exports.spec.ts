import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the exports: producing an on-demand export, its inscription in the journal and
// its identical re-download (REP-04, section 9.11/9.12).
//
// @screen:exports @req:REP-04 @req:CTL-06

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur produit un export, le journal l’inscrit et il se télécharge @screen:exports @req:REP-04 @role-action:OPERATOR-produce-export', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/exports');
  await page.getByTestId('exports-object').selectOption('COMMISSION_LINES');
  await page.getByTestId('exports-produce').click();
  await expect(page.getByTestId('exports-message')).toContainText('produit');
  const row = page.locator('[data-testid^="export-row-"]').first();
  await expect(row).toBeVisible();
  await page.locator('[data-testid^="export-download-"]').first().click();
  await expect(page.getByTestId('export-content')).toContainText('line_id;nature;status');
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

test.describe('Exports — accessibilité', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:exports', async ({ page }) => {
    await page.goto('/exports');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
