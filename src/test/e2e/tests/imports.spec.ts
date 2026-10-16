import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, USERS } from '../support/helpers';

// Per-screen tests for the imports screen (ING-01/04/05/08): upload with counters, journal,
// rejection resolution, expected flows, per-role access.
//
// @screen:imports @req:ING-01 @req:ING-05

const DISTRIBUTORS =
  'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n' +
  'ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n' +
  'ALPTIS_ASSURANCES;SESAME;;Sans code;INDEPENDENT;ACTIVE;2026-01-01\n';

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Imports — opérateur', () => {
  test.use({ httpCredentials: USERS.operatorAlptis });

  test('téléverse un fichier, journalise les compteurs et traite un rejet @screen:imports @req:ING-01 @req:ING-05', async ({
    page,
  }) => {
    await page.goto('/imports');
    await expect(page.getByTestId('page-title')).toHaveText('Imports et rejets');

    await page.getByTestId('upload-flow').selectOption('distributors');
    // The source system is chosen from a list (ING-01), not typed as a free-text code.
    await page.getByTestId('upload-source').selectOption('SESAME');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'ALPTIS_ASSURANCES_distributors_20260930.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(DISTRIBUTORS, 'utf-8'),
    });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s), 1 rejetée(s)');

    // Journal shows the batch with the partially-loaded state at its French label (ADM-03); the flow
    // reads as its French label too.
    const batchRow = page.locator('[data-testid^="batch-row-"]').first();
    await expect(batchRow).toContainText('Distributeurs');
    await expect(batchRow.getByTestId('batch-state')).toHaveText('Intégré partiellement');

    // Open the batch, correct the rejected line.
    await batchRow.click();
    await expect(page.getByTestId('batch-detail')).toBeVisible();
    const rejectionRow = page.locator('[data-testid^="rejection-row-"]').first();
    await expect(rejectionRow).toContainText('MISSING_FIELD');
    await rejectionRow.getByRole('button', { name: 'Corriger et relancer' }).click();
    await expect(page.locator('[data-testid^="rejection-row-"]').first()).toContainText('CORRECTED');

    // Expected flows: distributors is now received (the clock-driven late/missing states are
    // asserted deterministically by the INT-08 test with a frozen clock).
    await expect(page.getByTestId('flow-distributors')).toContainText('Reçu');
  });

  test('refuse un fichier déjà intégré, sans doublon (CR-68, INT-05) @screen:imports @cr:CR-68 @path:INT-05', async ({ page }) => {
    await page.goto('/imports');
    const upload = async () => {
      await page.getByTestId('upload-flow').selectOption('distributors');
      await page.getByTestId('upload-file').setInputFiles({
        name: 'same.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(DISTRIBUTORS, 'utf-8'),
      });
      await page.getByTestId('upload-submit').click();
    };
    await upload();
    await expect(page.getByTestId('upload-message')).toBeVisible();
    // The first deposit created exactly one batch in the journal.
    await expect(page.locator('[data-testid^="batch-row-"]')).toHaveCount(1);
    await upload();
    await expect(page.getByTestId('upload-error')).toContainText('déjà intégré');
    // The second, refused deposit created no new batch: no duplicate import (INT-05).
    await expect(page.locator('[data-testid^="batch-row-"]')).toHaveCount(1);
  });

  test('dépôt d’un fichier d’événements valide : compteurs, empreinte, auteur, événement visible (INT-01) @screen:imports @path:INT-01', async ({ page, request }) => {
    // A contract the valid event targets, so the line loads rather than being held.
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-67&entity=ALPTIS_ASSURANCES&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    const events =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n' +
      'ALPTIS_ASSURANCES;CLEVA;ENC-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('CLEVA');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'encaissements.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(events, 'utf-8'),
    });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s), 0 rejetée(s), 0 en attente');
    // The journal batch carries its fingerprint and the operator who actually deposited it — not a
    // generic placeholder but the authenticated user (INT-01).
    const row = page.locator('[data-testid^="batch-row-"]').first();
    await expect(row).toContainText('CLEVA');
    await row.click();
    await expect(page.getByTestId('batch-fingerprint')).not.toBeEmpty();
    await expect(page.getByTestId('batch-author')).toHaveText('operator-alptis');
    // The loaded event is visible with its status on the events screen.
    await page.goto('/evenements');
    await page.getByTestId('events-search').click();
    const eventRow = page.locator('[data-testid^="event-row-"]').filter({ hasText: 'Prime encaissée' });
    await expect(eventRow).toHaveCount(1);
  });

  test('correction d’un rejet à l’écran : la ligne repart seule, le rejet est corrigé (INT-03) @screen:imports @screen:events @path:INT-03', async ({ page, request }) => {
    // CR-67 seeds the AD-1 / C1 contract so a corrected premium loads rather than being held.
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-67&entity=ALPTIS_ASSURANCES&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    const header =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n';
    // A line missing its source key is rejected (MISSING_FIELD).
    const bad = 'ALPTIS_ASSURANCES;CLEVA;;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('CLEVA');
    await page.getByTestId('upload-file').setInputFiles({ name: 'int03.csv', mimeType: 'text/csv', buffer: Buffer.from(header + bad, 'utf-8') });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('0 intégrée(s), 1 rejetée(s)');

    // Open the batch and correct the rejected line with a valid one.
    await page.locator('[data-testid^="batch-row-"]').first().click();
    const rejection = page.locator('[data-testid^="rejection-row-"]').first();
    await expect(rejection).toContainText('MISSING_FIELD');
    await page.locator('[data-testid^="correct-line-"]').first()
      .fill('ALPTIS_ASSURANCES;CLEVA;ENC-FIX;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00');
    await rejection.getByRole('button', { name: 'Corriger et relancer' }).click();

    // The rejection is corrected and the corrected line reprocessed into a visible event (it reparts alone).
    await expect(page.locator('[data-testid^="rejection-row-"]').first()).toContainText('CORRECTED');
    await page.goto('/evenements');
    await page.getByTestId('events-search').click();
    await expect(page.locator('[data-testid^="event-row-"]').filter({ hasText: 'ENC-FIX' })).toHaveCount(1);
  });

  test('les flux attendus sont reçu / en retard / manquant selon l’horloge (INT-08) @screen:imports @path:INT-08', async ({ page, request }) => {
    // Freeze the clock at the 15th: coverages (due the 5th) and events (due the 10th) are past due,
    // distributors (due the 25th) is still awaited.
    await request.put('/api/test/clock', { data: { instant: '2026-06-15T12:00:00Z' } });
    try {
      // A coverages file received this month makes that flow "Reçu" (header only is enough).
      const covHeader =
        'contract_number;coverage_code;product_code;policyholder_id;status;remuneration_mode;effect_date;registration_date\n';
      await page.goto('/imports');
      await page.getByTestId('upload-flow').selectOption('coverages');
      await page.getByTestId('upload-source').selectOption('CLEVA');
      await page.getByTestId('upload-file').setInputFiles({ name: 'cov.csv', mimeType: 'text/csv', buffer: Buffer.from(covHeader, 'utf-8') });
      await page.getByTestId('upload-submit').click();
      await expect(page.getByTestId('upload-message')).toBeVisible();

      await expect(page.getByTestId('flow-coverages')).toContainText('Reçu');
      await expect(page.getByTestId('flow-events')).toContainText('En retard');
      await expect(page.getByTestId('flow-distributors')).toContainText('Manquant');
    } finally {
      await request.delete('/api/test/clock');
    }
  });

  test('lignes négatives : annulation liée, rapprochée, et mise en attente sans correspondance (INT-09) @screen:imports @screen:events @path:INT-09', async ({ page, request }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-67&entity=ALPTIS_ASSURANCES&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    const header =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n';
    // Two originals: one to cancel by key, one to reconcile by opposite amount.
    const originals = header
      + 'ALPTIS_ASSURANCES;CLEVA;ENC-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n'
      + 'ALPTIS_ASSURANCES;CLEVA;ENC-2;PREMIUM_COLLECTED;AD-1;C1;2020-01-06;50.00\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('CLEVA');
    await page.getByTestId('upload-file').setInputFiles({ name: 'orig.csv', mimeType: 'text/csv', buffer: Buffer.from(originals, 'utf-8') });
    await page.getByTestId('upload-submit').click();
    await expect(page.getByTestId('upload-message')).toContainText('2 intégrée(s)');

    // Negatives: ANN-1 cancels ENC-1 by key; NEG-1 reconciles ENC-2 by opposite amount; NEG-X matches
    // nothing and is held.
    const negatives =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount;reverses_source_key\n'
      + 'ALPTIS_ASSURANCES;CLEVA;ANN-1;PREMIUM_CANCELLED;AD-1;C1;2020-02-05;-100.00;ENC-1\n'
      + 'ALPTIS_ASSURANCES;CLEVA;NEG-1;PREMIUM_COLLECTED;AD-1;C1;2020-02-06;-50.00;\n'
      + 'ALPTIS_ASSURANCES;CLEVA;NEG-X;PREMIUM_COLLECTED;AD-1;C1;2020-02-07;-999.00;\n';
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('CLEVA');
    await page.getByTestId('upload-file').setInputFiles({ name: 'neg.csv', mimeType: 'text/csv', buffer: Buffer.from(negatives, 'utf-8') });
    await page.getByTestId('upload-submit').click();
    // Two integrated (the linked and the reconciled), one held (the unmatched).
    await expect(page.getByTestId('upload-message')).toContainText('2 intégrée(s), 0 rejetée(s), 1 en attente');

    // On the events screen: the unmatched negative is set aside with its reason; the linked and the
    // reconciled cancellations are pending (accepted).
    await page.goto('/evenements');
    await page.getByTestId('events-search').click();
    const held = page.locator('[data-testid^="event-row-"]').filter({ hasText: 'NEG-X' });
    await expect(held).toContainText('Mis à l’écart');
    await expect(held).toContainText('Négatif non rapproché');
    await expect(page.locator('[data-testid^="event-row-"]').filter({ hasText: 'ANN-1' })).toContainText('En attente de calcul');
    await expect(page.locator('[data-testid^="event-row-"]').filter({ hasText: 'NEG-1' })).toContainText('En attente de calcul');
  });

  test('mise à l’écart d’un doublon : statut du rejet, aucun événement créé (INT-04) @screen:imports @path:INT-04', async ({ page, request }) => {
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-67&entity=ALPTIS_ASSURANCES&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    const header =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n';
    const line = 'ALPTIS_ASSURANCES;CLEVA;DUP-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n';
    const deposit = async (name: string) => {
      await page.getByTestId('upload-flow').selectOption('events');
      await page.getByTestId('upload-source').selectOption('CLEVA');
      await page.getByTestId('upload-file').setInputFiles({ name, mimeType: 'text/csv', buffer: Buffer.from(header + line, 'utf-8') });
      await page.getByTestId('upload-submit').click();
    };
    await page.goto('/imports');
    await deposit('a.csv');
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s)');
    // The same source key, deposited again, is a duplicate.
    await deposit('b.csv');
    await expect(page.getByTestId('upload-message')).toContainText('0 intégrée(s), 1 rejetée(s)');
    // Discard the duplicate rejection; its status becomes discarded.
    await page.locator('[data-testid^="batch-row-"]').first().click();
    const rejection = page.locator('[data-testid^="rejection-row-"]').filter({ hasText: 'DUPLICATE' });
    await expect(rejection).toHaveCount(1);
    await rejection.getByRole('button', { name: 'Écarter' }).click();
    await expect(page.locator('[data-testid^="rejection-row-"]').first()).toContainText('DISCARDED');
    // No duplicate event was created: only the first deposit's event exists.
    await page.goto('/evenements');
    await page.getByTestId('events-search').click();
    await expect(page.locator('[data-testid^="event-row-"]').filter({ hasText: 'Prime encaissée' })).toHaveCount(1);
  });

  test('un fichier à une ligne fautive par motif : six rejets codés avec leur ligne brute, le reste intégré (INT-02) @screen:imports @path:INT-02', async ({ page, request }) => {
    // CR-67 seeds the AD-1 contract so the valid line loads and the duplicate can be detected.
    const seeded = await request.post('/api/test/cr-scenario?cr=CR-67&entity=ALPTIS_ASSURANCES&month=2026-01-01');
    expect(seeded.ok()).toBeTruthy();
    const header =
      'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n';
    const lines = [
      'ALPTIS_ASSURANCES;CLEVA;OK-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00', // loads
      'ALPTIS_ASSURANCES;CLEVA;OK-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00', // DUPLICATE
      'ALPTIS_ASSURANCES;CLEVA;;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00', // MISSING_FIELD
      'ALPTIS_ASSURANCES;CLEVA;BAD-T;BOGUS;AD-1;C1;2020-01-05;100.00', // TYPE
      'ALPTIS_ASSURANCES;CLEVA;BAD-F;PREMIUM_COLLECTED;AD-1;C1;2999-01-01;100.00', // FUTURE_DATE
      'ALPTIS_ASSURANCES;CLEVA;BAD-A;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;0.00', // AMOUNT
      'ALPTIS_ASSURANCES;CLEVA;BAD-S;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00;surplus', // STRUCTURE
    ].join('\n') + '\n';
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('events');
    await page.getByTestId('upload-source').selectOption('CLEVA');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'six-motifs.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(header + lines, 'utf-8'),
    });
    await page.getByTestId('upload-submit').click();
    // One line integrated, six rejected — one per reject code of section 8.2.
    await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s), 6 rejetée(s)');
    await page.locator('[data-testid^="batch-row-"]').first().click();
    // One rejection per reject code (matched on the code cell, not the raw line which echoes columns).
    const codeCells = page.locator('[data-testid^="detail-code-"]');
    await expect(codeCells).toHaveCount(6);
    for (const code of ['STRUCTURE', 'MISSING_FIELD', 'TYPE', 'FUTURE_DATE', 'AMOUNT', 'DUPLICATE']) {
      await expect(codeCells.filter({ hasText: new RegExp(`^${code}$`) })).toHaveCount(1);
    }
    // Each rejection carries its raw line: the structural one shows its source key.
    await expect(page.locator('[data-testid^="rejection-row-"]').filter({ hasText: 'BAD-S' }))
      .toContainText('STRUCTURE');
  });

  test('un même nom de fichier de deux systèmes source est accepté et distingué au journal (INT-06) @screen:imports @path:INT-06', async ({ page }) => {
    await page.goto('/imports');
    const header = 'entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n';
    const upload = async (source: string, partner: string) => {
      await page.getByTestId('upload-flow').selectOption('distributors');
      await page.getByTestId('upload-source').selectOption(source);
      await page.getByTestId('upload-file').setInputFiles({
        name: 'reseau.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(header + `ALPTIS_ASSURANCES;${source};${partner};Cabinet;INDEPENDENT;ACTIVE;2026-01-01\n`, 'utf-8'),
      });
      await page.getByTestId('upload-submit').click();
      await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s)');
    };
    await upload('SESAME', 'P13897');
    await upload('CLEVA', 'P99999');
    // Both deposits are kept, distinguished by their source system in the journal.
    const rows = page.locator('[data-testid^="batch-row-"]');
    await expect(rows).toHaveCount(2);
    await expect(rows.filter({ hasText: 'SESAME' })).toHaveCount(1);
    await expect(rows.filter({ hasText: 'CLEVA' })).toHaveCount(1);
  });

  test('un fichier réduit à son en-tête est reçu avec zéro ligne (INT-07) @screen:imports @path:INT-07', async ({ page }) => {
    await page.goto('/imports');
    await page.getByTestId('upload-flow').selectOption('distributors');
    await page.getByTestId('upload-source').selectOption('SESAME');
    await page.getByTestId('upload-file').setInputFiles({
      name: 'ALPTIS_ASSURANCES_distributors_entete.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n', 'utf-8'),
    });
    await page.getByTestId('upload-submit').click();
    // Zero lines integrated, rejected or held; the flow is nonetheless received.
    await expect(page.getByTestId('upload-message')).toContainText('0 intégrée(s), 0 rejetée(s)');
    const batchRow = page.locator('[data-testid^="batch-row-"]').first();
    await expect(batchRow).toContainText('Distributeurs');
    await expect(page.getByTestId('flow-distributors')).toContainText('Reçu');
  });

  test('aucune violation d’accessibilité grave ou critique @screen:imports', async ({ page }) => {
    await page.goto('/imports');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious, JSON.stringify(serious.map((v) => v.id))).toEqual([]);
  });
});

test.describe('Imports — lecture seule et accès', () => {
  test('l’auditeur voit le journal sans pouvoir déposer @screen:imports', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.auditor });
    const page = await context.newPage();
    await page.goto('/imports');
    await expect(page.getByTestId('page-title')).toHaveText('Imports et rejets');
    await expect(page.getByTestId('upload-form')).toHaveCount(0);
    await context.close();
  });

  test('un commercial n’a pas accès à l’écran @screen:imports', async ({ browser }) => {
    const context = await browser.newContext({ httpCredentials: USERS.salesRep });
    const page = await context.newPage();
    await page.goto('/imports');
    await expect(page.getByTestId('load-error')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Imports' })).toHaveCount(0);
    await context.close();
  });
});

// Parcours INT-10 (section 4.1): an event whose reference is unknown is held, then calculates on its
// own once the referential (the garantie) arrives.
test('INT-10 : un événement à référence inconnue est mis en attente puis calculé à l’arrivée du référentiel @screen:imports @screen:events @screen:calculations @path:INT-10', async ({ browser, request }) => {
  await resetDb(request);
  // CR-01 seeds the UNEP product, linear 15 % rule and the PC-CR01 production code.
  const seeded = await request.post('/api/test/cr-scenario?cr=CR-01&entity=UNEP&month=2026-01-01');
  expect(seeded.ok()).toBeTruthy();

  const ctx = await browser.newContext({ httpCredentials: USERS.operatorUnep });
  const page = await ctx.newPage();

  // An encaissement on a contract that is not yet in the referential is held "Référence inconnue".
  const events =
    'entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n' +
    'UNEP;CLEVA;INT10-E;PREMIUM_COLLECTED;AD-NEW;C-NEW;2026-01-05;100.00\n';
  await page.goto('/imports');
  await page.getByTestId('upload-flow').selectOption('events');
  await page.getByTestId('upload-source').selectOption('CLEVA');
  await page.getByTestId('upload-file').setInputFiles({ name: 'ev.csv', mimeType: 'text/csv', buffer: Buffer.from(events, 'utf-8') });
  await page.getByTestId('upload-submit').click();
  await expect(page.getByTestId('upload-message')).toContainText('1 en attente');
  await page.goto('/evenements');
  await page.getByTestId('events-search').click();
  await expect(page.locator('[data-testid^="event-row-"]').filter({ hasText: 'INT10-E' })).toContainText('Référence inconnue');

  // The referential arrives: a coverages file creates AD-NEW / C-NEW (same product, linear 15 %).
  const coverages =
    'contract_number;coverage_code;product_code;policyholder_id;registration_date;effect_date;status;remuneration_mode;production_code;linear_rate\n' +
    'AD-NEW;C-NEW;SANTE_SELECT;PH-1;2026-01-01;2026-01-01;IN_FORCE;LINEAR;PC-CR01;0.15\n';
  await page.goto('/imports');
  await page.getByTestId('upload-flow').selectOption('coverages');
  await page.getByTestId('upload-source').selectOption('CLEVA');
  await page.getByTestId('upload-file').setInputFiles({ name: 'cov.csv', mimeType: 'text/csv', buffer: Buffer.from(coverages, 'utf-8') });
  await page.getByTestId('upload-submit').click();
  await expect(page.getByTestId('upload-message')).toContainText('1 intégrée(s)');

  // Running the calculation releases and calculates the held event on its own.
  await page.goto('/calculs');
  await page.getByTestId('calc-run').click();
  await expect(page.getByTestId('calc-message')).toContainText('Calcul lancé');

  // The event is no longer held, and its 15,00 € line is visible on the commission search.
  await page.goto('/evenements');
  await page.getByTestId('events-search').click();
  const row = page.locator('[data-testid^="event-row-"]').filter({ hasText: 'INT10-E' });
  await expect(row).not.toContainText('Référence inconnue');
  await page.goto('/recherche-commissions');
  await page.getByTestId('cs-month').selectOption('2026-01-01');
  await page.getByTestId('cs-search').click();
  await expect(page.locator('[data-testid^="cs-row-"]').filter({ hasText: 'Linéaire' }).first()).toContainText('15,00');

  await ctx.close();
});
