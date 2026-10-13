import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the restitution screens: dashboards (REP-01), commission cross-search
// (REP-02/03), consolidated group view (REP-07) and operating indicators (REP-09). The cross-entity
// isolation (CR-70) is also verified by PublicApiResourceTest on the server.
//
// @screen:dashboards @screen:commissionSearch @screen:groupView @screen:indicators
// @req:REP-01 @req:REP-02 @req:REP-03 @req:REP-07 @req:REP-09

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('le consultant lit les tableaux de bord et la recherche de commissions @screen:dashboards @screen:commissionSearch @req:REP-01 @req:REP-02 @req:REP-03', async ({
  browser,
}) => {
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const page = await viewer.newPage();
  await page.goto('/tableaux-de-bord');
  await expect(page.getByTestId('dash-reprises')).toBeVisible();
  await expect(page.locator('[data-testid^="dash-nature-"]').first()).toBeVisible();

  await page.goto('/recherche-commissions');
  await page.getByTestId('cs-search').click();
  await expect(page.locator('[data-testid^="cs-row-"]').first()).toBeVisible();
  await viewer.close();
});

test('l’auditeur lit la vision consolidée, le consultant en est écarté @screen:groupView @req:REP-07', async ({
  browser,
}) => {
  const auditor = await browser.newContext({ httpCredentials: USERS.auditor });
  const auditorPage = await auditor.newPage();
  await auditorPage.goto('/vision-groupe');
  await expect(auditorPage.getByTestId('group-total')).toBeVisible();
  await auditor.close();

  // A single-entity viewer is not entitled to the consolidated view (CR-70).
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const viewerPage = await viewer.newPage();
  await viewerPage.goto('/vision-groupe');
  await expect(viewerPage.getByTestId('group-forbidden')).toBeVisible();
  await viewer.close();
});

test('le consultant lit les indicateurs de fonctionnement @screen:indicators @req:REP-09', async ({
  browser,
}) => {
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const page = await viewer.newPage();
  await page.goto('/indicateurs-fonctionnement');
  await expect(page.getByTestId('ind-rules')).toBeVisible();
  await expect(page.getByTestId('ind-clawback')).toBeVisible();
  await viewer.close();
});

test.describe('Restitutions — accessibilité', () => {
  test.use({ httpCredentials: USERS.viewerAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:dashboards', async ({ page }) => {
    await page.goto('/tableaux-de-bord');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
