import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the technical administration: supervision of treatments and flows (OUT-07,
// NFR-16) and purge/anonymisation of personal data (ADM-06), reserved to the technical administrator.
//
// @screen:supervision @screen:purge @req:ADM-06

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’administrateur technique lit la supervision @screen:supervision @req:ADM-06', async ({
  browser,
}) => {
  const itAdmin = await browser.newContext({ httpCredentials: USERS.itAdmin });
  const page = await itAdmin.newPage();
  await page.goto('/supervision');
  // ADM-03: the health status reads as its French label, never the raw code.
  await expect(page.getByTestId('sup-status')).toHaveText('Opérationnel');
  await expect(page.getByTestId('sup-pending')).toBeVisible();
  await itAdmin.close();
});

test('l’administrateur technique lance la purge des données personnelles @screen:purge @req:ADM-06 @role-action:IT_ADMIN-purge', async ({
  browser,
}) => {
  const itAdmin = await browser.newContext({ httpCredentials: USERS.itAdmin });
  const page = await itAdmin.newPage();
  await page.goto('/purge');
  await expect(page.getByTestId('purge-eligible')).toBeVisible();
  await page.getByTestId('purge-run').click();
  await expect(page.getByTestId('purge-message')).toContainText('anonymisé');
  await itAdmin.close();
});

test.describe('Supervision — accessibilité', () => {
  test.use({ httpCredentials: USERS.itAdmin });

  test('aucune violation d’accessibilité grave ou critique @screen:supervision', async ({ page }) => {
    await page.goto('/supervision');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
