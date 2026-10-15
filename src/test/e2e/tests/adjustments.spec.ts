import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, resetDb, seedCycle, USERS } from '../support/helpers';

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
  // ADM-03: the entity and month are chosen from lists shown by their names, never typed as codes.
  await expect(page.getByTestId('adj-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('adj-month').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
  // ADM-03: the adjustment type is picked from a list shown by its French label, never a raw code.
  await expect(page.getByTestId('adj-type').locator('option', { hasText: 'Correction' })).toHaveCount(1);
  await page.getByTestId('adj-type').selectOption('CORRECTION');
  await page.getByTestId('adj-amount').fill('9');
  await page.getByTestId('adj-payee').fill('1');
  await page.getByTestId('adj-reason').fill('Correction du taux');
  // REG-02: the adjustment accepts an attachment reference, shown back in the list.
  await page.getByTestId('adj-attachment').fill('GED-2026-07-123');
  await page.getByTestId('adj-submit').click();
  // ADM-03: the status is shown by its French label, not the raw enum code.
  await expect(page.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Soumis');
  await expect(page.getByText('GED-2026-07-123').first()).toBeVisible();
  // Separation of duties (section 3, rule 4): the author cannot approve their own adjustment.
  await page.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(page.getByTestId('adj-error')).toContainText('second utilisateur');
  await operator.close();

  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/ajustements');
  await adminPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(adminPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Appliqué');
  await admin.close();
});

// Parcours AJU-02 (section 4.4): an adjustment requires a reason (refused without one), accepts an
// attachment reference (shown back) and links to the corrected line (shown as #id). The corrected line
// is a real demo line, read from the seeded claim's contested line.
test('un ajustement exige un motif, porte une pièce jointe et le lien à la ligne corrigée @screen:adjustments @req:REG-02 @path:AJU-02', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await loadDemo(page.request);

  // Read a real commission line id from the demo claim's contested line.
  await page.goto('/reclamations');
  await page.locator('[data-testid^="claim-row-"]').first().getByRole('button', { name: 'Fiche' }).click();
  const contested = page.locator('[data-testid^="contested-line-"]').first();
  await expect(contested).toBeVisible();
  const lineId = ((await contested.getAttribute('data-testid')) ?? '').replace('contested-line-', '');

  await page.goto('/ajustements');
  // Reason absent: the adjustment is refused.
  await page.getByTestId('adj-type').selectOption('CORRECTION');
  await page.getByTestId('adj-amount').fill('9');
  await page.getByTestId('adj-payee').fill('1');
  await page.getByTestId('adj-reason').fill('');
  await page.getByTestId('adj-submit').click();
  await expect(page.getByTestId('adj-error')).toContainText('motif');

  // With a reason, an attachment and a corrected line: accepted, both shown back.
  await page.getByTestId('adj-reason').fill('Correction du taux');
  await page.getByTestId('adj-attachment').fill('GED-2026-09-77');
  await page.getByTestId('adj-corrected-line').fill(lineId);
  await page.getByTestId('adj-submit').click();
  await expect(page.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Soumis');
  await expect(page.getByText('GED-2026-09-77').first()).toBeVisible();
  await expect(page.getByText(`#${lineId}`).first()).toBeVisible();
  await operator.close();
});

// Parcours AJU-03 (section 4.4): approval of an adjustment above the entity threshold. The author
// cannot approve (refus), a non-validator second above the threshold cannot either (refus), and the
// business validator approves (approuvé). The threshold is first set on the entity-parameters screen.
test('l’approbation d’un ajustement au-dessus du seuil est refusée sauf au valideur métier @screen:adjustments @req:REG-03 @path:AJU-03', async ({
  browser,
}) => {
  // A business admin sets the entity's adjustment approval threshold to 50,00 €.
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/entites');
  await adminPage.getByTestId('entity-row-ALPTIS_ASSURANCES').click();
  await expect(adminPage.getByTestId('entity-form')).toBeVisible();
  await adminPage.getByTestId('field-approval-threshold').fill('50');
  await adminPage.getByTestId('save-entity').click();
  await expect(adminPage.getByTestId('save-message')).toHaveText('Paramètres enregistrés.');

  // An operator submits an adjustment of 100,00 €, above the threshold.
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const opPage = await operator.newPage();
  await opPage.goto('/ajustements');
  await opPage.getByTestId('adj-type').selectOption('CORRECTION');
  await opPage.getByTestId('adj-amount').fill('100');
  await opPage.getByTestId('adj-payee').fill('1');
  await opPage.getByTestId('adj-reason').fill('Correction au-dessus du seuil');
  await opPage.getByTestId('adj-submit').click();
  await expect(opPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Soumis');

  // Case 1: the author cannot approve their own adjustment.
  await opPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(opPage.getByTestId('adj-error')).toContainText('second utilisateur');
  await operator.close();

  // Case 3: a non-validator second, above the threshold, is refused.
  await adminPage.goto('/ajustements');
  await adminPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(adminPage.getByTestId('adj-error')).toContainText('seuil');
  await admin.close();

  // Case 2: the business validator approves — applied.
  const validator = await browser.newContext({ httpCredentials: USERS.validatorAlptis });
  const valPage = await validator.newPage();
  await valPage.goto('/ajustements');
  await valPage.locator('[data-testid^="adjustment-approve-"]').first().click();
  await expect(valPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Appliqué');
  await validator.close();
});

test('l’opérateur importe des ajustements en masse @screen:adjustments @req:REG-04 @role-action:OPERATOR-submit-adjustment @path:AJU-07', async ({
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
  await expect(page.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Soumis');
  await operator.close();
});

test('un ajustement à contrepassation automatique crée sa reprise au mois suivant @screen:adjustments @req:REG-05 @path:AJU-04', async ({
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
  await expect(adminPage.locator('[data-testid^="adjustment-status-"]').first()).toContainText('Appliqué');

  // The reversal lives on the next period (October), which the approval opened: reload so the month
  // catalogue now offers it, pick it from the list (ADM-03) and find the clawback.
  await adminPage.goto('/ajustements');
  await expect(adminPage.getByTestId('adj-month').locator('option', { hasText: 'octobre 2026' })).toHaveCount(1);
  await adminPage.getByTestId('adj-month').selectOption('2026-10-01');
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
