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

test('CPT-07 : les six statuts d’une commission sont visibles avec leur motif @screen:distributor-account @path:CPT-07', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=CPT-07&entity=UNEP&month=2027-05-01')).ok()).toBeTruthy();

  const context = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await context.newPage();
  await page.goto('/distributeur');
  await page.getByTestId('acc-entity').selectOption('UNEP');
  await page.getByTestId('acc-partner').selectOption('P-CPT07');
  await page.getByTestId('acc-search').click();
  await expect(page.getByTestId('account-view')).toBeVisible();

  // Each of the six commission statuses of section 5.7 is shown.
  const lines = page.locator('[data-testid^="line-row-"]');
  for (const label of ['Prévisionnelle', 'Acquise', 'À payer', 'Payée', 'Imputée sur escompte', 'Annulée']) {
    await expect(lines.filter({ hasText: label }).first()).toBeVisible();
  }
  // The blocked payable line carries its reason (motif).
  await expect(lines.filter({ hasText: 'À payer' }).filter({ hasText: 'Blocage manuel' }).first()).toBeVisible();
  await context.close();
});

test('CPT-03 : la compensation ne se fait jamais entre deux sociétés distinctes @screen:distributor-account @path:CPT-03', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=CPT-03&entity=UNEP&month=2027-05-01')).ok()).toBeTruthy();

  // In UNEP, the distributor is a debtor of 100 €; compensating moves nothing (its credit is elsewhere).
  const unepCtx = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const unep = await unepCtx.newPage();
  await unep.goto('/distributeur');
  await unep.getByTestId('acc-entity').selectOption('UNEP');
  await unep.getByTestId('acc-month').selectOption('2027-05-01');
  await unep.getByTestId('acc-partner').selectOption('P-CPT03');
  await unep.getByTestId('acc-search').click();
  await expect(unep.getByTestId('account-total')).toContainText('100,00');
  await unep.getByTestId('acc-compensate').click();
  await expect(unep.getByTestId('acc-message')).toContainText('compensation');
  // Still a debtor of 100 € — the Alptis credit was never pulled across.
  await expect(unep.getByTestId('account-total')).toContainText('100,00');
  await expect(unep.locator('[data-testid^="line-row-"]').filter({ hasText: 'Compensation' })).toHaveCount(0);
  await unepCtx.close();

  // In Alptis, the same distributor is still a creditor of 100 € — untouched.
  const alptisCtx = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const alptis = await alptisCtx.newPage();
  await alptis.goto('/distributeur');
  await alptis.getByTestId('acc-entity').selectOption('ALPTIS_ASSURANCES');
  await alptis.getByTestId('acc-partner').selectOption('P-CPT03');
  await alptis.getByTestId('acc-search').click();
  await expect(alptis.getByTestId('account-total')).toContainText('100,00');
  await alptisCtx.close();
});

test('AJU-05 : une avance exceptionnelle est remboursée à ses échéances @screen:distributor-account @path:AJU-05', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=AJU-05&entity=UNEP&month=2027-07-01')).ok()).toBeTruthy();

  const context = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await context.newPage();
  await page.goto('/distributeur');

  // The debtor's three-instalment repayment schedule is listed (CPT-07, CAL-14).
  await expect(page.getByTestId('schedule-row').filter({ hasText: '3 échéances' }).first()).toBeVisible();

  // The account detail: the 300 € advance has been fully repaid by its three instalments (balance zero).
  await page.getByTestId('acc-entity').selectOption('UNEP');
  await page.getByTestId('acc-partner').selectOption('P-AJU05');
  await page.getByTestId('acc-search').click();
  await expect(page.getByTestId('account-view')).toBeVisible();
  await expect(page.getByTestId('account-total')).toContainText('0,00');
  await expect(page.getByTestId('chart-account-balance')).toBeVisible();
  // The exceptional-advance line is shown among the account's commission lines.
  await expect(page.locator('[data-testid^="line-row-"]').filter({ hasText: '300,00' }).first()).toBeVisible();
  await context.close();
});

test.describe('Compte distributeur — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('affiche les comptes, compense et bloque un compte @screen:distributor-account @req:CPT-06 @req:CPT-04 @req:CPT-05 @role-action:OPERATOR-compensate-accounts @role-action:OPERATOR-block-account', async ({
    page,
  }) => {
    await page.goto('/distributeur');
    // ADM-03: the distributor is chosen from a list shown by its name, never typed as a code.
    await expect(page.getByTestId('acc-partner').locator('option', { hasText: 'P13897' })).toHaveCount(1);
    await page.getByTestId('acc-partner').selectOption('P13897');
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

  // Parcours CPT-06 (section 4.3): the debtors without an active portfolio are listed and signalled for
  // a repayment demand, each carrying its repayment schedule (échéancier d'apurement).
  test('liste les débiteurs sans portefeuille actif et leur échéancier d’apurement @screen:distributor-account @req:CPT-07 @path:CPT-06', async ({
    page,
  }) => {
    // Record an exceptional advance with a repayment schedule (an apurement for a debtor).
    const auth = 'Basic ' + Buffer.from(
      `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
    const response = await page.request.post('/api/ui/v1/adjustments/ALPTIS_ASSURANCES/2026-09-01', {
      headers: { Authorization: auth },
      data: {
        type: 'EXCEPTIONAL_ADVANCE', amount: 300, payeeType: 'DISTRIBUTOR', payeeId: 1,
        reason: 'Avance exceptionnelle', repaymentSchedule: JSON.stringify('3 × 100 € mensuels'),
      },
    });
    expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();

    await page.goto('/distributeur');
    // CPT-06: the debtor account, whose production code is no longer active, is flagged for a repayment
    // demand (section 6.4 point 7).
    const debtor = page.locator('[data-testid^="debtor-row-"]').first();
    await expect(debtor).toBeVisible();
    await expect(debtor).toContainText('Sans portefeuille actif');
    // Its repayment schedule is shown.
    const schedule = page.getByTestId('schedule-row').first();
    await expect(schedule).toBeVisible();
    await expect(schedule).toContainText('3 × 100');
  });

  test('enregistre et affiche un échéancier d’apurement @screen:distributor-account @req:CPT-07 @req:CAL-14', async ({
    page,
  }) => {
    // Record an exceptional advance with a repayment schedule (CAL-14), an apurement for a debtor.
    const auth = 'Basic ' + Buffer.from(
      `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
    const response = await page.request.post('/api/ui/v1/adjustments/ALPTIS_ASSURANCES/2026-09-01', {
      headers: { Authorization: auth },
      data: {
        type: 'EXCEPTIONAL_ADVANCE',
        amount: 300,
        payeeType: 'DISTRIBUTOR',
        payeeId: 1,
        reason: 'Avance exceptionnelle',
        repaymentSchedule: JSON.stringify('3 × 100 € mensuels'),
      },
    });
    expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();

    await page.goto('/distributeur');
    const schedule = page.getByTestId('schedule-row').first();
    await expect(schedule).toBeVisible();
    await expect(schedule).toContainText('3 × 100');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:distributor-account', async ({ page }) => {
    await page.goto('/distributeur');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
