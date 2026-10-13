import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the simulation of a rule version on a past period (PAR-13). The substitution
// arithmetic and the reproducibility (CR-69) are verified by SimulationServiceTest on the server;
// this exercises the screen against the real engine.
//
// @screen:simulation @req:PAR-13

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’administrateur lance une simulation et lit la comparaison @screen:simulation @req:PAR-13 @role-action:OPERATOR-run-simulation', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/simulation');
  // ADM-03: the rule is chosen from the list (the baseline barème rule) by its French label, never typed.
  await expect(
    page.getByTestId('sim-rule').locator('option', { hasText: 'Santé Équilibre — linéaire (SANTE_EQUILIBRE_LINEAR)' }),
  ).toHaveCount(1);
  await page.getByTestId('sim-rule').selectOption('SANTE_EQUILIBRE_LINEAR');
  await page.getByTestId('sim-version').fill('1');
  await page.getByTestId('sim-run').click();
  // The comparison cards appear, computed by the server in the separate simulation space.
  await expect(page.getByTestId('sim-real')).toBeVisible();
  await expect(page.getByTestId('sim-ecart')).toBeVisible();
  await expect(page.getByTestId('chart-sim')).toBeVisible();
  // The simulation is kept in the history (PAR-13).
  await expect(page.getByTestId('sim-history')).toBeVisible();
  await expect(page.locator('[data-testid="sim-history-row"]').first()).toBeVisible();
  await admin.close();
});

test.describe('Simulation — accessibilité', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:simulation', async ({ page }) => {
    await page.goto('/simulation');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
