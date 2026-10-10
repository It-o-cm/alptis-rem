#!/usr/bin/env node
// Build guard for the ergonomics pass (ERGONOMIE §5). It fails the build when:
//   1. a view or a component carries a `style` attribute, outside the named
//      exceptions (the SVG charts, whose geometry is computed at runtime);
//   2. a view uses a naked `<label>`, `<table>` or `<button>` instead of the
//      common socle component;
//   3. a router screen is not covered by the alignment end-to-end test
//      (every navigation entry must appear in the alignment screens manifest).
// Pure static analysis, no running application, wired into `mvn verify`.

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, relative } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(here, '..', '..', '..', '..');
const webuiSrc = join(projectRoot, 'src', 'main', 'webui', 'src');
const viewsDir = join(webuiSrc, 'views');
const componentsDir = join(webuiSrc, 'components');
const navFile = join(webuiSrc, 'presentation', 'navigation.ts');
const screensManifest = join(projectRoot, 'src', 'test', 'e2e', 'support', 'screens.ts');

// Named exceptions allowed to carry a style attribute (runtime-computed geometry).
const STYLE_EXCEPTIONS = [join(componentsDir, 'charts')];

/** Recursively lists every .vue file under a directory. */
function listVue(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      out.push(...listVue(full));
    } else if (name.endsWith('.vue')) {
      out.push(full);
    }
  }
  return out;
}

/** Whether a file sits under one of the named style exceptions. */
function isStyleException(file) {
  return STYLE_EXCEPTIONS.some((dir) => file.startsWith(dir));
}

/** Reports the 1-based line numbers where a regex matches a text. */
function matchingLines(text, regex) {
  const lines = [];
  text.split('\n').forEach((line, index) => {
    if (regex.test(line)) {
      lines.push(index + 1);
    }
  });
  return lines;
}

const errors = [];

// 1 + 2: static scan of views and components.
const styleAttr = /\s(?:v-bind:style|:style|style)\s*=/;
const nakedLabel = /<label[\s>/]/;
const nakedTable = /<table[\s>/]/;
const nakedButton = /<button[\s>/]/;

for (const file of listVue(componentsDir).concat(listVue(viewsDir))) {
  const text = readFileSync(file, 'utf8');
  const rel = relative(projectRoot, file);
  const isView = file.startsWith(viewsDir);

  if (!isStyleException(file)) {
    for (const line of matchingLines(text, styleAttr)) {
      errors.push(`${rel}:${line}: style attribute forbidden (use the common tokens / scoped styles)`);
    }
  }

  if (isView) {
    for (const [name, regex] of [
      ['<label>', nakedLabel],
      ['<table>', nakedTable],
      ['<button>', nakedButton],
    ]) {
      for (const line of matchingLines(text, regex)) {
        errors.push(`${rel}:${line}: naked ${name} forbidden in a view (use the common socle component)`);
      }
    }
  }
}

// 3: every router screen is covered by the alignment test.
/** Extracts the quoted values of a `key: '...'` field from a text. */
function quotedField(text, key) {
  const re = new RegExp(`${key}:\\s*'([^']+)'`, 'g');
  const out = new Set();
  let m;
  while ((m = re.exec(text)) !== null) {
    out.add(m[1]);
  }
  return out;
}

const routerScreens = quotedField(readFileSync(navFile, 'utf8'), 'name');
let coveredScreens;
try {
  coveredScreens = quotedField(readFileSync(screensManifest, 'utf8'), 'name');
} catch {
  errors.push(`missing alignment screens manifest: ${relative(projectRoot, screensManifest)}`);
  coveredScreens = new Set();
}
for (const screen of routerScreens) {
  if (!coveredScreens.has(screen)) {
    errors.push(`router screen "${screen}" is not covered by the alignment test (add it to support/screens.ts)`);
  }
}

if (errors.length) {
  console.error('UX guard failed:');
  for (const e of errors) {
    console.error(`  - ${e}`);
  }
  process.exit(1);
}
console.log(`UX guard OK: ${routerScreens.size} screens covered, no forbidden style attribute or naked element.`);
