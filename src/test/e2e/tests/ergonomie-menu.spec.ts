import { test, expect, type Page } from '@playwright/test';
import { resetDb, USERS } from '../support/helpers';

// The collapsible, role-aware navigation (ERGONOMIE §2). Rubrics collapse and
// expand by mouse and keyboard, the current screen's rubric stays open, the
// state survives a reload, the rubrics open on first connection match the
// role→rubrics table, and no empty rubric is shown.
//
// @screen:menu

/** Work rubrics opened by default for each role (docs/DECISIONS.md, ERGONOMIE §2.2). */
const WORK: Record<string, string[]> = {
  BUSINESS_ADMIN: ['Cycle mensuel', 'Règles', 'Référentiels', 'Flux'],
  OPERATOR: ['Cycle mensuel', 'Distributeurs', 'Flux'],
  BUSINESS_VALIDATOR: ['Cycle mensuel'],
  FINANCE_CONTROLLER: ['Cycle mensuel', 'Comptabilité'],
  VIEWER: ['Restitutions', 'Distributeurs'],
  AUDITOR: ['Administration', 'Restitutions'],
  IT_ADMIN: ['Administration', 'Flux'],
  SALES_MANAGER: ['Forces de vente'],
  SALES_REP: ['Forces de vente'],
};

/** An end-to-end user holding each single role. */
const USER_BY_ROLE: Record<string, { username: string; password: string }> = {
  BUSINESS_ADMIN: USERS.adminAlptis,
  OPERATOR: USERS.operatorAlptis,
  BUSINESS_VALIDATOR: USERS.validatorAlptis,
  FINANCE_CONTROLLER: USERS.financeAlptis,
  VIEWER: USERS.viewerAlptis,
  AUDITOR: USERS.auditor,
  IT_ADMIN: USERS.itAdmin,
  SALES_MANAGER: USERS.managerAlptis,
  SALES_REP: USERS.salesRep,
};

/** Reads the `aria-expanded` of a rubric's toggle. */
async function expanded(page: Page, heading: string): Promise<string | null> {
  return page.getByTestId(`nav-toggle-${heading}`).getAttribute('aria-expanded');
}

test.beforeEach(async ({ request }) => {
  await resetDb(request);
});

test.describe('Menu repliable', () => {
  test.use({ httpCredentials: USERS.adminAlptis });

  test('une rubrique se replie et se déplie à la souris @screen:menu', async ({ page }) => {
    await page.goto('/regles'); // "Règles" is current; toggle the open "Référentiels" rubric.
    await expect(page.getByTestId('nav-toggle-Référentiels')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('nav-link-products')).toBeVisible();

    await page.getByTestId('nav-toggle-Référentiels').click();
    await expect(page.getByTestId('nav-toggle-Référentiels')).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('nav-link-products')).toBeHidden();

    await page.getByTestId('nav-toggle-Référentiels').click();
    await expect(page.getByTestId('nav-toggle-Référentiels')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('nav-link-products')).toBeVisible();
  });

  test('une rubrique se replie et se déplie au clavier @screen:menu', async ({ page }) => {
    await page.goto('/regles');
    const toggle = page.getByTestId('nav-toggle-Flux');
    await toggle.focus();
    await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await page.keyboard.press(' ');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });

  test('l’état d’une rubrique est conservé après rechargement @screen:menu', async ({ page }) => {
    await page.goto('/regles');
    await page.getByTestId('nav-toggle-Référentiels').click();
    await expect(page.getByTestId('nav-toggle-Référentiels')).toHaveAttribute('aria-expanded', 'false');
    await page.reload();
    await expect(page.getByTestId('nav-toggle-Référentiels')).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('nav-link-products')).toBeHidden();
  });

  test('aucune rubrique affichée n’est vide @screen:menu', async ({ page }) => {
    await page.goto('/');
    const emptyPanels = await page.evaluate(() => {
      const toggles = [...document.querySelectorAll('[data-testid^="nav-toggle-"]')];
      return toggles.filter((t) => {
        const id = t.getAttribute('aria-controls');
        const panel = id ? document.getElementById(id) : null;
        return !panel || panel.querySelectorAll('a').length === 0;
      }).length;
    });
    expect(emptyPanels).toBe(0);
    // Sanity: several rubrics are actually rendered.
    expect(await page.getByTestId('nav-toggle-Cycle mensuel').count()).toBe(1);
  });
});

test.describe('Rubrique courante toujours ouverte', () => {
  test.use({ httpCredentials: USERS.auditor });

  test('la rubrique de l’écran courant est ouverte même hors rubriques de travail @screen:menu', async ({
    page,
  }) => {
    // "Cycle mensuel" is not an auditor work rubric, but the periods screen is in it.
    await page.goto('/');
    await expect(page.getByTestId('nav-toggle-Cycle mensuel')).toHaveAttribute('aria-expanded', 'true');
    // Moving to an accounting screen opens "Comptabilité" as the current rubric.
    await page.goto('/comptabilite');
    await expect(page.getByTestId('nav-toggle-Comptabilité')).toHaveAttribute('aria-expanded', 'true');
  });
});

test.describe('Rubriques ouvertes par défaut conformes au rôle', () => {
  for (const [role, work] of Object.entries(WORK)) {
    test(`le rôle ${role} ouvre ses rubriques de travail @screen:menu`, async ({ browser }) => {
      const context = await browser.newContext({ httpCredentials: USER_BY_ROLE[role] });
      const page = await context.newPage();
      await page.goto('/');
      const headings = await page.evaluate(() =>
        [...document.querySelectorAll('[data-testid^="nav-toggle-"]')].map((t) =>
          (t.getAttribute('data-testid') as string).replace('nav-toggle-', ''),
        ),
      );
      expect(headings.length, `${role}: at least one rubric is shown`).toBeGreaterThan(0);
      for (const heading of headings) {
        const state = await expanded(page, heading);
        if (work.includes(heading)) {
          expect(state, `${role}: work rubric ${heading} open`).toBe('true');
        } else if (heading === 'Cycle mensuel') {
          // The home screen's rubric is the current one, always open.
          expect(state, `${role}: current rubric ${heading} open`).toBe('true');
        } else {
          expect(state, `${role}: non-work rubric ${heading} collapsed`).toBe('false');
        }
      }
      await context.close();
    });
  }
});
