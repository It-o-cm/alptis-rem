import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the annual tax certificates (BRD-08): the paid-commission total per
// beneficiary for the fiscal year; per-role access.
//
// @screen:taxCertificates @req:BRD-08

test.describe('Attestations fiscales — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les attestations fiscales par bénéficiaire @screen:taxCertificates @req:BRD-08', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/attestations-fiscales');
    await expect(page.getByTestId('page-title')).toHaveText('Attestations fiscales annuelles');
    // The full demo settles payments, so at least one beneficiary has a certificate for the year.
    await expect(page.locator('[data-testid^="tax-row-"]').first()).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:taxCertificates', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/attestations-fiscales');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Attestations fiscales — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:taxCertificates', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/attestations-fiscales');
    await expect(page.getByRole('link', { name: 'Attestations fiscales' })).toHaveCount(0);
    await context.close();
  });
});
