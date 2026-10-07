import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// First end-to-end test, proving the whole chain works: the real dev server answers,
// serves the single-page application, renders the common navigation frame from the
// mock-up, and client-side routing moves between the empty titled pages.
//
// @screen:shell @path:BOOT-01

test.describe('Cadre applicatif', () => {
  test('le backend répond et sert le SPA @screen:shell @path:BOOT-01', async ({ request }) => {
    const ping = await request.get('/api/test/ping');
    expect(ping.ok()).toBeTruthy();
    const body = await ping.json();
    expect(body.status).toBe('ok');
    expect(typeof body.now).toBe('string');
  });

  test('la page des périodes affiche la navigation, l’en-tête et son titre @screen:shell @path:BOOT-01', async ({
    page,
  }) => {
    await page.goto('/');

    await expect(page.getByTestId('page-title')).toHaveText('Périodes');
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeVisible();
    await expect(page.getByTestId('header-scope')).toContainText('Alptis Assurances');
    await expect(page.getByTestId('header-role')).toContainText('valideur métier');

    // The six mock-up sections are present in the navigation.
    for (const heading of [
      'Cycle mensuel',
      'Distributeurs',
      'Règles',
      'Flux',
      'Comptabilité',
      'Forces de vente',
    ]) {
      await expect(page.getByText(heading, { exact: true })).toBeVisible();
    }
  });

  test('la navigation latérale change d’écran @screen:shell @path:BOOT-01', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Imports' }).click();
    await expect(page).toHaveURL(/\/imports$/);
    await expect(page.getByTestId('page-title')).toHaveText('Imports');

    await page.getByRole('link', { name: 'Familles de calcul' }).click();
    await expect(page).toHaveURL(/\/familles$/);
    await expect(page.getByTestId('page-title')).toHaveText('Familles de calcul');

    // Persistence across reload and direct address.
    await page.reload();
    await expect(page.getByTestId('page-title')).toHaveText('Familles de calcul');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:shell @path:BOOT-01', async ({
    page,
  }) => {
    await page.goto('/');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});
