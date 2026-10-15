import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the acquired-versus-paid reconciliation (FIN-06): the global totals and the
// per-beneficiary breakdown; per-role access.
//
// @screen:reconciliation @req:FIN-06

test.describe('Rapprochement — contrôleur Finance', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('rapproche les commissions acquises et payées @screen:reconciliation @req:FIN-06', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/rapprochement');
    await expect(page.getByTestId('page-title')).toHaveText('Rapprochement des commissions acquises et payées');
    // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
    await expect(page.getByTestId('rec-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);

    // The global totals are shown, and at least one beneficiary row.
    await expect(page.getByTestId('rec-acquired')).toBeVisible();
    await expect(page.getByTestId('rec-paid')).toBeVisible();
    await expect(page.getByTestId('rec-gap')).toBeVisible();
    await expect(page.locator('[data-testid^="rec-row-"]').first()).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:reconciliation', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/rapprochement');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Rapprochement — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:reconciliation', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/rapprochement');
    await expect(page.getByRole('link', { name: 'Rapprochement acquises/payées' })).toHaveCount(0);
    await context.close();
  });
});
