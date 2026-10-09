import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedFdv, USERS } from '../support/helpers';

// Per-screen tests for the sales-force module (section 8.14): the variable plan with collaborators
// and objectives (FDV-01/03/04), the monthly calculation, validation, adjustment and pay export
// (FDV-05/13/14) and a commercial's own variable (FDV-15). The weighting (CR-87) and absence (CR-88)
// arithmetic is verified by SalesForceServiceTest on the server.
//
// @screen:fdvPlan @screen:fdvValidation @screen:fdvCommercial
// @req:FDV-01 @req:FDV-04 @req:FDV-13 @req:FDV-14 @req:FDV-15

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedFdv(request);
});

test('l’administrateur lit le plan, lance le calcul et valide, la Finance exporte @screen:fdvPlan @screen:fdvValidation @req:FDV-01 @req:FDV-04 @req:FDV-13 @req:FDV-14 @role-action:SALES_MANAGER-validate-variable @role-action:FINANCE_CONTROLLER-export-pay', async ({
  browser,
}) => {
  // The admin reads the plan and the collaborators and launches the monthly calculation.
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/fdv/plan');
  await expect(adminPage.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  await expect(adminPage.getByTestId('collab-row-C-DEMO')).toBeVisible();
  await adminPage.getByTestId('fdvp-run').click();
  await expect(adminPage.getByTestId('fdvp-message')).toContainText('lancé');
  await admin.close();

  // The regional manager validates the computed instalment.
  const manager = await browser.newContext({ httpCredentials: USERS.managerAlptis });
  const managerPage = await manager.newPage();
  await managerPage.goto('/fdv/validation');
  await managerPage.locator('[data-testid^="result-validate-"]').first().click();
  await expect(managerPage.locator('[data-testid^="result-status-"]').first()).toContainText('VALIDATED');
  await manager.close();

  // Finance exports the validated variables to pay.
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const financePage = await finance.newPage();
  await financePage.goto('/fdv/validation');
  await financePage.getByTestId('fdvv-export').click();
  await expect(financePage.getByTestId('fdvv-message')).toContainText('ALPTIS_ASSURANCES_VAR_202609.csv');
  await finance.close();
});

test('le commercial consulte son variable @screen:fdvCommercial @req:FDV-15', async ({ browser }) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const adminPage = await admin.newPage();
  await adminPage.goto('/fdv/plan');
  await adminPage.getByTestId('fdvp-run').click();
  await expect(adminPage.getByTestId('fdvp-message')).toContainText('lancé');
  await admin.close();

  const rep = await browser.newContext({ httpCredentials: USERS.salesRep });
  const page = await rep.newPage();
  await page.goto('/fdv/commercial');
  await page.getByTestId('fdvc-id').fill('C-DEMO');
  await page.getByTestId('fdvc-load').click();
  await expect(page.locator('[data-testid^="my-result-"]').first()).toBeVisible();
  await rep.close();
});

test.describe('Forces de vente — accessibilité', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:fdvPlan', async ({ page }) => {
    await page.goto('/fdv/plan');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
