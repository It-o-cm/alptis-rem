import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedFdv, USERS } from '../support/helpers';

// Per-screen tests for the sales-force module (section 8.14): the variable plan with collaborators
// and objectives (FDV-01/03/04), the monthly calculation, validation, adjustment and pay export
// (FDV-05/13/14) and a commercial's own variable (FDV-15). The weighting (CR-87) and absence (CR-88)
// arithmetic is verified by SalesForceServiceTest on the server.
//
// @screen:fdvPlan @screen:fdvValidation @screen:fdvCommercial @screen:fdvCollaborators @screen:fdvMeasures
// @screen:fdvObjectives
// @req:FDV-01 @req:FDV-02 @req:FDV-03 @req:FDV-04 @req:FDV-06 @req:FDV-07 @req:FDV-13 @req:FDV-14 @req:FDV-15 @req:FDV-16

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedFdv(request);
});

test('l’administrateur lit le plan, lance le calcul et valide, la Finance exporte @screen:fdvPlan @screen:fdvValidation @req:FDV-01 @req:FDV-04 @req:FDV-06 @req:FDV-07 @req:FDV-13 @req:FDV-14 @role-action:SALES_MANAGER-validate-variable @role-action:FINANCE_CONTROLLER-export-pay', async ({
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

  // The admin launches the quarterly qualitative variable of the quarter ending at the month (FDV-07).
  await adminPage.getByTestId('fdvp-quarterly').click();
  await expect(adminPage.getByTestId('fdvp-message')).toContainText('Variable trimestriel qualitatif lancé');
  await expect(adminPage.getByTestId('fdvp-message')).toContainText('collaborateur');

  // The admin launches the annual individual regularization of January N+1 (FDV-06).
  await adminPage.getByTestId('fdvp-reg-year').fill('2026');
  await adminPage.getByTestId('fdvp-reg').click();
  await expect(adminPage.getByTestId('fdvp-message')).toContainText('Régularisation annuelle 2026');
  await expect(adminPage.getByTestId('fdvp-message')).toContainText('collaborateur');
  await admin.close();

  // The regional manager validates the computed instalment.
  const manager = await browser.newContext({ httpCredentials: USERS.managerAlptis });
  const managerPage = await manager.newPage();
  await managerPage.goto('/fdv/validation');
  await expect(managerPage.getByTestId('chart-fdvv')).toBeVisible();
  await managerPage.locator('[data-testid^="result-validate-"]').first().click();
  await expect(managerPage.locator('[data-testid^="result-status-"]').first()).toContainText('VALIDATED');
  // The per-person pay synthesis (FDV-14) now shows the validated collaborator.
  await expect(managerPage.getByTestId('fdvv-summary')).toBeVisible();
  await expect(managerPage.locator('[data-testid^="pay-summary-"]').first()).toBeVisible();
  await manager.close();

  // Finance exports the validated variables to pay.
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const financePage = await finance.newPage();
  await financePage.goto('/fdv/validation');
  await financePage.getByTestId('fdvv-export').click();
  await expect(financePage.getByTestId('fdvv-message')).toContainText('ALPTIS_ASSURANCES_VAR_202609.csv');
  await finance.close();
});

test('le commercial consulte son variable et le simule @screen:fdvCommercial @req:FDV-15 @req:FDV-16', async ({
  browser,
}) => {
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
  await expect(page.locator('[data-testid^="chart-gauge-"]').first()).toBeVisible();

  // The simulator (FDV-16): a hypothetical production above the objective pays the instalment.
  await page.getByTestId('sim-month').fill('2026-09-01');
  await page.getByTestId('sim-production').fill('5000');
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-alert')).toContainText('Objectif atteint');
  await expect(page.getByTestId('sim-amount')).toContainText('€');
  await rep.close();
});

test('l’administrateur crée puis modifie un collaborateur @screen:fdvCollaborators @req:FDV-01', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/collaborateurs');
  await expect(page.getByTestId('page-title')).toHaveText('Collaborateurs');

  // Create a collaborator.
  await page.getByTestId('collab-external-id').fill('E2E-C1');
  await page.getByTestId('collab-last-name').fill('Testeur');
  await page.getByTestId('collab-first-name').fill('Eve');
  await page.getByTestId('collab-job').selectOption('CAC');
  await page.getByTestId('collab-team').fill('Équipe A');
  await page.getByTestId('collab-region').fill('Sud');
  await page.getByTestId('collab-valid-from').fill('2026-01-01');
  await page.getByTestId('collab-submit').click();
  await expect(page.getByTestId('fdvcol-message')).toContainText('créé');
  const row = page.getByTestId('collaborator-row-E2E-C1');
  await expect(row).toBeVisible();
  await expect(row).toContainText('Conseiller');
  await expect(row).toContainText('Équipe A');

  // Edit it: change the post and the team.
  await page.getByTestId('collaborator-edit-E2E-C1').click();
  await expect(page.getByTestId('collab-external-id')).toBeDisabled();
  await page.getByTestId('collab-job').selectOption('RB');
  await page.getByTestId('collab-team').fill('Équipe Z');
  await page.getByTestId('collab-submit').click();
  await expect(page.getByTestId('fdvcol-message')).toContainText('mis à jour');
  await expect(page.getByTestId('collaborator-row-E2E-C1')).toContainText('Équipe Z');
  await expect(page.getByTestId('collaborator-row-E2E-C1')).toContainText('Responsable de secteur');
  await admin.close();
});

test('l’administrateur consulte les mesures reçues @screen:fdvMeasures @req:FDV-02', async ({ browser }) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/mesures');
  await expect(page.getByTestId('page-title')).toHaveText('Mesures reçues');
  // The demo seeds production and absence measures for the collaborators.
  const row = page.locator('[data-testid^="measure-row-"]').first();
  await expect(row).toBeVisible();
  await expect(page.getByText('Production par marché').first()).toBeVisible();
  await admin.close();
});

test('l’administrateur enregistre un plan de variable par poste @screen:fdvPlan @req:FDV-04', async ({ browser }) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/plan');
  await page.getByTestId('plan-job').selectOption('DR');
  await page.getByTestId('plan-component').selectOption('MONTHLY_INDIVIDUAL');
  await page.getByTestId('plan-envelope').fill('7700');
  await page.getByTestId('plan-instalment').fill('458');
  await page.getByTestId('plan-parameters').fill('{"absenceDays":10}');
  await page.getByTestId('plan-from').fill('2026-01-01');
  await page.getByTestId('plan-save').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('Plan enregistré');
  // The new plan appears with its post label and its parameters.
  await expect(page.getByText('Directeur régional').first()).toBeVisible();
  await expect(page.getByText('{"absenceDays":10}').first()).toBeVisible();
  await admin.close();
});

test('l’administrateur crée un objectif collectif versionné @screen:fdvObjectives @req:FDV-03', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/objectifs');
  await expect(page.getByTestId('page-title')).toHaveText('Objectifs');

  // A collective objective (no collaborator) at version 1.
  await page.getByTestId('obj-type').selectOption('COLLECTIVE_QUANTITATIVE');
  await page.getByTestId('obj-job').selectOption('RR');
  await page.getByTestId('obj-employee').fill('');
  await page.getByTestId('obj-target').fill('50000');
  await page.getByTestId('obj-from').fill('2026-01-01');
  await page.getByTestId('obj-to').fill('2026-12-31');
  await page.getByTestId('obj-submit').click();
  await expect(page.getByTestId('fdvo-message')).toContainText('enregistré');
  await expect(page.getByTestId('objective-row-collectif-COLLECTIVE_QUANTITATIVE-v1')).toBeVisible();

  // Submitting the same key again creates version 2 (append-only versioning).
  await page.getByTestId('obj-submit').click();
  await expect(page.getByTestId('objective-row-collectif-COLLECTIVE_QUANTITATIVE-v2')).toBeVisible();
  await admin.close();
});

test.describe('Forces de vente — accessibilité', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:fdvPlan', async ({ page }) => {
    await page.goto('/fdv/plan');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });

  test('aucune violation d’accessibilité grave ou critique sur les collaborateurs @screen:fdvCollaborators', async ({
    page,
  }) => {
    await page.goto('/fdv/collaborateurs');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
