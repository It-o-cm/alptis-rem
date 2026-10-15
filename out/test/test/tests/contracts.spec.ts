import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for contract and coverage search (section 5.2), partitioned by entity.
//
// @screen:contract-search @req:5.2

const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

const FILES: Array<[string, string]> = [
  ['product_ranges', 'range_code;label;target\nSANTE_IND;Santé individuelle;INDIVIDUAL\n'],
  ['products', 'entity_code;product_code;label;range_code;insurer_code;commissionable\n'
    + 'ALPTIS_ASSURANCES;SANTE_SELECT;Santé Select;SANTE_IND;;true\n'],
  ['distributors', 'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
    + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n'],
  ['production_codes', 'entity_code;production_code;partner_code;label;status\n'
    + 'ALPTIS_ASSURANCES;61723;P13897;Agence;ACTIVE\n'],
  ['coverages', 'entity_code;source_system;contract_number;coverage_code;product_code;policyholder_id;'
    + 'registration_date;effect_date;status;remuneration_mode;linear_rate;production_code;annual_premium\n'
    + 'ALPTIS_ASSURANCES;CLEVA;AD-1;C1;SANTE_SELECT;SUB-1;2026-01-01;2026-01-01;IN_FORCE;LINEAR;0.15;61723;1000.00\n'],
];

async function seed(page: Page) {
  for (const [flow, content] of FILES) {
    const response = await page.request.post('/api/ui/v1/imports', {
      headers: { Authorization: OPERATOR_AUTH },
      data: { entityCode: 'ALPTIS_ASSURANCES', flowCode: flow, sourceSystem: 'SESAME', fileName: `${flow}.csv`, content },
    });
    expect(response.ok()).toBeTruthy();
  }
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Contrats — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('recherche une adhésion, montre ses garanties et ouvre la fiche @screen:contract-search @req:5.2 @req:5.5', async ({ page }) => {
    await seed(page);
    await page.goto('/contrats');
    await page.getByTestId('search-term').fill('AD-1');
    await page.getByTestId('search-button').click();

    const row = page.getByTestId('contract-row-AD-1');
    await expect(row).toContainText('IN_FORCE');
    await expect(row).toContainText('C1');
    await expect(row).toContainText('1000.00');

    // Open the adhésion/guarantee fiche (sections 5.2, 5.5).
    await page.getByTestId('coverage-AD-1-C1').click();
    const fiche = page.getByTestId('coverage-detail');
    await expect(fiche).toBeVisible();
    await expect(fiche).toContainText('AD-1');
    await expect(fiche).toContainText('C1');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:contract-search', async ({ page }) => {
    await page.goto('/contrats');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Contrats — accès', () => {
  test('un commercial n’a pas accès à la recherche @screen:contract-search', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/contrats');
    await expect(page.getByRole('link', { name: 'Recherche contrats' })).toHaveCount(0);
    await context.close();
  });
});
