// Presentation helpers for commission lines. The server decides every nature, direction, status and
// amount; these maps only turn the stored codes into the French labels the operator reads (ADM-03).

/** French labels of the commission-line natures (section 5.7). */
const NATURE_LABELS: Record<string, string> = {
  LINEAR: 'Linéaire',
  ACQUISITION: 'Acquisition',
  SERVICING: 'Gestion',
  ADVANCE: 'Escompte',
  ADVANCE_ADJUSTMENT: 'Régularisation d’escompte',
  CLAWBACK: 'Reprise',
  RCD: 'Développement (RCD)',
  RCP: 'Portefeuille (RCP)',
  COMPLEMENT: 'Complément retraite',
  OUTSTANDING: 'Sur encours',
  ARBITRAGE: 'Arbitrage',
  RETROCESSION: 'Rétrocession',
  FEE: 'Frais de dossier',
  EXTERNAL: 'Commission externe',
  MANUAL: 'Ajustement manuel',
  TRANSFER: 'Compensation',
  INSURER: 'Commission reçue',
};

/** French labels of the line statuses (section 5.7). */
const STATUS_LABELS: Record<string, string> = {
  FORECAST: 'Prévisionnelle',
  EARNED: 'Acquise',
  PAYABLE: 'À payer',
  PAID: 'Payée',
  OFFSET: 'Imputée sur escompte',
  CANCELLED: 'Annulée',
};

/** French labels of the commission direction. */
const DIRECTION_LABELS: Record<string, string> = {
  INBOUND: 'Entrant',
  OUTBOUND: 'Sortant',
};

/** French label of a line nature, a dash for none, or the raw code when unknown. */
export function lineNatureLabel(nature: string | null): string {
  if (nature === null) return '—';
  return NATURE_LABELS[nature] ?? nature;
}

/** French label of a line status, a dash for none, or the raw code when unknown. */
export function lineStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return STATUS_LABELS[status] ?? status;
}

/** French label of a commission direction, a dash for none, or the raw code when unknown. */
export function directionLabel(direction: string | null): string {
  if (direction === null) return '—';
  return DIRECTION_LABELS[direction] ?? direction;
}

/** The commission-line natures offered as a filter, with their French labels (ADM-03). */
export const NATURE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = Object.entries(
  NATURE_LABELS,
).map(([value, label]) => ({ value, label }));
