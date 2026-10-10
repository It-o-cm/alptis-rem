import { test, expect, type Page } from '@playwright/test';
import { loadDemo, USERS } from '../support/helpers';
import { SCREENS, type Screen } from '../support/screens';

// Generic alignment sweep over every router screen (ERGONOMIE §5): at 1440 and
// 1024 pixels wide, in each form and each fiche every label shares a left edge
// and every control shares a left edge (one or two aligned columns), amount
// cells are right-aligned, and the page never scrolls horizontally.
//
// @screen:alignment

/** Groups the screens by the end-to-end user that opens them. */
function byUser(): Map<keyof typeof USERS, Screen[]> {
  const groups = new Map<keyof typeof USERS, Screen[]>();
  for (const screen of SCREENS) {
    const list = groups.get(screen.user) ?? [];
    list.push(screen);
    groups.set(screen.user, list);
  }
  return groups;
}

/** Number of distinct left-edge clusters in a list of x positions (2px tolerance). */
function clusterCount(xs: number[]): number {
  const sorted = [...xs].sort((a, b) => a - b);
  let count = 0;
  let last = Number.NEGATIVE_INFINITY;
  for (const x of sorted) {
    if (x - last > 2) {
      count += 1;
      last = x;
    }
  }
  return count;
}

interface Group {
  labels: number[];
  controls: number[];
}

/** Reads the label/control left edges of every form and every fiche on the page. */
async function readGroups(page: Page): Promise<{ forms: Group[]; fiches: Group[]; numAligns: string[] }> {
  return page.evaluate(() => {
    const left = (e: Element): number => Math.round(e.getBoundingClientRect().left);
    const read = (container: Element, labelSel: string, controlSel: string) => ({
      labels: [...container.querySelectorAll(labelSel)].map(left),
      controls: [...container.querySelectorAll(controlSel)].map(left),
    });
    const forms = [...document.querySelectorAll('[data-ui="form"]')].map((f) =>
      read(f, '[data-ui="field-label"]', '[data-ui="field-control"]'),
    );
    const fiches = [...document.querySelectorAll('[data-ui="fiche"]')].map((f) =>
      read(f, '[data-ui="fiche-label"]', '[data-ui="fiche-value"]'),
    );
    const numAligns = [...document.querySelectorAll('td.num, th.num')].map(
      (e) => getComputedStyle(e).textAlign,
    );
    return { forms, fiches, numAligns };
  });
}

/**
 * Opens every screen at the given width and asserts the layout invariants.
 * @param browser Playwright browser
 * @param width viewport width in pixels
 */
async function sweep(
  browser: import('@playwright/test').Browser,
  width: number,
): Promise<void> {
  for (const [userKey, screens] of byUser()) {
    const context = await browser.newContext({
      httpCredentials: USERS[userKey],
      viewport: { width, height: 900 },
    });
    const page = await context.newPage();
    for (const screen of screens) {
      await page.goto(screen.path);
      await page.getByTestId('page-title').waitFor();
      await page.waitForLoadState('networkidle');

      // Poll until the layout has settled: wide tables scroll inside their own
      // container, so the page itself must never scroll horizontally.
      await expect
        .poll(
          () => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth),
          { message: `${screen.name} @${width}: horizontal page scroll`, timeout: 4000 },
        )
        .toBeLessThanOrEqual(1);

      const { forms, fiches, numAligns } = await readGroups(page);
      for (const form of forms) {
        if (form.labels.length === 0) continue;
        const labelCols = clusterCount(form.labels);
        const controlCols = clusterCount(form.controls);
        expect(labelCols, `${screen.name} @${width}: label columns`).toBeLessThanOrEqual(2);
        expect(controlCols, `${screen.name} @${width}: control columns`).toBe(labelCols);
      }
      for (const fiche of fiches) {
        if (fiche.labels.length === 0) continue;
        const labelCols = clusterCount(fiche.labels);
        const valueCols = clusterCount(fiche.controls);
        expect(labelCols, `${screen.name} @${width}: fiche label columns`).toBeLessThanOrEqual(2);
        expect(valueCols, `${screen.name} @${width}: fiche value columns`).toBe(labelCols);
      }
      expect(
        numAligns.every((a) => a === 'right'),
        `${screen.name} @${width}: a numeric cell is not right-aligned`,
      ).toBeTruthy();
    }
    await context.close();
  }
}

test.describe('Alignement de tous les écrans', () => {
  test.beforeEach(async ({ request }) => {
    await loadDemo(request);
  });

  test('aligné et sans défilement horizontal en 1440 @screen:alignment', async ({ browser }) => {
    await sweep(browser, 1440);
  });

  test('aligné et sans défilement horizontal en 1024 @screen:alignment', async ({ browser }) => {
    await sweep(browser, 1024);
  });
});
