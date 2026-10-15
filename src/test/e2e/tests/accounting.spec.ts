import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the accounting screen: the schema and mapping (FIN-02), the entry search
// (FIN-05) and the monthly extraction at closing (FIN-03). The accounting cycle itself (CR-65/66,
// FIN-01) is verified by AccountingServiceTest on the server, as noted in docs/DECISIONS.md.
//
// @screen:accounting @req:FIN-02 @req:FIN-03 @req:FIN-05

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

// Parcours FIN-01 (section 4.6): before the business validation of a period, the accounting carries no
// entry. September is only calculated under seedCycle, so its extraction is header-only.
test('avant la validation métier la comptabilité ne porte aucune écriture @screen:accounting @req:FIN-01 @path:FIN-01', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  await page.goto('/comptabilite');
  await expect(page.getByTestId('acc-month').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
  await page.getByTestId('acc-extract').click();
  await expect(page.getByTestId('acc-message')).toContainText('ALPTIS_ASSURANCES_ECR_202609.csv');
  const csv = (await page.getByTestId('acc-extract-csv').textContent()) ?? '';
  // No entry: the CSV is header-only, so no six-digit account code appears (headers are column names).
  expect(csv).not.toMatch(/\d{6}/);
  await finance.close();
});

test('le contrôleur Finance lit le schéma et produit l’extraction @screen:accounting @req:FIN-02 @req:FIN-03 @req:FIN-05 @path:FIN-02 @path:FIN-03 @role-action:OPERATOR-extract-accounting', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  await page.goto('/comptabilite');
  // ADM-03: the entity and month are chosen from lists shown by their names, never typed as codes.
  await expect(page.getByTestId('acc-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('acc-month').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
  // The schema carries the accounting events, shown by their French label (FIN-02, ADM-03: no raw code).
  await expect(page.getByTestId('acc-scheme')).toContainText('Constatation du prévisionnel');
  await expect(page.getByTestId('acc-scheme')).toContainText('Provision pour commissions à payer');
  // FIN-02: the scheme defines a balanced double-entry (a debit and a credit role) for each of the
  // several section-10.2 accounting events; the forecast event shows both its debit and credit sides.
  expect(await page.locator('[data-testid^="scheme-row-"]').count()).toBeGreaterThanOrEqual(6);
  await expect(page.getByTestId('acc-scheme')).toContainText('Charge de commission prévisionnelle');
  await expect(page.getByTestId('acc-scheme')).toContainText('Commissions acquises');
  await expect(page.getByTestId('acc-scheme')).toContainText('Produits de commissions');
  // FIN-03: each scheme variant is shown with the direction of its entries — the standard case, the
  // negative-amount reversal, the no-forecast earning and the retirement-savings advance.
  await expect(page.getByTestId('acc-scheme')).toContainText('Standard');
  await expect(page.getByTestId('acc-scheme')).toContainText('Montant négatif');
  await expect(page.getByTestId('acc-scheme')).toContainText('Sans prévisionnel');
  await expect(page.getByTestId('acc-scheme')).toContainText('Épargne retraite');
  // The extraction is produced and its CSV carries the OUT-02 columns (FIN-03).
  await page.getByTestId('acc-extract').click();
  await expect(page.getByTestId('acc-message')).toContainText('ALPTIS_ASSURANCES_ECR_202609.csv');
  // The extraction carries the OUT-02 columns (section 9.11): entity, fiscal year, accounting month,
  // journal, event, the double-entry debit/credit accounts and the amount.
  const csv = page.getByTestId('acc-extract-csv');
  await expect(csv).toContainText('entity_code;fiscal_year;accounting_month;journal;entry_date;accounting_event');
  await expect(csv).toContainText('debit_account;credit_account;amount');
  await finance.close();
});

test('le contrôleur Finance remonte la chaîne du virement au bordereau, à la ligne, à l’événement, à la règle et à l’écriture @screen:accounting @req:FIN-05 @req:FIN-07 @path:REP-02', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  // The full demo runs the real cycle and pays past periods, so paid entries carry a full chain.
  await loadDemo(page.request);
  await page.goto('/comptabilite');

  // Cross-month search across the entity's entries (FIN-05).
  await page.getByTestId('acc-cross-search').click();
  const rows = page.locator('[data-testid^="cross-entry-row-"]');
  await expect(rows.first()).toBeVisible();
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);

  // REP-02: find a paid commission entry and read its full audit chain on a single entry — from the
  // payment order (virement) to the bordereau, the commission line, the source event, the rule version
  // and the accounting entry. A paid commission entry carries its line (bordereau/ligne/événement/règle)
  // and, through that line, the payment order that settled it.
  let found = false;
  for (let i = 0; i < count && !found; i += 1) {
    await rows.nth(i).click();
    await expect(page.getByTestId('acc-chain')).toBeVisible();
    const statement = ((await page.getByTestId('chain-statement').textContent()) ?? '').trim();
    const payment = ((await page.getByTestId('chain-payment').textContent()) ?? '').trim();
    const settled = (v: string) => v !== '' && v !== '—';
    if (settled(statement) && settled(payment)) {
      found = true;
      await expect(page.getByTestId('chain-line')).not.toHaveText('—');
      await expect(page.getByTestId('chain-event')).not.toHaveText('—');
      await expect(page.getByTestId('chain-rule')).not.toHaveText('—');
      await expect(page.getByTestId('chain-entry')).not.toHaveText('—');
    }
  }
  expect(found, 'une écriture de commission payée dont la chaîne remonte au virement').toBeTruthy();
  await finance.close();
});

test.describe('Comptabilité — accessibilité', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:accounting', async ({ page }) => {
    await page.goto('/comptabilite');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
