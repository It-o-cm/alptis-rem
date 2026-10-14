import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

/** Imports a referential file through the operator's upload endpoint, failing the test on refusal. */
async function importFile(page: Page, flow: string, content: string): Promise<void> {
  const response = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OPERATOR_AUTH },
    data: { entityCode: 'ALPTIS_ASSURANCES', flowCode: flow, sourceSystem: 'SESAME', fileName: `${flow}.csv`, content },
  });
  expect(response.ok()).toBeTruthy();
}

// Per-screen tests for the manual event entry screen (ING-07) and its journey (INT-11):
// a hand-entered event is controlled, journalled like an import and traceable; the failure
// path (rejected line) is shown; access is restricted to operators and business admins.
//
// @screen:manualEvent @req:ING-07

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Saisie manuelle — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('enregistre un événement et le trace dans le journal @screen:manualEvent @req:ING-07 @path:INT-11', async ({ page }) => {
    await page.goto('/saisie-evenement');
    await expect(page.getByTestId('page-title')).toHaveText('Saisie manuelle');

    // ADM-03: the source system and event type are picked from lists, never typed; the event type
    // is shown by its French label.
    await page.getByTestId('field-source').selectOption('MANUAL');
    await expect(page.getByTestId('field-type').locator('option', { hasText: 'Prime encaissée' })).toHaveCount(1);
    await page.getByTestId('field-key').fill('EVT-MAN-001');
    await page.getByTestId('field-type').selectOption('PREMIUM_COLLECTED');
    await page.getByTestId('field-contract').fill('C-UNKNOWN-1');
    await page.getByTestId('field-date').fill('2026-09-30');
    await page.getByTestId('field-amount').fill('120.00');
    await page.getByTestId('manual-submit').click();

    // No seeded contract yet: the event is accepted and held for an unknown reference.
    await expect(page.getByTestId('manual-message')).toContainText('attente');

    // The entry is journalled like an import, visible on the imports screen.
    await page.goto('/imports');
    const batchRow = page.locator('[data-testid^="batch-row-"]').first();
    await expect(batchRow).toContainText('Événements');
    await expect(batchRow).toContainText('manual-EVT-MAN-001');
  });

  test('enregistre une donnée complémentaire (garantie) et la trace @screen:manualEvent @req:ING-07', async ({ page }) => {
    // Seed a known product so it can be picked from the list (ADM-03); the production code stays
    // unknown, which is what holds the datum for an unknown reference.
    await importFile(page, 'product_ranges', 'range_code;label;target\nSANTE_IND;Santé individuelle;INDIVIDUAL\n');
    await importFile(page, 'products',
      'entity_code;product_code;label;range_code;insurer_code;commissionable\n'
      + 'ALPTIS_ASSURANCES;SANTE_SELECT;Santé Select;SANTE_IND;;true\n');

    await page.goto('/saisie-evenement');
    await expect(page.getByTestId('manual-coverage-form')).toBeVisible();

    // ADM-03: the source system and product are picked from lists, the mode and status shown by
    // their French labels, never typed as a code.
    await page.getByTestId('cov-source').selectOption('MANUAL');
    await expect(page.getByTestId('cov-mode').locator('option', { hasText: 'Linéaire' })).toHaveCount(1);
    await expect(page.getByTestId('cov-status').locator('option', { hasText: 'En vigueur' })).toHaveCount(1);
    await page.getByTestId('cov-contract').fill('AD-MAN-1');
    await page.getByTestId('cov-coverage').fill('GAR-MAN-1');
    await page.getByTestId('cov-product').selectOption('SANTE_SELECT');
    await page.getByTestId('cov-holder').fill('SUB-1');
    await page.getByTestId('cov-production').fill('PC-UNKNOWN');
    await page.getByTestId('cov-mode').selectOption('LINEAR');
    await page.getByTestId('cov-status').selectOption('IN_FORCE');
    await page.getByTestId('cov-registration').fill('2026-01-15');
    await page.getByTestId('cov-effect').fill('2026-02-01');
    await page.getByTestId('cov-premium').fill('1200.00');
    await page.getByTestId('coverage-submit').click();

    // The product is known but the production code PC-UNKNOWN is not: the datum is accepted and
    // held for an unknown reference (ADM-03 forbids typing the product code, so the failure path
    // is proven through the free-text production-code identifier instead).
    await expect(page.getByTestId('coverage-message')).toContainText('attente');

    // The entry is journalled like an import, visible on the imports screen as a coverage flow.
    await page.goto('/imports');
    const batchRow = page.locator('[data-testid^="batch-row-"]').first();
    await expect(batchRow).toContainText('Garanties');
    await expect(batchRow).toContainText('manual-coverage-GAR-MAN-1');
  });

  test('refuse une saisie incomplète @screen:manualEvent @req:ING-07', async ({ page }) => {
    await page.goto('/saisie-evenement');
    await page.getByTestId('field-source').selectOption('MANUAL');
    await page.getByTestId('field-key').fill('EVT-MAN-002');
    await page.getByTestId('field-type').selectOption('PREMIUM_COLLECTED');
    // Missing contract number and date.
    await page.getByTestId('field-amount').fill('50.00');
    await page.getByTestId('manual-submit').click();
    await expect(page.getByTestId('manual-error')).toContainText('rejeté');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:manualEvent', async ({ page }) => {
    await page.goto('/saisie-evenement');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Saisie manuelle — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:manualEvent', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/saisie-evenement');
    await expect(page.getByRole('link', { name: 'Saisie manuelle' })).toHaveCount(0);
    await context.close();
  });
});
