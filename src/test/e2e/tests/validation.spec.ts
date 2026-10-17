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

test('CYC-05 : un second niveau de validation est exigé ou non selon le paramétrage @screen:validation @path:CYC-05', async ({
  browser,
  request,
}) => {
  await resetDb(request);
  expect((await request.post('/api/test/cr-scenario?cr=CYC-05&entity=UNEP&month=2027-03-01')).ok()).toBeTruthy();

  // The entity validator opens the screen.
  const context = await browser.newContext({ httpCredentials: USERS.validatorUnep });
  const page = await context.newPage();
  await page.goto('/validation');

  // The modest, steady month needs no second level: a single validation business-validates it.
  await page.getByTestId('validation-period').selectOption('UNEP|2027-03-01');
  await expect(page.getByTestId('validation-second')).toHaveText('Non requis');
  await page.getByTestId('validation-validate').click();
  await expect(page.getByTestId('validation-message')).toContainText('validée par le métier');
  await expect(page.getByTestId('validation-state')).toHaveText('Validée métier');

  // The month over the threshold requires a second level: the first validation leaves it controlled.
  await page.getByTestId('validation-period').selectOption('UNEP|2027-04-01');
  await expect(page.getByTestId('validation-second')).toHaveText('Requis');
  await page.getByTestId('validation-validate').click();
  await expect(page.getByTestId('validation-message')).toContainText('second validateur');
  await expect(page.getByTestId('validation-first')).toHaveText('validator-unep');
  await expect(page.getByTestId('validation-state')).toHaveText('Contrôlée');
  await context.close();

  // A different, group-level validator gives the second level; the period is business-validated.
  const groupCtx = await browser.newContext({ httpCredentials: USERS.groupValidator });
  const groupPage = await groupCtx.newPage();
  await groupPage.goto('/validation');
  await groupPage.getByTestId('validation-period').selectOption('UNEP|2027-04-01');
  await groupPage.getByTestId('validation-validate').click();
  await expect(groupPage.getByTestId('validation-message')).toContainText('validée par le métier');
  await expect(groupPage.getByTestId('validation-state')).toHaveText('Validée métier');
  await groupCtx.close();
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
