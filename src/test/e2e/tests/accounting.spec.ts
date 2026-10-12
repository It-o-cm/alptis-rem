import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the accounting screen: the schema and mapping (FIN-02), the entry search
// (FIN-05) and the monthly extraction at closing (FIN-03). The accounting cycle itself (CR-65/66,
// FIN-01) is verified by AccountingServiceTest on the server, as noted in docs/DECISIONS.md.
//
// @screen:accounting @req:FIN-02 @req:FIN-03 @req:FIN-05

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('le contrôleur Finance lit le schéma et produit l’extraction @screen:accounting @req:FIN-02 @req:FIN-03 @req:FIN-05 @role-action:OPERATOR-extract-accounting', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  await page.goto('/comptabilite');
  // The schema carries the accounting events (FIN-02).
  await expect(page.getByTestId('acc-scheme')).toContainText('FORECAST_RECOGNITION');
  // The extraction is produced and its CSV carries the OUT-02 columns (FIN-03).
  await page.getByTestId('acc-extract').click();
  await expect(page.getByTestId('acc-message')).toContainText('ALPTIS_ASSURANCES_ECR_202609.csv');
  await expect(page.getByTestId('acc-extract-csv')).toContainText('accounting_event');
  await finance.close();
});

test('le contrôleur Finance recherche une écriture et remonte la chaîne @screen:accounting @req:FIN-05 @req:FIN-07', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  // The full demo runs the real cycle, which writes auxiliary accounting entries (FIN-01).
  await loadDemo(page.request);
  await page.goto('/comptabilite');

  // Cross-month search across the entity's entries (FIN-05).
  await page.getByTestId('acc-cross-search').click();
  const row = page.locator('[data-testid^="cross-entry-row-"]').first();
  await expect(row).toBeVisible();
  // Open the audit chain flux → bordereau → ligne → écriture (FIN-07).
  await row.click();
  await expect(page.getByTestId('acc-chain')).toBeVisible();
  await finance.close();
});

test.describe('Comptabilité — accessibilité', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:accounting', async ({ page }) => {
    await page.goto('/comptabilite');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
