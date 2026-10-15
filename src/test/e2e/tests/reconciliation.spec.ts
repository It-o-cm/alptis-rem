import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the acquired-versus-paid reconciliation (FIN-06): the global totals and the
// per-beneficiary breakdown; per-role access.
//
// @screen:reconciliation @req:FIN-06

test.describe('Rapprochement — contrôleur Finance', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  // Parcours FIN-08 (section 4.6): the acquired-versus-paid reconciliation, per beneficiary and global.
  test('rapproche les commissions acquises et payées, par bénéficiaire et au global @screen:reconciliation @req:FIN-06 @path:FIN-08', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/rapprochement');
    await expect(page.getByTestId('page-title')).toHaveText('Rapprochement des commissions acquises et payées');
    // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
    await expect(page.getByTestId('rec-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);

    // Au global: the acquired, paid and gap totals are shown, and the gap is acquired minus paid.
    const parseEuro = (s: string): number => {
      const neg = s.includes('-') || s.includes('−');
      const n = parseFloat(s.replace(/[^0-9,]/g, '').replace(',', '.'));
      return neg ? -n : n;
    };
    const acquired = parseEuro((await page.getByTestId('rec-acquired').textContent()) ?? '');
    const paid = parseEuro((await page.getByTestId('rec-paid').textContent()) ?? '');
    const gap = parseEuro((await page.getByTestId('rec-gap').textContent()) ?? '');
    expect(acquired).toBeGreaterThan(0);
    expect(Math.abs(gap - (acquired - paid))).toBeLessThan(0.005);

    // Par bénéficiaire: the beneficiary rows' acquired totals sum to the global acquired.
    await expect(page.locator('[data-testid^="rec-row-"]').first()).toBeVisible();
    const cells = page.locator('[data-testid^="rec-acq-"]');
    const texts = await cells.allTextContents();
    expect(texts.length).toBeGreaterThan(0);
    const sum = texts.reduce((acc, t) => acc + parseEuro(t), 0);
    expect(Math.abs(sum - acquired)).toBeLessThan(0.005);
  });

  test('aucune violation d’accessibilité grave ou critique @screen:reconciliation', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/rapprochement');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Rapprochement — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:reconciliation', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/rapprochement');
    await expect(page.getByRole('link', { name: 'Rapprochement acquises/payées' })).toHaveCount(0);
    await context.close();
  });
});
