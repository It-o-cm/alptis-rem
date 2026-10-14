import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the imports screen (ING-01/04/05/08): upload with counters, journal,
// rejection resolution, expected flows, per-role access.
//
// @screen:imports @req:ING-01 @req:ING-05

const DISTRIBUTORS =
  'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n' +
  'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n' +
  'ALPTIS_ASSURANCES;SESAME;;Sans code;INDEPENDENT;ACTIVE;2026-01-01\n';

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Imports — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('téléverse un fichier, journalise les compteurs et traite un rejet @screen:imports @req:ING-01 @req:ING-05', async ({
    page,
  }) => {
    await page.goto('/imports');
    await expect(page.getByTestId('page-title')).toHaveText('Imports et rejets');

    await page.getByTestId('upload-flow').selectOption('distributors');
    // The source system is chosen from a list (ING-01), not typed as a free-text code.
    await page.getByTestId('upload-source').selectOption('SESAME');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'ALPTIS_ASSURANCES_distributors_20260930.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(DISTRIBUTORS, 'utf-8'),
    });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s), 1 rejetée(s)');

    // Journal shows the batch with the partially-loaded state at its French label (ADM-03); the flow
    // reads as its French label too.
    const batchRow = page.locator('[data-testid^="batch-row-"]').first();
    await expect(batchRow).toContainText('Distributeurs');
    await expect(batchRow.getByTestId('batch-state')).toHaveText('Intégré partiellement');

    // Open the batch, correct the rejected line.
    await batchRow.click();
    await expect(page.getByTestId('batch-detail')).toBeVisible();
    const rejectionRow = page.locator('[data-testid^="rejection-row-"]').first();
    await expect(rejectionRow).toContainText('MISSING_FIELD');
    await rejectionRow.getByRole('button', { name: 'Corriger et relancer' }).click();
    await expect(page.locator('[data-testid^="rejection-row-"]').first()).toContainText('CORRECTED');

    // Expected flows: distributors is now received.
    await expect(page.getByTestId('flow-distributors')).toContainText('Reçu');
    await expect(page.getByTestId('flow-events')).toContainText('Manquant');
  });

  test('refuse un fichier déjà intégré, sans doublon (CR-68, INT-05) @screen:imports @cr:CR-68 @path:INT-05', async ({ page }) => {
    await page.goto('/imports');
    const upload = async () => {
      await page.getByTestId('upload-flow').selectOption('distributors');
      await page.getByTestId('upload-file').setInputFiles({
        name: 'same.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(DISTRIBUTORS, 'utf-8'),
      });
      await page.getByTestId('upload-submit').click();
    };
    await upload();
    await expect(page.getByTestId('upload-message')).toBeVisible();
    // The first deposit created exactly one batch in the journal.
    await expect(page.locator('[data-testid^="batch-row-"]')).toHaveCount(1);
    await upload();
    await expect(page.getByTestId('upload-error')).toContainText('déjà intégré');
    // The second, refused deposit created no new batch: no duplicate import (INT-05).
    await expect(page.locator('[data-testid^="batch-row-"]')).toHaveCount(1);
  });

  test('un même nom de fichier de deux systèmes source est accepté et distingué au journal (INT-06) @screen:imports @path:INT-06', async ({ page }) => {
    await page.goto('/imports');
    const header = 'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n';
    const upload = async (source: string, partner: string) => {
      await page.getByTestId('upload-flow').selectOption('distributors');
      await page.getByTestId('upload-source').selectOption(source);
      await page.getByTestId('upload-file').setInputFiles({
        name: 'reseau.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(header + `ALPTIS_ASSURANCES;${source};${partner};Cabinet;INDEPENDENT;ACTIVE;2026-01-01\n`, 'utf-8'),
      });
      await page.getByTestId('upload-submit').click();
      await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s)');
    };
    await upload('SESAME', 'P13897');
    await upload('CLEVA', 'P99999');
    // Both deposits are kept, distinguished by their source system in the journal.
    const rows = page.locator('[data-testid^="batch-row-"]');
    await expect(rows).toHaveCount(2);
    await expect(rows.filter({ hasText: 'SESAME' })).toHaveCount(1);
    await expect(rows.filter({ hasText: 'CLEVA' })).toHaveCount(1);
  });

  test('un fichier réduit à son en-tête est reçu avec zéro ligne (INT-07) @screen:imports @path:INT-07', async ({ page }) => {
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('distributors');
    await page.getByTestId('upload-source').selectOption('SESAME');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'ALPTIS_ASSURANCES_distributors_entete.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n', 'utf-8'),
    });
    await page.getByTestId('upload-submit').click();
    // Zero lines integrated, rejected or held; the flow is nonetheless received.
    await expect(page.getByTestId('upload-message')).toContainText('0 intégrée(s), 0 rejetée(s)');
    const batchRow = page.locator('[data-testid^="batch-row-"]').first();
    await expect(batchRow).toContainText('Distributeurs');
    await expect(page.getByTestId('flow-distributors')).toContainText('Reçu');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:imports', async ({ page }) => {
    await page.goto('/imports');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Imports — lecture seule et accès', () => {
  test('l’auditeur voit le journal sans pouvoir déposer @screen:imports', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.auditor });
    const page = await context.newPage();
    await page.goto('/imports');
    await expect(page.getByTestId('page-title')).toHaveText('Imports et rejets');
    await expect(page.getByTestId('upload-form')).toHaveCount(0);
    await context.close();
  });

  test('un commercial n’a pas accès à l’écran @screen:imports', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/imports');
    await expect(page.getByTestId('load-error')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Imports' })).toHaveCount(0);
    await context.close();
  });
});
