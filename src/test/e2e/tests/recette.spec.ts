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

  // Also parcours CAL-14 (section 4.3): at the 360th-day deadline, with no event on the silent
  // guarantee, the discount position is settled and a regularisation line is created.
  test('CR-28 : un déficit au 360e jour solde la position et reprend −30,00 € d’escompte indu @screen:calculations @screen:commissionSearch @screen:advance @cr:CR-28 @path:CAL-14', async ({
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

  test('CR-41 : deux clôtures de développement versent 300,00 € en janvier puis 1 050,00 € en février @screen:calculations @screen:commissionSearch @cr:CR-41', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-41&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-01-01');
    await page.getByTestId('close-development').click();
    await expect(page.getByTestId('calc-message')).toContainText('Surcommission de développement calculée');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-02-01');
    await page.getByTestId('close-development').click();
    await expect(page.getByTestId('calc-message')).toContainText('Surcommission de développement calculée');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const january = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Développement (RCD)' });
    await expect(january).toHaveCount(1);
    await expect(january.first()).toContainText('300,00');
    await page.getByTestId('cs-month').selectOption('2026-02-01');
    await page.getByTestId('cs-search').click();
    const february = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Développement (RCD)' });
    await expect(february).toHaveCount(1);
    await expect(february.first()).toContainText(/1.?050,00/);
  });

  test('CR-50 : une rétrocession de 60 % sur une commission reçue de 100 € verse 60,00 € @screen:calculations @screen:commissionSearch @cr:CR-50', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-50&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const retro = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Rétrocession' });
    await expect(retro).toHaveCount(1);
    await expect(retro.first()).toContainText('60,00');
  });

  // Parcours CAL-02 (section 4.3): a calculation re-run with nothing changed yields the same lines and
  // the same amounts, with no duplicate. CR-50 produces a single retrocession line of 60,00 €; running
  // the calculation twice must still leave exactly one such line (a recalculation deletes the period's
  // unvalidated lines before recreating them — D8 idempotence).
  test('un calcul relancé sans rien changer ne crée pas de doublon @screen:calculations @screen:commissionSearch @path:CAL-02', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-50&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    // Re-run the whole period with nothing changed.
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    const retro = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Rétrocession' });
    // Still exactly one line, same amount — no duplicate from the second run.
    await expect(retro).toHaveCount(1);
    await expect(retro.first()).toContainText('60,00');
  });

  test('CR-48 : douze adhésions atteignent le palier 2 % et versent une RCP de 1 000,00 € @screen:calculations @screen:commissionSearch @cr:CR-48', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-48&entity=UNEP&month=2026-12-01');
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
    await expect(rcp.first()).toContainText(/1.?000,00/);
  });

  test('CR-43 : une condition d’impayés non remplie écrit une RCD à 0,00 € avec son motif @screen:calculations @screen:commissionSearch @cr:CR-43', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-43&entity=UNEP&month=2026-01-01');
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
    await expect(rcd.first()).toContainText('0,00');
    await rcd.first().click();
    await expect(page.getByTestId('line-detail')).toContainText('forfeited');
  });

  test('CR-47 : un P/C de 96 % écrit une RCP à 0,00 € avec le détail des conditions @screen:calculations @screen:commissionSearch @cr:CR-47', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-47&entity=UNEP&month=2026-12-01');
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
    await expect(rcp.first()).toContainText('0,00');
    await rcp.first().click();
    await expect(page.getByTestId('line-detail')).toContainText('LOSS_RATIO');
  });

  test('CR-40 : une production sous le premier palier ne verse aucune RCD @screen:calculations @screen:commissionSearch @cr:CR-40', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-40&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-01-01');
    await page.getByTestId('close-development').click();
    await expect(page.getByTestId('calc-message')).toContainText('Surcommission de développement calculée');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    await expect(page.getByTestId('no-row')).toBeVisible();
    await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Développement (RCD)' })).toHaveCount(0);
  });

  test('CR-44 : une inscription sans avenant signé ne verse rien et lève une anomalie @screen:calculations @screen:commissionSearch @screen:controls @cr:CR-44', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-44&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-01-01');
    await page.getByTestId('close-development').click();
    await expect(page.getByTestId('calc-message')).toContainText('Surcommission de développement calculée');
    // No commission line is produced.
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-01-01');
    await page.getByTestId('cs-search').click();
    await expect(page.getByTestId('no-row')).toBeVisible();
    await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Développement (RCD)' })).toHaveCount(0);
    // The unsigned-amendment anomaly surfaces on the controls screen.
    await page.goto('/controles');
    await page.getByTestId('ctl-month').selectOption('2026-01-01');
    await page.getByTestId('ctl-refresh').click();
    await expect(page.locator('[data-testid^="anomaly-row-"]').filter({ hasText: 'ENR-01' })).toHaveCount(1);
  });

  test('CR-45 : une RCD payée reprise à la résiliation du 8e mois donne une reprise de −40,00 € @screen:calculations @screen:commissionSearch @cr:CR-45', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-45&entity=UNEP&month=2026-09-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-09-01');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-09-01');
    await page.getByTestId('cs-search').click();
    const clawback = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Reprise' });
    await expect(clawback).toHaveCount(1);
    await expect(clawback.first()).toContainText('-40,00');
  });

  // Insurer-commission recall (CR-33/34): the engine records an expected amount on the CAL-13 control
  // rather than a commission line; it is read on the controls screen's "Attendu" column.
  const insurerRecalls = [
    { cr: 'CR-33', expected: '-71.25' },
    { cr: 'CR-34', expected: '-158.10' },
  ];
  for (const c of insurerRecalls) {
    test(`${c.cr} : le rappel attendu de ${c.expected} est conservé sur le contrôle CAL-13 @screen:calculations @screen:controls @cr:${c.cr}`, async ({
      page,
      request,
    }) => {
      const seeded = await request.post(`/api/test/cr-scenario?cr=${c.cr}&entity=UNEP&month=2026-01-01`);
      expect(seeded.ok()).toBeTruthy();
      await page.goto('/calculs');
      await page.getByTestId('calc-run').click();
      await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
      await page.goto('/controles');
      await page.getByTestId('ctl-month').selectOption('2026-01-01');
      await page.getByTestId('ctl-refresh').click();
      const anomaly = page.locator('[data-testid^="anomaly-row-"]').filter({ hasText: 'CAL-13' });
      await expect(anomaly).toHaveCount(1);
      await expect(anomaly.first()).toContainText(c.expected);
    });
  }

  test('CR-36 : la sur-commission assureur attendue de 1 008,00 € est conservée sur le contrôle CAL-13 @screen:calculations @screen:controls @cr:CR-36', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-36&entity=UNEP&month=2026-12-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-12-01');
    await page.getByTestId('close-annual').click();
    await expect(page.getByTestId('calc-message')).toContainText('Complément retraite calculé');
    await page.goto('/controles');
    await page.getByTestId('ctl-month').selectOption('2026-12-01');
    await page.getByTestId('ctl-refresh').click();
    const anomaly = page.locator('[data-testid^="anomaly-row-"]').filter({ hasText: 'CAL-13' });
    await expect(anomaly).toHaveCount(1);
    await expect(anomaly.first()).toContainText('1008.00');
  });

  // Also parcours INT-14 (section 4.1): an insurer file filtered on the entity's share — the filtered
  // lines are counted and only the entity's part is integrated (0,24 € of the 0,24 + 0,19 = 0,43 €).
  test('CR-37 : le mapping du fichier UMR ne garde que la part de l’entité @screen:imports @cr:CR-37 @path:INT-14', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-37&entity=UNEP&month=2026-07-01');
    expect(seeded.ok()).toBeTruthy();
    const content =
      'CODE_MOUVEMENT;TYPE_MOUVEMENT;NUMERO_CONTRAT;DATE_MOUVEMENT;NOM_MUTUELLE;MONTANT_COMMISSION\n' +
      'M1;FG;AD-1;2026-07-05;ALPTIS;0.24\n' +
      'M1;FG;AD-1;2026-07-05;UMR;0.19\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('UMR');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'umr_prod.xlsx.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(content),
    });
    await page.getByTestId('upload-submit').click();
    // INT-14: only the ALPTIS line (the entity's part) is integrated; the UMR line is filtered out by
    // the mapping and counted as a filtered line, not a rejection.
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s), 0 rejetée(s), 0 en attente');
    await expect(page.getByTestId('upload-message')).toContainText('1 filtrée(s)');
    // The journal batch records the two source lines read, one filtered and one integrated.
    const row = page.locator('[data-testid^="batch-row-"]').first();
    await expect(row).toBeVisible();
  });

  test('CR-38 : le fichier de production UMR donne des commissions reçues de 451,54 € @screen:imports @screen:calculations @screen:commissionSearch @cr:CR-38', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-38&entity=UNEP&month=2026-07-01');
    expect(seeded.ok()).toBeTruthy();
    const content =
      'CODE_MOUVEMENT;TYPE_MOUVEMENT;NUMERO_CONTRAT;DATE_MOUVEMENT;NOM_MUTUELLE;MONTANT_COMMISSION\n' +
      'M10;VI;AD-1;2026-07-05;ALPTIS;449.08\n' +
      'M11;FG;AD-1;2026-07-10;ALPTIS;2.46\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('UMR');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'umr_prod_202607.xlsx.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(content),
    });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('2 intégrée(s)');
    // Running the calculation loads the received commissions as INSURER lines.
    await page.goto('/calculs');
    await page.getByTestId('calc-period').selectOption('UNEP|2026-07-01');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/recherche-commissions');
    await page.getByTestId('cs-month').selectOption('2026-07-01');
    await page.getByTestId('cs-search').click();
    const received = page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Commission reçue' });
    await expect(received).toHaveCount(2);
    await expect(received.filter({ hasText: '449,08' })).toHaveCount(1);
    await expect(received.filter({ hasText: '2,46' })).toHaveCount(1);
  });

  test('CR-67 : un fichier aux lignes fausses intègre 1 ligne, en rejette 3 et en met 1 en attente @screen:imports @cr:CR-67', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-67&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    const header =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount';
    const content =
      header +
      '\n' +
      'UNEP;CLEVA;ENC-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n' +
      'UNEP;CLEVA;ENC-2;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;0.00\n' +
      'UNEP;CLEVA;ENC-3;PREMIUM_COLLECTED;;C1;2020-01-05;100.00\n' +
      'UNEP;CLEVA;ENC-4;PREMIUM_COLLECTED;AD-1;C1;2999-01-01;100.00\n' +
      'UNEP;CLEVA;ENC-5;PREMIUM_COLLECTED;AD-UNKNOWN;C1;2020-01-05;100.00\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('CLEVA');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'unep_events_20260105.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(content),
    });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s), 3 rejetée(s), 1 en attente');
  });

  test('CR-69 : simuler la règle calculée reproduit la ligne au centime, écart nul @screen:calculations @screen:simulation @cr:CR-69', async ({
    page,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-01&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    await page.goto('/calculs');
    await page.getByTestId('calc-run').click();
    await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');
    await page.goto('/simulation');
    await page.getByTestId('sim-month').selectOption('2026-01-01');
    await page.getByTestId('sim-rule').selectOption('R-CR01-LIN');
    await page.getByTestId('sim-version').fill('1');
    await page.getByTestId('sim-run').click();
    await expect(page.getByTestId('sim-real')).toContainText('15,00');
    await expect(page.getByTestId('sim-sim')).toContainText('15,00');
    await expect(page.getByTestId('sim-ecart')).toContainText('0,00');
  });

  test('CR-63 : une commission sous le seuil de paiement n’est pas ordonnancée et figure en exclusion @screen:calculations @screen:periods @screen:payment @cr:CR-63', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-63&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    // The admin runs the real calculation, then advances the period through calculation and controls.
    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    await admin.goto('/calculs');
    await admin.getByTestId('calc-run').click();
    await expect(admin.getByTestId('calc-message')).toContainText('Calcul lancé');
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');

    // The business validator validates, then prepares the payment.
    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();
    await val.goto('/');
    await val.getByTestId('open-UNEP-2026-01-01').click();
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Validée métier');
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Paiement préparé');

    // On the payment screen the lot carries no order and a below-threshold exclusion.
    await admin.goto('/paiement');
    await admin.getByTestId('pay-month').selectOption('2026-01-01');
    await admin.getByTestId('pay-refresh').click();
    await admin.locator('[data-testid^="lot-open-"]').first().click();
    await expect(admin.getByTestId('lot-detail')).toBeVisible();
    await expect(admin.getByTestId('no-order')).toBeVisible();
    await expect(admin.locator('[data-testid^="exclusion-reason-"]').first()).toContainText(
      'Sous le seuil de paiement',
    );

    await adminCtx.close();
    await valCtx.close();
  });

  test('CR-64 : chaque ordre est aligné sur un bordereau de même montant @screen:calculations @screen:periods @screen:payment @screen:bordereaux @cr:CR-64', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-01&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    await admin.goto('/calculs');
    await admin.getByTestId('calc-run').click();
    await expect(admin.getByTestId('calc-message')).toContainText('Calcul lancé');
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');

    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();
    await val.goto('/');
    await val.getByTestId('open-UNEP-2026-01-01').click();
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Validée métier');
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Paiement préparé');

    // The lot carries one order of 15,00 € and its total is the same.
    await admin.goto('/paiement');
    await admin.getByTestId('pay-month').selectOption('2026-01-01');
    await admin.getByTestId('pay-refresh').click();
    await admin.locator('[data-testid^="lot-open-"]').first().click();
    await expect(admin.getByTestId('lot-detail')).toContainText('15,00');
    await expect(admin.locator('[data-testid^="order-row-"]')).toHaveCount(1);
    await expect(admin.locator('[data-testid^="order-row-"]').first()).toContainText('15,00');

    // A bordereau of the same net amount is published for the beneficiary.
    await admin.goto('/bordereaux');
    await admin.getByTestId('brd-month').selectOption('2026-01-01');
    await admin.getByTestId('brd-refresh').click();
    const statement = admin.locator('[data-testid^="statement-row-"]').filter({ hasText: '15,00' });
    await expect(statement).toHaveCount(1);

    await adminCtx.close();
    await valCtx.close();
  });

  test('CR-66 : une résiliation régularise le prévisionnel de 90,00 € en comptabilité @screen:periods @screen:accounting @cr:CR-66', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-66&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    // The forecast lines are seeded; the period only needs to advance to business validation.
    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');

    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();
    await val.goto('/');
    await val.getByTestId('open-UNEP-2026-01-01').click();
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Validée métier');

    // The forecast-adjustment entry of 90,00 € is read on the accounting screen.
    await admin.goto('/comptabilite');
    await admin.getByTestId('acc-month').selectOption('2026-01-01');
    await admin.getByTestId('acc-refresh').click();
    const adjustment = admin.locator('[data-testid^="entry-row-"]').filter({ hasText: 'FORECAST_ADJUSTMENT' });
    await expect(adjustment).toHaveCount(1);
    await expect(adjustment.first()).toContainText('90,00');

    await adminCtx.close();
    await valCtx.close();
  });

  test('CR-65 : le cycle comptable écrit la provision de 180 € puis l’acquisition et le payable de 15 € @screen:periods @screen:accounting @cr:CR-65 @path:FIN-05', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-65&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');

    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();
    await val.goto('/');
    await val.getByTestId('open-UNEP-2026-01-01').click();
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Validée métier');

    await admin.goto('/comptabilite');
    await admin.getByTestId('acc-month').selectOption('2026-01-01');
    await admin.getByTestId('acc-refresh').click();
    const forecast = admin.locator('[data-testid^="entry-row-"]').filter({ hasText: 'FORECAST_RECOGNITION' });
    await expect(forecast).toHaveCount(1);
    await expect(forecast.first()).toContainText('180,00');
    await expect(admin.locator('[data-testid^="entry-row-"]').filter({ hasText: 'EARNING' })).toContainText('15,00');
    await expect(
      admin.locator('[data-testid^="entry-row-"]').filter({ hasText: 'PAYABLE_RECOGNITION' }),
    ).toContainText('15,00');

    // FIN-05: the extraction of this not-yet-closed period carries the OUT-02 columns, the accounting
    // month and its balanced entries (a debit and a credit account, with the amounts).
    await admin.getByTestId('acc-extract').click();
    await expect(admin.getByTestId('acc-message')).toContainText('UNEP_ECR_202601.csv');
    const csv = admin.getByTestId('acc-extract-csv');
    await expect(csv).toContainText('entity_code;fiscal_year;accounting_month;journal;entry_date;accounting_event');
    await expect(csv).toContainText('debit_account;credit_account;amount');
    await expect(csv).toContainText('2026-01');
    await expect(csv).toContainText('180.00');

    await adminCtx.close();
    await valCtx.close();
  });

  test('CR-61 : la compensation des comptes d’un distributeur paie 300,00 € sur le code créditeur @screen:periods @screen:distributor @screen:payment @cr:CR-61 @path:CPT-02', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-61&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');

    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();
    await val.goto('/');
    await val.getByTestId('open-UNEP-2026-01-01').click();
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Validée métier');

    // The operator compensates the distributor's debtor code against its creditor code.
    await admin.goto('/distributeur');
    await admin.getByTestId('acc-month').selectOption('2026-01-01');
    await admin.getByTestId('acc-partner').selectOption('P-CR61');
    await admin.getByTestId('acc-search').click();
    await admin.getByTestId('acc-compensate').click();
    await expect(admin.getByTestId('acc-message')).toContainText('2 ligne(s) de compensation');

    // The period is prepared and the creditor code is paid 300,00 €, the debtor code is not ordered.
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Paiement préparé');
    await admin.goto('/paiement');
    await admin.getByTestId('pay-month').selectOption('2026-01-01');
    await admin.getByTestId('pay-refresh').click();
    await admin.locator('[data-testid^="lot-open-"]').first().click();
    await expect(admin.locator('[data-testid^="order-row-"]')).toHaveCount(1);
    await expect(admin.locator('[data-testid^="order-row-"]').first()).toContainText('300,00');

    await adminCtx.close();
    await valCtx.close();
  });

  test('CR-60 : un solde débiteur reporté bloque février puis paie 300,00 € en mars @screen:periods @screen:payment @cr:CR-60', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-60&entity=UNEP&month=2026-02-01');
    expect(seeded.ok()).toBeTruthy();

    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();

    // Drives one period from loaded to payment-prepared across the admin and validator.
    const prepare = async (month: string) => {
      await admin.goto('/');
      await admin.getByTestId(`open-UNEP-${month}`).click();
      await admin.getByTestId('advance-button').click();
      await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
      await admin.getByTestId('advance-button').click();
      await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');
      await val.goto('/');
      await val.getByTestId(`open-UNEP-${month}`).click();
      await val.getByTestId('advance-button').click();
      await expect(val.getByTestId('detail-state')).toContainText('Validée métier');
      await val.getByTestId('advance-button').click();
      await expect(val.getByTestId('detail-state')).toContainText('Paiement préparé');
    };

    const openLot = async (month: string) => {
      await admin.goto('/paiement');
      await admin.getByTestId('pay-month').selectOption(month);
      await admin.getByTestId('pay-refresh').click();
      await admin.locator('[data-testid^="lot-open-"]').first().click();
      await expect(admin.getByTestId('lot-detail')).toBeVisible();
    };

    // February: the −1 000 € opening balance leaves the account debtor, so no order is issued.
    await prepare('2026-02-01');
    await openLot('2026-02-01');
    await expect(admin.getByTestId('no-order')).toBeVisible();

    // March: a further 700 € brings the account to +300 €, which is ordered.
    await prepare('2026-03-01');
    await openLot('2026-03-01');
    await expect(admin.locator('[data-testid^="order-row-"]')).toHaveCount(1);
    await expect(admin.locator('[data-testid^="order-row-"]').first()).toContainText('300,00');

    await adminCtx.close();
    await valCtx.close();
  });

  test('CR-62 : un distributeur radié de l’ORIAS est bloqué puis réglé par un lot hors cycle @screen:periods @screen:payment @screen:imports @cr:CR-62 @path:PAI-06', async ({
    browser,
    request,
  }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-62&entity=UNEP&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();

    const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
    const admin = await adminCtx.newPage();
    await admin.goto('/');
    await admin.getByTestId('open-UNEP-2026-01-01').click();
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Calculée');
    await admin.getByTestId('advance-button').click();
    await expect(admin.getByTestId('detail-state')).toContainText('Contrôlée');

    const valCtx = await browser.newContext({ httpCredentials: USERS.validatorUnep });
    const val = await valCtx.newPage();
    await val.goto('/');
    await val.getByTestId('open-UNEP-2026-01-01').click();
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Validée métier');
    await val.getByTestId('advance-button').click();
    await expect(val.getByTestId('detail-state')).toContainText('Paiement préparé');

    // Preparing the cycle lot cancels the struck-off distributor's order and lists it as inactive.
    await admin.goto('/paiement');
    await admin.getByTestId('pay-month').selectOption('2026-01-01');
    await admin.getByTestId('pay-refresh').click();
    await admin.locator('[data-testid^="lot-open-"]').first().click();
    await admin.getByTestId('lot-prepare').click();
    await expect(
      admin.locator('[data-testid^="exclusion-reason-"]').filter({ hasText: 'Bénéficiaire inactif' }),
    ).toHaveCount(1);

    // Reinstatement: re-import the distributor referential with the ORIAS status registered.
    const dist =
      'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from;orias_status\n' +
      'UNEP;SESAME;P-CR62;P-CR62;INDEPENDENT;ACTIVE;2026-01-01;REGISTERED\n';
    await admin.goto('/imports');
    await admin.getByTestId('upload-flow').selectOption('distributors');
    await admin.getByTestId('upload-source').selectOption('SESAME');
    await admin.getByTestId('upload-file').setInputFiles({
      name: 'reinstate.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(dist),
    });
    await admin.getByTestId('upload-submit').click();
    await expect(admin.getByTestId('upload-message')).toContainText('1 intégrée(s)');

    // An off-cycle lot now pays the 110,00 € that was blocked.
    await admin.goto('/paiement');
    await admin.getByTestId('pay-month').selectOption('2026-01-01');
    await admin.getByTestId('pay-refresh').click();
    await admin.getByTestId('pay-offcycle-partner').fill('P-CR62');
    await admin.getByTestId('pay-offcycle-submit').click();
    const offCycle = admin.locator('[data-testid^="lot-row-"]').filter({ hasText: 'OFF_CYCLE' });
    await expect(offCycle).toHaveCount(1);
    await offCycle.locator('[data-testid^="lot-open-"]').click();
    await expect(admin.locator('[data-testid^="order-row-"]')).toHaveCount(1);
    await expect(admin.locator('[data-testid^="order-row-"]').first()).toContainText('110,00');

    await adminCtx.close();
    await valCtx.close();
  });
});

// Parcours CAL-05 (section 4.3): calculating a period whose previous period is not yet
// business-validated is refused. CR-41 seeds two consecutive UNEP periods (January and February),
// both LOADED; a business administrator who tries to move February to CALCULATED while January is
// still unvalidated is turned down by the cycle guard, and the refusal is shown on the screen.
test('un calcul de février est refusé tant que janvier n’est pas validé par le métier @screen:periods @path:CAL-05', async ({
  browser,
  request,
}) => {
  // This test lives outside the replay describe, so it resets the database itself before seeding
  // (the cr-scenario endpoint only seeds; it does not reset) to avoid leaked state from a prior test.
  await resetDb(request);
  const seeded = await request.post('/api/test/cr-scenario?cr=CR-41&entity=UNEP&month=2026-01-01');
  expect(seeded.ok()).toBeTruthy();

  const adminCtx = await browser.newContext({ httpCredentials: USERS.adminUnep });
  const admin = await adminCtx.newPage();
  await admin.goto('/periodes');
  // February exists and is still LOADED, as is its predecessor January.
  await admin.getByTestId('open-UNEP-2026-02-01').click();
  await expect(admin.getByTestId('period-detail')).toBeVisible();
  await expect(admin.getByTestId('detail-state')).toContainText('Données gelées');

  // The administrator asks to calculate February; the guard refuses because January is unvalidated.
  await admin.getByTestId('advance-button').click();
  await expect(admin.getByTestId('periods-error')).toContainText(
    'pas encore validée par le métier',
  );
  // The refusal left February unchanged: it is still LOADED, not CALCULATED.
  await expect(admin.getByTestId('detail-state')).toContainText('Données gelées');

  await adminCtx.close();
});
