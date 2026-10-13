import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, USERS } from '../support/helpers';

// Per-screen tests for the document templates (BRD-05): the business administrator lists and edits
// the models (logo, mentions, columns, order); per-role access.
//
// @screen:documentTemplates @req:BRD-05

test.describe('Modèles de documents — administrateur métier', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('liste et modifie un modèle de document @screen:documentTemplates @req:BRD-05', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/modeles-documents');
    await expect(page.getByTestId('page-title')).toHaveText('Modèles de documents');
    // ADM-03: the entity is chosen from a list shown by its name, never typed as a code.
    await expect(page.getByTestId('tpl-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
    // The demo seeds the statement, member-statement and tax-certificate models.
    await expect(page.getByTestId('tpl-row-STATEMENT')).toBeVisible();

    // The administrator edits the statement model; a new version is recorded.
    await page.getByTestId('tpl-type').selectOption('STATEMENT');
    await page.getByTestId('tpl-content').fill('{"logo":"ALPTIS","mentions":"Mise à jour","columns":["garantie"]}');
    await page.getByTestId('tpl-save').click();
    await expect(page.getByTestId('tpl-message')).toContainText('version 2');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:documentTemplates', async ({ page }) => {
    await loadDemo(page.request);
    await page.goto('/modeles-documents');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Modèles de documents — accès', () => {
  test('un commercial n’a pas accès à l’écran @screen:documentTemplates', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/modeles-documents');
    await expect(page.getByRole('link', { name: 'Modèles de documents' })).toHaveCount(0);
    await context.close();
  });
});
