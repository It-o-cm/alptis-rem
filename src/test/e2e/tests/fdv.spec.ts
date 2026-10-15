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
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(adminPage.getByTestId('fdvp-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
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
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(managerPage.getByTestId('fdvv-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(managerPage.getByTestId('chart-fdvv')).toBeVisible();
  await managerPage.locator('[data-testid^="result-validate-"]').first().click();
  await expect(managerPage.locator('[data-testid^="result-status-"]').first()).toContainText('Validé');
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

test('l’administrateur règle le variable collectif annuel en janvier N+1 @screen:fdvObjectives @screen:fdvPlan @screen:fdvValidation @req:FDV-08 @path:FDV-06', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();

  // The entity-wide collective objective for 2026 (FDV-03 collective), met by the seeded production
  // (2000 × 0,7 = 1400 against a 1000 target), so the collective rate is capped at 100 %.
  await page.goto('/fdv/objectifs');
  await page.getByTestId('obj-type').selectOption('COLLECTIVE_QUANTITATIVE');
  await page.getByTestId('obj-job').selectOption('CAC');
  await page.getByTestId('obj-employee').fill('');
  await page.getByTestId('obj-target').fill('1000');
  await page.getByTestId('obj-from').fill('2026-01-01');
  await page.getByTestId('obj-to').fill('2026-12-31');
  await page.getByTestId('obj-submit').click();
  await expect(page.getByTestId('fdvo-message')).toContainText('enregistré');

  // The admin settles the annual collective variable (FDV-08) in January N+1 (the settled year is 2026).
  await page.goto('/fdv/plan');
  await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  // ADM-03: the settlement month is picked from a list by its French name.
  await page.getByTestId('fdvp-month').selectOption('2027-01-01');
  await page.getByTestId('fdvp-collective').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('collectif annuel réglé');
  await expect(page.getByTestId('fdvp-message')).toContainText('collaborateur');

  // The collective result is visible on the validation screen for January 2027: the CAC's 550 € envelope
  // (collective objective met, no absence) shown by its French component label and amount.
  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2027-01-01');
  await page.getByTestId('fdvv-refresh').click();
  const row = page.locator('[data-testid^="result-row-"]').filter({ hasText: 'Collectif' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText('550,00');
  await admin.close();
});

test('l’administrateur règle le variable des animateurs et responsables @screen:fdvPlan @screen:fdvValidation @req:FDV-09', async ({
  browser,
}) => {
  // The admin settles the animators/managers variable (FDV-09) in January N+1 (measured year 2026).
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/plan');
  await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  await page.getByTestId('fdvp-month').selectOption('2027-01-01');
  await page.getByTestId('fdvp-manager').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('animateurs et responsables réglé');
  await expect(page.getByTestId('fdvp-message')).toContainText('collaborateur');

  // The result is visible on the validation screen for January 2027: weighted production 1400 over a
  // 1000 baseline is a 40 % growth reaching the 35 % tier (3000 €); two criteria met is below the
  // 3-criteria tier, so 3000 €, shown by its French component label and amount.
  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2027-01-01');
  await page.getByTestId('fdvv-refresh').click();
  const row = page.locator('[data-testid^="result-row-"]').filter({ hasText: 'Animateurs et responsables' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText(/3.000,00/);
  await admin.close();
});

test('l’administrateur règle le variable de la vente directe à 70 % en janvier @screen:fdvPlan @screen:fdvValidation @req:FDV-10', async ({
  browser,
}) => {
  // The admin settles the direct-sales variable (FDV-10) in January N+1: 70 % of the full right.
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/plan');
  await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  await page.getByTestId('fdvp-month').selectOption('2027-01-01');
  await page.getByTestId('fdvp-direct').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('vente directe réglé');
  await expect(page.getByTestId('fdvp-message')).toContainText('collaborateur');

  // The result is visible on the validation screen for January 2027: 10 % of 50 000 € margin (5000 €)
  // + 1000 € overshoot + 1500 € portfolio tier = 7500 € right, of which 70 % (5250 €) is paid.
  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2027-01-01');
  await page.getByTestId('fdvv-refresh').click();
  const row = page.locator('[data-testid^="result-row-"]').filter({ hasText: 'Vente directe' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText(/5.250,00/);
  await admin.close();
});

test('l’administrateur applique la réduction sur résiliation @screen:fdvPlan @screen:fdvValidation @req:FDV-12', async ({
  browser,
}) => {
  // The admin settles the termination reduction (FDV-12) in January N+1: a negative result.
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/plan');
  await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  await page.getByTestId('fdvp-month').selectOption('2027-01-01');
  await page.getByTestId('fdvp-termination').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('Réduction sur résiliation réglée');
  await expect(page.getByTestId('fdvp-message')).toContainText('collaborateur');

  // The reduction is visible on the validation screen for January 2027: 15 % of 10 000 € of early
  // terminations reduces the variable by 1500 €, shown as a negative amount by its French label.
  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2027-01-01');
  await page.getByTestId('fdvv-refresh').click();
  const row = page.locator('[data-testid^="result-row-"]').filter({ hasText: 'Réduction sur résiliation' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText(/-1.500,00/);
  await admin.close();
});

test('le commercial consulte son variable et le simule @screen:fdvCommercial @req:FDV-15 @req:FDV-16 @path:FDV-11', async ({
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
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('fdvc-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await page.getByTestId('fdvc-id').fill('C-DEMO');
  await page.getByTestId('fdvc-load').click();
  await expect(page.locator('[data-testid^="my-result-"]').first()).toBeVisible();
  await expect(page.locator('[data-testid^="chart-gauge-"]').first()).toBeVisible();

  // The simulator (FDV-16): a hypothetical production above the objective pays the instalment.
  // ADM-03: the month is picked from a list, never typed as an ISO code.
  await page.getByTestId('sim-month').selectOption('2026-09-01');
  await page.getByTestId('sim-production').fill('5000');
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-alert')).toContainText('Objectif atteint');
  await expect(page.getByTestId('sim-amount')).toContainText('€');
  // FDV-11: a production far above the objective caps the attainment at 100 %.
  await page.getByTestId('sim-production').fill('9999999');
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-amount')).toContainText('atteinte 100 %');
  await rep.close();
});

test('l’administrateur crée puis modifie un collaborateur @screen:fdvCollaborators @req:FDV-01', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/collaborateurs');
  await expect(page.getByTestId('page-title')).toHaveText('Collaborateurs');
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('fdvcol-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);

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
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('fdvm-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
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

test('l’administrateur crée un objectif collectif versionné @screen:fdvObjectives @req:FDV-03 @path:FDV-02', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await page.goto('/fdv/objectifs');
  await expect(page.getByTestId('page-title')).toHaveText('Objectifs');
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('fdvo-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);

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

  // Submitting the same key again creates version 2 (append-only versioning); version 1 is kept.
  await page.getByTestId('obj-submit').click();
  await expect(page.getByTestId('objective-row-collectif-COLLECTIVE_QUANTITATIVE-v2')).toBeVisible();
  await expect(page.getByTestId('objective-row-collectif-COLLECTIVE_QUANTITATIVE-v1')).toBeVisible();
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
