// Presentation helpers for the calculation-executions screen. The engine decides each run's type and
// status; these maps only turn the stored codes into the French labels the operator reads (ADM-03).

/** French labels of the run types (section 5.7). */
const RUN_TYPE_LABELS: Record<string, string> = {
  FORECAST: 'Prévisionnel',
  ACTUAL: 'Réel',
  SIMULATION: 'Simulation',
};

/** French labels of the run statuses. */
const RUN_STATUS_LABELS: Record<string, string> = {
  RUNNING: 'En cours',
  COMPLETED: 'Terminé',
  FAILED: 'En échec',
};

/** The partial-recalc scope kinds offered, with their labels. */
export const RECALC_SCOPES: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'DISTRIBUTOR', label: 'Distributeur' },
  { value: 'PRODUCT', label: 'Produit' },
  { value: 'RULE', label: 'Règle' },
];

/** French label of a run type, or the raw code when unknown. */
export function runTypeLabel(type: string): string {
  return RUN_TYPE_LABELS[type] ?? type;
}

/** French label of a run status, or the raw code when unknown. */
export function runStatusLabel(status: string): string {
  return RUN_STATUS_LABELS[status] ?? status;
}

/** Tag CSS class for a run status. */
export function runStatusTag(status: string): string {
  if (status === 'COMPLETED') return 'tag ok';
  if (status === 'FAILED') return 'tag ko';
  return 'tag mute';
}
