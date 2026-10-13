import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the claims screen (REG-06): list, fiche with contested lines, recording and
// closing a claim; per-role access.
//
// @screen:claims @req:REG-06

test.describe('Réclamations — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('consulte la fiche d’une réclamation, en enregistre une et la clôt @screen:claims @req:REG-06', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/reclamations');
    await expect(page.getByTestId('page-title')).toHaveText('Réclamations');

    // The demo seeds a claim: open its fiche and read the contested line.
    const row = page.locator('[data-testid^="claim-row-"]').first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Fiche' }).click();
    await expect(page.getByTestId('claim-detail')).toBeVisible();
    await expect(page.locator('[data-testid^="contested-line-"]').first()).toBeVisible();

    // Close that claim.
    await page.getByTestId('claim-resolution').fill('Taux corrigé, ajustement passé');
    await page.getByTestId('claim-resolve').click();
    await expect(page.getByTestId('claim-message')).toContainText('close');

    // Record a new claim for a known distributor, picked from the list by its name (ADM-03).
    await expect(page.getByTestId('claim-distributor').locator('option', { hasText: 'Cabinet Martin' })).toHaveCount(1);
    await page.getByTestId('claim-distributor').selectOption('P13897');
    await page.getByTestId('claim-subject').fill('Commission sous-évaluée');
    await page.getByTestId('claim-submit').click();
    await expect(page.getByTestId('claim-message')).toContainText('enregistrée');
    await expect(page.getByText('Commission sous-évaluée')).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:claims', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/reclamations');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Réclamations — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:claims', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/reclamations');
    await expect(page.getByRole('link', { name: 'Réclamations' })).toHaveCount(0);
    await context.close();
  });
});
