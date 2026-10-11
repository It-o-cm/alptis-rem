import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the held events screen (P8, ING-06): events on hold grouped by reason and
// listed; a hand-entered held event appears here (cross-screen effect); per-role access.
//
// @screen:heldEvents @req:ING-06

test.describe('Événements en attente — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('regroupe les événements en attente par motif @screen:heldEvents @req:ING-06', async ({ page }) => {
    await resetDb(page.request);

    // Create a held event on the manual screen (unknown contract → référence inconnue).
    await page.goto('/saisie-evenement');
    await page.getByTestId('field-source').fill('MANUAL');
    await page.getByTestId('field-key').fill('EVT-HELD-1');
    await page.getByTestId('field-type').selectOption('TERMINATION');
    await page.getByTestId('field-contract').fill('C-UNKNOWN');
    await page.getByTestId('field-date').fill('2026-09-30');
    await page.getByTestId('manual-submit').click();
    await expect(page.getByTestId('manual-message')).toContainText('attente');

    await page.goto('/evenements-en-attente');
    await expect(page.getByTestId('page-title')).toHaveText('Événements en attente');

    // The reason summary shows the French label, not the raw code.
    const reasonRow = page.getByTestId('reason-row-UNKNOWN_REFERENCE');
    await expect(reasonRow).toContainText('Référence inconnue');
    await expect(reasonRow).toContainText('1');

    // The held event is listed.
    const eventRow = page.locator('[data-testid^="held-row-"]').first();
    await expect(eventRow).toContainText('EVT-HELD-1');
    await expect(eventRow).toContainText('Résiliation');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:heldEvents', async ({ page }) => {
    await resetDb(page.request);
    await page.goto('/evenements-en-attente');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Événements en attente — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:heldEvents', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/evenements-en-attente');
    await expect(page.getByRole('link', { name: 'Événements en attente' })).toHaveCount(0);
    await context.close();
  });
});
