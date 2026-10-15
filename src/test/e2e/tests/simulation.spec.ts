import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the simulation of a rule version on a past period (PAR-13). The substitution
// arithmetic and the reproducibility (CR-69) are verified by SimulationServiceTest on the server;
// this exercises the screen against the real engine, over the demonstration's real rule-driven lines.
//
// @screen:simulation @req:PAR-13

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

// Parcours SIM-03 (section 4.4): a settled past period replayed with its own (active) parameterization
// reproduces the real lines to the cent. Run over the demonstration's real October lines
// (DEMO_2026_LINEAR, twenty lines actually replayed), so the zero gap is a genuine reproduction, not an
// empty run.
test('l’administrateur rejoue une période passée avec son paramétrage et retrouve les lignes au centime @screen:simulation @req:PAR-13 @role-action:OPERATOR-run-simulation @path:SIM-03', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await loadDemo(page.request);
  await page.goto('/simulation');
  // ADM-03: the month and rule are chosen from lists, never typed as codes.
  await page.getByTestId('sim-month').selectOption('2026-10-01');
  await page.getByTestId('sim-rule').selectOption('DEMO_2026_LINEAR');
  await page.getByTestId('sim-version').fill('1');
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-real')).toBeVisible();
  // SIM-03: replaying with the active version reproduces the lines — zero gap, no line modified.
  await expect(page.getByTestId('sim-ecart')).toHaveText('0,00 €');
  await expect(page.getByTestId('sim-modified')).toHaveText('0');
  // Genuine replay: lines were actually replayed (not a vacuous zero-line run), and the simulated total
  // equals the real total to the cent.
  expect(Number(await page.getByTestId('sim-replayed').textContent())).toBeGreaterThan(0);
  const real = (await page.getByTestId('sim-real').textContent()) ?? '';
  expect(real).not.toBe('0,00 €');
  await expect(page.getByTestId('sim-sim')).toHaveText(real);
  await admin.close();
});

// Parcours SIM-01 (section 4.4): a project version with a modified rate — the gap is limited to the
// modified rate, and no real line, account or accounting entry is touched.
test('l’administrateur simule un taux modifié : écart limité au taux, aucune ligne réelle touchée @screen:simulation @req:PAR-13 @path:SIM-01', async ({
  browser,
}) => {
  const admin = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await admin.newPage();
  await loadDemo(page.request);
  await page.goto('/simulation');
  await page.getByTestId('sim-month').selectOption('2026-10-01');
  await page.getByTestId('sim-rule').selectOption('DEMO_2026_LINEAR');
  await page.getByTestId('sim-version').fill('1');

  // Baseline with no substitution: zero gap, and the real total to hold on to.
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-ecart')).toHaveText('0,00 €');
  const realBaseline = (await page.getByTestId('sim-real').textContent()) ?? '';
  expect(realBaseline).not.toBe('0,00 €');

  // SIM-01: raise the carried rate from 0,10 to 0,20. Only the lines borne at 0,10 change, so the gap
  // is non-zero and limited to that rate (the simulated total exceeds the real by exactly the gap).
  await page.getByTestId('sim-old-0').fill('0.1');
  await page.getByTestId('sim-new-0').fill('0.2');
  await page.getByTestId('sim-run').click();
  const parseEuro = (s: string): number => parseFloat(s.replace(/[^0-9,]/g, '').replace(',', '.'));
  await expect(page.getByTestId('sim-ecart')).not.toHaveText('0,00 €');
  expect(Number(await page.getByTestId('sim-modified').textContent())).toBeGreaterThan(0);
  // The gap is exactly the simulated total minus the real total (the effect of the modified rate).
  const real = parseEuro((await page.getByTestId('sim-real').textContent()) ?? '');
  const sim = parseEuro((await page.getByTestId('sim-sim').textContent()) ?? '');
  const ecart = parseEuro((await page.getByTestId('sim-ecart').textContent()) ?? '');
  expect(Math.abs(sim - real - ecart)).toBeLessThan(0.005);
  // No real line touched: the real total is unchanged from the baseline (the substitution wrote only in
  // the simulation space).
  await expect(page.getByTestId('sim-real')).toHaveText(realBaseline);

  // A fresh baseline re-run still reads a zero gap, proving the real lines were never overwritten.
  await page.getByTestId('sim-old-0').fill('');
  await page.getByTestId('sim-new-0').fill('');
  await page.getByTestId('sim-run').click();
  await expect(page.getByTestId('sim-ecart')).toHaveText('0,00 €');
  await expect(page.getByTestId('sim-modified')).toHaveText('0');
  await expect(page.getByTestId('sim-real')).toHaveText(realBaseline);
  await admin.close();
});

test.describe('Simulation — accessibilité', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:simulation', async ({ page }) => {
    await page.goto('/simulation');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
