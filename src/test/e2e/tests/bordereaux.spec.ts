import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { readFileSync } from 'fs';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the bordereaux screen: the statements of a period, the rubric preview with a
// carried balance, the spreadsheet export, publication and the rectificative version (BRD-01,
// BRD-02, BRD-06, BRD-07, BRD-12).
//
// @screen:statements @req:BRD-01 @req:BRD-02 @req:BRD-04 @req:BRD-06 @req:BRD-07 @req:BRD-11 @req:BRD-12

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’opérateur consulte un bordereau, l’exporte, le publie puis le rectifie @screen:statements @req:BRD-01 @req:BRD-02 @req:BRD-04 @req:BRD-07 @req:BRD-12 @req:BRD-06 @role-action:OPERATOR-publish-statement @role-action:OPERATOR-rectify-statement', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/bordereaux');

  // A statement is listed for the period.
  const firstRow = page.locator('[data-testid^="statement-row-"]').first();
  await expect(firstRow).toBeVisible();

  // Its preview shows the rubric lines, the carried balance / net footer (section 9.5) and the
  // per-insurer/product declension column (BRD-04).
  await page.locator('[data-testid^="statement-open-"]').first().click();
  await expect(page.getByTestId('statement-detail')).toBeVisible();
  await expect(page.locator('[data-testid^="statement-line-section-"]').first()).not.toBeEmpty();
  await expect(page.getByTestId('statement-net')).not.toBeEmpty();
  await expect(page.getByTestId('statement-detail')).toContainText('Déclinaison');

  // Its spreadsheet export is downloadable and carries the net footer.
  await page.locator('[data-testid^="statement-csv-"]').first().click();
  await expect(page.getByTestId('spreadsheet-content')).toContainText('Net payé');

  // The real PDF and spreadsheet downloads carry their format's magic signature (BRD-01).
  const [pdfDownload] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('[data-testid^="statement-pdf-"]').first().click(),
  ]);
  expect(pdfDownload.suggestedFilename()).toMatch(/\.pdf$/);
  const pdfPath = await pdfDownload.path();
  expect(readFileSync(pdfPath).subarray(0, 4).toString('latin1')).toBe('%PDF');

  const [xlsxDownload] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('[data-testid^="statement-xlsx-"]').first().click(),
  ]);
  expect(xlsxDownload.suggestedFilename()).toMatch(/\.xlsx$/);
  const xlsxPath = await xlsxDownload.path();
  expect(readFileSync(xlsxPath).subarray(0, 2).toString('latin1')).toBe('PK');

  // Publication freezes the statement, then a rectificative version is created.
  await page.locator('[data-testid^="statement-publish-"]').first().click();
  await expect(page.locator('[data-testid^="statement-status-"]').first()).toContainText('Publié');
  await page.locator('[data-testid^="statement-rectify-"]').first().click();
  await expect(page.getByTestId('brd-message')).toContainText('rectificative');
  await expect(page.locator('[data-testid^="statement-row-"]', { hasText: 'v2' })).toHaveCount(1);
  await operator.close();
});

test('l’opérateur consulte le relevé informatif d’un membre @screen:statements @req:BRD-11', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/bordereaux');
  await page.getByTestId('brd-member').fill('PC-CREDIT');
  await page.getByTestId('brd-member-btn').click();
  await expect(page.getByTestId('statement-detail')).toBeVisible();
  await expect(page.getByTestId('statement-detail')).toContainText('PC-CREDIT');
  await expect(page.locator('[data-testid^="statement-line-"]').first()).toBeVisible();
  await operator.close();
});

test.describe('Bordereaux — accessibilité', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('aucune violation d’accessibilité grave ou critique @screen:statements', async ({ page }) => {
    await page.goto('/bordereaux');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
