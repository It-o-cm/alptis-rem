import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the parameter export/import (PAR-15): the seeded Alptis base plan exports and
// imports back under a new code.
//
// @screen:paramTransfer @req:PAR-15

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test('l’administrateur exporte le plan de base et le réimporte sous un nouveau code @screen:paramTransfer @req:PAR-15 @role-action:BUSINESS_ADMIN-import-plan', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/parametrage-transfert');
  await page.getByTestId('pt-plan').fill('GUIDE_2026');
  await page.getByTestId('pt-export').click();
  await expect(page.getByTestId('pt-json')).toHaveValue(/GUIDE_2026/);

  await page.getByTestId('pt-newcode').fill('GUIDE_2027');
  await page.getByTestId('pt-import').click();
  await expect(page.getByTestId('pt-message')).toContainText('GUIDE_2027');
  await admin.close();
});

test.describe('Paramétrage — accessibilité', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:paramTransfer', async ({ page }) => {
    await page.goto('/parametrage-transfert');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
