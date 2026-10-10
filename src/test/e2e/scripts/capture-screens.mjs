#!/usr/bin/env node
// Visual-review capture tool (ERGONOMIE §4). Drives the real dev application and
// saves a full-page screenshot of every router screen at 1440 and 1024 px wide,
// for a user who may access it, into src/test/e2e/screenshots/<width>/<name>.png
// (a git-ignored folder). Not part of the test suite — a human (or the model)
// looks at the captures. Requires the dev app running; pass its port as argv or
// E2E_PORT (default 8099).
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const outRoot = join(here, '..', 'screenshots');
const port = process.argv[2] ?? process.env.E2E_PORT ?? '8099';
const baseURL = `http://localhost:${port}`;

const AUDITOR = { username: 'auditor', password: 'auditor' };
const ADMIN = { username: 'admin-alptis', password: 'admin-alptis' };
const IT = { username: 'it-admin', password: 'it-admin' };

const SCREENS = [
  ['periods', '/', AUDITOR], ['imports', '/imports', AUDITOR], ['manualEvent', '/saisie-evenement', ADMIN],
  ['controls', '/controles', AUDITOR], ['adjustments', '/ajustements', AUDITOR], ['payment', '/paiement', AUDITOR],
  ['statements', '/bordereaux', AUDITOR], ['distributorSearch', '/distributeurs', AUDITOR],
  ['contractSearch', '/contrats', AUDITOR], ['transfers', '/cessions', AUDITOR], ['distributor', '/distributeur', AUDITOR],
  ['advance', '/escompte', AUDITOR], ['rules', '/regles', AUDITOR], ['enrollments', '/inscriptions', AUDITOR],
  ['simulation', '/simulation', AUDITOR], ['families', '/familles', AUDITOR],
  ['paramTransfer', '/parametrage-transfert', AUDITOR], ['flows', '/flux', AUDITOR], ['exports', '/exports', AUDITOR],
  ['formats', '/formats', AUDITOR], ['mapping', '/mapping', AUDITOR], ['reprise', '/reprise', AUDITOR],
  ['products', '/produits', AUDITOR], ['qualityIndicators', '/indicateurs', AUDITOR],
  ['bankAccounts', '/coordonnees-bancaires', AUDITOR], ['payeeRules', '/regles-versement', AUDITOR],
  ['externalCodes', '/codes-externes', AUDITOR], ['accounting', '/comptabilite', AUDITOR], ['cutoff', '/cutoff', AUDITOR],
  ['dashboards', '/tableaux-de-bord', AUDITOR], ['commissionSearch', '/recherche-commissions', AUDITOR],
  ['groupView', '/vision-groupe', AUDITOR], ['margin', '/marge', AUDITOR],
  ['indicators', '/indicateurs-fonctionnement', AUDITOR], ['adminEntities', '/entites', AUDITOR],
  ['adminRoleMappings', '/correspondances-roles', AUDITOR], ['adminAudit', '/audit', AUDITOR],
  ['supervision', '/supervision', AUDITOR], ['purge', '/purge', IT], ['fdvPlan', '/fdv/plan', AUDITOR],
  ['fdvValidation', '/fdv/validation', AUDITOR], ['fdvCommercial', '/fdv/commercial', AUDITOR],
];

async function main() {
  const seed = await chromium.launch();
  const seedCtx = await seed.newContext({ httpCredentials: ADMIN, baseURL });
  const reset = await seedCtx.request.post('/api/test/reset');
  const demo = await seedCtx.request.post('/api/test/demo');
  console.log(`seed: reset=${reset.status()} demo=${demo.status()}`);
  await seed.close();

  const browser = await chromium.launch();
  for (const width of [1440, 1024]) {
    mkdirSync(join(outRoot, String(width)), { recursive: true });
    for (const [name, path, creds] of SCREENS) {
      const ctx = await browser.newContext({ httpCredentials: creds, viewport: { width, height: 900 }, baseURL });
      const page = await ctx.newPage();
      try {
        await page.goto(path, { waitUntil: 'load' });
        await page.getByTestId('page-title').waitFor({ timeout: 10000 });
        await page.waitForLoadState('networkidle').catch(() => {});
        await page.screenshot({ path: join(outRoot, String(width), `${name}.png`), fullPage: true });
      } catch (e) {
        console.error(`FAILED ${name} @${width}: ${e.message}`);
      }
      await ctx.close();
    }
    console.log(`captured width ${width}`);
  }
  await browser.close();
}

main();
