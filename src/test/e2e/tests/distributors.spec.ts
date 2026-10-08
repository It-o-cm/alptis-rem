import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for distributor search (REF-02, REP-03) and the hierarchy / production-code
// detail (REF-03), partitioned by entity.
//
// @screen:distributor-search @screen:production-hierarchy @req:REF-02 @req:REF-03

const DISTRIBUTORS =
  'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n' +
  'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n';

const PRODUCTION_CODES =
  'entity_code;production_code;partner_code;label;status\n' +
  'ALPTIS_ASSURANCES;61723;P13897;Agence Lyon;ACTIVE\n';

const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

async function seed(page: Page) {
  for (const [flow, content] of [
    ['distributors', DISTRIBUTORS],
    ['production_codes', PRODUCTION_CODES],
  ]) {
    const response = await page.request.post('/api/ui/v1/imports', {
      headers: { Authorization: OPERATOR_AUTH },
      data: { entityCode: 'ALPTIS_ASSURANCES', flowCode: flow, sourceSystem: 'SESAME', fileName: `${flow}.csv`, content },
    });
    expect(response.ok()).toBeTruthy();
  }
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Distributeurs — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('recherche un distributeur et ouvre sa fiche hiérarchie @screen:distributor-search @screen:production-hierarchy @req:REF-02 @req:REF-03', async ({
    page,
  }) => {
    await seed(page);
    await page.goto('/distributeurs');
    await page.getByTestId('search-term').fill('P13897');
    await page.getByTestId('search-button').click();

    const row = page.getByTestId('distributor-row-P13897');
    await expect(row).toContainText('Cabinet Martin');

    await row.click();
    await expect(page.getByTestId('distributor-detail')).toBeVisible();
    await expect(page.getByTestId('status-row')).toContainText('ALPTIS_ASSURANCES');
    await expect(page.getByTestId('status-row')).toContainText('ACTIVE');
    await expect(page.getByTestId('code-row-61723')).toContainText('Agence Lyon');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:distributor-search', async ({ page }) => {
    await page.goto('/distributeurs');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Distributeurs — cloisonnement et accès', () => {
  test('CR-70 : un utilisateur UNEP ne voit pas un distributeur Alptis @screen:distributor-search @cr:CR-70', async ({
    browser,
  }) => {
    const alptis = await browser.newContext({ httpCredentials: USERS.operatorAlptis });
    const alptisPage = await alptis.newPage();
    await seed(alptisPage);
    await alptis.close();

    const unep = await browser.newContext({ httpCredentials: USERS.operatorUnep });
    const page = await unep.newPage();
    await page.goto('/distributeurs');
    await page.getByTestId('search-term').fill('P13897');
    await page.getByTestId('search-button').click();
    await expect(page.getByTestId('distributor-row-P13897')).toHaveCount(0);
    await unep.close();
  });

  test('un commercial n’a pas accès à la recherche @screen:distributor-search', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/distributeurs');
    await expect(page.getByRole('link', { name: 'Recherche distributeurs' })).toHaveCount(0);
    await context.close();
  });
});
