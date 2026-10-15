import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the payment screen: a cycle lot with its orders and exclusions, preparation,
// Finance validation with the SEPA file, the bank result and the off-cycle lot (PAY-01, PAY-02,
// PAY-03, PAY-06, PAY-07).
//
// @screen:payment @req:PAY-01 @req:PAY-02 @req:PAY-03 @req:PAY-04 @req:PAY-06 @req:PAY-07 @req:REF-08

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur prépare le lot, la Finance valide, produit le SEPA et règle l’ordre @screen:payment @req:PAY-01 @req:PAY-02 @req:PAY-03 @req:PAY-06 @req:REF-08 @role-action:OPERATOR-prepare-lot @role-action:FINANCE_CONTROLLER-validate-lot @role-action:FINANCE_CONTROLLER-record-bank-result @path:PAI-10', async ({
  browser,
}) => {
  // The operator prepares the cycle lot: an order gets a resolved IBAN and exclusions are listed.
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const operatorPage = await operator.newPage();
  await operatorPage.goto('/paiement');
  // ADM-03: the entity and month are chosen from lists shown by their names, never typed as codes.
  await expect(operatorPage.getByTestId('pay-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(operatorPage.getByTestId('pay-month').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
  const cycleRow = operatorPage.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' });
  await expect(cycleRow).toHaveCount(1);
  await cycleRow.getByRole('button', { name: 'Détail' }).click();
  await operatorPage.getByTestId('lot-prepare').click();
  await expect(operatorPage.locator('[data-testid^="order-iban-"]').first()).toContainText('FR76');
  // The debtor account is excluded for a non-positive balance.
  await expect(operatorPage.locator('[data-testid^="exclusion-reason-"]', { hasText: 'Solde non positif' }))
    .toHaveCount(1);
  // REF-08: a distributor terminated before the period is excluded as an inactive payee.
  await expect(operatorPage.locator('[data-testid^="exclusion-reason-"]', { hasText: 'Bénéficiaire inactif' }))
    .toHaveCount(1);
  await operator.close();

  // The Finance controller validates the lot, which issues its orders, then reads the SEPA file.
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const financePage = await finance.newPage();
  await financePage.goto('/paiement');
  await financePage.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' })
    .getByRole('button', { name: 'Détail' }).click();
  await financePage.getByTestId('lot-validate').click();
  await expect(financePage.locator('[data-testid^="order-status-"]').first()).toContainText('Émis');

  await financePage.getByTestId('lot-sepa').click();
  // PAI-10: the SEPA file carries the number of orders, the control sum, each end-to-end id, a label
  // and the requested execution date.
  const sepa = financePage.getByTestId('sepa-content');
  await expect(sepa).toContainText('pain.001.001.03');
  await expect(sepa).toContainText('<NbOfTxs>');
  await expect(sepa).toContainText('<CtrlSum>');
  await expect(sepa).toContainText('<EndToEndId>');
  await expect(sepa).toContainText('<ReqdExctnDt>2026-');
  await expect(sepa).toContainText('<Ustrd>');

  // The bank settles the order; re-reading the lot shows it settled (effect seen on reload).
  await financePage.locator('[data-testid^="order-settle-"]').first().click();
  await expect(financePage.locator('[data-testid^="order-status-"]').first()).toContainText('Réglé');
  await finance.close();
});

test('la Finance saisit l’émission des chèques @screen:payment @req:PAY-04 @role-action:FINANCE_CONTROLLER-record-cheque-emission', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  await page.goto('/paiement');
  const chequeRow = page.locator('[data-testid^="lot-row-"]', { hasText: 'CHEQUE' });
  await expect(chequeRow).toHaveCount(1);
  await chequeRow.getByRole('button', { name: 'Détail' }).click();

  await page.getByTestId('lot-cheque-emission').click();
  await expect(page.getByTestId('pay-message')).toContainText('Émission des chèques');
  await expect(chequeRow).toContainText('Réglé');
  await finance.close();
});

test('l’opérateur crée un lot hors-cycle pour un distributeur @screen:payment @req:PAY-07 @role-action:OPERATOR-create-off-cycle', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/paiement');
  await page.getByTestId('pay-offcycle-partner').fill('P13897');
  await page.getByTestId('pay-offcycle-submit').click();
  await expect(page.getByTestId('pay-message')).toContainText('hors-cycle');
  await expect(page.locator('[data-testid^="lot-type-"]', { hasText: 'OFF_CYCLE' })).toHaveCount(1);
  await operator.close();
});

test.describe('Paiement — accessibilité', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:payment', async ({ page }) => {
    await page.goto('/paiement');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
