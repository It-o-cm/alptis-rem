// Presentation helpers for accounting months. The server names a month by its ISO prefix
// (`2026-09`); the interface never shows that raw code (ADM-03) but the French month and year
// (`septembre 2026`). These functions only format already-decided values for display.

const EMPTY = '—';

/** French month names, index 0 = January. */
const MONTH_NAMES = [
  'janvier',
  'février',
  'mars',
  'avril',
  'mai',
  'juin',
  'juillet',
  'août',
  'septembre',
  'octobre',
  'novembre',
  'décembre',
];

/**
 * Formats an ISO month (`2026-09`) as a French month and year (`septembre 2026`).
 * Returns a dash for a missing or unparseable value.
 */
export function monthLabel(month: string | null | undefined): string {
  if (!month) {
    return EMPTY;
  }
  const match = /^(\d{4})-(\d{2})$/.exec(month);
  if (!match) {
    return EMPTY;
  }
  const year = match[1];
  const index = Number(match[2]) - 1;
  if (index < 0 || index >= MONTH_NAMES.length) {
    return EMPTY;
  }
  return `${MONTH_NAMES[index]} ${year}`;
}
