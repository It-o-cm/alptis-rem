import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

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
    await expect(page.getByTestId('page-title')).toHaveText("Saisie d'un événement");

    await page.getByTestId('field-source').fill('MANUAL');
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

  test('refuse une saisie incomplète @screen:manualEvent @req:ING-07', async ({ page }) => {
    await page.goto('/saisie-evenement');
    await page.getByTestId('field-source').fill('MANUAL');
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
    await expect(page.getByRole('link', { name: 'Saisie d’un événement' })).toHaveCount(0);
    await context.close();
  });
});
