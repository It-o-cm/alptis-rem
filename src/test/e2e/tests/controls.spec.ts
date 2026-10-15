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

  // Parcours CTL-05 (section 4.4): editing a control in the catalogue (its severity, then its
  // deactivation) writes a new version and takes effect at the next run. The debtor-balance control
  // fires once, is then edited and deactivated, and no longer fires on a second run.
  test('éditer puis désactiver un contrôle du catalogue le versionne et prend effet au calcul suivant @screen:controls @req:CTL-05 @path:CTL-05', async ({
    page,
  }) => {
    await page.goto('/controles');
    // Baseline: the debtor-balance control fires on the demo period.
    await page.getByTestId('ctl-run').click();
    await expect(page.getByTestId('ctl-message')).toContainText('anomalie');
    await expect(
      page.locator('[data-testid^="anomaly-row-"]').filter({ hasText: 'ACCOUNT-DEBTOR' }),
    ).toHaveCount(1);

    // Edit its severity, then deactivate it, saving a new active version.
    await page.getByTestId('catalog-edit-ACCOUNT-DEBTOR').click();
    await expect(page.getByTestId('catalog-edit-form')).toBeVisible();
    await page.getByTestId('edit-severity').selectOption('WARNING');
    await page.getByTestId('edit-active').uncheck();
    await page.getByTestId('edit-save').click();
    await expect(page.getByTestId('ctl-message')).toContainText('versionné');
    // The catalogue now shows the control inactive, and a second version is recorded.
    await expect(page.getByTestId('catalog-row-ACCOUNT-DEBTOR')).toContainText('Non');
    await page.getByTestId('catalog-versions-ACCOUNT-DEBTOR').click();
    await expect(page.getByTestId('version-row-2')).toBeVisible();
    await expect(page.getByTestId('version-row-1')).toBeVisible();

    // Effect at the next run: the deactivated control no longer raises its anomaly.
    await page.getByTestId('ctl-run').click();
    await expect(page.getByTestId('ctl-message')).toContainText('anomalie');
    await expect(
      page.locator('[data-testid^="anomaly-row-"]').filter({ hasText: 'ACCOUNT-DEBTOR' }),
    ).toHaveCount(0);
  });

  // Parcours CTL-03 (section 4.4): a blocking anomaly left open refuses the period's validation. The
  // admin (also an operator) makes the debtor-balance control blocking, runs the controls so its
  // anomaly is raised and left open, then tries to move the period on from CALCULATED — the cycle guard
  // turns the transition down. admin-alptis holds BUSINESS_ADMIN and OPERATOR, so one context suffices.
  test('une anomalie bloquante ouverte refuse l’avancement de la période @screen:controls @req:CTL-03 @path:CTL-03', async ({
    page,
  }) => {
    // Make the debtor-balance control blocking (a new active version).
    await page.goto('/controles');
    await page.getByTestId('catalog-edit-ACCOUNT-DEBTOR').click();
    await expect(page.getByTestId('catalog-edit-form')).toBeVisible();
    await page.getByTestId('edit-severity').selectOption('BLOCKING');
    await page.getByTestId('edit-save').click();
    await expect(page.getByTestId('ctl-message')).toContainText('versionné');

    // Run the controls: the debtor-balance anomaly is now blocking and stays open (no decision).
    await page.getByTestId('ctl-run').click();
    await expect(page.getByTestId('ctl-message')).toContainText('anomalie');
    const row = page.locator('[data-testid^="anomaly-row-"]').filter({ hasText: 'ACCOUNT-DEBTOR' }).first();
    await expect(row).toContainText('Bloquant');

    // The period cannot move on from CALCULATED while the blocking anomaly is open.
    await page.goto('/periodes');
    await page.getByTestId('open-ALPTIS_ASSURANCES-2026-09-01').click();
    await expect(page.getByTestId('period-detail')).toBeVisible();
    await expect(page.getByTestId('detail-state')).toContainText('Calculée');
    await page.getByTestId('advance-button').click();
    await expect(page.getByTestId('periods-error')).toContainText('anomalie bloquante reste ouverte');
    // The refusal left the period unchanged.
    await expect(page.getByTestId('detail-state')).toContainText('Calculée');
  });
});
