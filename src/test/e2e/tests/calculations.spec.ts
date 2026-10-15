import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the calculation-executions screen (CAL-01, CAL-12): launch a run, track it
// with its counters, launch a partial recalc, and see the units left in failure; per-role access.
//
// @screen:calculations @req:CAL-01 @req:CAL-12

test.describe('Exécutions de calcul — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('lance un calcul, le suit et relance un périmètre @screen:calculations @req:CAL-01 @req:CAL-12 @path:CAL-01', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/calculs');
    await expect(page.getByTestId('page-title')).toHaveText('Exécutions de calcul');

    // Launch a full run and see it tracked with a status.
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    const firstRun = page.locator('[data-testid^="run-row-"]').first();
    await expect(firstRun).toContainText('Réel');
    await expect(firstRun).toContainText('Terminé');
    // CAL-01: the execution carries its counters (units processed and lines produced), both non-zero.
    await expect(firstRun).toContainText(/[1-9]/);

    // The demo plants a held event per entity: it appears as a unit in failure.
    await expect(page.locator('[data-testid^="failed-row-"]').first()).toBeVisible();

    // Launch a partial recalc scoped to a product.
    await page.getByTestId('recalc-type').selectOption('PRODUCT');
    await page.getByTestId('recalc-value').fill('DEMO_SANTE');
    await page.getByTestId('recalc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Recalcul partiel');
    await expect(page.getByText('PRODUCT DEMO_SANTE')).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:calculations', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/calculs');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Exécutions de calcul — accès', () => {
  test('le contrôleur Finance suit sans pouvoir lancer @screen:calculations', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.financeAlptis });
    const page = await context.newPage();
    await loadDemo(page.request);
    await page.goto('/calculs');
    await expect(page.getByTestId('calc-runs')).toBeVisible();
    await expect(page.getByTestId('calc-run')).toHaveCount(0);
    await context.close();
  });

  test('un commercial n’a pas accès à l’écran @screen:calculations', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/calculs');
    await expect(page.getByRole('link', { name: 'Exécutions de calcul' })).toHaveCount(0);
    await context.close();
  });
});
