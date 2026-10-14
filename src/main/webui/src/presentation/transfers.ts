// Presentation helper for portfolio transfers and replacement orders. The server decides each
// transfer's status; this map only turns the stored code into the French label the operator reads
// (ADM-03), so no technical code shows on screen.

/** French labels of the portfolio-transfer statuses (section 5.4). */
const TRANSFER_STATUS_LABELS: Record<string, string> = {
  PENDING: 'En attente',
  PROCESSED: 'Traité',
};

/** French label of a transfer status, a dash for none, or the raw code when unknown. */
export function transferStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return TRANSFER_STATUS_LABELS[status] ?? status;
}
