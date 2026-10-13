import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen test for the discount-conventions screen (PAR-09): a convention is recorded for a
// distributor and listed.
//
// @screen:advance @req:PAR-09

const OPERATOR_AUTH =
  'Basic ' + Buffer.from(`${USERS.operatorAlptis.username}:${USERS.operatorAlptis.password}`).toString('base64');

async function importDistributor(page: Page) {
  const response = await page.request.post('/api/ui/v1/imports', {
    headers: { Authorization: OPERATOR_AUTH },
    data: {
      entityCode: 'ALPTIS_ASSURANCES', flowCode: 'distributors', sourceSystem: 'SESAME', fileName: 'd.csv',
      content: 'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n'
        + 'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n',
    },
  });
  expect(response.ok()).toBeTruthy();
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe("Conventions d'escompte", () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('enregistre et liste une convention d’escompte @screen:advance @req:PAR-09', async ({ page }) => {
    await importDistributor(page);
    await page.goto('/escompte');
    await expect(page.getByTestId('page-title')).toHaveText("Conventions d'escompte");

    await page.getByTestId('adv-partner').fill('P13897');
    await page.getByTestId('adv-from').fill('2026-01-01');
    await page.getByTestId('adv-to').fill('2026-12-31');
    await page.getByTestId('adv-create').click();
    await expect(page.getByTestId('advance-message')).toContainText('escompte');
    await expect(page.getByTestId('advance-row-0')).toContainText('P13897');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:advance', async ({ page }) => {
    await page.goto('/escompte');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});
