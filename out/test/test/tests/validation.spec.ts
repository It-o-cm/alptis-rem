import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, DEMO_MONTH, USERS } from '../support/helpers';

// Per-screen tests for the business-validation screen (WKF-02): the period totals, the gap with the
// previous month, the second-level rule and the validation level.
//
// @screen:validation @req:WKF-02

test('le validateur lit les totaux et donne un premier niveau de validation @screen:validation @req:WKF-02', async ({
  browser,
}) => {
  // An operator resets, seeds and advances the period to controlled through the periods screen.
  const op = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const opPage = await op.newPage();
  await resetDb(opPage.request);
  await seedCycle(opPage.request);
  await opPage.goto('/');
  await opPage.getByTestId(`open-ALPTIS_ASSURANCES-${DEMO_MONTH}`).click();
  await expect(opPage.getByTestId('detail-state')).toContainText('Calculée');
  await opPage.getByTestId('advance-button').click();
  await expect(opPage.getByTestId('detail-state')).toContainText('Contrôlée');
  await op.close();

  // The business validator reads the summary and gives a first level (a second is required: the
  // seeded month has no previous month, so the gap triggers the rule).
  const context = await browser.newContext({ httpCredentials: USERS.validatorAlptis });
  const page = await context.newPage();
  await page.goto('/validation');
  await expect(page.getByTestId('page-title')).toHaveText('Validation métier');
  await expect(page.getByTestId('validation-summary')).toBeVisible();
  await expect(page.getByTestId('validation-state')).toHaveText('Contrôlée');
  await expect(page.getByTestId('validation-second')).toHaveText('Requis');
  await expect(page.locator('[data-testid^="nature-row-"]').first()).toBeVisible();

  await page.getByTestId('validation-validate').click();
  await expect(page.getByTestId('validation-message')).toContainText('second validateur');
  await expect(page.getByTestId('validation-first')).toHaveText('validator-alptis');
  await context.close();
});

test.describe('Validation métier — accès', () => {
  test.beforeEach(async ({ request }) => {
    await resetDb(request);
    await seedCycle(request);
  });

  test('aucune violation d’accessibilité grave ou critique @screen:validation', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.validatorAlptis });
    const page = await context.newPage();
    await page.goto('/validation');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
    await context.close();
  });

  test('un commercial n’a pas accès à l’écran @screen:validation', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/validation');
    await expect(page.getByRole('link', { name: 'Validation métier' })).toHaveCount(0);
    await context.close();
  });
});
