// French-locale presentation helpers. The server computes every amount and rate;
// these functions only format already-decided values for display.

const EMPTY = '—';

const amountFormatter = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const percentFormatter = new Intl.NumberFormat('fr-FR', {
  style: 'percent',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/**
 * Formats a monetary amount in euros, French style (e.g. `1 912 480,10 €`).
 * Returns a dash for a missing value.
 */
export function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return EMPTY;
  }
  return amountFormatter.format(value);
}

/**
 * Formats a rate stored as a decimal fraction (0.15) as a French percentage (`15 %`).
 * Returns a dash for a missing value.
 */
export function formatPercent(fraction: number | null | undefined): string {
  if (fraction === null || fraction === undefined) {
    return EMPTY;
  }
  return percentFormatter.format(fraction);
}

/**
 * Formats an ISO date (`2026-09-15`) as a French date (`15/09/2026`).
 * Returns a dash for a missing or unparseable value.
 */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return EMPTY;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return EMPTY;
  }
  const day = String(date.getUTCDate()).padStart(2, '0');
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const year = date.getUTCFullYear();
  return `${day}/${month}/${year}`;
}
