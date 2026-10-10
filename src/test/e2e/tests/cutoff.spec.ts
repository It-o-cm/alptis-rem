import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the cut-off state: the pilotage figures and the reading mailles (FIN-04,
// FIN-09, section 10.4).
//
// @screen:cutoff @req:FIN-04 @req:FIN-09

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('le contrôleur Finance lit le cut-off et change de maille @screen:cutoff @req:FIN-04 @req:FIN-09', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  await page.goto('/cutoff');
  await expect(page.getByTestId('cutoff-total-earned')).not.toBeEmpty();
  // Reading by distributor gives the accrued-versus-paid reconciliation (FIN-06).
  await page.getByTestId('cutoff-view-DISTRIBUTOR').click();
  await expect(page.locator('[data-testid^="cutoff-row-"]').first()).toBeVisible();
  await expect(page.getByTestId('chart-cutoff')).toBeVisible();
  await finance.close();
});

test.describe('Cut-off — accessibilité', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:cutoff', async ({ page }) => {
    await page.goto('/cutoff');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
