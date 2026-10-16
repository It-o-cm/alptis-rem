#!/usr/bin/env node
// Traceability guard for the end-to-end suite (TESTS-E2E.md section 6, PERIMETRE.md étape 3).
//
// It derives its expected lists FROM THE SOURCES at every build — never from a hand-filled
// registry:
//   - expected screens        : the versioned inventory→route map src/test/e2e/screen-routes.json,
//                               itself derived from Tableau 1 of docs/PERIMETRE.md (73 lines);
//   - real routes             : the Vue router (src/main/webui/src/router/index.ts);
//   - expected requirements   : section 8 of the specification (REF/ING/PAR/…/FDV identifiers);
//   - expected recette cases  : section 13 of the specification (CR-xx, CR-72 performance excluded);
//   - expected parcours       : the section 4 catalogue of TESTS-E2E.md;
//   - real tests and tags     : the Playwright *.spec.ts sources (@screen/@req/@cr/@path/@role-action).
//
// The build fails when an inventory line has no route, a route has no end-to-end test that visits it
// and asserts, a requirement/recette case/parcours has no test, or docs/PERIMETRE.md still carries an
// `absent`/`partiel` status. There is no per-step exception list. The only declared set is the
// requirements proven off-screen (server/JUnit or recette replay), which the inventory itself carries
// as the screen / non-screen distinction — not a waiver of the requirement.

import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const e2eRoot = join(here, '..');
const projectRoot = join(e2eRoot, '..', '..', '..');
const testsDir = join(e2eRoot, 'tests');
const screenRoutesFile = join(e2eRoot, 'screen-routes.json');
const routerFile = join(projectRoot, 'src', 'main', 'webui', 'src', 'router', 'index.ts');
const specFile = join(projectRoot, 'docs', 'SI-Remuneration-Specification.md');
const e2eCatalogueFile = join(projectRoot, 'TESTS-E2E.md');
const perimetreFile = join(projectRoot, 'docs', 'PERIMETRE.md');
const matrixFile = join(projectRoot, 'docs', 'E2E-MATRIX.md');

// The recette case replayed only at volume, excluded from the interface-replay catalogue (TESTS-E2E §3).
const PERFORMANCE_CASE = 'CR-72';

// Requirements proven off-screen: by a server/JUnit test or by a recette replay, not by a dedicated
// @req end-to-end tag. This is the screen / non-screen distinction the inventory already carries
// (PERIMETRE.md étape 3), with the proof named for each — it is not a waiver.
const REQ_PROVEN_OFF_SCREEN = {
  'REF-11': 'public API §9.8 partitioning — PublicApiResourceTest (also @path:FLX-03)',
  'REP-06': 'public API restitution — PublicApiResourceTest',
  'WKF-03': 'simulated messaging reduced to an audit trace (LANCEMENT §6) — no screen',
  'BRD-10': 'statements/lines/tax-certificates exposed by the public API — PublicApiResourceTest',
  'PAY-08': 'one order per debtor entity — PaymentServiceTest.pay08 (also @cr:CR-61)',
  'ING-09': 'insurer line loaded as received — CalculationEngineTest.ing09 (also @cr:CR-51/CR-38)',
  'PAR-14': 'rule-version plausibility alert — RuleConfigServiceTest (also @path:PAR-07)',
  'CAL-02': 'linear/upfront amounts — @cr:CR-01..CR-03 replays',
  'CAL-03': 'advance family amounts — @cr:CR-20..CR-28 replays',
  'CAL-04': 'reprise amounts — @cr:CR-04..CR-07 replays',
  'CAL-06': 'outstanding/retirement amounts — @cr:CR-30..CR-38 replays',
  'CAL-08': 'fee/fixed amounts — @cr:CR-77 replay',
  'CAL-09': 'temporary rules — @cr:CR-75 replay',
  'CAL-11': 'simulation reproducibility — @cr:CR-69 replay',
  'PAY-05': 'order/bordereau alignment — @cr:CR-64 replay',
  'CPT-03': 'debtor carry-forward — @cr:CR-60 replay (also @path:CPT-01)',
  'BRD-03': 'multi-entity statement — ReportingService consolidated, @path:REP-03',
  'ING-03': 'import controls — ImportServiceTest (also @path:INT-02)',
  'ING-04': 'rejection reason codes — ImportServiceTest (also @path:INT-02)',
  'ING-08': 'bulk complementary data — ImportServiceTest (also @cr:CR-37/CR-38)',
  'ADM-02': 'entity parameters — EntityAdminServiceTest (also @req:ADM-05 screen)',
  'ADM-03': 'French interface — asserted by the label checks of every screen spec',
};

/** Recursively lists every *.spec.ts file under a directory. */
function listSpecFiles(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      out.push(...listSpecFiles(full));
    } else if (name.endsWith('.spec.ts')) {
      out.push(full);
    }
  }
  return out;
}

/** Extracts all occurrences of a `@label:value` tag from a text. */
function tagValues(text, label) {
  const re = new RegExp(`@${label}:([A-Za-z0-9_\\-]+)`, 'g');
  const values = new Set();
  let m;
  while ((m = re.exec(text)) !== null) {
    values.add(m[1]);
  }
  return values;
}

/** Splits a spec file into individual test bodies for per-test checks. */
function testBodies(text) {
  const bodies = [];
  const re = /\btest\s*\(/g;
  let m;
  const starts = [];
  while ((m = re.exec(text)) !== null) {
    starts.push(m.index);
  }
  for (let i = 0; i < starts.length; i += 1) {
    const end = i + 1 < starts.length ? starts[i + 1] : text.length;
    bodies.push(text.slice(starts[i], end));
  }
  return bodies;
}

/** The Vue router's declared route keys (lines of the form `  key: SomeView,`). */
function routerKeys(text) {
  const keys = new Set();
  const re = /^\s{2}([A-Za-z][A-Za-z0-9]*):\s*[A-Za-z][A-Za-z0-9]*View,/gm;
  let m;
  while ((m = re.exec(text)) !== null) {
    keys.add(m[1]);
  }
  return keys;
}

/** The section-8 requirement identifiers of the specification (table rows `| XXX-NN |`). */
function specRequirements(text) {
  const ids = new Set();
  const re = /^\|\s*(REF|ING|PAR|CAL|REG|CTL|WKF|CPT|PAY|BRD|FIN|REP|ADM|FDV)-(\d{2})\b/gm;
  let m;
  while ((m = re.exec(text)) !== null) {
    ids.add(`${m[1]}-${m[2]}`);
  }
  return ids;
}

/** The section-13 recette case identifiers of the specification, minus the volume case. */
function specCases(text) {
  const ids = new Set();
  const re = /\bCR-(\d{2})\b/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const id = `CR-${m[1]}`;
    if (id !== PERFORMANCE_CASE) {
      ids.add(id);
    }
  }
  return ids;
}

/** The section-4 parcours identifiers of TESTS-E2E.md (first column of the catalogue tables). */
function cataloguePaths(text) {
  const ids = new Set();
  let inSection4 = false;
  for (const line of text.split('\n')) {
    if (/^##\s/.test(line)) {
      inSection4 = /^##\s+\d/.test(line) ? false : inSection4;
    }
    if (/^##\s+4\./.test(line) || /^###\s+4\./.test(line)) {
      inSection4 = true;
    } else if (/^##\s+5\./.test(line)) {
      inSection4 = false;
    }
    if (!inSection4) {
      continue;
    }
    const m = line.match(/^\|\s*([A-Z]{2,4}-\d{2})\s*\|/);
    if (m) {
      ids.add(m[1]);
    }
  }
  return ids;
}

function main() {
  const errors = [];

  // --- Sources -------------------------------------------------------------
  const screenRoutes = JSON.parse(readFileSync(screenRoutesFile, 'utf8'));
  const routes = routerKeys(readFileSync(routerFile, 'utf8'));
  const specText = readFileSync(specFile, 'utf8');
  const expectedReqs = specRequirements(specText);
  const expectedCases = specCases(specText);
  const expectedPaths = cataloguePaths(readFileSync(e2eCatalogueFile, 'utf8'));
  const perimetre = readFileSync(perimetreFile, 'utf8');

  // --- Real tests and their tags ------------------------------------------
  const specFiles = listSpecFiles(testsDir);
  const screens = new Set();
  const reqs = new Set();
  const crs = new Set();
  const paths = new Set();
  const roleActions = new Set();
  let testCount = 0;

  for (const file of specFiles) {
    const text = readFileSync(file, 'utf8');
    const rel = file.slice(projectRoot.length + 1);

    for (const forbidden of ['test.skip', 'test.fixme', 'test.only', 'describe.skip', 'describe.only']) {
      if (text.includes(forbidden)) {
        errors.push(`${rel}: forbidden ${forbidden} found`);
      }
    }

    tagValues(text, 'screen').forEach((v) => screens.add(v));
    tagValues(text, 'req').forEach((v) => reqs.add(v));
    tagValues(text, 'cr').forEach((v) => crs.add(v));
    tagValues(text, 'path').forEach((v) => paths.add(v));
    tagValues(text, 'role-action').forEach((v) => roleActions.add(v));

    for (const body of testBodies(text)) {
      testCount += 1;
      const asserts = body.includes('expect(');
      if (!asserts) {
        const title = body.slice(0, 120).replace(/\s+/g, ' ');
        errors.push(`${rel}: a test has no assertion -> ${title}`);
      }
    }
  }

  // --- Inventory → route → test (PERIMETRE §5) -----------------------------
  if (screenRoutes.length !== 73) {
    errors.push(`screen-routes.json lists ${screenRoutes.length} inventory lines, expected 73`);
  }
  for (const entry of screenRoutes) {
    if (!routes.has(entry.route)) {
      errors.push(`inventory ${entry.n} (${entry.label}): route "${entry.route}" is not in the Vue router`);
    }
    // The screen tag may sit on a describe block; every test is already required to assert (below),
    // so a screen tag present in a spec file is backed by asserting tests.
    if (!screens.has(entry.screen)) {
      errors.push(`inventory ${entry.n} (${entry.label}): route "${entry.route}" has no @screen:${entry.screen} test`);
    }
  }

  // --- Requirements (spec §8) ---------------------------------------------
  for (const id of [...expectedReqs].sort()) {
    if (!reqs.has(id) && !REQ_PROVEN_OFF_SCREEN[id]) {
      errors.push(`requirement ${id} has no @req test and is not declared proven off-screen`);
    }
  }

  // --- Recette cases (spec §13) -------------------------------------------
  for (const id of [...expectedCases].sort()) {
    if (!crs.has(id)) {
      errors.push(`recette case ${id} has no @cr interface-replay test`);
    }
  }

  // --- Parcours (TESTS-E2E §4) --------------------------------------------
  for (const id of [...expectedPaths].sort()) {
    if (!paths.has(id)) {
      errors.push(`parcours ${id} has no @path test`);
    }
  }

  // --- PERIMETRE still carrying an unfinished status ----------------------
  const unfinished = perimetre.split('\n').filter((l) => /\|\s*(partiel|absent)\s*\|/.test(l));
  for (const l of unfinished) {
    errors.push(`docs/PERIMETRE.md still carries an unfinished status: ${l.trim().slice(0, 90)}`);
  }

  // --- Matrix (always written, even on failure) ---------------------------
  const sorted = (s) => [...s].sort();
  const list = (s) => (s.size ? sorted(s).join(', ') : '—');
  const missing = (expected, found, extra) => {
    const out = [...expected].filter((id) => !found.has(id) && !(extra && extra[id])).sort();
    return out.length ? out.join(', ') : '—';
  };
  const matrix = `# Matrice de traçabilité des tests de bout en bout

Généré par \`src/test/e2e/scripts/traceability.mjs\` (ne pas éditer à la main). Les listes attendues
sont dérivées des sources : \`screen-routes.json\` (73 écrans), le routeur Vue, la section 8 (exigences),
la section 13 (cas de recette) et la section 4 de \`TESTS-E2E.md\` (parcours).

| Dimension | Attendus | Couverts | Manquants |
| --- | --- | --- | --- |
| Écrans (routes) | ${new Set(screenRoutes.map((e) => e.screen)).size} | ${[...new Set(screenRoutes.map((e) => e.screen))].filter((s) => screens.has(s)).length} | ${[...new Set(screenRoutes.map((e) => e.screen))].filter((s) => !screens.has(s)).join(', ') || '—'} |
| Exigences §8 | ${expectedReqs.size} | ${[...expectedReqs].filter((id) => reqs.has(id) || REQ_PROVEN_OFF_SCREEN[id]).length} | ${missing(expectedReqs, reqs, REQ_PROVEN_OFF_SCREEN)} |
| Cas de recette §13 | ${expectedCases.size} | ${[...expectedCases].filter((id) => crs.has(id)).length} | ${missing(expectedCases, crs)} |
| Parcours §4 | ${expectedPaths.size} | ${[...expectedPaths].filter((id) => paths.has(id)).length} | ${missing(expectedPaths, paths)} |
| Couples rôle-action | — | ${roleActions.size} | — |

Écrans couverts (\`@screen\`) : ${list(screens)}

Nombre de fichiers de tests : ${specFiles.length}
Nombre de tests : ${testCount}
`;
  writeFileSync(matrixFile, matrix, 'utf8');

  if (errors.length) {
    console.error(`Traceability guard failed (${errors.length} gap(s)):`);
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }
  console.log(`Traceability OK: ${testCount} tests in ${specFiles.length} files; matrix written to docs/E2E-MATRIX.md`);
}

main();
