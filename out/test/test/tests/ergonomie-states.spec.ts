import { test, expect } from '@playwright/test';
import { loadDemo, USERS } from '../support/helpers';

// Loading states (ERGONOMIE §3/§5): while a list is loading the table says so,
// and "Aucun résultat" never shows before the server has answered.
//
// @screen:states

test.use({ httpCredentials: USERS.adminAlptis });

test.beforeEach(async ({ request }) => {
  await loadDemo(request);
});

test.describe('États de chargement', () => {
  test('la liste affiche le chargement et jamais « Aucun résultat » avant la réponse @screen:states', async ({
    page,
  }) => {
    // Slow the distributor search so the loading state is observable.
    await page.route('**/api/ui/v1/distributors**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
    });

    await page.goto('/distributeurs');

    // During the call: the loading row is shown, the empty message is not.
    await expect(page.getByTestId('table-loading')).toBeVisible();
    expect(await page.getByTestId('empty').count()).toBe(0);

    // After the answer: loading is gone and the seeded distributor appears.
    await expect(page.getByTestId('table-loading')).toBeHidden();
    await expect(page.getByTestId('distributor-row-P13897')).toBeVisible();
  });
});
