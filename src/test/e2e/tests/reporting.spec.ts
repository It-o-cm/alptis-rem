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

/** Sums the euro amounts shown by a set of cells (handles the French decimal comma and minus sign). */
async function sumEuros(cells: import('@playwright/test').Locator): Promise<number> {
  const texts = await cells.allTextContents();
  return texts.reduce((acc, t) => {
    const n = parseFloat(t.replace(/[^0-9,\-−]/g, '').replace('−', '-').replace(',', '.'));
    return acc + (Number.isNaN(n) ? 0 : n);
  }, 0);
}

test('le consultant lit les tableaux de bord et la recherche de commissions @screen:dashboards @screen:commissionSearch @req:REP-01 @req:REP-02 @req:REP-03 @path:REP-01', async ({
  browser,
}) => {
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const page = await viewer.newPage();
  await page.goto('/tableaux-de-bord');
  await expect(page.getByTestId('dash-reprises')).toBeVisible();
  await expect(page.locator('[data-testid^="dash-nature-amount-"]').first()).toBeVisible();

  // REP-01: the dashboard figures equal the sums of the dataset. The commission total broken down by
  // nature must equal the sum of the very lines the commission search lists over the same (unfiltered)
  // scope — both read the counted lines of the entity. Captured here before any nature filter narrows it.
  const dashboardTotal = await sumEuros(page.locator('[data-testid^="dash-nature-amount-"]'));
  expect(dashboardTotal).not.toBe(0);

  // ADM-03: the dashboard filters are chosen from lists shown by their French labels, never typed as
  // codes. The entity reads as a name; the grouping, delegation and nature read as labels.
  await expect(page.getByTestId('dash-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('dash-group').locator('option', { hasText: 'Groupement Sud (GRP-SUD)' })).toHaveCount(1);
  await expect(page.getByTestId('dash-delegation').locator('option', { hasText: 'SUD-EST' })).toHaveCount(1);
  await expect(page.getByTestId('dash-nature-filter').locator('option', { hasText: 'Acquisition' })).toHaveCount(1);

  // The charts render the data: the stacked bars, the nature donut and the top-distributor bars.
  await expect(page.getByTestId('chart-month-nature')).toBeVisible();
  await expect(page.locator('[data-testid^="chart-month-nature-bar-"]').first()).toBeVisible();
  await expect(page.locator('[data-testid^="chart-nature-slice-"]').first()).toBeVisible();
  const bar = page.locator('[data-testid^="chart-top-bar-"]').first();
  await expect(bar).toBeVisible();
  await expect(bar).toHaveAttribute('aria-label', /\d/);

  // A filter that matches nothing redraws the chart to its empty state: the seeded lines are all
  // linear, so filtering on the acquisition nature (a known value picked from the list) empties it.
  await page.getByTestId('dash-nature-filter').selectOption('ACQUISITION');
  await page.getByTestId('dash-refresh').click();
  await expect(page.getByTestId('chart-month-nature-empty')).toBeVisible();

  await page.goto('/recherche-commissions');
  // ADM-03: the entity, nature, grouping, delegation and month are chosen from lists shown by their
  // French labels, never typed as codes.
  await expect(page.getByTestId('cs-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('cs-nature-filter').locator('option', { hasText: 'Linéaire' })).toHaveCount(1);
  await expect(page.getByTestId('cs-group').locator('option', { hasText: 'Groupement Sud (GRP-SUD)' })).toHaveCount(1);
  await expect(page.getByTestId('cs-delegation').locator('option', { hasText: 'SUD-EST' })).toHaveCount(1);
  await page.getByTestId('cs-search').click();
  await expect(page.locator('[data-testid^="cs-row-"]').first()).toBeVisible();
  // The nature cell shows its French label, not the raw enum code (ADM-03).
  const natureText = (await page.locator('[data-testid^="cs-nature-"]').first().textContent())?.trim() ?? '';
  expect(natureText.length).toBeGreaterThan(0);
  expect(natureText).not.toMatch(/^[A-Z][A-Z_]+$/);
  // REP-01: the sum of the listed lines equals the dashboard's by-nature total to the cent.
  const searchTotal = await sumEuros(page.locator('[data-testid^="cs-amount-"]'));
  expect(Math.abs(searchTotal - dashboardTotal)).toBeLessThan(0.005);
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

test('l’auditeur lit la vision consolidée, le consultant en est écarté @screen:groupView @req:REP-07 @path:REP-03', async ({
  browser,
}) => {
  const auditor = await browser.newContext({ httpCredentials: USERS.auditor });
  const auditorPage = await auditor.newPage();
  // The full demo runs the cycle for both group entities, so the consolidated view totals each one.
  await loadDemo(auditorPage.request);
  await auditorPage.goto('/vision-groupe');
  // REP-03: the consolidated view is reserved to the entitled auditor and shows a total per entity —
  // both group entities appear with their own commission total, each a euro amount.
  const alptisRow = auditorPage.getByTestId('group-row-ALPTIS_ASSURANCES');
  const unepRow = auditorPage.getByTestId('group-row-UNEP');
  await expect(alptisRow).toBeVisible();
  await expect(unepRow).toBeVisible();
  await expect(alptisRow).toContainText('€');
  await expect(unepRow).toContainText('€');
  await expect(auditorPage.getByTestId('chart-group')).toBeVisible();
  await auditor.close();

  // A single-entity viewer is not entitled to the consolidated view (CR-70).
  const viewer = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
  const viewerPage = await viewer.newPage();
  await viewerPage.goto('/vision-groupe');
  await expect(viewerPage.getByTestId('group-forbidden')).toBeVisible();
  await viewer.close();
});

test('BRD-03 : le bordereau consolidé multi-entités porte un sous-total par entité @screen:groupView @req:BRD-04 @path:BRD-03', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=BRD-03&entity=UNEP&month=2028-08-01')).ok()).toBeTruthy();

  // Drive both entities' cycles to payment-prepared so the distributor's bordereaux exist in each.
  const unepCtx = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const unep = await unepCtx.newPage();
  await unep.goto('/');
  await unep.getByTestId('open-UNEP-2028-08-01').click();
  await unep.getByTestId('advance-button').click();
  await expect(unep.getByTestId('detail-state')).toContainText('Paiement préparé');
  await unepCtx.close();

  const alptisCtx = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const alptis = await alptisCtx.newPage();
  await alptis.goto('/');
  await alptis.getByTestId('open-ALPTIS_ASSURANCES-2028-08-01').click();
  await alptis.getByTestId('advance-button').click();
  await expect(alptis.getByTestId('detail-state')).toContainText('Paiement préparé');
  await alptisCtx.close();

  // The auditor (group-level) reads the distributor's consolidated bordereau: a subtotal per entity.
  const auditorCtx = await browser.newContext({ httpCredentials: USERS.auditor });
  const auditor = await auditorCtx.newPage();
  await auditor.goto('/vision-groupe');
  await auditor.getByTestId('conso-partner').selectOption('P-BRD03');
  await auditor.getByTestId('conso-month').selectOption('2028-08-01');
  await auditor.getByTestId('conso-btn').click();
  await expect(auditor.getByTestId('conso-detail')).toBeVisible();
  await expect(auditor.locator('[data-testid^="conso-row-"]')).toHaveCount(2);
  await expect(auditor.getByTestId('conso-net')).toContainText('160,00');
  await auditorCtx.close();
});

test('le contrôleur Finance lit la marge et le rapprochement assureurs @screen:margin @req:REP-08 @req:FIN-08', async ({
  browser,
}) => {
  const finance = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const page = await finance.newPage();
  // The full demo runs the annual close, which records the expected-vs-received insurer control.
  await loadDemo(page.request);
  await page.goto('/marge');
  // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
  await expect(page.getByTestId('margin-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.locator('[data-testid^="margin-product-"]').first()).toBeVisible();
  await expect(page.getByTestId('chart-margin-product')).toBeVisible();
  // FIN-08: the insurer reconciliation (expected vs received) section is shown, with at least one
  // insurer row recorded by the annual close.
  await expect(page.getByTestId('insurer-recon')).toBeVisible();
  await expect(page.locator('[data-testid^="recon-row-"]').first()).toBeVisible();
  // FIN-08 settlement: Finance records the insurer's payment advice for the month, picked from the
  // month list by its French name (ADM-03).
  await page.getByTestId('margin-month').selectOption('2026-09-01');
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
