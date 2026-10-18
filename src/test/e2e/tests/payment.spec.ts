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

test('PAI-02 : le lot exclut un bénéficiaire pour chacun des six motifs @screen:payment @path:PAI-02', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=PAI-02&entity=UNEP&month=2027-05-01')).ok()).toBeTruthy();

  const context = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await context.newPage();

  // Advance the business-validated period to payment-prepared (proposes the lot).
  await page.goto('/');
  await page.getByTestId('open-UNEP-2027-05-01').click();
  await expect(page.getByTestId('detail-state')).toContainText('Validée métier');
  await page.getByTestId('advance-button').click();
  await expect(page.getByTestId('detail-state')).toContainText('Paiement préparé');

  // Prepare the cycle lot, which applies the inactive / frequency / no-bank exclusions.
  await page.goto('/paiement');
  await page.getByTestId('pay-entity').selectOption('UNEP');
  await page.getByTestId('pay-month').selectOption('2027-05-01');
  await page.getByTestId('pay-refresh').click();
  await page.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  await page.getByTestId('lot-prepare').click();

  // Each of the six exclusion motifs of section 5.7 is shown.
  for (const label of [
    'Compte bloqué',
    'Solde non positif',
    'Sous le seuil de paiement',
    'Bénéficiaire inactif',
    'Hors fréquence de versement',
    'Sans coordonnées bancaires',
  ]) {
    await expect(page.locator('[data-testid^="exclusion-reason-"]', { hasText: label })).toHaveCount(1);
  }
  await context.close();
});

test('PAI-04 : le net négatif de l’épargne retraite est imputé en une ligne sur le bordereau du lot de cycle @screen:payment @screen:statements @path:PAI-04', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=PAI-04&entity=UNEP&month=2028-03-01')).ok()).toBeTruthy();

  const context = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await context.newPage();

  // Advance the business-validated period to payment-prepared (splits the lines into lots and proposes).
  await page.goto('/');
  await page.getByTestId('open-UNEP-2028-03-01').click();
  await expect(page.getByTestId('detail-state')).toContainText('Validée métier');
  await page.getByTestId('advance-button').click();
  await expect(page.getByTestId('detail-state')).toContainText('Paiement préparé');

  // A single cycle lot pays 150,00 € (200 € de cycle − 50 € d’épargne retraite imputés); no savings lot.
  await page.goto('/paiement');
  await page.getByTestId('pay-entity').selectOption('UNEP');
  await page.getByTestId('pay-month').selectOption('2028-03-01');
  await page.getByTestId('pay-refresh').click();
  await expect(page.locator('[data-testid^="lot-row-"]')).toHaveCount(1);
  await expect(page.locator('[data-testid^="lot-type-"]').filter({ hasText: 'SAVINGS_CYCLE' })).toHaveCount(0);
  await page.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  // The order equals the net of the bordereau (section 10.5, PAY-05).
  await expect(page.locator('[data-testid^="order-amount-"]')).toHaveCount(1);
  await expect(page.locator('[data-testid^="order-amount-"]').first()).toContainText('150,00');

  // On the paying bordereau, the imputation of the savings lot figures as exactly one line, and the net is
  // the cycle net (150 €) — the savings lines are not re-listed as rubric rows.
  await page.goto('/bordereaux');
  await page.getByTestId('brd-entity').selectOption('UNEP');
  await page.getByTestId('brd-month').selectOption('2028-03-01');
  await page.getByTestId('brd-refresh').click();
  await page.locator('[data-testid^="statement-open-"]').first().click();
  await expect(page.getByTestId('statement-detail')).toBeVisible();
  await expect(page.locator('[data-testid^="statement-line-section-"]', { hasText: 'Imputation' })).toHaveCount(1);
  await expect(page.getByTestId('statement-detail')).toContainText('Lot épargne retraite');
  await expect(page.getByTestId('statement-net')).toContainText('150,00');
  await context.close();
});

test('PAI-07 : la validation du paiement par celui qui a validé les montants est refusée @screen:payment @path:PAI-07 @role-action:FINANCE_CONTROLLER-validate-lot', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=PAI-07&entity=UNEP&month=2028-04-01')).ok()).toBeTruthy();

  // The amounts were validated under finance-unep's identity (seeded). Prepare the payment as the operator.
  const opContext = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const op = await opContext.newPage();
  await op.goto('/');
  await op.getByTestId('open-UNEP-2028-04-01').click();
  await expect(op.getByTestId('detail-state')).toContainText('Validée métier');
  await op.getByTestId('advance-button').click();
  await expect(op.getByTestId('detail-state')).toContainText('Paiement préparé');
  await opContext.close();

  // finance-unep, who validated the amounts, is refused the payment validation (separation of duties).
  const finContext = await browser.newContext({ httpCredentials: USERS.financeUnep });
  const fin = await finContext.newPage();
  await fin.goto('/paiement');
  await fin.getByTestId('pay-entity').selectOption('UNEP');
  await fin.getByTestId('pay-month').selectOption('2028-04-01');
  await fin.getByTestId('pay-refresh').click();
  await fin.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  await fin.getByTestId('lot-validate').click();
  await expect(fin.getByTestId('pay-error')).toContainText('Séparation des tâches');
  await finContext.close();
});

test('PAI-11 : un ordre réglé, un rejeté dont les lignes repartent payables puis réémises avec leur suffixe @screen:payment @screen:controls @screen:distributor-account @path:PAI-11 @role-action:FINANCE_CONTROLLER-record-bank-result', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=PAI-11&entity=UNEP&month=2028-05-01')).ok()).toBeTruthy();

  // Operator prepares the payment: business-validated -> payment-prepared proposes a cycle lot of 2 orders.
  const opCtx = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const op = await opCtx.newPage();
  await op.goto('/');
  await op.getByTestId('open-UNEP-2028-05-01').click();
  await expect(op.getByTestId('detail-state')).toContainText('Validée métier');
  await op.getByTestId('advance-button').click();
  await expect(op.getByTestId('detail-state')).toContainText('Paiement préparé');

  // Finance validates the cycle lot, then records the bank result: the 100 € settles, the 120 € is rejected.
  const finCtx = await browser.newContext({ httpCredentials: USERS.financeUnep });
  const fin = await finCtx.newPage();
  await fin.goto('/paiement');
  await fin.getByTestId('pay-entity').selectOption('UNEP');
  await fin.getByTestId('pay-month').selectOption('2028-05-01');
  await fin.getByTestId('pay-refresh').click();
  await fin.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  await fin.getByTestId('lot-validate').click();
  await expect(fin.locator('[data-testid^="order-status-"]').filter({ hasText: 'Émis' })).toHaveCount(2);
  await fin.locator('[data-testid^="order-row-"]', { hasText: '120,00' }).getByRole('button', { name: 'Rejeté' }).click();
  await fin.locator('[data-testid^="order-row-"]', { hasText: '100,00' }).getByRole('button', { name: 'Réglé' }).click();
  await expect(fin.locator('[data-testid^="order-status-"]').filter({ hasText: 'Réglé' })).toHaveCount(1);
  await expect(fin.locator('[data-testid^="order-status-"]').filter({ hasText: 'Rejeté' })).toHaveCount(1);
  await finCtx.close();

  // The rejection raised a payment anomaly on the controls screen.
  await op.goto('/controles');
  await op.getByTestId('ctl-entity').selectOption('UNEP');
  await op.getByTestId('ctl-month').selectOption('2028-05-01');
  await op.getByTestId('ctl-refresh').click();
  await expect(op.locator('[data-testid^="anomaly-row-"]', { hasText: 'PAY-REJECT' })).toHaveCount(1);

  // The settled beneficiary's line is paid; the rejected one's line returned payable.
  await op.goto('/distributeur');
  await op.getByTestId('acc-entity').selectOption('UNEP');
  await op.getByTestId('acc-partner').selectOption('P-PAI11-A');
  await op.getByTestId('acc-search').click();
  await expect(op.locator('[data-testid^="line-row-"]', { hasText: 'Payée' }).first()).toBeVisible();
  await op.getByTestId('acc-partner').selectOption('P-PAI11-B');
  await op.getByTestId('acc-search').click();
  await expect(op.locator('[data-testid^="line-row-"]', { hasText: 'À payer' }).first()).toBeVisible();

  // The rejected beneficiary is reissued off cycle: its order reference carries the -R1 reissue suffix.
  await op.goto('/paiement');
  await op.getByTestId('pay-entity').selectOption('UNEP');
  await op.getByTestId('pay-month').selectOption('2028-05-01');
  await op.getByTestId('pay-refresh').click();
  await op.getByTestId('pay-offcycle-partner').fill('P-PAI11-B');
  await op.getByTestId('pay-offcycle-submit').click();
  await op.getByTestId('pay-refresh').click();
  await op.locator('[data-testid^="lot-row-"]', { hasText: 'OFF_CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  await expect(op.getByTestId('lot-detail')).toContainText('-R1');
  await opCtx.close();
});

test('PAI-12 : un distributeur commun à deux entités est payé par un ordre dans chaque entité débitrice @screen:payment @screen:distributor-account @path:PAI-12', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=PAI-12&entity=UNEP&month=2028-06-01')).ok()).toBeTruthy();

  // Each entity drives its own cycle to payment-prepared (role-null transition, by that entity's operator).
  const unepCtx = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const unep = await unepCtx.newPage();
  await unep.goto('/');
  await unep.getByTestId('open-UNEP-2028-06-01').click();
  await unep.getByTestId('advance-button').click();
  await expect(unep.getByTestId('detail-state')).toContainText('Paiement préparé');

  const alptisCtx = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const alptis = await alptisCtx.newPage();
  await alptis.goto('/');
  await alptis.getByTestId('open-ALPTIS_ASSURANCES-2028-06-01').click();
  await alptis.getByTestId('advance-button').click();
  await expect(alptis.getByTestId('detail-state')).toContainText('Paiement préparé');

  // The same distributor is a creditor in both entities, with its own account and amount in each.
  await unep.goto('/distributeur');
  await unep.getByTestId('acc-entity').selectOption('UNEP');
  await unep.getByTestId('acc-partner').selectOption('P-PAI12');
  await unep.getByTestId('acc-search').click();
  await expect(unep.getByTestId('account-total')).toContainText('100,00');
  await alptis.goto('/distributeur');
  await alptis.getByTestId('acc-entity').selectOption('ALPTIS_ASSURANCES');
  await alptis.getByTestId('acc-partner').selectOption('P-PAI12');
  await alptis.getByTestId('acc-search').click();
  await expect(alptis.getByTestId('account-total')).toContainText('60,00');

  // Each entity's cycle lot carries its own order for the distributor (one order per debtor entity).
  await unep.goto('/paiement');
  await unep.getByTestId('pay-entity').selectOption('UNEP');
  await unep.getByTestId('pay-month').selectOption('2028-06-01');
  await unep.getByTestId('pay-refresh').click();
  await unep.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  await expect(unep.getByTestId('lot-detail')).toContainText('100,00');

  await alptis.goto('/paiement');
  await alptis.getByTestId('pay-entity').selectOption('ALPTIS_ASSURANCES');
  await alptis.getByTestId('pay-month').selectOption('2028-06-01');
  await alptis.getByTestId('pay-refresh').click();
  await alptis.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();
  await expect(alptis.getByTestId('lot-detail')).toContainText('60,00');

  await unepCtx.close();
  await alptisCtx.close();
});

test('CPT-04 : un compte bloqué est exclu du lot jusqu’à sa date de levée @screen:payment @path:CPT-04', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=CPT-04&entity=UNEP&month=2027-05-01')).ok()).toBeTruthy();

  const context = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await context.newPage();

  // Advance the business-validated period to payment-prepared, which proposes the lot.
  await page.goto('/');
  await page.getByTestId('open-UNEP-2027-05-01').click();
  await expect(page.getByTestId('detail-state')).toContainText('Validée métier');
  await page.getByTestId('advance-button').click();
  await expect(page.getByTestId('detail-state')).toContainText('Paiement préparé');

  // On the payment screen, open the cycle lot.
  await page.goto('/paiement');
  await page.getByTestId('pay-entity').selectOption('UNEP');
  await page.getByTestId('pay-month').selectOption('2027-05-01');
  await page.getByTestId('pay-refresh').click();
  await page.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' }).getByRole('button', { name: 'Détail' }).click();

  // The still-blocked account is excluded from the lot; the account whose block has lapsed is paid.
  await expect(page.locator('[data-testid^="exclusion-reason-"]', { hasText: 'Compte bloqué' })).toHaveCount(1);
  await expect(page.locator('[data-testid^="order-amount-"]')).toHaveCount(1);
  await expect(page.locator('[data-testid^="order-amount-"]').first()).toContainText('150,00');
  await context.close();
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

test('la constitution du lot de cycle donne un ordre par bénéficiaire, son total égal à la somme des lignes @screen:payment @req:PAY-01 @path:PAI-01', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/paiement');
  const cycleRow = page.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' });
  await cycleRow.getByRole('button', { name: 'Détail' }).click();
  await page.getByTestId('lot-prepare').click();

  // PAI-01: the preparation yields one order per beneficiary (distinct payees). Wait for the prepared
  // orders to render before reading, so the assertions see the settled state and not a mid-prepare one.
  const orderRows = page.locator('[data-testid^="order-row-"]');
  await expect(orderRows.first()).toBeVisible();
  const count = await orderRows.count();
  expect(count).toBeGreaterThan(0);
  const payees = await page.locator('[data-testid^="order-payee-"]').allTextContents();
  expect(new Set(payees).size).toBe(count);

  // PAI-01: the lot total equals the sum of its orders (each the sum of a beneficiary's lines, CR-64).
  // Cancelled orders (excluded beneficiaries) stay listed but do not count toward the lot total, so the
  // sum is taken over the orders that are not cancelled. The total and the rows are re-read together
  // under a poll so the comparison always sees a consistent snapshot, never a partial re-render.
  const parseEuro = (s: string): number => parseFloat(s.replace(/[^0-9,]/g, '').replace(',', '.'));
  await expect
    .poll(async () => {
      let sum = 0;
      const n = await orderRows.count();
      for (let i = 0; i < n; i += 1) {
        const row = orderRows.nth(i);
        const status = ((await row.locator('[data-testid^="order-status-"]').textContent()) ?? '').trim();
        if (status === 'Annulé') continue;
        sum += parseEuro(((await row.locator('[data-testid^="order-amount-"]').textContent()) ?? ''));
      }
      const total = parseEuro((await page.getByTestId('lot-total').textContent()) ?? '');
      return Math.abs(total - sum);
    })
    .toBeLessThan(0.005);
  await operator.close();
});

test('la Finance saisit l’émission des chèques @screen:payment @req:PAY-04 @path:PAI-05 @role-action:FINANCE_CONTROLLER-record-cheque-emission', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  await page.goto('/paiement');
  const chequeRow = page.locator('[data-testid^="lot-row-"]', { hasText: 'CHEQUE' });
  await expect(chequeRow).toHaveCount(1);
  await chequeRow.getByRole('button', { name: 'Détail' }).click();

  // PAI-05: the cheque list is downloaded and read before the emission is recorded.
  await page.getByTestId('lot-cheque-list').click();
  await expect(page.getByTestId('cheque-list-content')).toContainText('beneficiaire;montant;reference_cheque');

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

// Parcours INT-19 (section 4.2): a payment rule whose beneficiary shares do not total 100 % is
// refused, and a rule's periodicity is applied to the lot — a half-yearly payee is excluded from a
// month that is not a half-year close, its amount staying on the account.
test('une règle de versement aux parts ≠ 100 % est refusée, sa périodicité exclut du lot hors échéance @screen:payment @req:PAY-02 @path:INT-19', async ({
  browser,
}) => {
  const OP_AUTH = 'Basic ' + Buffer.from(
    `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();

  // Two payment rules on the creditor production code: one whose shares total 80 % (refused), and a
  // valid half-yearly rule in force from January.
  const imp = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OP_AUTH },
    data: {
      entityCode: 'ALPTIS_ASSURANCES', flowCode: 'payee_rules', sourceSystem: 'SESAME', fileName: 'r.csv',
      content: 'production_code;partner_code;payment_frequency;shares;valid_from;valid_to\n'
        + 'PC-CREDIT;;MONTHLY;PRODUCTION_CODE:PC-CREDIT:0.5|PRODUCTION_CODE:PC-DEBIT:0.3;;\n'
        + 'PC-CREDIT;;HALF_YEARLY;PRODUCTION_CODE:PC-CREDIT:1;2026-01-01;\n',
    },
  });
  expect(imp.ok(), `${imp.status()} ${await imp.text()}`).toBeTruthy();
  const result = await imp.json();
  // The parts-≠-100 % line is refused; the valid half-yearly rule is integrated.
  expect(result.linesRejected).toBe(1);
  expect(result.linesLoaded).toBe(1);

  // Preparing the lot applies the periodicity: September is not a half-year close, so the creditor is
  // excluded from the lot with its frequency reason.
  await page.goto('/paiement');
  const cycleRow = page.locator('[data-testid^="lot-row-"]', { hasText: 'CYCLE' });
  await cycleRow.getByRole('button', { name: 'Détail' }).click();
  await page.getByTestId('lot-prepare').click();
  await expect(page.locator('[data-testid^="exclusion-reason-"]', { hasText: 'Hors fréquence de versement' }))
    .toHaveCount(1);
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
