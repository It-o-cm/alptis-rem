// Presentation helpers for coverages (memberships). The server decides each remuneration mode and
// contract status; these maps only turn the stored codes into the French labels the operator reads
// (ADM-03), so no technical code shows on screen.

/** French labels of the remuneration modes (section 5.6, glossary). */
const REMUNERATION_MODE_LABELS: Record<string, string> = {
  LINEAR: 'Linéaire',
  UPFRONT: 'Précompté',
};

/** French labels of the membership/coverage statuses (section 5.7, glossary). */
const CONTRACT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Enregistrée',
  IN_FORCE: 'En vigueur',
  SUSPENDED: 'Mise en réduction',
  TERMINATED: 'Résiliée',
  WITHDRAWN: 'Renonciation',
  NOT_PURSUED: 'Sans suite',
  LITIGATION: 'Contentieux',
};

/** French label of a remuneration mode, a dash for none, or the raw code when unknown. */
export function remunerationModeLabel(mode: string | null): string {
  if (mode === null) return '—';
  return REMUNERATION_MODE_LABELS[mode] ?? mode;
}

/** French label of a membership status, a dash for none, or the raw code when unknown. */
export function contractStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return CONTRACT_STATUS_LABELS[status] ?? status;
}

/** The remuneration modes offered as options, with their French labels (ADM-03). */
export const REMUNERATION_MODE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = Object.entries(
  REMUNERATION_MODE_LABELS,
).map(([value, label]) => ({ value, label }));

/** The membership statuses offered as options, with their French labels (ADM-03). */
export const CONTRACT_STATUS_OPTIONS: ReadonlyArray<{ value: string; label: string }> = Object.entries(
  CONTRACT_STATUS_LABELS,
).map(([value, label]) => ({ value, label }));
