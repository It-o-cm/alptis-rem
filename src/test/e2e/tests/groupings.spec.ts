import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the groupings screen (CPT-02, REF-03): the groupings and, for one, its
// members with their production codes; per-role access.
//
// @screen:groupings @req:CPT-02 @req:REF-03

test.describe('Groupements — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  // Parcours CPT-05 (section 4.3): the view of a grouping regroups its members, each shown with its
  // production-code account. The demo's GRP-NORD, headed by Cabinet Dubois, carries three members.
  test('la vue d’un groupement regroupe les comptes de ses membres @screen:groupings @req:CPT-02 @req:REF-03 @path:CPT-05', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/groupements');
    await expect(page.getByTestId('page-title')).toHaveText('Groupements');

    // The grouping lists its head.
    await expect(page.getByTestId('group-row-GRP-NORD')).toContainText('Cabinet Dubois');
    await page.getByTestId('open-group-GRP-NORD').click();
    await expect(page.getByTestId('group-detail')).toBeVisible();

    // CPT-05: its three members are regrouped under it, each carrying its production-code account.
    await expect(page.locator('[data-testid^="member-row-"]')).toHaveCount(3);
    await expect(page.getByTestId('member-row-P20001')).toContainText('Cabinet Dubois');
    await expect(page.getByTestId('member-row-P20001')).toContainText('PC-P20001');
    await expect(page.getByTestId('member-row-P20002')).toContainText('PC-P20002');
    await expect(page.getByTestId('member-row-P20003')).toContainText('PC-P20003');
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
