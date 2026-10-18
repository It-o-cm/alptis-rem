import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, DEMO_MONTH, USERS } from '../support/helpers';

// Per-screen tests for the period advancement and detail (WKF-01, WKF-04, section 7.1).
//
// @screen:periods @req:WKF-01 @req:WKF-04

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test.describe('Périodes — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('liste les périodes ouvertes, ouvre le détail et avance la période @screen:periods @req:WKF-04 @req:WKF-01 @role-action:OPERATOR-advance-period', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('period-row-ALPTIS_ASSURANCES-' + DEMO_MONTH)).toBeVisible();

    await page.getByTestId('open-ALPTIS_ASSURANCES-' + DEMO_MONTH).click();
    await expect(page.getByTestId('period-detail')).toBeVisible();
    await expect(page.getByTestId('detail-state')).toContainText('Calculée');

    // The target calendar (section 7.3): the calcul milestone reached, a later one still pending.
    await expect(page.getByText('Gel des données, calcul prévisionnel et réel')).toBeVisible();
    await expect(page.getByTestId('milestone-reached-true').first()).toBeVisible();
    await expect(page.getByTestId('milestone-reached-false').first()).toBeVisible();
    // The period's payment lot, translated to its French label.
    await expect(page.getByTestId('lot-row-CYCLE')).toContainText('Lot de cycle');
    // The reste à traiter.
    await expect(page.getByTestId('detail-remaining')).toContainText('Reste à traiter');

    await page.getByTestId('advance-button').click();
    await expect(page.getByTestId('detail-state')).toContainText('Contrôlée');
    await expect(page.getByTestId('periods-message')).toContainText('Contrôlée');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:periods', async ({ page }) => {
    await page.goto('/');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});

test.describe('Périodes — lecteur', () => {
  test.use({ httpCredentials: USERS.viewerAlptis });

  test('le lecteur voit la liste mais n’a pas de bouton d’avancement @screen:periods @role-action:VIEWER-read-periods', async ({
    page,
  }) => {
    await page.goto('/');
    await page.getByTestId('open-ALPTIS_ASSURANCES-' + DEMO_MONTH).click();
    await expect(page.getByTestId('period-detail')).toBeVisible();
    await expect(page.getByTestId('advance-button')).toHaveCount(0);
  });
});

test('FIN-06 : une période clôturée est scellée — plus rien ne change, toute action est refusée @screen:periods @req:FIN-06 @path:FIN-06 @role-action:FINANCE_CONTROLLER-close-period', async ({
  browser,
  request,
}) => {
  const month = '2029-08-01';
  expect(
    (await request.post(`/api/test/cr-scenario?cr=FIN-06&entity=ALPTIS_ASSURANCES&month=${month}`)).ok(),
  ).toBeTruthy();

  // The finance controller closes the paid period.
  const finCtx = await browser.newContext({ httpCredentials: USERS.financeAlptis });
  const fin = await finCtx.newPage();
  await fin.goto('/');
  await fin.getByTestId(`open-ALPTIS_ASSURANCES-${month}`).click();
  await expect(fin.getByTestId('detail-state')).toContainText('Payée');
  await fin.getByTestId('advance-button').click();
  await expect(fin.getByTestId('detail-state')).toContainText('Clôturée');
  // Nothing more to do: the cycle is over, no further transition is offered.
  await expect(fin.getByTestId('advance-button')).toHaveCount(0);

  // The closed period has left the list of open periods.
  await fin.goto('/');
  await expect(fin.getByTestId(`period-row-ALPTIS_ASSURANCES-${month}`)).toHaveCount(0);
  await finCtx.close();

  // Any action on it is refused by the server: a real recalculation is rejected.
  const auth = 'Basic ' + Buffer.from(`${USERS.adminAlptis.username}:${USERS.adminAlptis.password}`).toString('base64');
  const resp = await request.post(`/api/ui/v1/calculations/ALPTIS_ASSURANCES/${month}/run`, {
    headers: { Authorization: auth },
  });
  expect(resp.status()).toBe(400);
  expect((await resp.json()).detail).toContain('clôturée');
});
