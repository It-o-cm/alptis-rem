import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the adjustments: submission, the two-signature approval, bulk import and the
// automatic reversal (REG-01 to REG-05).
//
// @screen:adjustments @req:REG-01 @req:REG-02 @req:REG-03 @req:REG-04 @req:REG-05

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur saisit un ajustement, un administrateur l’approuve @screen:adjustments @req:REG-01 @req:REG-02 @req:REG-03 @role-action:OPERATOR-submit-adjustment @role-action:BUSINESS_ADMIN-approve-adjustment', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/ajustements');
  await page.getByTestId('adj-type').selectOption('CORRECTION');
  await page.getByTestId('adj-amount').fill('9');
  await page.getByTestId('adj-payee').fill('1');
  await page.getByTestId('adj-reason').fill('Correction du taux');
  // REG-02: the adjustment accepts an attachment reference, shown back in the list.
  await page.getByTestId('adj-attachment').fill('GED-2026-07-123');
  await page.getByTestId('adj-submit').click();
  await expect(page.locator('[data-testid^="adjustment-status-"]').first()).toContainText('SUBMITTED');
  await expect(page.getByText('GED-2026-07-123').first()).toBeVisible();
  await operator.close();

  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/ajustements');
  await adminPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(adminPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('APPLIED');
  await admin.close();
});

test('l’opérateur importe des ajustements en masse @screen:adjustments @req:REG-04 @role-action:OPERATOR-submit-adjustment', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/ajustements');
  await expect(page.getByTestId('adj-bulk')).toBeVisible();
  const csv =
    'adjustment_type;payee_code;amount;reason;auto_reverse\n' +
    'CORRECTION;PC-CREDIT;42.00;import en masse;false\n' +
    'CORRECTION;INCONNU;9.00;ignorée;false\n';
  await page.getByTestId('adj-bulk-file').setInputFiles({
    name: 'ajustements.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  });
  await expect(page.getByTestId('adj-message')).toContainText('1 ajustement(s) importé(s)');
  await expect(page.locator('[data-testid^="adjustment-status-"]').first()).toContainText('SUBMITTED');
  await operator.close();
});

test('un ajustement à contrepassation automatique crée sa reprise au mois suivant @screen:adjustments @req:REG-05', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/ajustements');
  await page.getByTestId('adj-type').selectOption('EXCEPTIONAL_ADVANCE');
  await page.getByTestId('adj-amount').fill('1000');
  await page.getByTestId('adj-payee').fill('7');
  await page.getByTestId('adj-reason').fill('Avance exceptionnelle');
  await page.getByTestId('adj-reverse').check();
  await page.getByTestId('adj-submit').click();
  await expect(page.getByTestId('adj-message')).toContainText('soumis');
  await operator.close();

  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/ajustements');
  await adminPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(adminPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('APPLIED');

  // The reversal lives on the next period (October): switch the month and find the clawback.
  await adminPage.getByTestId('adj-month').fill('2026-10-01');
  await adminPage.getByTestId('adj-refresh').click();
  await expect(adminPage.getByText('Contrepassation')).toBeVisible();
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
