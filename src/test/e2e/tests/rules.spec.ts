import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the rule-configuration screen (PAR-01 to PAR-05, PAR-12): plans, rules,
// versions, the approval by a second administrator and the conflict display.
//
// @screen:rules @req:PAR-01 @req:PAR-03 @req:PAR-04 @req:PAR-05 @req:PAR-06 @req:PAR-07 @req:PAR-08 @req:PAR-11 @req:PAR-12

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Paramétrage des règles', () => {
  test('crée un plan, une règle et une version, approuvée par un second administrateur '
    + '@screen:rules @req:PAR-01 @req:PAR-04 @req:PAR-11 @path:PAR-01 @path:PAR-03', async ({ browser }) => {
    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const admin = await adminCtx.newPage();
    await admin.goto('/regles');
    await expect(admin.getByTestId('page-title')).toHaveText('Paramétrage des règles');

    await admin.getByTestId('plan-code').fill('E2E_PLAN');
    await admin.getByTestId('plan-label').fill('Plan E2E');
    await admin.getByTestId('plan-vintage').fill('2026');
    await admin.getByTestId('plan-create').click();
    await expect(admin.getByTestId('plan-row-E2E_PLAN')).toBeVisible();

    await admin.getByTestId('plan-open-E2E_PLAN').click();
    await admin.getByTestId('rule-code').fill('E2E_RULE');
    await admin.getByTestId('rule-label').fill('Règle E2E');
    await admin.getByTestId('rule-family').selectOption('LINEAR');
    await admin.getByTestId('rule-create').click();
    await expect(admin.getByTestId('rule-row-E2E_RULE')).toBeVisible();

    await admin.getByTestId('rule-open-E2E_RULE').click();
    await admin.getByTestId('v-from').fill('2026-01-01');
    await admin.getByTestId('v-to').fill('2026-12-31');
    await admin.getByTestId('v-scope').fill('{"product":["E2E_PROD"]}');
    await admin.getByTestId('v-parameters').fill('{"allowedRates":[0.15]}');
    await admin.getByTestId('v-group').fill('TEST');
    await admin.getByTestId('version-create').click();
    await expect(admin.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('Brouillon');

    // A second administrator approves it.
    const admin2Ctx = await browser.newContext({ httpCredentials: USERS.admin2Alptis });
    const admin2 = await admin2Ctx.newPage();
    await admin2.goto('/regles');
    await admin2.getByTestId('plan-open-E2E_PLAN').click();
    await admin2.getByTestId('rule-open-E2E_RULE').click();
    await admin2.getByTestId('version-approve-1').click();
    await expect(admin2.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('Approuvée');

    // PAR-11: generate the amendment from the version and send it for signature.
    await admin2.getByTestId('version-amendment-1').click();
    await expect(admin2.getByTestId('rules-message')).toContainText('Avenant');
    await expect(admin2.getByTestId('rules-message')).toContainText('signature');

    await adminCtx.close();
    await admin2Ctx.close();
  });

  // Parcours PAR-10 (section 4.2): the annual duplication of a plan copies every version as a project
  // (DRAFT), at the dates of the following year. The seeded Guide barème (GUIDE_2026, vintage 2026, its
  // versions ACTIVE over 2026) is duplicated into GUIDE_2027, vintage 2027.
  test('la duplication annuelle d’un plan copie toutes les versions en projet aux dates de l’année suivante @screen:rules @req:PAR-01 @path:PAR-10', async ({
    browser,
  }) => {
    const ctx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const admin = await ctx.newPage();
    await admin.goto('/regles');

    // The source plan's version is ACTIVE over 2026.
    await admin.getByTestId('plan-open-GUIDE_2026').click();
    await admin.getByTestId('rule-open-SANTE_EQUILIBRE_LINEAR').click();
    const sourceVersion = admin.getByTestId('version-row-1');
    await expect(sourceVersion.getByTestId('version-status')).toHaveText('En vigueur');
    await expect(sourceVersion).toContainText('2026-01-01');

    // Duplicate the whole plan to the next vintage.
    await admin.getByTestId('dup-code').fill('GUIDE_2027');
    await admin.getByTestId('dup-vintage').fill('2027');
    await admin.getByTestId('plan-duplicate').click();
    await expect(admin.getByTestId('rules-message')).toContainText('dupliqué');
    await expect(admin.getByTestId('plan-row-GUIDE_2027')).toBeVisible();

    // The copied plan carries the same rule, whose version is now a project at the following year's dates.
    await admin.getByTestId('plan-open-GUIDE_2027').click();
    await admin.getByTestId('rule-open-SANTE_EQUILIBRE_LINEAR').click();
    const copiedVersion = admin.getByTestId('version-row-1');
    await expect(copiedVersion.getByTestId('version-status')).toHaveText('Brouillon');
    await expect(copiedVersion).toContainText('2027-01-01');
    await expect(copiedVersion).toContainText('2027-12-31');
    await expect(copiedVersion).not.toContainText('2026');

    // The source plan is untouched: its version is still Active over 2026.
    await admin.getByTestId('plan-open-GUIDE_2026').click();
    await admin.getByTestId('rule-open-SANTE_EQUILIBRE_LINEAR').click();
    await expect(admin.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('En vigueur');

    await ctx.close();
  });

  // Parcours PAR-02 (section 4.2): invalid parameters for each of the nine calculation families are
  // refused with the family's schema message. A non-object payload violates every family schema
  // (all are type:object), so each family rejects it, naming the family.
  test('des paramètres invalides sont refusés pour chacune des neuf familles @screen:rules @req:PAR-02 @path:PAR-02', async ({
    browser,
  }) => {
    const ctx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const admin = await ctx.newPage();
    await admin.goto('/regles');
    await admin.getByTestId('plan-code').fill('PAR02_PLAN');
    await admin.getByTestId('plan-label').fill('Plan PAR-02');
    await admin.getByTestId('plan-vintage').fill('2026');
    await admin.getByTestId('plan-create').click();
    await expect(admin.getByTestId('plan-row-PAR02_PLAN')).toBeVisible();
    await admin.getByTestId('plan-open-PAR02_PLAN').click();

    const families = ['LINEAR', 'UPFRONT', 'ADVANCE', 'TIERED', 'PORTFOLIO', 'SHARE', 'OUTSTANDING', 'FIXED', 'EXTERNAL'];
    for (let i = 0; i < families.length; i += 1) {
      const family = families[i];
      const code = `PAR02_R${i}`;
      await admin.getByTestId('rule-code').fill(code);
      await admin.getByTestId('rule-label').fill(code);
      await admin.getByTestId('rule-family').selectOption(family);
      await admin.getByTestId('rule-create').click();
      await expect(admin.getByTestId(`rule-row-${code}`)).toBeVisible();
      await admin.getByTestId(`rule-open-${code}`).click();
      await admin.getByTestId('v-from').fill('2026-01-01');
      await admin.getByTestId('v-to').fill('2026-12-31');
      await admin.getByTestId('v-scope').fill('{"product":["PAR02"]}');
      // A non-object payload violates every family's type:object schema.
      await admin.getByTestId('v-parameters').fill('[]');
      await admin.getByTestId('v-group').fill('TEST');
      await admin.getByTestId('version-create').click();
      await expect(admin.getByTestId('rules-error')).toContainText(`Paramètres invalides pour la famille ${family}`);
    }
    await ctx.close();
  });

  test('CR-71 : un administrateur ne peut approuver sa propre version de règle '
    + '@screen:rules @req:PAR-04 @cr:CR-71 @path:PAR-03', async ({ browser }) => {
    const ctx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const admin = await ctx.newPage();
    await admin.goto('/regles');

    await admin.getByTestId('plan-code').fill('SOD_PLAN');
    await admin.getByTestId('plan-label').fill('Plan SoD');
    await admin.getByTestId('plan-vintage').fill('2026');
    await admin.getByTestId('plan-create').click();
    await expect(admin.getByTestId('plan-row-SOD_PLAN')).toBeVisible();

    await admin.getByTestId('plan-open-SOD_PLAN').click();
    await admin.getByTestId('rule-code').fill('SOD_RULE');
    await admin.getByTestId('rule-label').fill('Règle SoD');
    await admin.getByTestId('rule-family').selectOption('LINEAR');
    await admin.getByTestId('rule-create').click();
    await expect(admin.getByTestId('rule-row-SOD_RULE')).toBeVisible();

    await admin.getByTestId('rule-open-SOD_RULE').click();
    await admin.getByTestId('v-from').fill('2026-01-01');
    await admin.getByTestId('v-to').fill('2026-12-31');
    await admin.getByTestId('v-scope').fill('{"product":["SOD_PROD"]}');
    await admin.getByTestId('v-parameters').fill('{"allowedRates":[0.15]}');
    await admin.getByTestId('v-group').fill('SOD');
    await admin.getByTestId('version-create').click();
    await expect(admin.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('Brouillon');

    // The very administrator who created the version cannot approve it (separation of duties, CR-71).
    await admin.getByTestId('version-approve-1').click();
    await expect(admin.getByTestId('rules-error')).toContainText('Séparation des tâches');
    // The version stays a draft; the refusal changed nothing.
    await expect(admin.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('Brouillon');

    await ctx.close();
  });

  test('ajoute une version avec barème de famille, conditions et politique de reprise '
    + '@screen:rules @req:PAR-06 @req:PAR-07 @req:PAR-08', async ({ browser }) => {
    const ctx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const admin = await ctx.newPage();
    await admin.goto('/regles');

    await admin.getByTestId('plan-code').fill('PAR_PLAN');
    await admin.getByTestId('plan-label').fill('Plan paramètres');
    await admin.getByTestId('plan-vintage').fill('2026');
    await admin.getByTestId('plan-create').click();
    await admin.getByTestId('plan-open-PAR_PLAN').click();

    // A TIERED rule so the family schema guides the cumulative barème (PAR-06).
    await admin.getByTestId('rule-code').fill('PAR_RULE');
    await admin.getByTestId('rule-label').fill('Règle paliers');
    await admin.getByTestId('rule-family').selectOption('TIERED');
    await admin.getByTestId('rule-create').click();
    await admin.getByTestId('rule-open-PAR_RULE').click();

    // The family schema hint is shown so the operator knows the expected parameters.
    await expect(admin.getByTestId('family-schema')).toBeVisible();
    await expect(admin.getByTestId('family-schema')).toContainText('Barème sur cumul');

    await admin.getByTestId('v-from').fill('2026-01-01');
    await admin.getByTestId('v-scope').fill('{"product":["PAR_PROD"]}');
    await admin.getByTestId('v-parameters').fill('{"counterCode":"CUM_PREMIUM"}');
    await admin.getByTestId('v-group').fill('TIER');
    await admin.getByTestId('v-clawback').fill('{"mode":"PRORATA"}');
    await admin.getByTestId('v-schedule').fill('{"months":12}');

    // A payment condition (PAR-07): defer when the unpaid rate reaches the threshold.
    await admin.getByTestId('condition-add').click();
    await admin.getByTestId('c-indicator-0').fill('UNPAID_RATE');
    await admin.getByTestId('c-operator-0').selectOption('LT');
    await admin.getByTestId('c-threshold-0').fill('0.05');
    await admin.getByTestId('c-period-0').fill('MONTH');
    await admin.getByTestId('c-failure-0').selectOption('DEFER');

    await admin.getByTestId('version-create').click();

    const row = admin.getByTestId('version-row-1');
    await expect(row.getByTestId('version-status')).toHaveText('Brouillon');
    await expect(row).toContainText('Reprise {"mode":"PRORATA"}');
    await expect(row).toContainText('Échéancier {"months":12}');
    await expect(row).toContainText('UNPAID_RATE LT 0.05 → Report');
    await ctx.close();
  });

  test('refuse l’approbation d’une version en conflit, en nommant la règle en conflit @screen:rules @req:PAR-05 @path:PAR-05', async ({ browser }) => {
    const ctx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const admin = await ctx.newPage();
    await admin.goto('/regles');
    await admin.getByTestId('plan-code').fill('CONF_PLAN');
    await admin.getByTestId('plan-label').fill('Conflit');
    await admin.getByTestId('plan-vintage').fill('2026');
    await admin.getByTestId('plan-create').click();
    await admin.getByTestId('plan-open-CONF_PLAN').click();

    for (const code of ['C1', 'C2']) {
      await admin.getByTestId('rule-code').fill(code);
      await admin.getByTestId('rule-label').fill(code);
      await admin.getByTestId('rule-family').selectOption('LINEAR');
      await admin.getByTestId('rule-create').click();
      await admin.getByTestId(`rule-open-${code}`).click();
      await admin.getByTestId('v-from').fill('2026-01-01');
      await admin.getByTestId('v-to').fill('2026-12-31');
      await admin.getByTestId('v-scope').fill('{"product":["SAME"]}');
      await admin.getByTestId('v-parameters').fill('{"allowedRates":[0.15]}');
      await admin.getByTestId('v-group').fill('CONFGROUP');
      await admin.getByTestId('version-create').click();
    }
    await ctx.close();

    // A second administrator approves C1, then is refused on the conflicting C2.
    const ctx2 = await browser.newContext({ httpCredentials: USERS.admin2Alptis });
    const admin2 = await ctx2.newPage();
    await admin2.goto('/regles');
    await admin2.getByTestId('plan-open-CONF_PLAN').click();
    await admin2.getByTestId('rule-open-C1').click();
    await admin2.getByTestId('version-approve-1').click();
    await expect(admin2.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('Approuvée');
    await admin2.getByTestId('rule-open-C2').click();
    await admin2.getByTestId('version-approve-1').click();
    // The refusal names the conflicting rule (PAR-05), not just a count.
    await expect(admin2.getByTestId('rules-error')).toContainText('conflit');
    await expect(admin2.getByTestId('rules-error')).toContainText('C1');
    await ctx2.close();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:rules', async ({ browser }) => {
    const ctx = await browser.newContext({ httpCredentials: USERS.adminAlptis });
    const page = await ctx.newPage();
    await page.goto('/regles');
    await expect(page.getByTestId('page-title')).toBeVisible();
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
    await ctx.close();
  });
});
