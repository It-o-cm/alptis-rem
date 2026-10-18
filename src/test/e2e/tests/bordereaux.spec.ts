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

test('l’opérateur consulte un bordereau, l’exporte, le publie puis le rectifie @screen:statements @req:BRD-01 @req:BRD-02 @req:BRD-04 @req:BRD-07 @req:BRD-12 @req:BRD-06 @path:BRD-01 @path:BRD-05 @role-action:OPERATOR-publish-statement @role-action:OPERATOR-rectify-statement', async ({
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
  // BRD-05: the original version stays listed and consultable alongside the rectificative v2.
  await expect(page.locator('[data-testid^="statement-row-"]')).not.toHaveCount(1);
  const original = page.locator('[data-testid^="statement-row-"]').filter({ hasNotText: 'v2' }).first();
  await original.locator('[data-testid^="statement-open-"]').click();
  await expect(page.getByTestId('statement-detail')).toBeVisible();
  await operator.close();
});

test('BRD-02 : un bordereau à chaque niveau — code de production, distributeur, groupement @screen:statements @req:BRD-03 @path:BRD-02', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=BRD-02&entity=UNEP&month=2028-07-01')).ok()).toBeTruthy();

  const ctx = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await ctx.newPage();
  // Prepare the payment so the three bordereaux (one per level) are produced.
  await page.goto('/');
  await page.getByTestId('open-UNEP-2028-07-01').click();
  await page.getByTestId('advance-button').click();
  await expect(page.getByTestId('detail-state')).toContainText('Paiement préparé');

  await page.goto('/bordereaux');
  await page.getByTestId('brd-entity').selectOption('UNEP');
  await page.getByTestId('brd-month').selectOption('2028-07-01');
  await page.getByTestId('brd-refresh').click();
  await expect(page.locator('[data-testid^="statement-open-"]')).toHaveCount(3);

  // Open each bordereau and collect its level, beneficiary and net. Wait for the detail to show the
  // opened statement's number before reading, so the asynchronous content reload is not read stale.
  const levels: string[] = [];
  const beneficiaries: string[] = [];
  const nets: string[] = [];
  for (let i = 0; i < 3; i += 1) {
    const row = page.locator('[data-testid^="statement-row-"]').nth(i);
    const number = (((await row.textContent()) ?? '').match(/ST-[A-Z_]+-\d{4}-\d{2}-\d{2}-\d+/) ?? [''])[0];
    await row.locator('[data-testid^="statement-open-"]').click();
    await expect(page.getByTestId('statement-detail')).toContainText(number);
    levels.push(((await page.getByTestId('statement-level').textContent()) ?? '').trim());
    beneficiaries.push(((await page.getByTestId('statement-beneficiary').textContent()) ?? '').trim());
    nets.push(((await page.getByTestId('statement-net').textContent()) ?? '').trim());
  }
  // One bordereau per level, each naming the right beneficiary.
  expect([...levels].sort()).toEqual(['Code de production', 'Distributeur', 'Groupement']);
  expect(beneficiaries).toEqual(
    expect.arrayContaining(['Code de production PC-BRD02', 'Distributeur Distributeur BRD02', 'Groupement G-BRD02']),
  );
  // Each bordereau carries its own beneficiary's lines (net 100,00 / 80,00 / 60,00).
  expect(nets.some((n) => n.includes('100,00'))).toBeTruthy();
  expect(nets.some((n) => n.includes('80,00'))).toBeTruthy();
  expect(nets.some((n) => n.includes('60,00'))).toBeTruthy();
  await ctx.close();
});

test('BRD-04 : la publication date le bordereau du lendemain de l’exécution et dépose ses métadonnées en GED @screen:statements @req:BRD-10 @path:BRD-04 @role-action:OPERATOR-publish-statement', async ({
  browser,
  request,
}) => {
  expect((await request.post('/api/test/cr-scenario?cr=BRD-04&entity=UNEP&month=2029-03-01')).ok()).toBeTruthy();

  const op = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await op.newPage();
  await page.goto('/bordereaux');
  await page.getByTestId('brd-entity').selectOption('UNEP');
  await page.getByTestId('brd-month').selectOption('2029-03-01');
  await page.getByTestId('brd-refresh').click();
  await page.locator('[data-testid^="statement-publish-"]').first().click();
  await expect(page.locator('[data-testid^="statement-status-"]').first()).toContainText('Publié');

  // Published the day after the cycle execution (2029-03-15) → 16/03/2029; GED metadata deposited.
  await expect(page.locator('[data-testid^="statement-publication-"]').first()).toHaveText('16/03/2029');
  await expect(page.locator('[data-testid^="statement-ged-"]').first()).toContainText('GED-');
  await op.close();
});

test('l’opérateur consulte le relevé informatif d’un membre @screen:statements @req:BRD-11 @path:BRD-06', async ({
  browser,
}) => {
  const operator = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
  const page = await operator.newPage();
  await page.goto('/bordereaux');
  // ADM-03: the entity, month and member are chosen from lists shown by their French labels, never
  // typed as codes. The entity reads as a name, the month as a French label, the member by its code.
  await expect(page.getByTestId('brd-entity').locator('option', { hasText: 'Alptis Assurances' })).toHaveCount(1);
  await expect(page.getByTestId('brd-month').locator('option', { hasText: 'septembre 2026' })).toHaveCount(1);
  await expect(page.getByTestId('brd-member').locator('option', { hasText: 'PC-CREDIT — Cabinet Martin' })).toHaveCount(1);
  await page.getByTestId('brd-member').selectOption('PC-CREDIT');
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
