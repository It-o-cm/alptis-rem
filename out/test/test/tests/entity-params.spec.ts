import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the entity parameters screen (REF-01, ADM-05), including the entity
// partition (CR-70) and per-role access.
//
// @screen:entities @req:REF-01 @req:ADM-05

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Entités et paramètres — administrateur', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('affiche les entités visibles et leurs paramètres @screen:entities @req:REF-01 @req:ADM-05', async ({ page }) => {
    await page.goto('/entites');
    await expect(page.getByTestId('page-title')).toHaveText('Entités et paramètres');
    await expect(page.getByTestId('entity-row-ALPTIS_ASSURANCES')).toBeVisible();
    await expect(page.getByTestId('entity-row-ALPTIS_ASSURANCES')).toContainText('Alptis Assurances');
  });

  test('enregistre une modification de paramètre et la persiste @screen:entities @req:ADM-05', async ({ page }) => {
    await page.goto('/entites');
    await page.getByTestId('entity-row-ALPTIS_ASSURANCES').click();
    await expect(page.getByTestId('entity-form')).toBeVisible();
    await page.getByTestId('field-min-payment').fill('42.50');
    await page.getByTestId('save-entity').click();
    await expect(page.getByTestId('save-message')).toHaveText('Paramètres enregistrés.');

    // Persisted: reload and reopen.
    await page.reload();
    await page.getByTestId('entity-row-ALPTIS_ASSURANCES').click();
    await expect(page.getByTestId('field-min-payment')).toHaveValue('42.5');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:entities', async ({ page }) => {
    await page.goto('/entites');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Entités et paramètres — cloisonnement', () => {
  test.use({ httpCredentials: USERS.operatorUnep });

  test('CR-70 : un utilisateur UNEP ne voit aucune donnée Alptis @screen:entities @cr:CR-70 @path:CR-70', async ({
    page,
  }) => {
    await page.goto('/entites');
    await expect(page.getByTestId('entity-row-UNEP')).toBeVisible();
    await expect(page.getByTestId('entity-row-ALPTIS_ASSURANCES')).toHaveCount(0);
  });

  test('un opérateur ne peut pas modifier les paramètres @screen:entities', async ({ page }) => {
    await page.goto('/entites');
    await page.getByTestId('entity-row-UNEP').click();
    await expect(page.getByTestId('entity-form')).toBeVisible();
    await expect(page.getByTestId('save-entity')).toHaveCount(0);
  });
});

test.describe('Entités et paramètres — accès refusé', () => {
  test.use({ httpCredentials: USERS.itAdmin });

  test('le rôle technique n’a aucun droit sur les montants @screen:entities', async ({ page }) => {
    await page.goto('/entites');
    await expect(page.getByTestId('load-error')).toBeVisible();
    // The navigation does not offer the screen to this role.
    await expect(page.getByRole('link', { name: 'Entités et paramètres' })).toHaveCount(0);
  });
});
