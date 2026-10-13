import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the restitution screens: dashboards over a range (REP-01), filters (REP-03),
// commission cross-search (REP-02/03), CSV exports of the lists (REP-04), consolidated group view
// (REP-07), margin by product/insurer/distributor (REP-08) and operating indicators month by month
// (REP-09). The cross-entity isolation (CR-70) is also verified on the server.
//
// @screen:dashboards @screen:commissionSearch @screen:groupView @screen:margin @screen:indicators
// @req:REP-01 @req:REP-02 @req:REP-03 @req:REP-04 @req:REP-07 @req:REP-08 @req:REP-09

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

  // The charts render the data: the stacked bars, the nature donut and the top-distributor bars.
  await expect(page.getByTestId('chart-month-nature')).toBeVisible();
  await expect(page.locator('[data-testid^="chart-month-nature-bar-"]').first()).toBeVisible();
  await expect(page.locator('[data-testid^="chart-nature-slice-"]').first()).toBeVisible();
  const bar = page.locator('[data-testid^="chart-top-bar-"]').first();
  await expect(bar).toBeVisible();
  await expect(bar).toHaveAttribute('aria-label', /\d/);

  // A filter that matches nothing redraws the chart to its empty state.
  await page.getByTestId('dash-product').fill('PRODUIT-INEXISTANT');
  await page.getByTestId('dash-refresh').click();
  await expect(page.getByTestId('chart-month-nature-empty')).toBeVisible();

  await page.goto('/recherche-commissions');
  // ADM-03: the entity, nature and month are chosen from lists shown by their French labels,
  // never typed as codes. The entity option reads as a name, the nature option as a glossary label.
  await expect(page.getByTestId('cs-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('cs-nature-filter').locator('option', { hasText: 'Linéaire' })).toHaveCount(1);
  await page.getByTestId('cs-search').click();
  await expect(page.locator('[data-testid^="cs-row-"]').first()).toBeVisible();
  // The nature cell shows its French label, not the raw enum code (ADM-03).
  const natureText = (await page.locator('[data-testid^="cs-nature-"]').first().textContent())?.trim() ?? '';
  expect(natureText.length).toBeGreaterThan(0);
  expect(natureText).not.toMatch(/^[A-Z][A-Z_]+$/);
  await viewer.close();
});

test('le consultant ouvre une ligne et lit son explication @screen:commissionSearch @req:CAL-10 @req:FIN-07', async ({
  browser,
}) => {
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const page = await viewer.newPage();
  // The full demo runs the real engine, so its lines carry a native explanation (D14).
  await loadDemo(page.request);
  await page.goto('/recherche-commissions');
  await page.getByTestId('cs-search').click();
  await expect(page.locator('[data-testid^="cs-row-"]').first()).toBeVisible();

  // Open lines until one carries a native explanation (every engine-calculated line does).
  const rows = page.locator('[data-testid^="cs-row-"]');
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  let explained = false;
  for (let i = 0; i < count; i += 1) {
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/ui/v1/commission-lines/') && r.ok()),
      rows.nth(i).click(),
    ]);
    // Wait until the explanation area has settled to either entries or the empty message.
    await expect(
      page.getByTestId('line-explanation').or(page.getByTestId('no-explanation')),
    ).toBeVisible();
    if (await page.getByTestId('line-explanation').isVisible()) {
      explained = true;
      break;
    }
  }
  // CAL-10: at least one line shows its stored explanation in clear.
  expect(explained).toBeTruthy();
  // ADM-03: the detail shows statuses and directions by their French labels, never the raw enum.
  const detailText = (await page.getByTestId('line-detail').textContent()) ?? '';
  for (const code of ['FORECAST', 'EARNED', 'PAYABLE', 'OFFSET', 'CANCELLED', 'INBOUND', 'OUTBOUND']) {
    expect(detailText).not.toContain(code);
  }
  await viewer.close();
});

test('les listes de restitution s’exportent en CSV @screen:dashboards @screen:commissionSearch @req:REP-04', async ({
  browser,
}) => {
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const page = await viewer.newPage();
  await page.goto('/tableaux-de-bord');
  await page.getByTestId('dash-export').click();
  await expect(page.getByTestId('dash-export-content')).toContainText('month;nature;amount');

  await page.goto('/recherche-commissions');
  await page.getByTestId('cs-search').click();
  await page.getByTestId('cs-export').click();
  await expect(page.getByTestId('cs-export-content')).toContainText('distributor;product;gamme');
  await viewer.close();
});

test('l’auditeur lit la vision consolidée, le consultant en est écarté @screen:groupView @req:REP-07', async ({
  browser,
}) => {
  const auditor = await browser.newContext({ httpCredentials: USERS.auditor });
  const auditorPage = await auditor.newPage();
  await auditorPage.goto('/vision-groupe');
  await expect(auditorPage.locator('[data-testid^="group-row-"]').first()).toBeVisible();
  await expect(auditorPage.getByTestId('chart-group')).toBeVisible();
  await auditor.close();

  // A single-entity viewer is not entitled to the consolidated view (CR-70).
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const viewerPage = await viewer.newPage();
  await viewerPage.goto('/vision-groupe');
  await expect(viewerPage.getByTestId('group-forbidden')).toBeVisible();
  await viewer.close();
});

test('le contrôleur Finance lit la marge et le rapprochement assureurs @screen:margin @req:REP-08 @req:FIN-08', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  // The full demo runs the annual close, which records the expected-vs-received insurer control.
  await loadDemo(page.request);
  await page.goto('/marge');
  await expect(page.locator('[data-testid^="margin-product-"]').first()).toBeVisible();
  await expect(page.getByTestId('chart-margin-product')).toBeVisible();
  // FIN-08: the insurer reconciliation (expected vs received) section is shown, with at least one
  // insurer row recorded by the annual close.
  await expect(page.getByTestId('insurer-recon')).toBeVisible();
  await expect(page.locator('[data-testid^="recon-row-"]').first()).toBeVisible();
  // FIN-08 settlement: Finance records the insurer's payment advice for the month.
  await page.getByTestId('margin-month').fill('2026-09-01');
  await page.getByTestId('margin-settle').click();
  await expect(page.getByTestId('margin-message')).toContainText('Règlement assureur enregistré');
  await finance.close();
});

test('le consultant lit les indicateurs de fonctionnement mois par mois @screen:indicators @req:REP-09', async ({
  browser,
}) => {
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const page = await viewer.newPage();
  await page.goto('/indicateurs-fonctionnement');
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('ind-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.locator('[data-testid^="ind-rules-"]').first()).toBeVisible();
  await expect(page.locator('[data-testid^="ind-clawback-"]').first()).toBeVisible();
  await expect(page.getByTestId('chart-ind-rules')).toBeVisible();
  await expect(page.getByTestId('chart-ind-clawback')).toBeVisible();
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
