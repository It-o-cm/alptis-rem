import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen test for the calculation-families screen (PAR-02): the list of families and the form
// derived from a family's parameter schema.
//
// @screen:families @req:PAR-02

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Familles de calcul', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('liste les familles et dérive le formulaire du schéma @screen:families @req:PAR-02', async ({ page }) => {
    await page.goto('/familles');
    await expect(page.getByTestId('page-title')).toHaveText('Familles de calcul');
    await expect(page.getByTestId('family-row-UPFRONT')).toBeVisible();

    await page.getByTestId('family-open-UPFRONT').click();
    await expect(page.getByTestId('field-allowedRates')).toBeVisible();
    await expect(page.getByTestId('field-firstYearDays')).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:families', async ({ page }) => {
    await page.goto('/familles');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});
