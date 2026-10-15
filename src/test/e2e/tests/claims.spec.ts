import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the claims screen (REG-06): list, fiche with contested lines, recording and
// closing a claim; per-role access.
//
// @screen:claims @req:REG-06

test.describe('Réclamations — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('consulte la fiche d’une réclamation, en enregistre une et la clôt @screen:claims @req:REG-06', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/reclamations');
    await expect(page.getByTestId('page-title')).toHaveText('Réclamations');

    // The demo seeds a claim: open its fiche and read the contested line.
    const row = page.locator('[data-testid^="claim-row-"]').first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Fiche' }).click();
    await expect(page.getByTestId('claim-detail')).toBeVisible();
    await expect(page.locator('[data-testid^="contested-line-"]').first()).toBeVisible();

    // Close that claim.
    await page.getByTestId('claim-resolution').fill('Taux corrigé, ajustement passé');
    await page.getByTestId('claim-resolve').click();
    await expect(page.getByTestId('claim-message')).toContainText('close');

    // Record a new claim for a known distributor, picked from the list by its name (ADM-03).
    await expect(page.getByTestId('claim-distributor').locator('option', { hasText: 'Cabinet Martin' })).toHaveCount(1);
    await page.getByTestId('claim-distributor').selectOption('P13897');
    await page.getByTestId('claim-subject').fill('Commission sous-évaluée');
    await page.getByTestId('claim-submit').click();
    await expect(page.getByTestId('claim-message')).toContainText('enregistrée');
    await expect(page.getByText('Commission sous-évaluée')).toBeVisible();
  });

  // Parcours REC-01 (section 4.4): a claim is opened against a contested line, taken into treatment,
  // regularised by an adjustment that corrects that line, and closed — its state is shown at each step
  // (Ouverte → En cours → Close) and the regularising adjustment is visible on the fiche (ajustement lié).
  test('une réclamation suit ses états et porte l’ajustement qui la régularise @screen:claims @req:REG-06 @path:REC-01', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/reclamations');

    // The demo-seeded claim is open and lists a contested line.
    await page.locator('[data-testid^="claim-row-"]').first().getByRole('button', { name: 'Fiche' }).click();
    await expect(page.getByTestId('claim-detail')).toBeVisible();
    await expect(page.getByTestId('claim-detail-status')).toContainText('Ouverte');
    const contested = page.locator('[data-testid^="contested-line-"]').first();
    await expect(contested).toBeVisible();
    const lineId = Number(((await contested.getAttribute('data-testid')) ?? '').replace('contested-line-', ''));

    // An adjustment corrects that very line: the regularisation (REG-02).
    const auth = 'Basic ' + Buffer.from(
      `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
    const adj = await page.request.post('/api/ui/v1/adjustments/ALPTIS_ASSURANCES/2026-09-01', {
      headers: { Authorization: auth },
      data: {
        type: 'CORRECTION',
        amount: 9,
        payeeType: 'DISTRIBUTOR',
        payeeId: 1,
        reason: 'Régularisation réclamation',
        correctedLineId: lineId,
      },
    });
    expect(adj.ok(), `${adj.status()} ${await adj.text()}`).toBeTruthy();

    // Take the claim into treatment: Ouverte → En cours (état à chaque étape).
    await page.getByTestId('claim-take').click();
    await expect(page.getByTestId('claim-detail-status')).toContainText('En cours');

    // The regularising adjustment is now linked on the fiche (ajustement lié).
    const linked = page.locator('[data-testid^="claim-adjustment-"]').first();
    await expect(linked).toBeVisible();
    await expect(linked).toContainText('Correction');

    // Close it: En cours → Close.
    await page.getByTestId('claim-resolution').fill('Régularisé par ajustement +9');
    await page.getByTestId('claim-resolve').click();
    await expect(page.getByTestId('claim-message')).toContainText('close');
    await expect(page.getByTestId('claim-detail-status')).toContainText('Close');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:claims', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/reclamations');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Réclamations — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:claims', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/reclamations');
    await expect(page.getByRole('link', { name: 'Réclamations' })).toHaveCount(0);
    await context.close();
  });
});
