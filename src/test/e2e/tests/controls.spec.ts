import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the controls, anomalies and catalogue (CTL-01, CTL-02, CTL-05, section 8.6).
//
// @screen:controls @req:CTL-01 @req:CTL-02 @req:CTL-05

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test.describe('Contrôles — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('lance les contrôles, voit le solde débiteur et décide l’anomalie @screen:controls @req:CTL-02 @req:CTL-05 @role-action:OPERATOR-decide-anomaly', async ({
    page,
  }) => {
    await page.goto('/controles');
    await page.getByTestId('ctl-run').click();
    await expect(page.getByTestId('ctl-message')).toContainText('anomalie');

    const row = page.locator('[data-testid^="anomaly-row-"]').first();
    await expect(row).toContainText('ACCOUNT-DEBTOR');
    const accept = page.locator('[data-testid^="anomaly-accept-"]').first();
    await accept.click();
    await expect(page.locator('[data-testid^="anomaly-decision-"]').first()).toContainText('Acceptée');
  });

  test('le catalogue des contrôles liste le contrôle de solde débiteur @screen:controls @req:CTL-01', async ({
    page,
  }) => {
    await page.goto('/controles');
    await expect(page.getByTestId('catalog-row-ACCOUNT-DEBTOR')).toBeVisible();
  });

  test('aucune violation d’accessibilité grave ou critique @screen:controls', async ({ page }) => {
    await page.goto('/controles');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
