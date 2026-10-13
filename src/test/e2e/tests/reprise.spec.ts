import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the history reprise (ING-11): an opening-balance file opens a migrated
// account at its carried balance. The import controls are verified by OpeningBalanceImportServiceTest
// on the server; this exercises the screen against the real import.
//
// @screen:reprise @req:ING-11

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur dépose un fichier d’ouverture et lit les compteurs @screen:reprise @req:ING-11 @role-action:OPERATOR-import-opening-balance', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/reprise');
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('reprise-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await page.getByTestId('reprise-content').fill(
    'payee_type;payee_code;opening_balance\nPRODUCTION_CODE;PC-DEBIT;-200.00\n',
  );
  await page.getByTestId('reprise-upload').click();
  await expect(page.getByTestId('reprise-loaded')).toHaveText('1');
  await operator.close();
});

test.describe('Reprise — accessibilité', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:reprise', async ({ page }) => {
    await page.goto('/reprise');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
