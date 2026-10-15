import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the directory-group to role correspondence (section 3) and the ADM-01
// journey: a change takes effect at the next login.
//
// @screen:role-mappings

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Groupes et rôles — administrateur technique', () => {
  test.use({ httpCredentials: USERS.itAdmin });

  test('liste les correspondances seedées @screen:role-mappings', async ({ page }) => {
    await page.goto('/correspondances-roles');
    await expect(page.getByTestId('page-title')).toHaveText('Groupes et rôles');
    await expect(page.getByTestId('mapping-row-grp-op-alptis-OPERATOR')).toBeVisible();
    // ADM-03: the role and entity are chosen from lists shown by their French labels, and the table
    // restitutes them by label, never by their raw code.
    await expect(page.getByTestId('field-role').locator('option', { hasText: 'gestionnaire' })).toHaveCount(1);
    await expect(page.getByTestId('field-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
    await expect(page.getByTestId('mapping-row-grp-op-alptis-OPERATOR')).toContainText('gestionnaire');
    await expect(page.getByTestId('mapping-row-grp-op-alptis-OPERATOR')).toContainText('Alptis Assurances');
  });

  test('ajoute puis supprime une correspondance @screen:role-mappings', async ({ page }) => {
    await page.goto('/correspondances-roles');
    await page.getByTestId('field-group').fill('grp-new');
    await page.getByTestId('field-role').selectOption('VIEWER');
    await page.getByTestId('field-entity').selectOption('ALPTIS_ASSURANCES');
    await page.getByTestId('create-mapping').click();
    await expect(page.getByTestId('mapping-message')).toHaveText('Correspondance ajoutée.');
    await expect(page.getByTestId('mapping-row-grp-new-VIEWER')).toBeVisible();

    await page.getByTestId('delete-grp-new-VIEWER').click();
    await expect(page.getByTestId('mapping-message')).toHaveText('Correspondance supprimée.');
    await expect(page.getByTestId('mapping-row-grp-new-VIEWER')).toHaveCount(0);
  });

  test('refuse une correspondance en double, avec le message du serveur @screen:role-mappings', async ({ page }) => {
    await page.goto('/correspondances-roles');
    await page.getByTestId('field-group').fill('grp-op-alptis');
    await page.getByTestId('field-role').selectOption('OPERATOR');
    await page.getByTestId('field-entity').selectOption('ALPTIS_ASSURANCES');
    await page.getByTestId('create-mapping').click();
    await expect(page.getByTestId('mapping-error')).toContainText('existe déjà');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:role-mappings', async ({ page }) => {
    await page.goto('/correspondances-roles');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Groupes et rôles — accès refusé', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('un opérateur ne voit pas l’écran et se voit refuser l’API @screen:role-mappings', async ({ page }) => {
    await page.goto('/correspondances-roles');
    await expect(page.getByTestId('load-error')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Groupes et rôles' })).toHaveCount(0);
  });
});

test.describe('Parcours ADM-01', () => {
  test('un changement de correspondance modifie les droits à la connexion suivante @screen:role-mappings @path:ADM-01', async ({
    browser,
  }) => {
    // Before: the viewer cannot see the entities screen (VIEWER is not allowed).
    const before = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
    const beforePage = await before.newPage();
    await beforePage.goto('/');
    await expect(beforePage.getByRole('link', { name: 'Entités et paramètres' })).toHaveCount(0);
    await before.close();

    // The technical administrator grants OPERATOR to the viewer's directory group.
    const admin = await browser.newContext({ httpCredentials: USERS.itAdmin });
    const adminPage = await admin.newPage();
    await adminPage.goto('/correspondances-roles');
    await adminPage.getByTestId('field-group').fill('grp-vw-alptis');
    await adminPage.getByTestId('field-role').selectOption('OPERATOR');
    await adminPage.getByTestId('field-entity').selectOption('ALPTIS_ASSURANCES');
    await adminPage.getByTestId('create-mapping').click();
    await expect(adminPage.getByTestId('mapping-message')).toHaveText('Correspondance ajoutée.');
    await admin.close();

    // Next login: the viewer now holds OPERATOR and sees the entities screen.
    const after = await browser.newContext({ httpCredentials: USERS.viewerAlptis });
    const afterPage = await after.newPage();
    await afterPage.goto('/');
    // The "Administration" rubric is not a work rubric for this user, so it is
    // collapsed by default (ERGONOMIE §2.2); expand it to reveal its entries.
    await afterPage.getByTestId('nav-toggle-Administration').click();
    await expect(afterPage.getByRole('link', { name: 'Entités et paramètres' })).toBeVisible();
    await after.close();
  });
});
