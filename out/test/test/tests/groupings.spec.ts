import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the groupings screen (CPT-02, REF-03): the groupings and, for one, its
// members with their production codes; per-role access.
//
// @screen:groupings @req:CPT-02 @req:REF-03

test.describe('Groupements — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les groupements et ouvre les membres de l’un @screen:groupings @req:CPT-02 @req:REF-03', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/groupements');
    await expect(page.getByTestId('page-title')).toHaveText('Groupements');

    const row = page.locator('[data-testid^="group-row-"]').first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Membres' }).click();
    await expect(page.getByTestId('group-detail')).toBeVisible();
    await expect(page.locator('[data-testid^="member-row-"]').first()).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:groupings', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/groupements');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Groupements — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:groupings', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/groupements');
    await expect(page.getByRole('link', { name: 'Groupements' })).toHaveCount(0);
    await context.close();
  });
});
