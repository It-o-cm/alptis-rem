import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the annual tax certificates (BRD-08): the paid-commission total per
// beneficiary for the fiscal year; per-role access.
//
// @screen:taxCertificates @req:BRD-08

test.describe('Attestations fiscales — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les attestations fiscales par bénéficiaire @screen:taxCertificates @req:BRD-08 @path:BRD-08', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/attestations-fiscales');
    await expect(page.getByTestId('page-title')).toHaveText('Attestations fiscales annuelles');
    // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
    await expect(page.getByTestId('tax-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
    // BRD-08: the certificate carries the civil-year total of commissions paid to the beneficiary.
    const row = page.locator('[data-testid^="tax-row-"]').first();
    await expect(row).toBeVisible();
    await expect(row).toContainText('2026');
    await expect(row).toContainText('€');
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
