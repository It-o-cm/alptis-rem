// Presentation helpers for distributors. The server decides each distributor's commercial type and
// each per-entity / production-code lifecycle status; these maps only turn the stored codes into the
// French labels the operator reads (ADM-03), so no technical code shows on screen.

/** French labels of the distributor commercial types (section 5.1 glossary). */
const DISTRIBUTOR_TYPE_LABELS: Record<string, string> = {
  INDEPENDENT: 'Indépendant',
  GROUP_MEMBER: 'Membre de groupement',
  REMOTE_SALES: 'Vente à distance',
  AGENT: 'Mandataire',
};

/** French labels of the distributor / production-code lifecycle statuses (section 5.1). */
const PARTNER_STATUS_LABELS: Record<string, string> = {
  CANDIDATE: 'En cours de codification',
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  TERMINATED: 'Résilié',
};

/** French label of a distributor type, a dash for none, or the raw code when unknown. */
export function distributorTypeLabel(type: string | null): string {
  if (type === null) return '—';
  return DISTRIBUTOR_TYPE_LABELS[type] ?? type;
}

/** French label of a distributor / production-code status, a dash for none, or the raw code when unknown. */
export function partnerStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return PARTNER_STATUS_LABELS[status] ?? status;
}
