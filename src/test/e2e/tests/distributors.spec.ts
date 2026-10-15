import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadDemo, resetDb, USERS } from '../support/helpers';

// Per-screen tests for distributor search (REF-02, REP-03) and the hierarchy / production-code
// detail (REF-03), partitioned by entity. The consolidated fiche (REF-10, CPT-01, CPT-06) unites
// identity, hierarchy, statuses, production codes, agreements, enrollments, quality indicators and
// the per-entity account balances with their history.
//
// @screen:distributor-search @screen:production-hierarchy @req:REF-02 @req:REF-03 @req:REF-10 @req:CPT-01 @req:CPT-06

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
    // ADM-03: the distributor status is shown by its French label, not the raw code.
    await expect(page.getByTestId('status-row')).toContainText('Actif');
    await expect(page.getByTestId('code-row-61723')).toContainText('Agence Lyon');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:distributor-search', async ({ page }) => {
    await page.goto('/distributeurs');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });

  test('la fiche consolidée réunit accords, inscriptions, indicateurs et comptes @screen:distributor-search @req:REF-10 @req:CPT-01 @req:CPT-06', async ({
    page,
  }) => {
    await loadDemo(page.request);
    await page.goto('/distributeurs');
    await page.getByTestId('search-term').fill('P20001');
    await page.getByTestId('search-button').click();

    await page.getByTestId('distributor-row-P20001').click();
    const fiche = page.getByTestId('distributor-detail');
    await expect(fiche).toBeVisible();
    // Hierarchy: the fiche names the grouping it heads.
    await expect(fiche).toContainText('GRP-NORD');
    // Agreements: at least the signed RCD amendment.
    await expect(page.getByTestId('agreement-row-ACC-RCD-ALPTIS_ASSURANCES')).toBeVisible();
    // Enrollments: the reserved RCD rule, active (ADM-03: French label, not the raw code).
    const enrollment = page.locator('[data-testid^="enrollment-row-"]').first();
    await expect(enrollment).toBeVisible();
    await expect(enrollment).toContainText('Active');
    // Quality indicators and accounts are present in the same fiche.
    await expect(page.locator('[data-testid="indicator-row"]').first()).toBeVisible();
    await expect(page.getByTestId('account-ALPTIS_ASSURANCES')).toBeVisible();
  });

  // Parcours INT-16 (section 4.1): a CRM update of a distributor refreshes its fiche while the
  // application's own attributes (reserved-rule agreements, enrollments, quality indicators) are kept.
  test('une mise à jour CRM rafraîchit la fiche en conservant les attributs propres à l’application @screen:distributor-search @req:REF-10 @path:INT-16', async ({
    page,
  }) => {
    await loadDemo(page.request);
    const open = async () => {
      await page.goto('/distributeurs');
      await page.getByTestId('search-term').fill('P20001');
      await page.getByTestId('search-button').click();
      await page.getByTestId('distributor-row-P20001').click();
      return page.getByTestId('distributor-detail');
    };

    // Before: the fiche names the distributor and carries the application's own data.
    const before = await open();
    await expect(before).toContainText('Cabinet Dubois');
    await expect(before).not.toContainText('Rénové');
    await expect(page.locator('[data-testid^="enrollment-row-"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="indicator-row"]').first()).toBeVisible();
    await expect(page.getByTestId('agreement-row-ACC-RCD-ALPTIS_ASSURANCES')).toBeVisible();

    // The CRM re-imports the distributor with a new legal name, same hierarchy and status.
    const auth = 'Basic ' + Buffer.from(
      `${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');
    const crm =
      'partner_code;legal_name;distributor_type;status;valid_from;group_code\n' +
      'P20001;Cabinet Dubois Rénové;INDEPENDENT;ACTIVE;2025-01-01;GRP-NORD\n';
    const res = await page.request.post(
      '/api/v1/imports/distributors?entity=ALPTIS_ASSURANCES&sourceSystem=SESAME&fileName=crm-p20001.csv',
      { headers: { 'Content-Type': 'text/plain', Authorization: auth }, data: crm });
    expect(res.ok(), `${res.status()} ${await res.text()}`).toBeTruthy();

    // After: the fiche is refreshed (new legal name, same grouping) and the application attributes are kept.
    const after = await open();
    await expect(after).toContainText('Cabinet Dubois Rénové');
    await expect(after).toContainText('GRP-NORD');
    await expect(page.locator('[data-testid^="enrollment-row-"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="indicator-row"]').first()).toBeVisible();
    await expect(page.getByTestId('agreement-row-ACC-RCD-ALPTIS_ASSURANCES')).toBeVisible();
  });

  // Parcours INT-21 (section 4.1): a change of grouping is historised on the distributor and the
  // grouping fiche is up to date (the member moves from one grouping to the other).
  test('un changement de groupement est historisé et la fiche du groupement est à jour @screen:production-hierarchy @screen:groupings @req:REF-03 @path:INT-21', async ({
    page,
  }) => {
    const post = (flow: string, content: string) =>
      page.request.post('/api/ui/v1/imports', {
        headers: { Authorization: OPERATOR_AUTH },
        data: { entityCode: 'ALPTIS_ASSURANCES', flowCode: flow, sourceSystem: 'SESAME', fileName: `${flow}-${content.length}.csv`, content },
      });
    await post('distributor_groups', 'group_code;label;head_partner_code\nGA;Groupe A;\nGB;Groupe B;\n');
    const dh =
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from;group_code\n';
    await post('distributors', dh + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01;GA\n');
    // A later import moves the distributor to another grouping — this is historised (REF-03).
    await post('distributors', dh + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01;GB\n');

    // The distributor fiche historises the change, from GA to GB, with its date.
    await page.goto('/distributeurs');
    await page.getByTestId('search-term').fill('P13897');
    await page.getByTestId('search-button').click();
    await page.getByTestId('distributor-row-P13897').click();
    const history = page.getByTestId('history-row').first();
    await expect(history).toBeVisible();
    await expect(history).toContainText('Changement de groupement');
    await expect(history).toContainText('GA');
    await expect(history).toContainText('GB');
    await expect(history).toContainText('2026');

    // The grouping fiche is up to date: P13897 now belongs to GB, no longer to GA.
    await page.goto('/groupements');
    await page.getByTestId('open-group-GB').click();
    await expect(page.getByTestId('group-detail')).toBeVisible();
    await expect(page.getByTestId('member-row-P13897')).toBeVisible();
    await page.getByTestId('open-group-GA').click();
    await expect(page.getByTestId('group-detail')).toBeVisible();
    await expect(page.getByTestId('member-row-P13897')).toHaveCount(0);
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
