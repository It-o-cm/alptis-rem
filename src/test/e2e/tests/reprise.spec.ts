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

test('HIS-01 : la reprise ouvre soldes, escomptes et cumuls, et la première période se calcule dessus @screen:reprise @screen:calculations @screen:commissionSearch @req:ING-11 @path:HIS-01', async ({
  browser,
  request,
}) => {
  const month = '2027-02-01';
  expect((await request.post(`/api/test/cr-scenario?cr=HIS-01&entity=UNEP&month=${month}`)).ok()).toBeTruthy();

  const op = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await op.newPage();
  await page.goto('/reprise');
  await page.getByTestId('reprise-entity').selectOption('UNEP');

  // Lot by lot (ING-11): opening account balances (soldes des comptes).
  await page.getByTestId('reprise-dataset').selectOption('opening_balances');
  await page
    .getByTestId('reprise-content')
    .fill('payee_type;payee_code;opening_balance\nDISTRIBUTOR;P-HIS01;-250.00\n');
  await page.getByTestId('reprise-upload').click();
  await expect(page.getByTestId('reprise-loaded')).toHaveText('1');

  // Open discount positions (positions d'escompte en cours).
  await page.getByTestId('reprise-dataset').selectOption('opening_advances');
  await page
    .getByTestId('reprise-content')
    .fill('coverage_code;advanced;total_due;settlement_due_date\nHIS01-1-C1;400.00;400.00;2027-12-31\n');
  await page.getByTestId('reprise-upload').click();
  await expect(page.getByTestId('reprise-loaded')).toHaveText('1');

  // Annual surcommission counters (cumuls annuels) : January's carried cumul of the RCD rule.
  await page.getByTestId('reprise-dataset').selectOption('opening_counters');
  await page
    .getByTestId('reprise-content')
    .fill(
      'rule_code;payee_type;payee_code;year;month;cumulative_basis;cumulative_entitlement\n' +
        'R-HIS01-RCD;DISTRIBUTOR;P-HIS01;2027;1;30000.00;300.00\n',
    );
  await page.getByTestId('reprise-upload').click();
  await expect(page.getByTestId('reprise-loaded')).toHaveText('1');

  // The first month (February) is calculated on top of the opening state: the development remuneration
  // pays only the 1 050,00 € gain over the carried January cumul, not the whole year (ING-11, §6.7).
  await page.goto('/calculs');
  await page.getByTestId('calc-period').selectOption(`UNEP|${month}`);
  await page.getByTestId('close-development').click();
  await expect(page.getByTestId('calc-message')).toContainText('Surcommission de développement calculée');

  await page.goto('/recherche-commissions');
  await page.getByTestId('cs-month').selectOption(month);
  await page.getByTestId('cs-search').click();
  const row = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Développement (RCD)' }).first();
  await expect(row).toContainText('1 050,00');
  await op.close();
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
