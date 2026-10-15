import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the referential consultation screens: products (REF-01), quality
// indicators (REF-07) and bank accounts (REF-05, masked IBAN).
//
// @screen:products @screen:quality-indicators @screen:bank-accounts @req:REF-01 @req:REF-05 @req:REF-07

const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

async function importFile(page: Page, flow: string, content: string) {
  const response = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OPERATOR_AUTH },
    data: { entityCode: 'ALPTIS_ASSURANCES', flowCode: flow, sourceSystem: 'SESAME', fileName: `${flow}.csv`, content },
  });
  expect(response.ok()).toBeTruthy();
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Référentiels — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste assureurs, gammes, produits et options @screen:products @req:REF-01', async ({ page }) => {
    await importFile(page, 'product_ranges', 'range_code;label;target\nSANTE_IND;Santé individuelle;INDIVIDUAL\n');
    await importFile(page, 'products',
      'entity_code;product_code;label;range_code;insurer_code;commissionable\n'
      + 'ALPTIS_ASSURANCES;SANTE_SELECT;Santé Select;SANTE_IND;;true\n');
    await page.goto('/produits');
    await expect(page.getByTestId('page-title')).toHaveText('Assureurs, gammes, produits et options');
    // The ranges and products sections list the imported referential.
    await expect(page.getByTestId('range-row-SANTE_IND')).toContainText('Santé individuelle');
    await expect(page.getByTestId('product-row-SANTE_SELECT')).toContainText('Santé Select');
    // The insurers and options sections are present.
    await expect(page.getByTestId('insurers')).toBeVisible();
    await expect(page.getByTestId('options')).toBeVisible();
  });

  test('liste les indicateurs de qualité @screen:quality-indicators @req:REF-07', async ({ page }) => {
    await importFile(page, 'distributors',
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
      + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n');
    await importFile(page, 'quality_indicators',
      'entity_code;partner_code;indicator_type;range_code;period_from;period_to;value\n'
      + 'ALPTIS_ASSURANCES;P13897;UNPAID_RATE;;2026-01-01;2026-01-31;0.06\n');
    await page.goto('/indicateurs');
    // The indicator is shown by distributor and by period, with its value (INT-23, first half).
    const row = page.getByTestId('indicator-row').first();
    await expect(row.getByTestId('indicator-type')).toHaveText('UNPAID_RATE');
    await expect(row).toContainText('P13897');
    await expect(row).toContainText('2026-01-31');
    await expect(row).toContainText('%');
  });

  test('liste les coordonnées bancaires avec IBAN masqué @screen:bank-accounts @req:REF-05', async ({ page }) => {
    await importFile(page, 'distributors',
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
      + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n');
    await importFile(page, 'bank_accounts',
      'entity_code;holder_type;holder_code;iban;bic;holder_name;valid_from\n'
      + 'ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;FR7630006000011234567890189;AGRIFRPP;Cabinet Martin;2026-01-01\n');
    await page.goto('/coordonnees-bancaires');
    const iban = page.getByTestId('bank-iban').first();
    await expect(iban).toContainText('FR76');
    await expect(iban).toContainText('0189');
    await expect(iban).toContainText('…');
    // REF-05: the change-since-last-payment signal column is present; this freshly imported account,
    // with no prior payment, is not flagged.
    await expect(page.getByText('À vérifier')).toBeVisible();
    await expect(page.getByTestId('bank-changed')).toHaveCount(0);
  });

  test('aucune violation d’accessibilité grave ou critique @screen:products', async ({ page }) => {
    await page.goto('/produits');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Référentiels — accès', () => {
  test('un consultant ne voit pas les coordonnées bancaires @screen:bank-accounts', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
    const page = await context.newPage();
    await page.goto('/coordonnees-bancaires');
    await expect(page.getByRole('link', { name: 'Coordonnées bancaires' })).toHaveCount(0);
    await context.close();
  });
});
