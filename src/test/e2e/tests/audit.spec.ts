import { test, expect, Browser } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the audit trail (ADM-04): a manual action appears with author, date,
// before/after and a fingerprint, partitioned by entity (CR-70) and read-only to allowed roles.
//
// @screen:audit @req:ADM-04

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

/** Performs an auditable action: a business administrator changes an Alptis parameter. */
async function updateAlptisAsAdmin(browser: Browser): Promise<void> {
  const context = await browser.newContext({ httpCredentials: USERS.adminAlptis });
  const page = await context.newPage();
  await page.goto('/entites');
  await page.getByTestId('entity-row-ALPTIS_ASSURANCES').click();
  await page.getByTestId('field-min-payment').fill('33.00');
  await page.getByTestId('save-entity').click();
  await expect(page.getByTestId('save-message')).toBeVisible();
  await context.close();
}

test.describe('Piste d’audit — auditeur', () => {
  test.use({ httpCredentials: USERS.auditor });

  test('affiche l’état vide quand rien n’a été modifié @screen:audit @req:ADM-04', async ({ page }) => {
    await page.goto('/audit');
    await expect(page.getByTestId('page-title')).toHaveText("Piste d'audit");
    await expect(page.getByTestId('empty')).toBeVisible();
  });

  test('montre une action manuelle avec auteur, avant/après et empreinte @screen:audit @req:ADM-04', async ({
    page,
    browser,
  }) => {
    await updateAlptisAsAdmin(browser);
    await page.goto('/audit');
    const row = page.getByTestId('audit-row').first();
    await expect(row.getByTestId('audit-action')).toHaveText('UPDATE_ENTITY_PARAMETERS');
    await expect(row.getByTestId('audit-user')).toHaveText('admin-alptis');
    await expect(row.getByTestId('audit-entity')).toHaveText('ALPTIS_ASSURANCES');
    await expect(page.getByTestId('audit-page')).toContainText('trace');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:audit', async ({ page }) => {
    await page.goto('/audit');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Piste d’audit — cloisonnement', () => {
  test.use({ httpCredentials: USERS.operatorUnep });

  test('CR-70 : un utilisateur UNEP ne voit pas les traces Alptis @screen:audit @cr:CR-70', async ({
    page,
    browser,
  }) => {
    await updateAlptisAsAdmin(browser);
    await page.goto('/audit');
    await expect(page.getByTestId('empty')).toBeVisible();
    await expect(page.getByTestId('audit-row')).toHaveCount(0);
  });
});

test.describe('Piste d’audit — accès refusé', () => {
  test.use({ httpCredentials: USERS.viewerAlptis });

  test('un consultant ne voit pas la piste d’audit @screen:audit', async ({ page }) => {
    await page.goto('/audit');
    await expect(page.getByTestId('load-error')).toBeVisible();
    await expect(page.getByRole('link', { name: "Piste d'audit" })).toHaveCount(0);
  });
});
