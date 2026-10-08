// Presentation helpers for the payment-rule screen (REF-06): formatting beneficiary shares.

/** One beneficiary share of a payment rule, as returned by the server. */
export interface Share {
  /** Beneficiary type (PRODUCTION_CODE, DISTRIBUTOR, GROUP), or null. */
  readonly payeeType: string | null;
  /** Beneficiary business code, or null. */
  readonly payeeCode: string | null;
  /** Share as a decimal fraction string (e.g. "0.9"), or null. */
  readonly share: string | null;
}

/**
 * Formats a decimal fraction as a percentage: "0.9" becomes "90 %", "0.125" becomes "12.50 %",
 * a null fraction becomes a dash.
 */
export function formatShare(fraction: string | null): string {
  if (fraction === null) {
    return '—';
  }
  const percent = Number(fraction) * 100;
  return `${Number.isInteger(percent) ? percent : percent.toFixed(2)} %`;
}

/**
 * Builds a one-line label for a list of shares, as "TYPE CODE 90 % · TYPE CODE 10 %". An empty
 * list yields a dash.
 */
export function formatShareList(shares: readonly Share[]): string {
  if (shares.length === 0) {
    return '—';
  }
  return shares
    .map((s) => `${s.payeeType ?? '?'} ${s.payeeCode ?? '?'} ${formatShare(s.share)}`)
    .join(' · ');
}
