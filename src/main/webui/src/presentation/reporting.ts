// Presentation helpers for the accounting and flow screens (step 11). The server computes every
// figure and state; these functions only label already-decided values.

/** French labels of the cut-off reading mailles (section 10.4). */
const VIEW_LABELS: Record<string, string> = {
  COVERAGE: 'Garantie',
  DISTRIBUTOR: 'Distributeur',
  PRODUCT: 'Produit',
  ENTITY: 'Entité',
};

/** French labels of the flow directions. */
const DIRECTION_LABELS: Record<string, string> = {
  INBOUND: 'Entrant',
  OUTBOUND: 'Sortant',
};

/** French labels of the export journal statuses. */
const EXPORT_STATUS_LABELS: Record<string, string> = {
  GENERATED: 'Généré',
  DELIVERED: 'Déposé',
  FAILED: 'Échec',
};

/** French label of a cut-off maille, or the raw value when unknown. */
export function cutoffViewLabel(view: string): string {
  return VIEW_LABELS[view] ?? view;
}

/** French label of a flow direction, or the raw value when unknown. */
export function flowDirectionLabel(direction: string): string {
  return DIRECTION_LABELS[direction] ?? direction;
}

/** French label of an export status, or the raw value when unknown. */
export function exportStatusLabel(status: string): string {
  return EXPORT_STATUS_LABELS[status] ?? status;
}

/** Tells whether a user holding the given roles may produce an export or trigger an extraction. */
export function canProduceExport(roles: readonly string[]): boolean {
  return (
    roles.includes('OPERATOR') ||
    roles.includes('BUSINESS_ADMIN') ||
    roles.includes('FINANCE_CONTROLLER')
  );
}
