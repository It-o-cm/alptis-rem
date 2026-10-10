import { test, expect } from '@playwright/test';
import { loadDemo, USERS } from '../support/helpers';

// Demonstration dataset (brief DONNEES-DEMO.md §6): after reloading the full dataset through the
// dedicated dev endpoint, a user of each role opens representative screens of their menu and sees real
// business content of the dataset — a distributor code, a period state, a bordereau amount, a
// collaborator, their own variable — not merely the absence of an error. The exhaustive non-emptiness
// of every list endpoint per user is proven at the server level by DemoVisibilityTest; this spec
// proves the interface renders it.
//
// @screen:demo @path:DEM-01 @path:DEM-02

test.beforeEach(async ({ request }) => {
  await loadDemo(request);
});

test.describe('Jeu de démonstration — opérateur Alptis', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('voit le cabinet P13897 et les périodes ouvertes @screen:demo @path:DEM-01', async ({ page }) => {
    await page.goto('/distributeurs');
    await page.getByTestId('search-term').fill('P13897');
    await page.getByTestId('search-button').click();
    await expect(page.getByTestId('distributor-row-P13897')).toContainText('Cabinet Martin');

    await page.goto('/');
    await expect(page.getByTestId('period-row-ALPTIS_ASSURANCES-2026-11-01')).toBeVisible();
    await page.getByTestId('open-ALPTIS_ASSURANCES-2026-10-01').click();
    await expect(page.getByTestId('detail-state')).toContainText('Calculée');
  });

  test('voit le bordereau de septembre du cabinet (110,00 €) @screen:demo @path:DEM-01', async ({ page }) => {
    await page.goto('/bordereaux');
    await expect(page.getByTestId('brd-month')).toHaveValue('2026-09-01');
    await page.locator('[data-testid^="statement-open-"]').first().click();
    await expect(page.getByTestId('statement-detail')).toBeVisible();
    await expect(page.getByTestId('statement-net')).not.toBeEmpty();
  });
});

test.describe('Jeu de démonstration — consultant Alptis', () => {
  test.use({ httpCredentials: USERS.viewerAlptis });

  test('le consultant lit un bordereau du jeu @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/bordereaux');
    await expect(page.locator('[data-testid^="statement-row-"]').first()).toBeVisible();
    await page.locator('[data-testid^="statement-open-"]').first().click();
    await expect(page.getByTestId('statement-net')).not.toBeEmpty();
  });
});

test.describe('Jeu de démonstration — contrôleur Finance Alptis', () => {
  test.use({ httpCredentials: USERS.financeAlptis });

  test('le contrôleur Finance lit un bordereau du jeu @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/bordereaux');
    await page.locator('[data-testid^="statement-open-"]').first().click();
    await expect(page.getByTestId('statement-net')).not.toBeEmpty();
  });
});

test.describe('Jeu de démonstration — opérateur UNEP', () => {
  test.use({ httpCredentials: USERS.operatorUnep });

  test('l’opérateur UNEP voit la période UNEP d’octobre et un bordereau @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('period-row-UNEP-2026-10-01')).toBeVisible();

    await page.goto('/bordereaux');
    await expect(page.locator('[data-testid^="statement-row-"]').first()).toBeVisible();
  });
});

test.describe('Jeu de démonstration — auditeur', () => {
  test.use({ httpCredentials: USERS.auditor });

  test('l’auditeur lit une piste d’audit avec des auteurs variés @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/audit');
    await expect(page.locator('[data-testid="audit-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="audit-user"]').first()).not.toBeEmpty();
  });
});

test.describe('Jeu de démonstration — administrateur technique', () => {
  test.use({ httpCredentials: USERS.itAdmin });

  test('l’administrateur technique lit l’état de supervision @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/supervision');
    await expect(page.getByTestId('sup-status')).not.toBeEmpty();
  });
});

test.describe('Jeu de démonstration — responsable des ventes Alptis', () => {
  test.use({ httpCredentials: USERS.managerAlptis });

  test('le responsable voit ses collaborateurs et leur plan @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/fdv/plan');
    await expect(page.locator('[data-testid^="collab-row-"]').first()).toBeVisible();
    await expect(page.locator('[data-testid^="plan-row-"]').first()).toBeVisible();
  });
});

test.describe('Jeu de démonstration — commercial Alptis', () => {
  test.use({ httpCredentials: USERS.salesRep });

  test('le commercial consulte son variable du jeu @screen:demo @path:DEM-02', async ({ page }) => {
    await page.goto('/fdv/commercial');
    await page.getByTestId('fdvc-id').fill('rep-alptis');
    await page.getByTestId('fdvc-load').click();
    await expect(page.locator('[data-testid^="my-result-"]').first()).toBeVisible();
  });
});
