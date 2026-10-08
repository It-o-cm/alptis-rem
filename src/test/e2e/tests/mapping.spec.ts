import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the file-mapping screen (ING-02, INT-13): test a mapping on a sample and
// save a version, with per-role access.
//
// @screen:mapping @req:ING-02 @path:INT-13

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Mapping — administrateur métier', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('teste un mapping sur l’exemple et montre le résultat pivot @screen:mapping @req:ING-02 @path:INT-13', async ({
    page,
  }) => {
    await page.goto('/mapping');
    await expect(page.getByTestId('page-title')).toHaveText('Mapping de fichier');

    await page.getByTestId('mapping-test').click();
    await expect(page.getByTestId('mapping-result')).toBeVisible();
    await expect(page.getByTestId('result-kept')).toContainText('1 ligne(s) conforme(s)');
    await expect(page.getByTestId('result-filtered')).toContainText('1 ligne(s) filtrée(s)');
    // The derived INSURER_COMMISSION row carries the received amount.
    await expect(page.getByTestId('mapping-result')).toContainText('INSURER_COMMISSION');
    await expect(page.getByTestId('mapping-result')).toContainText('30.00');
  });

  test('enregistre une version du mapping @screen:mapping @req:ING-02', async ({ page }) => {
    await page.goto('/mapping');
    await page.getByTestId('mapping-save').click();
    await expect(page.getByTestId('mapping-message')).toContainText('Version 1 enregistrée');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:mapping', async ({ page }) => {
    await page.goto('/mapping');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Mapping — accès', () => {
  test('un opérateur peut tester mais pas enregistrer @screen:mapping', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
    const page = await context.newPage();
    await page.goto('/mapping');
    await expect(page.getByTestId('mapping-test')).toBeVisible();
    await expect(page.getByTestId('mapping-save')).toHaveCount(0);
    await context.close();
  });

  test('un commercial n’a pas accès à l’écran @screen:mapping', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/mapping');
    await expect(page.getByRole('link', { name: 'Mapping de fichier' })).toHaveCount(0);
    await context.close();
  });
});
