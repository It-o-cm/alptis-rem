import { test, expect } from '@playwright/test';
import { resetDb, USERS } from '../support/helpers';

// Niveau 2 (TESTS-E2E.md §3) : les cas de recette de la section 13 rejoués par l'interface. Le test
// charge les données du cas par le point d'accès dev de scénario, lance le calcul réel depuis l'écran
// des exécutions, puis lit la ligne produite et son explication là où un utilisateur les lirait.
//
// @screen:calculations @screen:commissionSearch

test.describe('Rejeu des cas de recette par l’interface', () => {
  // UNEP carries no initial barème (it is seeded for Alptis only), so a case's own rule is the single
  // applicable one — as in the engine unit tests.
  test.use({ httpCredentials: USERS.operatorUnep });

  test.beforeEach(async ({ request }) => {
    await resetDb(request);
  });

  test('CR-01 : linéaire 15 % sur 100 € donne une ligne LINEAR de 15,00 € @screen:calculations @screen:commissionSearch @cr:CR-01', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-01&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    // Launch the real calculation of the seeded period from the executions screen (CAL-01).
    await page.goto('/calculs');
    await expect(page.getByTestId('calc-period')).toContainText('2026-01-01');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');

    // The computed LINEAR line of 15,00 € is read on the commission search (REP-02).
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const row = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' }).first();
    await expect(row).toBeVisible();
    await expect(row).toContainText('15,00');

    // Its explanation is read on the line detail (CAL-10): the computation that produced the amount.
    await row.click();
    await expect(page.getByTestId('line-detail')).toBeVisible();
    await expect(page.getByTestId('line-detail')).toContainText('Linéaire');
  });

  test('CR-02 : précompté année 1 donne ACQUISITION 300,00 € et SERVICING 100,00 € @screen:calculations @screen:commissionSearch @cr:CR-02', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-02&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');

    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const acquisition = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Acquisition' }).first();
    await expect(acquisition).toContainText('300,00');
    const servicing = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Gestion' }).first();
    await expect(servicing).toContainText('100,00');
  });

  test('CR-03 : précompté année 2 ne donne que SERVICING 100,00 € @screen:calculations @screen:commissionSearch @cr:CR-03', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-03&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');

    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const servicing = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Gestion' }).first();
    await expect(servicing).toContainText('100,00');
    // No acquisition line in the second year.
    await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Acquisition' })).toHaveCount(0);
  });
});
