import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedFdv, USERS } from '../support/helpers';

// Per-screen tests for the sales-force module (section 8.14): the variable plan with collaborators
// and objectives (FDV-01/03/04), the monthly calculation, validation, adjustment and pay export
// (FDV-05/13/14) and a commercial's own variable (FDV-15). The weighting (CR-87) and absence (CR-88)
// cases are replayed by the interface below and their arithmetic is also pinned by SalesForceServiceTest.
//
// @screen:fdvPlan @screen:fdvValidation @screen:fdvCommercial @screen:fdvCollaborators @screen:fdvMeasures
// @screen:fdvObjectives
// @req:FDV-01 @req:FDV-02 @req:FDV-03 @req:FDV-04 @req:FDV-06 @req:FDV-07 @req:FDV-13 @req:FDV-14 @req:FDV-15 @req:FDV-16

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedFdv(request);
});

test('l’administrateur lit le plan, lance le calcul et valide, la Finance exporte @screen:fdvPlan @screen:fdvValidation @req:FDV-01 @req:FDV-04 @req:FDV-06 @req:FDV-07 @req:FDV-13 @req:FDV-14 @path:FDV-09 @role-action:SALES_MANAGER-validate-variable @role-action:FINANCE_CONTROLLER-export-pay', async ({
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
  // FDV-09: the pay export is reserved to the Finance controller — the manager has no export button.
  await expect(managerPage.getByTestId('fdvv-export')).toHaveCount(0);
  await manager.close();

  // FDV-09: only the Finance controller exports the validated variables to pay, and the produced file
  // carries its lines (fichier lu).
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const financePage = await finance.newPage();
  await financePage.goto('/fdv/validation');
  await financePage.getByTestId('fdvv-export').click();
  await expect(financePage.getByTestId('fdvv-message')).toContainText('ALPTIS_ASSURANCES_VAR_202609.csv');
  await expect(financePage.getByTestId('fdvv-message')).toContainText(/\([1-9]\d* lignes\)/);
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

// Parcours FDV-08 (section 4.9): a bonus granted despite a missed objective is refused without a
// justification, and recorded as a traced manual-adjustment component when a reason is given.
test('l’administrateur accorde une prime malgré un objectif manqué : refus sans motif, composante tracée avec motif @screen:fdvValidation @req:FDV-13 @path:FDV-08', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();

  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2026-09-01');
  await page.getByTestId('fdvv-refresh').click();

  // Without a reason the grant is refused (FDV-08): the server rejects the blank justification.
  await page.getByTestId('fdvv-adj-id').fill('C-DEMO');
  await page.getByTestId('fdvv-adj-amount').fill('100');
  await page.getByTestId('fdvv-adj-submit').click();
  await expect(page.getByTestId('fdvv-error')).toContainText('Motif');

  // With a reason the grant is recorded as a traced manual-adjustment component carrying its motive.
  await page.getByTestId('fdvv-adj-reason').fill('Effort commercial malgré objectif manqué');
  await page.getByTestId('fdvv-adj-submit').click();
  await expect(page.getByTestId('fdvv-message')).toContainText('Ajustement enregistré');
  const row = page.locator('[data-testid^="result-row-"]').filter({ hasText: 'Ajustement' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText('100,00');
  await expect(row).toContainText('Effort commercial malgré objectif manqué');
  await admin.close();
});

// Parcours FDV-05 (section 4.9): the quarterly qualitative variable pays an amount per met criterion;
// six weeks of absence in the quarter block it to nothing.
test('le variable trimestriel qualitatif paie par critère atteint, et rien après six semaines d’absence @screen:fdvPlan @screen:fdvValidation @req:FDV-07 @path:FDV-05', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();

  // The seeded collaborator meets criteria with no absence, so the quarterly qualitative run pays a
  // per-criterion amount.
  await page.goto('/fdv/plan');
  await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  await page.getByTestId('fdvp-month').selectOption('2026-09-01');
  await page.getByTestId('fdvp-quarterly').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('trimestriel qualitatif lancé');

  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2026-09-01');
  await page.getByTestId('fdvv-refresh').click();
  await expect(page.getByTestId('result-amount-QUARTERLY_QUALITATIVE')).toHaveText(/[1-9]/);

  // Six weeks of absence in the quarter (35 days, past the 30-day block): re-running pays nothing.
  const OP_AUTH = 'Basic ' + Buffer.from(
    `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
  const imp = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OP_AUTH },
    data: {
      entityCode: 'ALPTIS_ASSURANCES', flowCode: 'employee_measures', sourceSystem: 'SIRH', fileName: 'm.csv',
      content: 'external_id;measure_type;period_from;period_to;value;dimension\n'
        + 'C-DEMO;ABSENCE;2026-07-01;2026-09-30;35;absence\n',
    },
  });
  expect(imp.ok(), `${imp.status()} ${await imp.text()}`).toBeTruthy();

  await page.goto('/fdv/plan');
  await page.getByTestId('fdvp-month').selectOption('2026-09-01');
  await page.getByTestId('fdvp-quarterly').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('trimestriel qualitatif lancé');

  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2026-09-01');
  await page.getByTestId('fdvv-refresh').click();
  await expect(page.getByTestId('result-amount-QUARTERLY_QUALITATIVE')).not.toHaveText(/[1-9]/);
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

// Parcours FDV-04 (section 4.7) and recette case CR-87: market weighting. A collaborator with 10 000 €
// on the particulier market and 5 000 € on the professionnel market weighs 10 000 × 0,7 + 5 000 × 1,3 =
// 13 500 € against the objective. The simulator reads that weighted production through the interface.
test('la pondération par marché porte 10 000 € et 5 000 € à 13 500 € @screen:fdvCommercial @req:FDV-16 @path:FDV-04 @cr:CR-87', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();

  // Create a dedicated collaborator (no other measures) to read its weighting in isolation.
  await page.goto('/fdv/collaborateurs');
  await page.getByTestId('collab-external-id').fill('E2E-FDV04');
  await page.getByTestId('collab-last-name').fill('Marché');
  await page.getByTestId('collab-first-name').fill('Léa');
  await page.getByTestId('collab-job').selectOption('CAC');
  await page.getByTestId('collab-team').fill('Équipe M');
  await page.getByTestId('collab-region').fill('Est');
  await page.getByTestId('collab-valid-from').fill('2026-01-01');
  await page.getByTestId('collab-submit').click();
  await expect(page.getByTestId('fdvcol-message')).toContainText('créé');

  // Import its September production, split across the two markets.
  const csv =
    'external_id;measure_type;dimension;period_from;period_to;value\n'
    + 'E2E-FDV04;PRODUCTION;particulier;2026-09-01;2026-09-30;10000\n'
    + 'E2E-FDV04;PRODUCTION;professionnel;2026-09-01;2026-09-30;5000\n';
  const auth = 'Basic ' + Buffer.from(
    `${USERS.adminAlptis.username}:${USERS.adminAlptis.password}`).toString('base64');
  const imported = await page.request.post(
    '/api/v1/imports/employee_measures?entity=ALPTIS_ASSURANCES&sourceSystem=HR&fileName=mesures.csv',
    { headers: { 'Content-Type': 'text/plain', Authorization: auth }, data: csv });
  expect(imported.ok(), `${imported.status()} ${await imported.text()}`).toBeTruthy();

  // Simulate September with no hypothetical, so the measured weighted production is read: 13 500 €.
  await page.goto('/fdv/commercial');
  await page.getByTestId('fdvc-id').fill('E2E-FDV04');
  await page.getByTestId('fdvc-load').click();
  await page.getByTestId('sim-month').selectOption('2026-09-01');
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-weighted')).toContainText(/13.?500,00/);
  await admin.close();
});

// Recette case CR-88 (section 13): the monthly instalment is paid when the objective is met, but ten
// working days of absence in the month block it. The seeded C-DEMO collaborator (CAC plan 2450/146,
// objective 1000, production 2000) is run monthly — 146,00 € — then, after importing 10 absence days,
// re-run to nothing. Replayed through the plan and validation screens.
test('CR-88 : la mensualité de 146,00 € est versée objectif atteint, mais bloquée par dix jours d’absence @screen:fdvPlan @screen:fdvValidation @cr:CR-88', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();

  // Objective met (production 2000 > 1000), no absence: the monthly run pays the 146,00 € instalment.
  await page.goto('/fdv/plan');
  await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  await page.getByTestId('fdvp-month').selectOption('2026-09-01');
  await page.getByTestId('fdvp-run').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('lancé');

  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2026-09-01');
  await page.getByTestId('fdvv-refresh').click();
  await expect(page.getByTestId('result-amount-MONTHLY_INDIVIDUAL')).toContainText('146,00');

  // Ten working days of absence in September (the monthly block threshold): re-running pays nothing.
  const OP_AUTH = 'Basic ' + Buffer.from(
    `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
  const imp = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OP_AUTH },
    data: {
      entityCode: 'ALPTIS_ASSURANCES', flowCode: 'employee_measures', sourceSystem: 'SIRH', fileName: 'm.csv',
      content: 'external_id;measure_type;period_from;period_to;value;dimension\n'
        + 'C-DEMO;ABSENCE;2026-09-01;2026-09-30;10;absence\n',
    },
  });
  expect(imp.ok(), `${imp.status()} ${await imp.text()}`).toBeTruthy();

  await page.goto('/fdv/plan');
  await page.getByTestId('fdvp-month').selectOption('2026-09-01');
  await page.getByTestId('fdvp-run').click();
  await expect(page.getByTestId('fdvp-message')).toContainText('lancé');

  await page.goto('/fdv/validation');
  await page.getByTestId('fdvv-month').selectOption('2026-09-01');
  await page.getByTestId('fdvv-refresh').click();
  await expect(page.getByTestId('result-amount-MONTHLY_INDIVIDUAL')).toContainText('0,00');
  await expect(page.getByTestId('result-amount-MONTHLY_INDIVIDUAL')).not.toHaveText(/[1-9]/);
  await admin.close();
});

// Parcours FDV-01 (section 4.5): importing collaborators and their measures feeds the collaborator
// fiches and the measures by period.
test('l’import des collaborateurs et des mesures alimente les fiches et les mesures par période @screen:fdvCollaborators @screen:fdvMeasures @req:FDV-01 @req:FDV-02 @path:FDV-01', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  const auth = 'Basic ' + Buffer.from(
    `${USERS.adminAlptis.username}:${USERS.adminAlptis.password}`).toString('base64');

  // Import a collaborator (EMPLOYEES flow, section 9.9).
  const collaborators =
    'external_id;last_name;first_name;job_code;team;region;valid_from\n' +
    'E2E-FDV01;Importé;Paul;CAC;Équipe I;Nord;2026-01-01\n';
  const impCollab = await page.request.post(
    '/api/v1/imports/employees?entity=ALPTIS_ASSURANCES&sourceSystem=HR&fileName=collaborateurs.csv',
    { headers: { 'Content-Type': 'text/plain', Authorization: auth }, data: collaborators });
  expect(impCollab.ok(), `${impCollab.status()} ${await impCollab.text()}`).toBeTruthy();

  // Its fiche appears on the collaborators screen, with its post label (CAC → Conseiller).
  await page.goto('/fdv/collaborateurs');
  const fiche = page.getByTestId('collaborator-row-E2E-FDV01');
  await expect(fiche).toBeVisible();
  await expect(fiche).toContainText('Importé');
  await expect(fiche).toContainText('Conseiller');

  // Import its measures over two periods (EMPLOYEE_MEASURES flow).
  const measures =
    'external_id;measure_type;dimension;period_from;period_to;value\n' +
    'E2E-FDV01;PRODUCTION;particulier;2026-08-01;2026-08-31;8000\n' +
    'E2E-FDV01;PRODUCTION;particulier;2026-09-01;2026-09-30;12000\n';
  const impMeasures = await page.request.post(
    '/api/v1/imports/employee_measures?entity=ALPTIS_ASSURANCES&sourceSystem=HR&fileName=mesures-fdv01.csv',
    { headers: { 'Content-Type': 'text/plain', Authorization: auth }, data: measures });
  expect(impMeasures.ok(), `${impMeasures.status()} ${await impMeasures.text()}`).toBeTruthy();

  // The measures appear on the measures screen, one row per period with its dates.
  await page.goto('/fdv/mesures');
  await page.getByTestId('fdvm-refresh').click();
  const august = page.getByTestId('measure-row-E2E-FDV01-PRODUCTION-2026-08-01');
  const september = page.getByTestId('measure-row-E2E-FDV01-PRODUCTION-2026-09-01');
  await expect(august).toContainText('2026-08-01');
  await expect(august).toContainText('2026-08-31');
  await expect(september).toContainText('2026-09-01');
  await expect(september).toContainText('2026-09-30');
  await admin.close();
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
