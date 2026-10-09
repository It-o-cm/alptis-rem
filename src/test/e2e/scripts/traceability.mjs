#!/usr/bin/env node
// Traceability guard for the end-to-end suite (TESTS-E2E.md, section 6).
//
// It scans the Playwright test sources for the labels every test carries
// (@screen:, @req:, @cr:, @path:, @role-action:), writes docs/E2E-MATRIX.md, and
// fails the build when a solidity rule is broken. The per-step inventory, recette,
// parcours and role-action requirements are registered in REQUIREMENTS_BY_STEP as
// each step delivers its screens; the always-on solidity checks below apply from
// step 0 onward.

import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const e2eRoot = join(here, '..');
const projectRoot = join(e2eRoot, '..', '..', '..');
const testsDir = join(e2eRoot, 'tests');
const planFile = join(projectRoot, 'PLAN.md');
const matrixFile = join(projectRoot, 'docs', 'E2E-MATRIX.md');

/**
 * Required coverage per plan step, filled in as screens are delivered.
 * Shape: { stepNumber: { screens: [], reqs: [], crs: [], paths: [], roleActions: [] } }.
 * Step 0 delivers the common frame only; the inventory assigns its screens to later steps.
 */
const REQUIREMENTS_BY_STEP = {
  0: { screens: [], reqs: [], crs: [], paths: [], roleActions: [] },
  // Step 2 delivers the entity-parameters, role-mapping and audit screens. CR-71's interface
  // replay is delivered with the rule-version approval screen at step 4 (see docs/DECISIONS.md);
  // its mechanism is covered by a unit test at step 2.
  2: {
    screens: ['entities', 'role-mappings', 'audit'],
    reqs: ['REF-01', 'ADM-05', 'ADM-04'],
    crs: ['CR-70'],
    paths: ['ADM-01'],
    roleActions: [],
  },
  // Step 3 delivers the integration and referential screens. The commission-engine recette cases
  // (CR-37/38/67) are verified by server unit tests until the engine screens arrive; CR-68
  // (reimport refused) is observable at the imports screen.
  3: {
    screens: [
      'imports', 'manualEvent', 'mapping', 'distributor-search', 'production-hierarchy',
      'contract-search', 'products', 'quality-indicators', 'bank-accounts', 'transfers',
      'payee-rules', 'external-codes',
    ],
    reqs: [
      'ING-01', 'ING-02', 'ING-05', 'ING-07', 'REF-01', 'REF-02', 'REF-03', 'REF-04', 'REF-05',
      'REF-06', 'REF-07', 'REF-09',
    ],
    crs: ['CR-68'],
    paths: ['INT-11', 'INT-13'],
    roleActions: [],
  },
  // Step 4 delivers the rule-configuration screens: plans/rules/versions and their approval cycle,
  // the calculation-family forms, the discount conventions and the enrollments to reserved rules.
  4: {
    screens: ['rules', 'families', 'enrollments', 'advance'],
    reqs: ['PAR-01', 'PAR-02', 'PAR-03', 'PAR-04', 'PAR-05', 'PAR-09', 'PAR-10', 'PAR-12'],
    crs: [],
    paths: ['PAR-01'],
    roleActions: [],
  },
  // Step 9 delivers the cycle screens: period advancement and transitions, controls and anomalies
  // with their catalogue, adjustments with the two-signature approval, and the distributor account
  // with compensation and block. CR-60/61/63 (account arithmetic) are covered by server unit tests
  // (AccountServiceTest), as noted in docs/DECISIONS.md.
  9: {
    screens: ['periods', 'controls', 'adjustments', 'distributor-account'],
    reqs: [
      'WKF-01', 'WKF-04', 'CTL-01', 'CTL-02', 'CTL-05',
      'REG-01', 'REG-03', 'CPT-02', 'CPT-04', 'CPT-05', 'CPT-06', 'CPT-07',
    ],
    crs: [],
    paths: [],
    roleActions: [
      'OPERATOR-advance-period', 'OPERATOR-decide-anomaly', 'OPERATOR-submit-adjustment',
      'BUSINESS_ADMIN-approve-adjustment', 'OPERATOR-compensate-accounts', 'OPERATOR-block-account',
    ],
  },
  // Step 10 delivers the payment screen (cycle lot, preparation, Finance validation with the SEPA
  // file, bank result, off-cycle lot) and the bordereaux screen (rubric preview, spreadsheet export,
  // publication and rectificative version). CR-62/64/85/86 (lot alignment, ORIAS release, bank
  // rejection, period PAID) are covered by server unit tests (PaymentServiceTest), as noted in
  // docs/DECISIONS.md.
  10: {
    screens: ['payment', 'statements'],
    reqs: ['PAY-01', 'PAY-02', 'PAY-03', 'PAY-06', 'PAY-07', 'BRD-01', 'BRD-02', 'BRD-06', 'BRD-07', 'BRD-12'],
    crs: [],
    paths: [],
    roleActions: [
      'OPERATOR-prepare-lot', 'FINANCE_CONTROLLER-validate-lot', 'FINANCE_CONTROLLER-record-bank-result',
      'OPERATOR-create-off-cycle', 'OPERATOR-publish-statement', 'OPERATOR-rectify-statement',
    ],
  },
  // Step 11 delivers the accounting and flow screens: the accounting schema and extraction
  // (FIN-02/03/05), the cut-off pilotage (FIN-04/09), the flow catalogue (ING-10), the exchange-format
  // reference and the export journal with its on-demand production (REP-04). The accounting cycle
  // (CR-65/66, FIN-01) and the public §9.8 API isolation (CR-70) are covered by server unit tests
  // (AccountingServiceTest, PublicApiResourceTest), as noted in docs/DECISIONS.md.
  11: {
    screens: ['accounting', 'cutoff', 'flows', 'formats', 'exports'],
    reqs: ['FIN-02', 'FIN-03', 'FIN-04', 'FIN-05', 'FIN-09', 'ING-10', 'REP-04'],
    crs: [],
    paths: [],
    roleActions: ['OPERATOR-extract-accounting', 'OPERATOR-produce-export'],
  },
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

function main() {
  const specFiles = listSpecFiles(testsDir);
  const errors = [];
  const screens = new Set();
  const reqs = new Set();
  const crs = new Set();
  const paths = new Set();
  const roleActions = new Set();
  let testCount = 0;

  for (const file of specFiles) {
    const text = readFileSync(file, 'utf8');
    const rel = file.slice(projectRoot.length + 1);

    // Solidity: no disabled or focused tests.
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

    // Solidity: every test asserts something.
    for (const body of testBodies(text)) {
      testCount += 1;
      if (!body.includes('expect(')) {
        const title = body.slice(0, 120).replace(/\s+/g, ' ');
        errors.push(`${rel}: a test has no assertion -> ${title}`);
      }
    }
  }

  // Checked steps in PLAN.md (rows of the form `| 0 | ... | [x] | ... |`).
  const planText = readFileSync(planFile, 'utf8');
  const checkedSteps = [];
  for (const line of planText.split('\n')) {
    const cell = line.match(/^\|\s*(\d+)\s*\|.*\|\s*\[x\]\s*\|/i);
    if (cell) {
      checkedSteps.push(Number(cell[1]));
    }
  }

  // Per-step requirement gaps (only for registered requirements).
  for (const step of checkedSteps) {
    const req = REQUIREMENTS_BY_STEP[step];
    if (!req) {
      continue;
    }
    const check = (kind, required, found) => {
      for (const item of required) {
        if (!found.has(item)) {
          errors.push(`step ${step}: missing ${kind} test for ${item}`);
        }
      }
    };
    check('screen', req.screens, screens);
    check('req', req.reqs, reqs);
    check('cr', req.crs, crs);
    check('path', req.paths, paths);
    check('role-action', req.roleActions, roleActions);
  }

  const sorted = (s) => [...s].sort();
  const list = (s) => (s.size ? sorted(s).join(', ') : '—');
  const matrix = `# Matrice de traçabilité des tests de bout en bout

Généré par \`src/test/e2e/scripts/traceability.mjs\` (ne pas éditer à la main).

Étapes cochées dans \`PLAN.md\` : ${checkedSteps.length ? checkedSteps.join(', ') : '—'}

| Dimension | Couverts |
| --- | --- |
| Écrans (\`@screen\`) | ${list(screens)} |
| Exigences (\`@req\`) | ${list(reqs)} |
| Cas de recette (\`@cr\`) | ${list(crs)} |
| Parcours (\`@path\`) | ${list(paths)} |
| Couples rôle-action (\`@role-action\`) | ${list(roleActions)} |

Nombre de fichiers de tests : ${specFiles.length}
Nombre de tests : ${testCount}
`;
  writeFileSync(matrixFile, matrix, 'utf8');

  if (errors.length) {
    console.error('Traceability guard failed:');
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }
  console.log(`Traceability OK: ${testCount} tests in ${specFiles.length} files; matrix written to docs/E2E-MATRIX.md`);
}

main();
