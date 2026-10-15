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
    // ADM-03: the entity and month are chosen from lists shown by their names, never typed as codes.
    await expect(page.getByTestId('ctl-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
    await expect(page.getByTestId('ctl-month').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
    await page.getByTestId('ctl-run').click();
    await expect(page.getByTestId('ctl-message')).toContainText('anomalie');

    // The debtor-balance anomaly is raised among the delivered controls (section 8.6).
    const row = page
      .locator('[data-testid^="anomaly-row-"]')
      .filter({ hasText: 'ACCOUNT-DEBTOR' })
      .first();
    await expect(row).toBeVisible();
    // ADM-03: the severity is shown by its French label, not the raw enum code.
    await expect(row).toContainText(/Bloquant|Avertissement|Information/);
    await expect(page.getByTestId('chart-ctl-severity')).toBeVisible();
    await expect(page.getByTestId('chart-ctl-family')).toBeVisible();
    // Decide that very anomaly and read its traced decision back.
    await row.locator('[data-testid^="anomaly-accept-"]').click();
    await expect(row.locator('[data-testid^="anomaly-decision-"]')).toContainText('Acceptée');
  });

  test('le catalogue des contrôles liste les familles livrées @screen:controls @req:CTL-01 @req:CTL-02', async ({
    page,
  }) => {
    await page.goto('/controles');
    // The delivered catalogue spans the ten families of section 8.6 (CTL-02), not just the account one.
    await expect(page.getByTestId('catalog-row-ACCOUNT-DEBTOR')).toBeVisible();
    await expect(page.getByTestId('catalog-row-COMP-FLOWS')).toBeVisible();
    await expect(page.getByTestId('catalog-row-DATA-IBAN')).toBeVisible();
    await expect(page.getByTestId('catalog-row-CALC-DIST-VARIATION')).toBeVisible();
    await expect(page.getByTestId('catalog-row-ADV-RECEIVABLE-CAP')).toBeVisible();
    await expect(page.getByTestId('catalog-row-PAY-LOT-BALANCE')).toBeVisible();
    await expect(page.getByTestId('catalog-row-ACC-BALANCED')).toBeVisible();
    await expect(page.getByTestId('catalog-row-EXT-INSURER-MISMATCH')).toBeVisible();
  });

  test('décide trois anomalies — acceptée, corrigée, reportée — et suit le compteur d’ouvertes (CTL-02) @screen:controls @req:CTL-02 @path:CTL-02', async ({
    page,
  }) => {
    await page.goto('/controles');
    await page.getByTestId('ctl-run').click();
    await expect(page.getByTestId('ctl-message')).toContainText('anomalie');
    const openCount = async () => Number((await page.getByTestId('ctl-open-count').textContent())?.trim());
    const start = await openCount();
    expect(start).toBeGreaterThanOrEqual(3);
    const rows = page.locator('[data-testid^="anomaly-row-"]');
    // Accept the first, fix the second, defer the third; each decision is labelled and traced.
    await rows.nth(0).locator('[data-testid^="anomaly-accept-"]').click();
    await rows.nth(1).locator('[data-testid^="anomaly-fix-"]').click();
    await rows.nth(2).locator('[data-testid^="anomaly-defer-"]').click();
    await expect(page.locator('[data-testid^="anomaly-decision-"]').filter({ hasText: 'Acceptée' })).not.toHaveCount(0);
    await expect(page.locator('[data-testid^="anomaly-decision-"]').filter({ hasText: 'Corrigée' })).not.toHaveCount(0);
    await expect(page.locator('[data-testid^="anomaly-decision-"]').filter({ hasText: 'Reportée' })).not.toHaveCount(0);
    // Three decisions close three open anomalies.
    expect(await openCount()).toBe(start - 3);
  });

  test('coche un contrôle dans la liste de contrôle de la période @screen:controls @req:CTL-03 @path:CTL-06', async ({
    page,
  }) => {
    await page.goto('/controles');
    await expect(page.getByTestId('checklist')).toBeVisible();
    await expect(page.getByTestId('checklist-state-ACCOUNT-DEBTOR')).toContainText('À faire');
    await page.getByTestId('checklist-toggle-ACCOUNT-DEBTOR').click();
    await expect(page.getByTestId('checklist-state-ACCOUNT-DEBTOR')).toContainText('Oui');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:controls', async ({ page }) => {
    await page.goto('/controles');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});

test.describe('Contrôles — administrateur métier', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('édite un contrôle du catalogue et le versionne @screen:controls @req:CTL-01 @req:CTL-04', async ({
    page,
  }) => {
    await page.goto('/controles');
    await expect(page.getByTestId('catalog-row-DATA-FUTURE')).toBeVisible();

    await page.getByTestId('catalog-edit-DATA-FUTURE').click();
    await expect(page.getByTestId('catalog-edit-form')).toBeVisible();
    await page.getByTestId('edit-severity').selectOption('BLOCKING');
    await page.getByTestId('edit-save').click();
    await expect(page.getByTestId('ctl-message')).toContainText('versionné');

    await page.getByTestId('catalog-versions-DATA-FUTURE').click();
    await expect(page.getByTestId('catalog-versions')).toBeVisible();
    await expect(page.getByTestId('version-row-2')).toBeVisible();
    await expect(page.getByTestId('version-row-1')).toBeVisible();
  });
});
