import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the event search and detail screen (ING-06, section 5.3): search by key,
// status and type; the detail shows the status, the hold reason and the produced lines; the entry
// made on the manual screen is seen here (cross-screen effect); per-role access.
//
// @screen:events @req:ING-06

test.describe('Recherche d’événements — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('recherche un événement saisi à la main et lit son détail @screen:events @req:ING-06', async ({ page }) => {
    await resetDb(page.request);

    // Create a held event on the manual screen, then find it on the events screen (cross-screen effect).
    await page.goto('/saisie-evenement');
    await page.getByTestId('field-source').selectOption('MANUAL');
    await page.getByTestId('field-key').fill('EVT-SEARCH-1');
    await page.getByTestId('field-type').selectOption('TERMINATION');
    await page.getByTestId('field-contract').fill('C-UNKNOWN');
    await page.getByTestId('field-date').fill('2026-09-30');
    await page.getByTestId('manual-submit').click();
    await expect(page.getByTestId('manual-message')).toContainText('attente');

    await page.goto('/evenements');
    await expect(page.getByTestId('page-title')).toHaveText("Recherche d'événements");
    await page.getByTestId('events-q').fill('EVT-SEARCH-1');
    await page.getByTestId('events-status').selectOption('ON_HOLD');
    await page.getByTestId('events-search').click();

    const row = page.locator('[data-testid^="event-row-"]').first();
    await expect(row).toContainText('Résiliation');
    await expect(row).toContainText('Mis à l’écart');
    await expect(row).toContainText('Référence inconnue');

    await row.getByRole('button', { name: 'Détail' }).click();
    await expect(page.getByTestId('event-detail')).toBeVisible();
    await expect(page.getByTestId('detail-status')).toHaveText('Mis à l’écart');
    await expect(page.getByTestId('detail-reason')).toHaveText('Référence inconnue');
    await expect(page.getByTestId('no-produced-line')).toBeVisible();
  });

  test('recherche les événements du jeu complet et ouvre un détail @screen:events @req:ING-06', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/evenements');
    await page.getByTestId('events-type').selectOption('PREMIUM_COLLECTED');
    await page.getByTestId('events-search').click();
    const row = page.locator('[data-testid^="event-row-"]').first();
    await expect(row).toBeVisible();
    await expect(row).toContainText('Prime encaissée');

    await row.getByRole('button', { name: 'Détail' }).click();
    await expect(page.getByTestId('event-detail')).toBeVisible();
    await expect(page.getByTestId('detail-type')).toHaveText('Prime encaissée');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:events', async ({ page }) => {
    await resetDb(page.request);
    await page.goto('/evenements');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Recherche d’événements — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:events', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/evenements');
    await expect(page.getByRole('link', { name: 'Recherche d’événements' })).toHaveCount(0);
    await context.close();
  });
});
