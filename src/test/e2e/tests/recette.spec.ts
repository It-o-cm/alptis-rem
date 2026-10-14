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

  // The reprise (clawback) cases are read on the commission search: the aggregate CLAWBACK line and its
  // amount, or its absence. The per-line arithmetic is pinned by CrScenarioResourceTest on the server.
  const reprise = [
    { cr: 'CR-04', titre: 'reprise en 1re année', amount: '-180,00' },
    { cr: 'CR-05', titre: 'reprise en 2e année', amount: '-150,00' },
    { cr: 'CR-07', titre: 'sans suite', amount: '-80,00' },
  ];
  for (const c of reprise) {
    test(`${c.cr} : ${c.titre} produit une ligne Reprise de ${c.amount} € @screen:calculations @screen:commissionSearch @cr:${c.cr}`, async ({
      page,
      request,
    }) => {
      const seeded = await request.post(`/api/test/cr-scenario?cr=${c.cr}&entity=UNEP&month=2026-01-01`);
      expect(seeded.ok()).toBeTruthy();
      await page.goto('/calculs');
      await page.getByTestId('calc-run').click();
      await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
      await page.goto('/recherche-commissions');
      await page.getByTestId('cs-month').selectOption('2026-01-01');
      await page.getByTestId('cs-search').click();
      const row = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Reprise' }).first();
      await expect(row).toContainText(c.amount);
    });
  }

  const noReprise = [
    { cr: 'CR-06', titre: 'pas de reprise au-delà de deux ans' },
    { cr: 'CR-08', titre: 'décès sans reprise' },
  ];
  for (const c of noReprise) {
    test(`${c.cr} : ${c.titre} ne produit aucune ligne Reprise @screen:calculations @screen:commissionSearch @cr:${c.cr}`, async ({
      page,
      request,
    }) => {
      const seeded = await request.post(`/api/test/cr-scenario?cr=${c.cr}&entity=UNEP&month=2026-01-01`);
      expect(seeded.ok()).toBeTruthy();
      await page.goto('/calculs');
      await page.getByTestId('calc-run').click();
      await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
      await page.goto('/recherche-commissions');
      await page.getByTestId('cs-month').selectOption('2026-01-01');
      await page.getByTestId('cs-search').click();
      await expect(page.locator('[data-testid^="cs-row-"]').first()).toBeVisible();
      await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Reprise' })).toHaveCount(0);
    });
  }

  test('CR-09 : l’annulation d’un encaissement ajoute la ligne inverse @screen:calculations @screen:commissionSearch @cr:CR-09', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-09&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const linear = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' });
    await expect(linear).toHaveCount(2);
    await expect(linear.filter({ hasText: '-15,00' })).toHaveCount(1);
  });

  test('CR-10 : un changement de taux laisse l’encaissement antérieur à 15 % et le suivant à 12 % @screen:calculations @screen:commissionSearch @cr:CR-10', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-10&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const linear = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' });
    await expect(linear).toHaveCount(2);
    await expect(linear.filter({ hasText: '15,00' })).toHaveCount(1);
    await expect(linear.filter({ hasText: '12,00' })).toHaveCount(1);
  });

  test('CR-11 : un taux hors grille met l’événement en attente et ne produit aucune ligne @screen:calculations @screen:commissionSearch @cr:CR-11', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-11&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    // The event is held with the "rate out of grid" reason, shown among the failed units.
    await expect(page.getByTestId('failed-row-CR11-E1')).toContainText('Taux hors grille');
    // No commission line is produced.
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    await expect(page.locator('[data-testid^="cs-row-"]')).toHaveCount(0);
  });

  test('CR-12 : les collèges paient 70,00 € aux cadres et 50,00 € aux non-cadres @screen:calculations @screen:commissionSearch @cr:CR-12', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-12&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const linear = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' });
    await expect(linear.filter({ hasText: '70,00' })).toHaveCount(1);
    await expect(linear.filter({ hasText: '50,00' })).toHaveCount(1);
  });

  test('CR-20 : le versement d’escompte paie deux lignes Escompte de 300,00 € et 100,00 € @screen:calculations @screen:commissionSearch @cr:CR-20', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-20&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const advance = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Escompte' });
    await expect(advance).toHaveCount(2);
    await expect(advance.filter({ hasText: '300,00' })).toHaveCount(1);
    await expect(advance.filter({ hasText: '100,00' })).toHaveCount(1);
  });

  test('CR-21 : l’apurement laisse la position active avec 480,00 € escompté et 80,00 € apuré @screen:calculations @screen:advance @cr:CR-21', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-21&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    // The discount position is read on the convention screen (the offset lines are never paid).
    await page.goto('/escompte');
    await page.getByTestId('advance-row-0').click();
    const position = page.getByTestId('adv-position-row');
    await expect(position).toContainText('Active');
    await expect(position).toContainText('480,00');
    await expect(position).toContainText('80,00');
  });

  test('CR-22 : la résiliation annule l’escompte, régularise −200,00 € et reprend −150,00 € @screen:calculations @screen:commissionSearch @screen:advance @cr:CR-22', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-22&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const rows = page.locator('[data-testid^="cs-row-"]');
    await expect(rows.filter({ hasText: 'Régularisation d’escompte' }).filter({ hasText: '-200,00' })).toHaveCount(1);
    await expect(rows.filter({ hasText: 'Reprise' }).filter({ hasText: '-150,00' })).toHaveCount(1);
    await page.goto('/escompte');
    await page.getByTestId('advance-row-0').click();
    await expect(page.getByTestId('adv-position-row')).toContainText('Annulée');
  });

  test('CR-23 : le solde par apurement passe la position à Apurée @screen:calculations @screen:advance @cr:CR-23', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-23&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/escompte');
    await page.getByTestId('advance-row-0').click();
    await expect(page.getByTestId('adv-position-row')).toContainText('Apurée');
  });

  test('CR-24 : le complément d’escompte solde la position avec une régularisation de +30,00 € @screen:calculations @screen:commissionSearch @screen:advance @cr:CR-24', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-24&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Régularisation d’escompte' })
      .filter({ hasText: '30,00' })).toHaveCount(1);
    await page.goto('/escompte');
    await page.getByTestId('advance-row-0').click();
    await expect(page.getByTestId('adv-position-row')).toContainText('Apurée');
  });

  // The no-advance escompte cases: the premiums are paid normally (acquisition + servicing), no Escompte line.
  const noAdvance = [
    { cr: 'CR-25', titre: 'hors convention' },
    { cr: 'CR-27', titre: 'plafond atteint' },
  ];
  for (const c of noAdvance) {
    test(`${c.cr} : ${c.titre} ne verse aucun escompte et paie les commissions @screen:calculations @screen:commissionSearch @cr:${c.cr}`, async ({
      page,
      request,
    }) => {
      const seeded = await request.post(`/api/test/cr-scenario?cr=${c.cr}&entity=UNEP&month=2026-01-01`);
      expect(seeded.ok()).toBeTruthy();
      await page.goto('/calculs');
      await page.getByTestId('calc-run').click();
      await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
      await page.goto('/recherche-commissions');
      await page.getByTestId('cs-month').selectOption('2026-01-01');
      await page.getByTestId('cs-search').click();
      const rows = page.locator('[data-testid^="cs-row-"]');
      await expect(rows).toHaveCount(2);
      await expect(rows.filter({ hasText: 'Escompte' })).toHaveCount(0);
    });
  }

  test('CR-26 : un impayé de rang 2 annule l’escompte et régularise −400,00 € @screen:calculations @screen:commissionSearch @screen:advance @cr:CR-26', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-26&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Régularisation d’escompte' })
      .filter({ hasText: '-400,00' })).toHaveCount(1);
    await page.goto('/escompte');
    await page.getByTestId('advance-row-0').click();
    await expect(page.getByTestId('adv-position-row')).toContainText('Annulée');
  });

  test('CR-28 : un déficit au 360e jour solde la position et reprend −30,00 € d’escompte indu @screen:calculations @screen:commissionSearch @screen:advance @cr:CR-28', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-28&entity=UNEP&month=2026-12-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-12-01');
    await page.getByTestId('cs-search').click();
    await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Régularisation d’escompte' })
      .filter({ hasText: '-30,00' })).toHaveCount(1);
    await page.goto('/escompte');
    await page.getByTestId('advance-row-0').click();
    await expect(page.getByTestId('adv-position-row')).toContainText('Apurée');
  });

  // Retirement savings, linear cases: a single LINEAR line on the collected payment.
  const savingsLinear = [
    { cr: 'CR-30', titre: 'versement initial', amount: '120,00' },
    { cr: 'CR-31', titre: 'versement programmé', amount: '9,00' },
  ];
  for (const c of savingsLinear) {
    test(`${c.cr} : ${c.titre} épargne retraite donne une ligne Linéaire de ${c.amount} € @screen:calculations @screen:commissionSearch @cr:${c.cr}`, async ({
      page,
      request,
    }) => {
      const seeded = await request.post(`/api/test/cr-scenario?cr=${c.cr}&entity=UNEP&month=2026-01-01`);
      expect(seeded.ok()).toBeTruthy();
      await page.goto('/calculs');
      await page.getByTestId('calc-run').click();
      await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
      await page.goto('/recherche-commissions');
      await page.getByTestId('cs-month').selectOption('2026-01-01');
      await page.getByTestId('cs-search').click();
      const linear = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' });
      await expect(linear).toHaveCount(1);
      await expect(linear.first()).toContainText(c.amount);
    });
  }

  test('CR-32 : un plan programmé escompté donne une ligne Escompte de 288,00 € en année 1 @screen:calculations @screen:commissionSearch @cr:CR-32', async ({
    page,
    request,
  }) => {
    // The discount falls due the month after the first payment, so the January plan is run in February.
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-32&entity=UNEP&month=2026-02-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-02-01');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-02-01');
    await page.getByTestId('cs-search').click();
    const rows = page.locator('[data-testid^="cs-row-"]');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('Escompte');
    await expect(rows.first()).toContainText('288,00');
  });

  test('CR-35 : la clôture annuelle verse un complément retraite de 351,00 € et 352,98 € @screen:calculations @screen:commissionSearch @cr:CR-35', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-35&entity=UNEP&month=2026-12-01');
    expect(seeded.ok()).toBeTruthy();
    // The retirement complement is computed at the annual close, triggered from the executions screen.
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-12-01');
    await page.getByTestId('close-annual').click();
    await expect(page.getByTestId('calc-message')).toContainText('Complément retraite calculé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-12-01');
    await page.getByTestId('cs-search').click();
    const complement = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Complément retraite' });
    await expect(complement).toHaveCount(2);
    await expect(complement.filter({ hasText: '351,00' })).toHaveCount(1);
    await expect(complement.filter({ hasText: '352,98' })).toHaveCount(1);
  });

  test('CR-42 : la clôture de développement verse une surcommission RCD de 6 000,00 € @screen:calculations @screen:commissionSearch @cr:CR-42', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-42&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-01-01');
    await page.getByTestId('close-development').click();
    await expect(page.getByTestId('calc-message')).toContainText('Surcommission de développement calculée');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const rcd = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Développement (RCD)' });
    await expect(rcd).toHaveCount(1);
    await expect(rcd.first()).toContainText(/6.?000,00/);
  });

  test('CR-46 : la clôture de portefeuille verse une surcommission RCP de 8 000,00 € @screen:calculations @screen:commissionSearch @cr:CR-46', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-46&entity=UNEP&month=2026-12-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-12-01');
    await page.getByTestId('close-portfolio').click();
    await expect(page.getByTestId('calc-message')).toContainText('Surcommission de portefeuille calculée');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-12-01');
    await page.getByTestId('cs-search').click();
    const rcp = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Portefeuille (RCP)' });
    await expect(rcp).toHaveCount(1);
    await expect(rcp.first()).toContainText(/8.?000,00/);
  });

  test('CR-49 : le partage avec un groupement verse 27,00 € au distributeur et 3,00 € au groupement @screen:calculations @screen:commissionSearch @cr:CR-49', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-49&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const linear = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' });
    await expect(linear).toHaveCount(2);
    await expect(linear.filter({ hasText: '27,00' })).toHaveCount(1);
    await expect(linear.filter({ hasText: '3,00' })).toHaveCount(1);
  });

  test('CR-51 : une commission externe est intégrée telle quelle, 17,50 € @screen:calculations @screen:commissionSearch @cr:CR-51', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-51&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const external = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Commission externe' });
    await expect(external).toHaveCount(1);
    await expect(external.first()).toContainText('17,50');
  });
});
