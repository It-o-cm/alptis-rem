import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the distributor account: balances, compensation, block and debtor list
// (CPT-02, CPT-04, CPT-05, CPT-06, CPT-07, section 8.8).
//
// @screen:distributor-account @req:CPT-02 @req:CPT-04 @req:CPT-05 @req:CPT-06 @req:CPT-07

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test.describe('Compte distributeur — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('affiche les comptes, compense et bloque un compte @screen:distributor-account @req:CPT-06 @req:CPT-04 @req:CPT-05 @role-action:OPERATOR-compensate-accounts @role-action:OPERATOR-block-account', async ({
    page,
  }) => {
    await page.goto('/distributeur');
    await page.getByTestId('acc-partner').fill('P13897');
    await page.getByTestId('acc-search').click();
    await expect(page.getByTestId('account-view')).toBeVisible();
    await expect(page.getByTestId('account-total')).toContainText('400,00');
    await expect(page.getByTestId('chart-account-balance')).toBeVisible();

    await page.getByTestId('acc-compensate').click();
    await expect(page.getByTestId('acc-message')).toContainText('compensation');
    await expect(page.getByTestId('account-total')).toContainText('400,00');

    // Block an account with a reason and a lifting date (CPT-05).
    await page.getByTestId('block-reason').fill('Litige en cours');
    await page.getByTestId('block-until').fill('2026-12-31');
    const block = page.locator('[data-testid^="account-block-"]').first();
    await block.click();
    const blockedRow = page.locator('[data-testid^="account-row-"]').filter({ hasText: 'Oui' }).first();
    await expect(blockedRow).toContainText('Litige en cours');
    await expect(blockedRow).toContainText('2026-12-31');

    // Block a single commission line with the same reason and date (CPT-05).
    const lineBlock = page.locator('[data-testid^="line-block-"]').first();
    await lineBlock.click();
    await expect(page.locator('[data-testid^="line-row-"]').filter({ hasText: 'Oui' }).first())
      .toContainText('Litige en cours');
  });

  test('liste les comptes débiteurs @screen:distributor-account @req:CPT-07', async ({ page }) => {
    await page.goto('/distributeur');
    await expect(page.locator('[data-testid^="debtor-row-"]').first()).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:distributor-account', async ({ page }) => {
    await page.goto('/distributeur');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
