import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the discount-créances synthesis (BRD-09, CPT-08): the escomptes by
// convention (versé, apuré, créance) and the detail by adherent; per-role access.
//
// @screen:discounts @req:BRD-09 @req:CPT-08

test.describe('Créances d’escompte — contrôleur Finance', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('liste la synthèse par convention et ouvre le détail par adhérent @screen:discounts @req:BRD-09 @req:CPT-08', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/creances-escompte');
    await expect(page.getByTestId('page-title')).toHaveText("Créances d'escompte");

    const row = page.locator('[data-testid^="discount-row-"]').first();
    await expect(row).toBeVisible();
    await row.click();
    await expect(page.getByTestId('discount-detail')).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:discounts', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/creances-escompte');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Créances d’escompte — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:discounts', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/creances-escompte');
    await expect(page.getByRole('link', { name: 'Créances d’escompte' })).toHaveCount(0);
    await context.close();
  });
});
