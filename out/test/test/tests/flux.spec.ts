import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the flow catalogue (ING-10, section 9.1) and the exchange-format reference
// (section 9.9).
//
// @screen:flows @screen:formats @req:ING-10

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test('l’opérateur lit le catalogue des flux et le filtre par sens @screen:flows @req:ING-10', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/flux');
  await expect(page.getByTestId('flow-row-IN-01')).toBeVisible();
  await expect(page.getByTestId('flow-row-OUT-05')).toBeVisible();
  await page.getByTestId('flux-filter-INBOUND').click();
  await expect(page.getByTestId('flow-row-IN-01')).toBeVisible();
  await expect(page.getByTestId('flow-row-OUT-05')).toHaveCount(0);
  await operator.close();
});

test('l’opérateur lit la référence des formats @screen:formats', async ({ browser }) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/formats');
  await expect(page.getByTestId('format-file-0')).toBeVisible();
  await expect(page.getByTestId('format-convention-0')).toContainText('UTF-8');
  await operator.close();
});

test.describe('Flux — accessibilité', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:flows', async ({ page }) => {
    await page.goto('/flux');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
