// Presentation helpers for discount (escompte) agreements and positions. The server decides each
// agreement status and advance-position status; these maps only turn the stored codes into the
// French labels the operator reads (ADM-03), so no technical code shows on screen.

/** French labels of the discount-agreement statuses (section 5.4). */
const AGREEMENT_STATUS_LABELS: Record<string, string> = {
  VALID: 'Valide',
  SUSPENDED: 'Suspendue',
  CLOSED: 'Clôturée',
};

/** French labels of the advance (escompte) position statuses (section 6.4). */
const ADVANCE_STATUS_LABELS: Record<string, string> = {
  PENDING: 'En attente',
  ACTIVE: 'Active',
  SETTLED: 'Apurée',
  CANCELLED: 'Annulée',
};

/** French label of a discount-agreement status, a dash for none, or the raw code when unknown. */
export function advanceAgreementStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return AGREEMENT_STATUS_LABELS[status] ?? status;
}

/** French label of an advance-position status, a dash for none, or the raw code when unknown. */
export function advanceStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return ADVANCE_STATUS_LABELS[status] ?? status;
}
