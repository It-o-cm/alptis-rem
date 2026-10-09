import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the adjustments: submission and the two-signature approval (REG-01, REG-03).
//
// @screen:adjustments @req:REG-01 @req:REG-03

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur saisit un ajustement, un administrateur l’approuve @screen:adjustments @req:REG-01 @req:REG-03 @role-action:OPERATOR-submit-adjustment @role-action:BUSINESS_ADMIN-approve-adjustment', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/ajustements');
  await page.getByTestId('adj-type').selectOption('CORRECTION');
  await page.getByTestId('adj-amount').fill('9');
  await page.getByTestId('adj-payee').fill('1');
  await page.getByTestId('adj-reason').fill('Correction du taux');
  await page.getByTestId('adj-submit').click();
  await expect(page.locator('[data-testid^="adjustment-status-"]').first()).toContainText('SUBMITTED');
  await operator.close();

  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/ajustements');
  await adminPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(adminPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('APPLIED');
  await admin.close();
});

test.describe('Ajustements — accessibilité', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:adjustments', async ({ page }) => {
    await page.goto('/ajustements');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
