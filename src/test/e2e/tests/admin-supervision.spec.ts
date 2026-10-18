import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resetDb, seedCycle, USERS } from '../support/helpers';

// Per-screen tests for the technical administration: supervision of treatments and flows (OUT-07,
// NFR-16) and purge/anonymisation of personal data (ADM-06), reserved to the technical administrator.
//
// @screen:supervision @screen:purge @req:ADM-06

test.beforeEach(async ({ request }) => {
  await resetDb(request);
  await seedCycle(request);
});

test('l’administrateur technique lit la supervision @screen:supervision @req:ADM-06', async ({
  browser,
}) => {
  const itAdmin = await browser.newContext({ httpCredentials: USERS.itAdmin });
  const page = await itAdmin.newPage();
  await page.goto('/supervision');
  // ADM-03: the health status reads as its French label, never the raw code.
  await expect(page.getByTestId('sup-status')).toHaveText('Opérationnel');
  await expect(page.getByTestId('sup-pending')).toBeVisible();
  await itAdmin.close();
});

test('ADM-04 : un traitement en échec est visible et relançable @screen:supervision @req:ADM-06 @path:ADM-04 @role-action:IT_ADMIN-relaunch-treatment', async ({
  browser,
  request,
}) => {
  expect(
    (await request.post('/api/test/cr-scenario?cr=ADM-04&entity=UNEP&month=2027-07-01')).ok(),
  ).toBeTruthy();

  const itAdmin = await browser.newContext({ httpCredentials: USERS.itAdmin });
  const page = await itAdmin.newPage();
  await page.goto('/supervision');

  // The failed calculation run is visible as a failed treatment.
  const row = page.getByTestId('failed-row-UNEP-2027-07-01');
  await expect(row).toBeVisible();
  await expect(row).toContainText('UNEP');

  // Relaunching re-runs the period's calculation; the treatment then drops off the failed list.
  await page.getByTestId('failed-relaunch-UNEP-2027-07-01').click();
  await expect(page.getByTestId('sup-message')).toContainText('Traitement relancé');
  await expect(page.getByTestId('failed-row-UNEP-2027-07-01')).toHaveCount(0);
  await expect(page.getByTestId('sup-no-failed')).toBeVisible();
  await itAdmin.close();
});

// Parcours ADM-03 (section 4.8): at the retention deadline the personal data is anonymised while the
// amounts are conserved. The cycle seed plants a long-ended policyholder, so the purge has someone to
// anonymise; the commission amounts stay the same across the anonymisation.
test('l’administrateur technique lance la purge des données personnelles @screen:purge @req:ADM-06 @role-action:IT_ADMIN-purge @path:ADM-03', async ({
  browser,
}) => {
  const itAdmin = await browser.newContext({ httpCredentials: USERS.itAdmin });
  const page = await itAdmin.newPage();
  await page.goto('/purge');
  // At least one record is eligible for anonymisation (the long-ended policyholder).
  await expect(page.getByTestId('purge-eligible')).not.toHaveText('—');
  await expect(page.getByTestId('purge-eligible')).not.toHaveText('0');

  // Personal data anonymised: the purge anonymises at least one record and reports the preserved
  // commission total.
  await page.getByTestId('purge-run').click();
  await expect(page.getByTestId('purge-message')).toContainText(/[1-9]\d* enregistrement\(s\) anonymisé/);
  await expect(page.getByTestId('purge-message')).toContainText('Montants conservés :');
  const firstMessage = await page.getByTestId('purge-message').textContent();
  const preserved = firstMessage?.match(/Montants conservés : (.+)\./)?.[1];
  expect(preserved, firstMessage ?? '').toBeTruthy();
  expect(preserved).not.toEqual('—');

  // Idempotent: nothing is left to anonymise, and the preserved amount is unchanged — the amounts
  // were conserved through the anonymisation.
  await expect(page.getByTestId('purge-eligible')).toHaveText('0');
  await page.getByTestId('purge-run').click();
  await expect(page.getByTestId('purge-message')).toContainText('0 enregistrement(s) anonymisé(s)');
  await expect(page.getByTestId('purge-message')).toContainText(`Montants conservés : ${preserved}.`);
  await itAdmin.close();
});

test.describe('Supervision — accessibilité', () => {
  test.use({ httpCredentials: USERS.itAdmin });

  test('aucune violation d’accessibilité grave ou critique @screen:supervision', async ({ page }) => {
    await page.goto('/supervision');
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  });
});
