import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the rule-configuration screen (PAR-01 to PAR-05, PAR-12): plans, rules,
// versions, the approval by a second administrator and the conflict display.
//
// @screen:rules @req:PAR-01 @req:PAR-03 @req:PAR-04 @req:PAR-05 @req:PAR-12

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Paramétrage des règles', () => {
  test('crée un plan, une règle et une version, approuvée par un second administrateur '
    + '@screen:rules @req:PAR-01 @req:PAR-04 @path:PAR-01', async ({ browser }) => {
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
    await expect(admin.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('DRAFT');

    // A second administrator approves it.
    const admin2Ctx = await browser.newContext({ httpCredentials: USERS.admin2Alptis });
    const admin2 = await admin2Ctx.newPage();
    await admin2.goto('/regles');
    await admin2.getByTestId('plan-open-E2E_PLAN').click();
    await admin2.getByTestId('rule-open-E2E_RULE').click();
    await admin2.getByTestId('version-approve-1').click();
    await expect(admin2.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('APPROVED');

    await adminCtx.close();
    await admin2Ctx.close();
  });

  test('refuse l’approbation d’une version en conflit @screen:rules @req:PAR-05', async ({ browser }) => {
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
    await expect(admin2.getByTestId('version-row-1').getByTestId('version-status')).toHaveText('APPROVED');
    await admin2.getByTestId('rule-open-C2').click();
    await admin2.getByTestId('version-approve-1').click();
    await expect(admin2.getByTestId('rules-error')).toContainText('conflit');
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
