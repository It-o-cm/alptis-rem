import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, DEMO_MONTH, USERS } from '../support/helpers';

// Per-screen tests for the period advancement and detail (WKF-01, WKF-04, section 7.1).
//
// @screen:periods @req:WKF-01 @req:WKF-04

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test.describe('Périodes — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les périodes ouvertes, ouvre le détail et avance la période @screen:periods @req:WKF-04 @req:WKF-01 @role-action:OPERATOR-advance-period', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('period-row-ALPTIS_ASSURANCES-' + DEMO_MONTH)).toBeVisible();

    await page.getByTestId('open-ALPTIS_ASSURANCES-' + DEMO_MONTH).click();
    await expect(page.getByTestId('period-detail')).toBeVisible();
    await expect(page.getByTestId('detail-state')).toContainText('Calculée');

    await page.getByTestId('advance-button').click();
    await expect(page.getByTestId('detail-state')).toContainText('Contrôlée');
    await expect(page.getByTestId('periods-message')).toContainText('Contrôlée');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:periods', async ({ page }) => {
    await page.goto('/');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});

test.describe('Périodes — lecteur', () => {
  test.use({ httpCredentials: USERS.viewerAlptis });

  test('le lecteur voit la liste mais n’a pas de bouton d’avancement @screen:periods @role-action:VIEWER-read-periods', async ({
    page,
  }) => {
    await page.goto('/');
    await page.getByTestId('open-ALPTIS_ASSURANCES-' + DEMO_MONTH).click();
    await expect(page.getByTestId('period-detail')).toBeVisible();
    await expect(page.getByTestId('advance-button')).toHaveCount(0);
  });
});
